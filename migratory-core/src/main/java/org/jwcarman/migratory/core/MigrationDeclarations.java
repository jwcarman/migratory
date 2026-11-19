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
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

  private static Collection<URL> urls(ClassLoader classLoader) {
    // keyed by the URL's text: URL.equals may resolve host names
    Map<String, URL> urls = new LinkedHashMap<>();
    try {
      Enumeration<URL> found = classLoader.getResources(LOCATION);
      Collections.list(found).forEach(url -> urls.putIfAbsent(url.toExternalForm(), url));
    } catch (IOException e) {
      throw new MigratoryException("Cannot search the class path for " + LOCATION, e);
    }
    return urls.values();
  }

  private static Properties load(URL url) {
    Set<String> repeated = new TreeSet<>();
    // Properties keeps the last of a repeated key; a declaration with one is refused instead
    Properties properties =
        new Properties() {
          @Override
          public synchronized Object put(Object key, Object value) {
            if (containsKey(key)) {
              repeated.add(String.valueOf(key));
            }
            return super.put(key, value);
          }
        };
    try (InputStream in = url.openStream()) {
      properties.load(in);
    } catch (IOException | IllegalArgumentException e) {
      throw new MigratoryException("Cannot read migration declaration " + url, e);
    }
    if (!repeated.isEmpty()) {
      throw invalid(
          url.toString(),
          repeated.stream().map(key -> "'" + key + "'").collect(Collectors.joining(", "))
              + " written twice");
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
    if (path.isEmpty()) {
      throw invalid(source, "changelog '" + changelog + "' has no path after classpath:");
    }
    if (path.startsWith("/")) {
      throw invalid(
          source, "changelog '" + changelog + "' must be written without the leading slash");
    }
    String[] segments = path.split("/", -1);
    if (Arrays.stream(segments).anyMatch(String::isEmpty)) {
      throw invalid(source, "changelog '" + changelog + "' has an empty segment");
    }
    if (Arrays.stream(segments).anyMatch(part -> part.equals(".") || part.equals(".."))) {
      throw invalid(source, "changelog '" + changelog + "' must not contain . or .. segments");
    }
    if (!path.contains("/")) {
      throw invalid(
          source,
          "changelog '"
              + changelog
              + "' must be in a directory of the library's own, such as com/example/db/: a native"
              + " image includes everything under the changelog's directory");
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
