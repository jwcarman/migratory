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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jwcarman.migratory.core.Migration;
import org.jwcarman.migratory.core.MigrationDeclarations;
import org.jwcarman.migratory.core.MigrationSorter;
import org.jwcarman.migratory.core.ResolvedMigrations;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.hint.ResourceHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.io.ByteArrayResource;

/**
 * Bakes the sorted migrations into a native image at build time.
 *
 * <p>Spring's ahead-of-time step runs with the application's full class path, so it sees every
 * declaring library the image will hold. This processor discovers, validates and sorts their
 * declarations there (failing the build on a bad declaration or a cycle), writes the sorted list to
 * {@value ResolvedMigrations#LOCATION}, and registers that file and everything under each declared
 * changelog's directory as resources. The image then never searches its class path for
 * declarations.
 */
public final class MigratoryAotProcessor implements BeanFactoryInitializationAotProcessor {

  /** Creates the processor; Spring loads it from {@code META-INF/spring/aot.factories}. */
  public MigratoryAotProcessor() {
    // no state
  }

  private static final String LIQUIBASE_SCHEMAS = "www.liquibase.org/xml/ns/**/*.xsd";

  @Override
  public BeanFactoryInitializationAotContribution processAheadOfTime(
      ConfigurableListableBeanFactory beanFactory) {
    // the runner is defined only when Liquibase is on the class path
    ClassLoader classLoader = beanFactory.getBeanClassLoader();
    if (classLoader == null
        || beanFactory.getBeanNamesForType(MigratoryLiquibase.class, true, false).length == 0) {
      return null;
    }
    List<Migration> sorted = MigrationSorter.sort(MigrationDeclarations.discover(classLoader));
    return (generationContext, code) -> {
      // process-test-aot gives every test context's processors one set of generated files, and
      // each writes the same list
      generationContext
          .getGeneratedFiles()
          .handleFile(
              Kind.RESOURCE,
              ResolvedMigrations.LOCATION,
              file -> {
                if (!file.exists()) {
                  file.create(
                      new ByteArrayResource(
                          ResolvedMigrations.write(sorted).getBytes(StandardCharsets.UTF_8)));
                }
              });
      ResourceHints resources = generationContext.getRuntimeHints().resources();
      resources.registerPattern(ResolvedMigrations.LOCATION);
      // Liquibase's own schemas, which changelogs (the generated root among them) name: Liquibase
      // reads them from its jar and fails to parse a changelog whose schema it cannot find there.
      // GraalVM's metadata for Liquibase lists them too, but only for the versions it covers.
      resources.registerPattern(LIQUIBASE_SCHEMAS);
      for (Migration migration : sorted) {
        String path = migration.resourceName();
        int slash = path.lastIndexOf('/');
        resources.registerPattern(slash < 0 ? path : path.substring(0, slash) + "/**");
      }
    };
  }
}
