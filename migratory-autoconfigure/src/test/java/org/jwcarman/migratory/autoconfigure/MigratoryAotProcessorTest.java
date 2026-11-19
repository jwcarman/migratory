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
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jwcarman.migratory.core.Migration;
import org.jwcarman.migratory.core.MigrationDeclarations;
import org.jwcarman.migratory.core.MigratoryException;
import org.jwcarman.migratory.core.ResolvedMigrations;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.aot.test.generate.TestGenerationContext;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

class MigratoryAotProcessorTest {

  @TempDir Path temp;

  private final MigratoryAotProcessor processor = new MigratoryAotProcessor();

  @Test
  void writesSortedListAsResource() throws IOException {
    try (URLClassLoader loader = twoLibraries()) {
      TestGenerationContext generation = new TestGenerationContext();

      contribution(loader).applyTo(generation, mock(BeanFactoryInitializationCode.class));

      String content =
          generation
              .getGeneratedFiles()
              .getGeneratedFileContent(Kind.RESOURCE, ResolvedMigrations.LOCATION);
      assertThat(
              ResolvedMigrations.read(
                  new ByteArrayInputStream(content.getBytes(StandardCharsets.ISO_8859_1))))
          .extracting(Migration::name)
          .containsExactly("a", "b");
      assertThat(RuntimeHintsPredicates.resource().forResource(ResolvedMigrations.LOCATION))
          .accepts(generation.getRuntimeHints());
    }
  }

  @Test
  void registersChangelogDirectories() throws IOException {
    try (URLClassLoader loader = twoLibraries()) {
      TestGenerationContext generation = new TestGenerationContext();

      contribution(loader).applyTo(generation, mock(BeanFactoryInitializationCode.class));

      assertThat(RuntimeHintsPredicates.resource().forResource("libs/a/changelog.xml"))
          .accepts(generation.getRuntimeHints());
      assertThat(RuntimeHintsPredicates.resource().forResource("libs/a/nested/more.xml"))
          .accepts(generation.getRuntimeHints());
      assertThat(RuntimeHintsPredicates.resource().forResource("libs/b/changelog.xml"))
          .accepts(generation.getRuntimeHints());
    }
  }

  @Test
  void cycleFailsTheBuild() throws IOException {
    library("a", "name=a\nchangelog=classpath:libs/a/changelog.xml\nafter=b\n", "libs/a");
    library("b", "name=b\nchangelog=classpath:libs/b/changelog.xml\nafter=a\n", "libs/b");
    try (URLClassLoader loader = loader("a", "b")) {
      assertThatThrownBy(() -> processor.processAheadOfTime(beanFactory(loader, true)))
          .isInstanceOf(MigratoryException.class)
          .hasMessageContaining("a, b");
    }
  }

  @Test
  void noBeanNoContribution() throws IOException {
    try (URLClassLoader loader = twoLibraries()) {
      assertThat(processor.processAheadOfTime(beanFactory(loader, false))).isNull();
    }
  }

  @Test
  void readsResolvedListInAotMode() throws IOException {
    Path root = Files.createDirectories(temp.resolve("resolved"));
    Path file = root.resolve(ResolvedMigrations.LOCATION);
    Files.createDirectories(file.getParent());
    Files.writeString(
        file,
        ResolvedMigrations.write(
            List.of(
                new Migration(
                    "baked", "classpath:libs/baked/changelog.xml", Set.of(), Set.of(), "build"))),
        StandardCharsets.ISO_8859_1);
    try (URLClassLoader loader = new URLClassLoader(new URL[] {root.toUri().toURL()}, null)) {
      assertThat(Migrations.load(loader)).isEmpty();
      System.setProperty("spring.aot.enabled", "true");
      try {
        assertThat(Migrations.load(loader)).extracting(Migration::name).containsExactly("baked");
      } finally {
        System.clearProperty("spring.aot.enabled");
      }
    }
  }

  private URLClassLoader twoLibraries() throws IOException {
    library("a", "name=a\nchangelog=classpath:libs/a/changelog.xml\n", "libs/a");
    library("b", "name=b\nchangelog=classpath:libs/b/changelog.xml\nafter=a\n", "libs/b");
    Files.writeString(
        Files.createDirectories(temp.resolve("a/libs/a/nested")).resolve("more.xml"),
        "<databaseChangeLog/>");
    return loader("a", "b");
  }

  private BeanFactoryInitializationAotContribution contribution(ClassLoader loader) {
    BeanFactoryInitializationAotContribution contribution =
        processor.processAheadOfTime(beanFactory(loader, true));
    assertThat(contribution).isNotNull();
    return contribution;
  }

  private static DefaultListableBeanFactory beanFactory(ClassLoader loader, boolean withRunner) {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.setBeanClassLoader(loader);
    if (withRunner) {
      beanFactory.registerBeanDefinition(
          "migratoryLiquibase", new RootBeanDefinition(MigratoryLiquibase.class));
    }
    return beanFactory;
  }

  private void library(String directory, String declaration, String changelogDirectory)
      throws IOException {
    Path root = temp.resolve(directory);
    Path file = root.resolve(MigrationDeclarations.LOCATION);
    Files.createDirectories(file.getParent());
    Files.writeString(file, declaration);
    Path changelogs = Files.createDirectories(root.resolve(changelogDirectory));
    Files.writeString(changelogs.resolve("changelog.xml"), "<databaseChangeLog/>");
  }

  private URLClassLoader loader(String... directories) throws IOException {
    URL[] urls = new URL[directories.length];
    for (int i = 0; i < directories.length; i++) {
      urls[i] = temp.resolve(directories[i]).toUri().toURL();
    }
    return new URLClassLoader(urls, null);
  }
}
