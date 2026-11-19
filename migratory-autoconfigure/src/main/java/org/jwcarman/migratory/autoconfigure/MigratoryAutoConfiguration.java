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
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.jwcarman.migratory.core.Migration;
import org.jwcarman.migratory.core.MigrationDeclarations;
import org.jwcarman.migratory.core.MigratoryException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseConnectionDetails;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseDataSource;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.util.StringUtils;

/**
 * Runs the migrations libraries declare in {@code META-INF/migratory.properties} at startup, before
 * the application's own Liquibase.
 *
 * <p>{@link MigratoryLiquibase} exists whenever Liquibase and a data source do; it reads {@code
 * migratory.enabled} when it runs, so the switch behaves the same in a native image, where
 * conditions are decided at build time.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@EnableConfigurationProperties(MigratoryProperties.class)
public class MigratoryAutoConfiguration {

  private static final String ENABLED = "migratory.enabled";

  /** Creates the auto-configuration; Spring Boot instantiates it. */
  public MigratoryAutoConfiguration() {
    // no state
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(
      name = {
        "liquibase.integration.spring.SpringLiquibase",
        "org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties"
      })
  @Conditional(MigrationDatabaseCondition.class)
  @EnableConfigurationProperties(LiquibaseProperties.class)
  @Import(DatabaseInitializationDependencyConfigurer.class)
  static class LiquibaseMigrationsConfiguration {

    // eager under spring.main.lazy-initialization too: Spring Boot leaves its own Liquibase lazy,
    // but the libraries' tables are created at startup, so their first query finds them
    @Bean
    @Lazy(false)
    MigratoryLiquibase migratoryLiquibase(
        ObjectProvider<DataSource> dataSource,
        @LiquibaseDataSource ObjectProvider<DataSource> liquibaseDataSource,
        ObjectProvider<LiquibaseConnectionDetails> connectionDetails,
        LiquibaseProperties liquibase,
        MigratoryProperties properties,
        ResourceLoader resourceLoader) {
      // with migratory off, the declarations are not even read: a broken one in a third-party jar
      // must not stop an application that includes the changelogs itself
      return new MigratoryLiquibase(
          properties.isEnabled() ? Migrations.load(resourceLoader.getClassLoader()) : List.of(),
          migrationDataSource(
              liquibaseDataSource.getIfAvailable(),
              dataSource,
              connectionDetails.getIfAvailable(() -> new PropertiesConnectionDetails(liquibase))),
          liquibase,
          properties,
          resourceLoader);
    }

    /**
     * The data source Spring Boot's Liquibase migrates the application's tables in, chosen in the
     * same order as Boot's {@code LiquibaseAutoConfiguration}.
     */
    private static DataSource migrationDataSource(
        DataSource liquibaseDataSource,
        ObjectProvider<DataSource> dataSource,
        LiquibaseConnectionDetails connectionDetails) {
      if (liquibaseDataSource != null) {
        return liquibaseDataSource;
      }
      String url = connectionDetails.getJdbcUrl();
      if (url != null) {
        DataSourceBuilder<?> builder =
            DataSourceBuilder.create().type(SimpleDriverDataSource.class).url(url);
        applyConnectionDetails(connectionDetails, builder);
        return builder.build();
      }
      DataSource primary = dataSource.getIfUnique();
      if (primary == null) {
        throw new MigratoryException(
            "migratory found no single DataSource to migrate: mark one @Primary or"
                + " @LiquibaseDataSource, or set spring.liquibase.url");
      }
      if (connectionDetails.getUsername() != null) {
        DataSourceBuilder<?> builder =
            DataSourceBuilder.derivedFrom(primary).type(SimpleDriverDataSource.class);
        applyConnectionDetails(connectionDetails, builder);
        return builder.build();
      }
      return primary;
    }

    private static void applyConnectionDetails(
        LiquibaseConnectionDetails connectionDetails, DataSourceBuilder<?> builder) {
      builder.username(connectionDetails.getUsername());
      builder.password(connectionDetails.getPassword());
      String driverClassName = connectionDetails.getDriverClassName();
      if (StringUtils.hasText(driverClassName)) {
        builder.driverClassName(driverClassName);
      }
    }
  }

  /**
   * Fails startup when libraries declare migrations but Liquibase is not on the classpath, since
   * their tables would never be created, unless {@code migratory.enabled} is false.
   */
  @Configuration(proxyBeanMethods = false)
  @Conditional({LiquibaseMissingCondition.class, DeclarationsPresentCondition.class})
  static class LiquibaseRequiredConfiguration {

    // exists only to fail startup, so it must be created even under lazy initialization
    @Bean
    @Lazy(false)
    Object migratoryLiquibaseRequired(Environment environment, ResourceLoader resourceLoader) {
      if (!environment.getProperty(ENABLED, Boolean.class, true)) {
        return ENABLED + "=false";
      }
      String names =
          Migrations.load(resourceLoader.getClassLoader()).stream()
              .map(Migration::name)
              .collect(Collectors.joining(", "));
      throw new MigratoryException(
          "migratory found migrations ("
              + names
              + ") but Liquibase is not on the classpath: add spring-boot-starter-liquibase, or"
              + " set migratory.enabled=false and include their changelogs yourself.");
    }
  }

  /**
   * Matches when there is a database to migrate, as Spring Boot's Liquibase decides: a {@code
   * DataSource}, JDBC connection details, or {@code spring.liquibase.url}.
   */
  static final class MigrationDatabaseCondition extends AnyNestedCondition {

    MigrationDatabaseCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnBean(DataSource.class)
    static class DataSourceBean {}

    @ConditionalOnBean(JdbcConnectionDetails.class)
    static class JdbcConnectionDetailsBean {}

    @ConditionalOnProperty("spring.liquibase.url")
    static class LiquibaseUrl {}
  }

  /** Matches when Liquibase, or Spring Boot's support for it, is missing. */
  static final class LiquibaseMissingCondition extends AnyNestedCondition {

    LiquibaseMissingCondition() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnMissingClass("liquibase.integration.spring.SpringLiquibase")
    static class LiquibaseCoreMissing {}

    @ConditionalOnMissingClass(
        "org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties")
    static class BootSupportMissing {}
  }

  /** Matches when at least one library declares a migration. */
  static final class DeclarationsPresentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      ClassLoader classLoader = context.getClassLoader();
      if (classLoader == null) {
        return false;
      }
      try {
        return classLoader.getResources(MigrationDeclarations.LOCATION).hasMoreElements();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  /**
   * {@code spring.liquibase.*} as connection details, as Spring Boot reads them, for when Boot's
   * Liquibase configuration stood down and registered none.
   */
  private static final class PropertiesConnectionDetails implements LiquibaseConnectionDetails {

    private final LiquibaseProperties properties;

    private PropertiesConnectionDetails(LiquibaseProperties properties) {
      this.properties = properties;
    }

    @Override
    public String getUsername() {
      return properties.getUser();
    }

    @Override
    public String getPassword() {
      return properties.getPassword();
    }

    @Override
    public String getJdbcUrl() {
      return properties.getUrl();
    }

    @Override
    public String getDriverClassName() {
      String driverClassName = properties.getDriverClassName();
      return driverClassName != null
          ? driverClassName
          : LiquibaseConnectionDetails.super.getDriverClassName();
    }
  }
}
