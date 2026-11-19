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
package org.jwcarman.migratory.autoconfigure;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.List;

import org.jwcarman.migratory.core.Migration;
import org.jwcarman.migratory.core.MigrationDeclarations;
import org.jwcarman.migratory.core.MigrationSorter;
import org.jwcarman.migratory.core.ResolvedMigrations;
import org.springframework.aot.AotDetector;

/** Finds the migrations to run, in the order they run. */
public final class Migrations {

  private Migrations() {}

  /**
   * Loads the migrations: in a native image (or any run on AOT-generated artifacts), the sorted
   * list written at build time; otherwise every declaration on the class path, sorted.
   *
   * @param classLoader the application's class loader
   * @return the migrations in the order they run
   */
  public static List<Migration> load(ClassLoader classLoader) {
    if (AotDetector.useGeneratedArtifacts()) {
      URL resolved = classLoader.getResource(ResolvedMigrations.LOCATION);
      if (resolved != null) {
        try (InputStream in = resolved.openStream()) {
          return ResolvedMigrations.read(in);
        } catch (IOException e) {
          throw new UncheckedIOException("Cannot read " + resolved, e);
        }
      }
    }
    return MigrationSorter.sort(MigrationDeclarations.discover(classLoader));
  }
}
