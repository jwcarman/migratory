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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
   * Finds every declaration on a class path: one per jar or directory holding {@value #LOCATION}.
   *
   * @param classLoader the class loader whose class path is searched
   * @return the declared migrations, by name
   * @throws MigratoryException when a declaration is invalid, its changelog does not exist, or two
   *     declare the same name
   */
  public static List<Migration> discover(ClassLoader classLoader) {
    Map<String, Migration> byName = new HashMap<>();
    for (URL url : urls(classLoader)) {
      String source = url.toString();
      Migration migration = parse(load(url), source);
      if (classLoader.getResource(migration.resourceName()) == null) {
        throw invalid(source, "changelog " + migration.changelog() + " does not exist");
      }
      Migration existing = byName.putIfAbsent(migration.name(), migration);
      if (existing != null) {
        throw new MigratoryException(
            "Two migrations are named '"
                + migration.name()
                + "': "
                + existing.source()
                + " and "
                + source);
      }
    }
    List<Migration> migrations = new ArrayList<>(byName.values());
    migrations.sort(Comparator.comparing(Migration::name));
    return List.copyOf(migrations);
  }

  private static Set<URL> urls(ClassLoader classLoader) {
    Set<URL> urls = new LinkedHashSet<>();
    try {
      Enumeration<URL> found = classLoader.getResources(LOCATION);
      urls.addAll(Collections.list(found));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot search the class path for " + LOCATION, e);
    }
    return urls;
  }

  private static Properties load(URL url) {
    Properties properties = new Properties();
    try (InputStream in = url.openStream()) {
      properties.load(in);
    } catch (IOException e) {
      throw new MigratoryException("Cannot read migration declaration " + url, e);
    }
    return properties;
  }

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
