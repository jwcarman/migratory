/*
 * Copyright © 2026 James Carman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jwcarman.migratory.core;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Reads the migrations libraries declare in {@value #LOCATION}. */
public final class MigrationDeclarations {

  /** Where a library declares its migration, one file per jar. */
  public static final String LOCATION = "META-INF/migratory.properties";

  static final String CLASSPATH_PREFIX = "classpath:";

  private static final String NAME = "name";
  private static final String CHANGELOG = "changelog";
  private static final String AFTER = "after";
  private static final String BEFORE = "before";
  private static final Set<String> KEYS = Set.of(NAME, CHANGELOG, AFTER, BEFORE);
  private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

  private MigrationDeclarations() {}

  /**
   * Reads one declaration.
   *
   * @param properties the declaration file's contents
   * @param source where the file came from, named in error messages
   * @return the declared migration
   * @throws MigratoryException when the declaration is incomplete or invalid
   */
  public static Migration parse(Properties properties, String source) {
    Set<String> unknown = new TreeSet<>(properties.stringPropertyNames());
    unknown.removeAll(KEYS);
    if (!unknown.isEmpty()) {
      throw invalid(
          source, "unknown key(s) " + unknown + "; the keys are name, changelog, after and before");
    }
    String name = required(properties, NAME, source);
    if (!NAME_PATTERN.matcher(name).matches()) {
      throw invalid(
          source,
          "name '"
              + name
              + "' must be lowercase letters and digits, in words joined by single hyphens");
    }
    String changelog = required(properties, CHANGELOG, source);
    if (!changelog.startsWith(CLASSPATH_PREFIX)) {
      throw invalid(source, "changelog '" + changelog + "' must start with classpath:");
    }
    String path = changelog.substring(CLASSPATH_PREFIX.length());
    if (path.isEmpty() || path.startsWith("/")) {
      throw invalid(
          source,
          "changelog path '"
              + path
              + "' must not be empty or start with /: Liquibase records the path as written,"
              + " and an include of the same changelog writes it without the slash");
    }
    Set<String> after = names(properties, AFTER);
    Set<String> before = names(properties, BEFORE);
    if (after.contains(name) || before.contains(name)) {
      throw invalid(source, "migration '" + name + "' cannot run after or before itself");
    }
    return new Migration(name, changelog, after, before, source);
  }

  private static String required(Properties properties, String key, String source) {
    String value = properties.getProperty(key, "").strip();
    if (value.isEmpty()) {
      throw invalid(source, "'" + key + "' is required");
    }
    return value;
  }

  private static Set<String> names(Properties properties, String key) {
    Set<String> names = new LinkedHashSet<>();
    Arrays.stream(properties.getProperty(key, "").split(","))
        .map(String::strip)
        .filter(value -> !value.isEmpty())
        .forEach(names::add);
    return names;
  }

  private static MigratoryException invalid(String source, String reason) {
    return new MigratoryException("Invalid migration declaration " + source + ": " + reason);
  }
}
