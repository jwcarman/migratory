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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jwcarman.migratory.core.Migration;
import org.jwcarman.migratory.core.ResolvedMigrations;
import org.springframework.aot.AotDetector;
import org.springframework.core.SpringProperties;

/** On AOT-generated artifacts, migratory reads the baked list, and discovers without one. */
class MigrationsTest {

  @AfterEach
  void jvmAgain() {
    SpringProperties.setProperty(AotDetector.AOT_ENABLED, null);
  }

  @Test
  void onGeneratedArtifactsWithoutABakedListDiscovers() {
    SpringProperties.setProperty(AotDetector.AOT_ENABLED, "true");

    assertThat(Migrations.load(getClass().getClassLoader()))
        .extracting(Migration::name)
        .containsExactly("inventory", "orders");
  }

  @Test
  void onGeneratedArtifactsReadsTheBakedList(@TempDir Path directory) throws IOException {
    // the baked order is the reverse of discovery's, so only reading the list can produce it
    List<Migration> reversed = Migrations.load(getClass().getClassLoader()).reversed();
    Path baked = directory.resolve("resolved-migrations.properties");
    Files.writeString(baked, ResolvedMigrations.write(reversed));
    SpringProperties.setProperty(AotDetector.AOT_ENABLED, "true");

    assertThat(Migrations.load(serving(baked.toUri().toURL())))
        .extracting(Migration::name)
        .containsExactly("orders", "inventory");
  }

  @Test
  void anUnreadableBakedListFailsNamingIt(@TempDir Path directory) {
    SpringProperties.setProperty(AotDetector.AOT_ENABLED, "true");
    ClassLoader loader = serving(toUrl(directory.resolve("gone.properties").toUri()));

    assertThatThrownBy(() -> Migrations.load(loader))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("gone.properties");
  }

  private ClassLoader serving(URL bakedList) {
    return new ClassLoader(getClass().getClassLoader()) {
      @Override
      public URL getResource(String name) {
        return ResolvedMigrations.LOCATION.equals(name) ? bakedList : super.getResource(name);
      }
    };
  }

  private static URL toUrl(URI uri) {
    try {
      return uri.toURL();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
