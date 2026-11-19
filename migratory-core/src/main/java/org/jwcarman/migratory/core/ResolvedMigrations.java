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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * The sorted migrations, written once at build time for a native image, so the image never searches
 * its class path for declarations.
 */
public final class ResolvedMigrations {

  /** Where the sorted list is written in a native image's resources. */
  public static final String LOCATION = "META-INF/migratory/resolved-migrations.properties";

  private static final String COUNT = "count";

  private ResolvedMigrations() {}

  /**
   * Writes sorted migrations as the bytes of the file {@link #read(InputStream)} reads. Every
   * character outside printable ASCII is written as a Unicode escape, so the file is plain ASCII
   * and reads back exactly whatever characters a changelog's path or a declaration's location
   * holds.
   *
   * @param sorted the migrations in the order they run
   * @return the file's bytes
   */
  public static byte[] write(List<Migration> sorted) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    write(sorted, out);
    return out.toByteArray();
  }

  /**
   * Writes sorted migrations to a stream.
   *
   * @param sorted the migrations in the order they run
   * @param out where the file's bytes go
   * @throws UncheckedIOException when the stream fails
   */
  static void write(List<Migration> sorted, OutputStream out) {
    Properties properties = new Properties();
    properties.setProperty(COUNT, Integer.toString(sorted.size()));
    for (int i = 0; i < sorted.size(); i++) {
      Migration migration = sorted.get(i);
      properties.setProperty(i + ".name", migration.name());
      properties.setProperty(i + ".changelog", migration.changelog());
      properties.setProperty(i + ".source", migration.source());
    }
    try {
      properties.store(out, "Written by migratory at build time; the order the migrations run");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads sorted migrations written by {@link #write(List)}. Their {@code after} and {@code before}
   * are empty: the order already honours them.
   *
   * @param in the file's contents
   * @return the migrations in the order they run
   * @throws MigratoryException when the file cannot be read
   */
  public static List<Migration> read(InputStream in) {
    Properties properties = new Properties();
    try {
      properties.load(in);
    } catch (IOException | IllegalArgumentException e) {
      throw new MigratoryException("Cannot read " + LOCATION, e);
    }
    try {
      int count = Integer.parseInt(properties.getProperty(COUNT, "0"));
      List<Migration> migrations = new ArrayList<>(count);
      for (int i = 0; i < count; i++) {
        migrations.add(
            new Migration(
                required(properties, i + ".name"),
                required(properties, i + ".changelog"),
                Set.of(),
                Set.of(),
                required(properties, i + ".source")));
      }
      return List.copyOf(migrations);
    } catch (IllegalArgumentException e) {
      throw new MigratoryException(LOCATION + " is damaged", e);
    }
  }

  private static String required(Properties properties, String key) {
    String value = properties.getProperty(key);
    if (value == null) {
      throw new IllegalArgumentException(key + " is missing");
    }
    return value;
  }
}
