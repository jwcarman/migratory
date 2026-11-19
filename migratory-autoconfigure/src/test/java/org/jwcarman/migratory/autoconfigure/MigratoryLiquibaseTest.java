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

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jwcarman.migratory.autoconfigure.MigratoryAutoConfiguration.LiquibaseRequiredConfiguration.LiquibaseNotRequired;
import org.jwcarman.migratory.core.Migration;
import org.jwcarman.migratory.core.MigrationDeclarations;
import org.jwcarman.migratory.core.MigratoryException;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseDataSource;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import liquibase.UpdateSummaryEnum;
import liquibase.UpdateSummaryOutputEnum;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.ui.UIServiceEnum;

/**
 * The migrations declared on the test class path (inventory, then orders after it) run before the
 * application's changelog, whose table references theirs, on PostgreSQL.
 */
class MigratoryLiquibaseTest {

  private static final String EMPTY_CHANGELOG = "classpath:app-changelog/empty.yaml";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  DataSourceAutoConfiguration.class,
                  LiquibaseAutoConfiguration.class,
                  MigratoryAutoConfiguration.class))
          .withPropertyValues(MigratoryTestPostgres.freshDatabaseProperties());

  @Test
  void runsMigrationsInOrderBeforeApplicationLiquibase() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed().hasSingleBean(MigratoryLiquibase.class);
          assertThat(changesets(context)).containsExactly("inventory-1", "orders-1", "app-1");
          assertThat(context.getBean(MigratoryLiquibase.class).getMigrations())
              .extracting(Migration::name)
              .containsExactly("inventory", "orders");
        });
  }

  @Test
  void bootsSpringLiquibaseDependsOnMigratory() {
    runner.run(
        context ->
            assertThat(context.getBeanFactory().getBeanDefinition("liquibase").getDependsOn())
                .contains(context.getBeanNamesForType(MigratoryLiquibase.class)[0]));
  }

  @Test
  void anApplicationsOwnSpringLiquibaseBeanAlsoWaits() {
    runner
        .withUserConfiguration(OwnLiquibase.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBeanFactory().getBeanDefinition("appLiquibase").getDependsOn())
                  .contains(context.getBeanNamesForType(MigratoryLiquibase.class)[0]);
              assertThat(changesets(context)).containsExactly("inventory-1", "orders-1", "app-1");
            });
  }

  @Test
  void recordsSamePathsAsAHandInclude() {
    List<String> byMigratory =
        filenames(runner.withPropertyValues("spring.liquibase.change-log=" + EMPTY_CHANGELOG));
    List<String> byHand =
        filenames(
            runner.withPropertyValues(
                "migratory.enabled=false",
                "spring.liquibase.change-log=classpath:app-changelog/includes-both.yaml"));

    assertThat(byMigratory)
        .containsExactly("test-libs/inventory/changelog.xml", "test-libs/orders/changelog.xml")
        .isEqualTo(byHand);
  }

  @Test
  void handIncludedChangelogRunsNothingTwice() {
    runner
        .withPropertyValues(
            "spring.liquibase.change-log=classpath:app-changelog/includes-inventory.yaml")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(changesets(context)).containsExactly("inventory-1", "orders-1");
            });
  }

  @Test
  void handIncludeWithoutClasspathPrefixRunsNothingTwice() {
    runner
        .withPropertyValues(
            "spring.liquibase.change-log=classpath:app-changelog/includes-inventory-bare.yaml")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(changesets(context)).containsExactly("inventory-1", "orders-1");
            });
  }

  @Test
  void switchingFromHandIncludesRunsNothingAgain() {
    String[] database = MigratoryTestPostgres.freshDatabaseProperties();
    runner
        .withPropertyValues(database)
        .withPropertyValues(
            "migratory.enabled=false",
            "spring.liquibase.change-log=classpath:app-changelog/includes-both.yaml")
        .run(context -> assertThat(changesets(context)).hasSize(2));
    runner
        .withPropertyValues(database)
        .withPropertyValues("spring.liquibase.change-log=" + EMPTY_CHANGELOG)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(changesets(context)).containsExactly("inventory-1", "orders-1");
            });
  }

  @Test
  void disabledRunsNothing() {
    runner
        .withPropertyValues(
            "migratory.enabled=false", "spring.liquibase.change-log=" + EMPTY_CHANGELOG)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(tables(context)).doesNotContain("inventory_item", "order_line");
            });
  }

  @Test
  void springLiquibaseDisabledStillRunsMigratory() {
    runner
        .withPropertyValues("spring.liquibase.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed().doesNotHaveBean(SpringLiquibase.class);
              assertThat(tables(context)).contains("inventory_item", "order_line");
            });
  }

  @Test
  void noDeclarationsRunsNothing() {
    runner
        .withClassLoader(new NoDeclarationsClassLoader(getClass().getClassLoader()))
        .withPropertyValues("spring.liquibase.change-log=" + EMPTY_CHANGELOG)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(MigratoryLiquibase.class).getMigrations()).isEmpty();
              assertThat(tables(context)).doesNotContain("inventory_item");
            });
  }

  @Test
  void noDataSourceStartsQuietly() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                LiquibaseAutoConfiguration.class, MigratoryAutoConfiguration.class))
        .run(
            context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(MigratoryLiquibase.class));
  }

  @Test
  void runsWithSpringLiquibaseUrlAndNoDataSourceBean() {
    String other = MigratoryTestPostgres.newDatabaseUrl();
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                LiquibaseAutoConfiguration.class, MigratoryAutoConfiguration.class))
        .withPropertyValues(
            "spring.liquibase.url=" + other,
            "spring.liquibase.user=" + MigratoryTestPostgres.username(),
            "spring.liquibase.password=" + MigratoryTestPostgres.password())
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(MigratoryLiquibase.class));
    assertThat(tablesAt(other)).contains("inventory_item", "order_line", "app_note");
  }

  @Test
  void disabledIgnoresBrokenDeclarations(@TempDir Path library) throws IOException {
    Path declaration = library.resolve("META-INF/migratory.properties");
    Files.createDirectories(declaration.getParent());
    Files.writeString(declaration, "name=broken\nchangelog=classpath:no/such/changelog.xml\n");
    try (URLClassLoader loader =
        new URLClassLoader(new URL[] {library.toUri().toURL()}, getClass().getClassLoader())) {
      runner
          .withClassLoader(loader)
          .withPropertyValues(
              "migratory.enabled=false", "spring.liquibase.change-log=" + EMPTY_CHANGELOG)
          .run(context -> assertThat(context).hasNotFailed());
    }
  }

  @Test
  void dropFirstFailsStartup() {
    runner
        .withPropertyValues("spring.liquibase.drop-first=true")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(MigratoryException.class)
                    .hasMessageContaining("spring.liquibase.drop-first"));
  }

  @Test
  void dropFirstWithMigratoryDisabledStarts() {
    runner
        .withPropertyValues(
            "spring.liquibase.drop-first=true",
            "migratory.enabled=false",
            "spring.liquibase.change-log=" + EMPTY_CHANGELOG)
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void usesSpringLiquibaseUrlWhenSet() {
    String other = MigratoryTestPostgres.newDatabaseUrl();
    runner
        .withPropertyValues(
            "spring.liquibase.url=" + other,
            "spring.liquibase.user=" + MigratoryTestPostgres.username(),
            "spring.liquibase.password=" + MigratoryTestPostgres.password())
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(tables(context)).doesNotContain("inventory_item");
              assertThat(context.getBean(MigratoryLiquibase.class).getDataSource())
                  .isInstanceOfSatisfying(
                      SimpleDriverDataSource.class,
                      dataSource -> assertThat(dataSource.getUrl()).isEqualTo(other));
            });
    assertThat(tablesAt(other)).contains("inventory_item", "order_line", "app_note");
  }

  @Test
  void usesTheLiquibaseDataSourceWhenThereIsOne() {
    String other = MigratoryTestPostgres.newDatabaseUrl();
    runner
        .withUserConfiguration(OwnLiquibaseDataSource.class)
        .withPropertyValues("test.liquibase-url=" + other)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(MigratoryLiquibase.class).getDataSource())
                  .isSameAs(context.getBean("liquibaseDataSource"));
            });
    assertThat(tablesAt(other)).contains("inventory_item");
  }

  @Test
  void usesSpringLiquibaseUserOnThePrimaryDatabaseWhenNoUrlIsSet() throws SQLException {
    String url = MigratoryTestPostgres.newDatabaseUrl();
    execute(url, "CREATE ROLE migratory_migrator LOGIN PASSWORD 'migrator'");
    execute(url, "GRANT ALL ON SCHEMA public TO migratory_migrator");
    runner
        .withPropertyValues(
            "spring.datasource.url=" + url,
            "spring.liquibase.user=migratory_migrator",
            "spring.liquibase.password=migrator")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(
                      new JdbcTemplate(context.getBean(DataSource.class))
                          .queryForObject(
                              "SELECT tableowner FROM pg_tables WHERE tablename = 'inventory_item'",
                              String.class))
                  .isEqualTo("migratory_migrator");
            });
  }

  @Test
  void honoursTheDefaultSchemaAndChangelogTable() throws SQLException {
    String url = MigratoryTestPostgres.newDatabaseUrl();
    execute(url, "CREATE SCHEMA app");
    runner
        .withPropertyValues(
            "spring.datasource.url=" + url,
            "spring.liquibase.default-schema=app",
            "spring.liquibase.database-change-log-table=app_changelog",
            "spring.liquibase.change-log=" + EMPTY_CHANGELOG)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(tablesIn(context, "app")).contains("inventory_item", "app_changelog");
            });
  }

  @Test
  void appliesSpringLiquibaseRunnerSettings() {
    runner
        .withPropertyValues(
            "spring.liquibase.analytics-enabled=false",
            "spring.liquibase.ui-service=console",
            "spring.liquibase.license-key=migratory-test-key",
            "spring.liquibase.show-summary=verbose",
            "spring.liquibase.show-summary-output=all")
        .run(
            context -> {
              SpringLiquibase liquibase =
                  context.getBean(MigratoryLiquibase.class).springLiquibase();
              assertThat(liquibase.getAnalyticsEnabled()).isFalse();
              assertThat(liquibase.getUiService()).isEqualTo(UIServiceEnum.CONSOLE);
              assertThat(liquibase.getLicenseKey()).isEqualTo("migratory-test-key");
              // SpringLiquibase has no getters for these
              assertThat(liquibase)
                  .extracting("showSummary", "showSummaryOutput")
                  .containsExactly(UpdateSummaryEnum.VERBOSE, UpdateSummaryOutputEnum.ALL);
            });
  }

  @Test
  void runsUnderLazyInitialization() {
    // what spring.main.lazy-initialization=true does; SpringApplication, not the runner, reads it
    runner
        .withPropertyValues("spring.liquibase.change-log=" + EMPTY_CHANGELOG)
        .withInitializer(
            context ->
                context.addBeanFactoryPostProcessor(
                    new LazyInitializationBeanFactoryPostProcessor()))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(tables(context)).contains("inventory_item", "order_line");
            });
  }

  @Test
  void liquibaseMissingFailsWithMessage() {
    runner
        .withClassLoader(new FilteredClassLoader(SpringLiquibase.class))
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "migratory found migrations (inventory, orders) but Liquibase is not on"
                            + " the classpath: add spring-boot-starter-liquibase, or set"
                            + " migratory.enabled=false and include their changelogs yourself."));
  }

  @Test
  void bootsLiquibaseSupportMissingFailsWithMessage() {
    // Boot's Liquibase auto-configuration is itself in the hidden package, so it is not listed
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration.class, MigratoryAutoConfiguration.class))
        .withPropertyValues(MigratoryTestPostgres.freshDatabaseProperties())
        .withClassLoader(new FilteredClassLoader("org.springframework.boot.liquibase."))
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("Liquibase is not on the classpath"));
  }

  @Test
  void jdbcConnectionDetailsAloneAreADatabaseToMigrate() {
    // as for Boot's own Liquibase, the details mean a DataSource is coming; with none, migratory
    // says so rather than standing down
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                LiquibaseAutoConfiguration.class, MigratoryAutoConfiguration.class))
        .withPropertyValues("spring.liquibase.enabled=false")
        .withBean(JdbcConnectionDetails.class, MigratoryLiquibaseTest::connectionDetails)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("no single DataSource"));
  }

  // only the bean's presence matters: nothing connects with these
  private static JdbcConnectionDetails connectionDetails() {
    return new JdbcConnectionDetails() {
      @Override
      public String getUsername() {
        return "unused";
      }

      @Override
      public String getPassword() {
        return "unused";
      }

      @Override
      public String getJdbcUrl() {
        return "jdbc:postgresql://localhost/unused";
      }
    };
  }

  @Test
  void liquibaseMissingButDisabledStarts() {
    runner
        .withClassLoader(new FilteredClassLoader(SpringLiquibase.class))
        .withPropertyValues("migratory.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed().doesNotHaveBean(String.class);
              assertThat(context.getBean(LiquibaseNotRequired.class).reason())
                  .isEqualTo("migratory.enabled is false");
            });
  }

  @Test
  void liquibaseMissingFailsUnderLazyInitialization() {
    runner
        .withClassLoader(new FilteredClassLoader(SpringLiquibase.class))
        .withInitializer(
            context ->
                context.addBeanFactoryPostProcessor(
                    new LazyInitializationBeanFactoryPostProcessor()))
        .run(context -> assertThat(context).hasFailed());
  }

  private static List<String> filenames(ApplicationContextRunner runner) {
    List<List<String>> result = new ArrayList<>();
    runner.run(
        context ->
            result.add(
                new JdbcTemplate(context.getBean(DataSource.class))
                    .queryForList(
                        "SELECT filename FROM databasechangelog ORDER BY orderexecuted",
                        String.class)));
    return result.getFirst();
  }

  private static List<String> changesets(AssertableApplicationContext context) {
    return new JdbcTemplate(context.getBean(DataSource.class))
        .queryForList("SELECT id FROM databasechangelog ORDER BY orderexecuted", String.class);
  }

  private static List<String> tables(AssertableApplicationContext context) {
    return tablesIn(context, "public");
  }

  private static List<String> tablesIn(AssertableApplicationContext context, String schema) {
    return new JdbcTemplate(context.getBean(DataSource.class))
        .queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = ?",
            String.class,
            schema);
  }

  private static List<String> tablesAt(String url) {
    return new JdbcTemplate(
            new DriverManagerDataSource(
                url, MigratoryTestPostgres.username(), MigratoryTestPostgres.password()))
        .queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);
  }

  private static void execute(String url, String sql) throws SQLException {
    try (Connection connection =
            DriverManager.getConnection(
                url, MigratoryTestPostgres.username(), MigratoryTestPostgres.password());
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  /** Hides every declaration, as a class path with no declaring library would. */
  static final class NoDeclarationsClassLoader extends ClassLoader {

    NoDeclarationsClassLoader(ClassLoader parent) {
      super(parent);
    }

    @Override
    public URL getResource(String name) {
      return MigrationDeclarations.LOCATION.equals(name) ? null : super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
      return MigrationDeclarations.LOCATION.equals(name)
          ? Collections.emptyEnumeration()
          : super.getResources(name);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnLiquibaseDataSource {

    @Bean
    @Primary
    DataSource dataSource(Environment environment) {
      return new DriverManagerDataSource(
          environment.getRequiredProperty("spring.datasource.url"),
          MigratoryTestPostgres.username(),
          MigratoryTestPostgres.password());
    }

    @Bean
    @LiquibaseDataSource
    DataSource liquibaseDataSource(Environment environment) {
      return new DriverManagerDataSource(
          environment.getRequiredProperty("test.liquibase-url"),
          MigratoryTestPostgres.username(),
          MigratoryTestPostgres.password());
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnLiquibase {

    @Bean
    SpringLiquibase appLiquibase(DataSource dataSource) {
      SpringLiquibase liquibase = new SpringLiquibase();
      liquibase.setDataSource(dataSource);
      liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
      return liquibase;
    }
  }

  @Test
  void twoDataSourcesAndNoneChosenFailsSayingHowToChoose() {
    runner
        .withUserConfiguration(TwoDataSources.class)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(MigratoryException.class)
                    .hasMessageContaining("@Primary or @LiquibaseDataSource"));
  }

  @Test
  void readsSpringLiquibasePropertiesWhenBootsLiquibaseStandsDown() {
    String other = MigratoryTestPostgres.newDatabaseUrl();
    runner
        .withPropertyValues(
            "spring.liquibase.enabled=false",
            "spring.liquibase.url=" + other,
            "spring.liquibase.user=" + MigratoryTestPostgres.username(),
            "spring.liquibase.password=" + MigratoryTestPostgres.password(),
            "spring.liquibase.driver-class-name=org.postgresql.Driver")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(MigratoryLiquibase.class).getDataSource())
                  .isInstanceOfSatisfying(
                      SimpleDriverDataSource.class,
                      dataSource -> assertThat(dataSource.getUrl()).isEqualTo(other));
            });
    assertThat(tablesAt(other)).contains("inventory_item", "order_line");
  }

  @Test
  void usesSpringLiquibaseDriverClassNameWhenBootsLiquibaseStandsDown() {
    runner
        .withPropertyValues(
            "spring.liquibase.enabled=false",
            "spring.liquibase.url=" + MigratoryTestPostgres.newDatabaseUrl(),
            "spring.liquibase.driver-class-name=com.example.NoSuchDriver")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessageContaining("com.example.NoSuchDriver"));
  }

  @Test
  void findsTheDriverFromTheUrlWhenBootsLiquibaseStandsDown() {
    String other = MigratoryTestPostgres.newDatabaseUrl();
    runner
        .withPropertyValues(
            "spring.liquibase.enabled=false",
            "spring.liquibase.url=" + other,
            "spring.liquibase.user=" + MigratoryTestPostgres.username(),
            "spring.liquibase.password=" + MigratoryTestPostgres.password())
        .run(context -> assertThat(context).hasNotFailed());
    assertThat(tablesAt(other)).contains("inventory_item", "order_line");
  }

  @Configuration(proxyBeanMethods = false)
  static class TwoDataSources {

    @Bean
    DataSource first(Environment environment) {
      return new DriverManagerDataSource(environment.getRequiredProperty("spring.datasource.url"));
    }

    @Bean
    DataSource second(Environment environment) {
      return new DriverManagerDataSource(environment.getRequiredProperty("spring.datasource.url"));
    }
  }
}
