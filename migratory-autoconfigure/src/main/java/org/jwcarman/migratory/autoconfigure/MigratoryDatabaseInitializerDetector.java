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

import java.util.Set;

import org.springframework.boot.sql.init.dependency.AbstractBeansOfTypeDatabaseInitializerDetector;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;

/**
 * Finds {@link MigratoryLiquibase} as a database initializer, ordered ahead of every other, so
 * every {@code SpringLiquibase} bean, JPA, and each bean that depends on database initialization
 * waits for the declared migrations.
 */
public final class MigratoryDatabaseInitializerDetector
    extends AbstractBeansOfTypeDatabaseInitializerDetector {

  private static final String SPRING_LIQUIBASE = "liquibase.integration.spring.SpringLiquibase";

  private final ClassLoader classLoader;

  /** Creates the detector; Spring Boot loads it from {@code META-INF/spring.factories}. */
  public MigratoryDatabaseInitializerDetector() {
    this(MigratoryDatabaseInitializerDetector.class.getClassLoader());
  }

  MigratoryDatabaseInitializerDetector(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  @Override
  protected Set<Class<?>> getDatabaseInitializerBeanTypes() {
    if (ClassUtils.isPresent(SPRING_LIQUIBASE, classLoader)) {
      return Set.of(MigratoryLiquibase.class);
    }
    return Set.of();
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
