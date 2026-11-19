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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationDeclarationsDiscoverTest {

  @TempDir Path temp;

  @Test
  void findsOnePerDirectory() throws IOException {
    Path a = library("a", "name=a\nchangelog=classpath:a/changelog.xml\n", "a/changelog.xml");
    Path b =
        library("b", "name=b\nchangelog=classpath:b/changelog.xml\nafter=a\n", "b/changelog.xml");

    try (URLClassLoader loader = loader(b, a)) {
      assertThat(MigrationDeclarations.discover(loader))
          .extracting(Migration::name)
          .containsExactly("a", "b");
    }
  }

  @Test
  void noDeclarationsIsEmpty() throws IOException {
    try (URLClassLoader loader = loader(Files.createDirectories(temp.resolve("empty")))) {
      assertThat(MigrationDeclarations.discover(loader)).isEmpty();
    }
  }

  @Test
  void sameUrlTwiceCountsOnce() throws IOException {
    Path a = library("a", "name=a\nchangelog=classpath:a/changelog.xml\n", "a/changelog.xml");

    try (URLClassLoader loader = loader(a, a)) {
      assertThat(MigrationDeclarations.discover(loader)).hasSize(1);
    }
  }

  @Test
  void duplicateNameFailsNamingBothSources() throws IOException {
    Path one = library("one", "name=a\nchangelog=classpath:a/changelog.xml\n", "a/changelog.xml");
    Path two = library("two", "name=a\nchangelog=classpath:a/changelog.xml\n", "a/changelog.xml");

    try (URLClassLoader loader = loader(one, two)) {
      assertThatThrownBy(() -> MigrationDeclarations.discover(loader))
          .isInstanceOf(MigratoryException.class)
          .hasMessageContaining("'a'")
          .hasMessageContaining(one.toUri().toURL().toString())
          .hasMessageContaining(two.toUri().toURL().toString());
    }
  }

  @Test
  void missingChangelogFails() throws IOException {
    Path a = library("a", "name=a\nchangelog=classpath:a/missing.xml\n", "a/changelog.xml");

    try (URLClassLoader loader = loader(a)) {
      assertThatThrownBy(() -> MigrationDeclarations.discover(loader))
          .isInstanceOf(MigratoryException.class)
          .hasMessageContaining("classpath:a/missing.xml")
          .hasMessageContaining(a.toUri().toURL().toString());
    }
  }

  @Test
  void keyWrittenTwiceFails() throws IOException {
    Path a =
        library("a", "name=a\nchangelog=classpath:a/changelog.xml\nname=b\n", "a/changelog.xml");

    try (URLClassLoader loader = loader(a)) {
      assertThatThrownBy(() -> MigrationDeclarations.discover(loader))
          .isInstanceOf(MigratoryException.class)
          .hasMessageContaining("'name'")
          .hasMessageContaining("twice");
    }
  }

  @Test
  void classPathSearchFailureIsAMigratoryException() {
    ClassLoader broken =
        new ClassLoader(null) {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            throw new IOException("unreadable");
          }
        };

    assertThatThrownBy(() -> MigrationDeclarations.discover(broken))
        .isInstanceOf(MigratoryException.class)
        .hasMessageContaining(MigrationDeclarations.LOCATION);
  }

  @Test
  void malformedEscapeFailsNamingTheFile() throws IOException {
    Path a = library("a", "name=\\uZZZZ\nchangelog=classpath:a/changelog.xml\n", "a/changelog.xml");

    try (URLClassLoader loader = loader(a)) {
      assertThatThrownBy(() -> MigrationDeclarations.discover(loader))
          .isInstanceOf(MigratoryException.class)
          .hasMessageContaining(a.toUri().toURL().toString());
    }
  }

  private Path library(String directory, String declaration, String changelog) throws IOException {
    Path root = temp.resolve(directory);
    Path file = root.resolve(MigrationDeclarations.LOCATION);
    Files.createDirectories(file.getParent());
    Files.writeString(file, declaration);
    Path log = root.resolve(changelog);
    Files.createDirectories(log.getParent());
    Files.writeString(log, "<databaseChangeLog/>");
    return root;
  }

  private static URLClassLoader loader(Path... roots) throws IOException {
    URL[] urls = new URL[roots.length];
    for (int i = 0; i < roots.length; i++) {
      urls[i] = roots[i].toUri().toURL();
    }
    return new URLClassLoader(urls, null);
  }
}
