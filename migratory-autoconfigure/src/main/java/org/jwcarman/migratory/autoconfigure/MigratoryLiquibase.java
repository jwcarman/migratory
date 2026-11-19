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

import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jwcarman.migratory.core.Migration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;
import org.springframework.core.io.ResourceLoader;

import liquibase.UpdateSummaryEnum;
import liquibase.UpdateSummaryOutputEnum;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.ui.UIServiceEnum;

/**
 * Runs the migrations libraries declare, in order, in one Liquibase update, before the
 * application's own Liquibase.
 *
 * <p>It uses the data source, schemas and history tables Spring Boot's Liquibase uses for the
 * application, so the libraries' tables land beside the application's and their changesets are
 * recorded beside the application's. Every {@link SpringLiquibase} bean in the context depends on
 * it.
 */
public final class MigratoryLiquibase implements InitializingBean {

  private static final Log log = LogFactory.getLog(MigratoryLiquibase.class);

  private final List<Migration> migrations;
  private final DataSource dataSource;
  private final LiquibaseProperties liquibase;
  private final MigratoryProperties properties;
  private final ResourceLoader resourceLoader;

  /**
   * Creates the runner.
   *
   * @param migrations the migrations, in the order they run
   * @param dataSource the data source the migrations run against
   * @param liquibase Spring Boot's Liquibase settings, for the schemas, history tables and runner
   * @param properties migratory's settings
   * @param resourceLoader the loader that resolves the changelogs
   */
  public MigratoryLiquibase(
      List<Migration> migrations,
      DataSource dataSource,
      LiquibaseProperties liquibase,
      MigratoryProperties properties,
      ResourceLoader resourceLoader) {
    this.migrations = List.copyOf(migrations);
    this.dataSource = dataSource;
    this.liquibase = liquibase;
    this.properties = properties;
    this.resourceLoader = resourceLoader;
  }

  /**
   * The migrations this runner runs, in order.
   *
   * @return the migrations
   */
  public List<Migration> getMigrations() {
    return migrations;
  }

  /**
   * The data source the migrations run against: the one Spring Boot's Liquibase uses.
   *
   * @return the data source
   */
  public DataSource getDataSource() {
    return dataSource;
  }

  /**
   * Runs the migrations, unless {@code migratory.enabled} is false or there are none.
   *
   * @throws LiquibaseException when a changeset fails, which fails startup
   */
  @Override
  public void afterPropertiesSet() throws LiquibaseException {
    if (!properties.isEnabled()) {
      log.info("migratory.enabled is false: not running the declared migrations");
      return;
    }
    if (migrations.isEmpty()) {
      return;
    }
    log.info(
        "Running migrations in order: "
            + migrations.stream().map(Migration::name).collect(Collectors.joining(", ")));
    springLiquibase().afterPropertiesSet();
  }

  /**
   * Builds the Liquibase runner over the generated root changelog from Spring Boot's settings.
   *
   * @return the runner, not yet run
   */
  SpringLiquibase springLiquibase() {
    SpringLiquibase runner = new SpringLiquibase();
    runner.setDataSource(dataSource);
    runner.setResourceLoader(new RootChangelogResourceLoader(resourceLoader, rootChangelog()));
    runner.setChangeLog(RootChangelogResourceLoader.ROOT_LOCATION);
    runner.setDefaultSchema(liquibase.getDefaultSchema());
    runner.setLiquibaseSchema(liquibase.getLiquibaseSchema());
    runner.setLiquibaseTablespace(liquibase.getLiquibaseTablespace());
    runner.setDatabaseChangeLogTable(liquibase.getDatabaseChangeLogTable());
    runner.setDatabaseChangeLogLockTable(liquibase.getDatabaseChangeLogLockTable());
    if (liquibase.getShowSummary() != null) {
      runner.setShowSummary(UpdateSummaryEnum.valueOf(liquibase.getShowSummary().name()));
    }
    if (liquibase.getShowSummaryOutput() != null) {
      runner.setShowSummaryOutput(
          UpdateSummaryOutputEnum.valueOf(liquibase.getShowSummaryOutput().name()));
    }
    if (liquibase.getUiService() != null) {
      runner.setUiService(UIServiceEnum.valueOf(liquibase.getUiService().name()));
    }
    if (liquibase.getAnalyticsEnabled() != null) {
      runner.setAnalyticsEnabled(liquibase.getAnalyticsEnabled());
    }
    if (liquibase.getLicenseKey() != null) {
      runner.setLicenseKey(liquibase.getLicenseKey());
    }
    return runner;
  }

  /** The root changelog: an include of each migration's changelog, in order. */
  String rootChangelog() {
    StringBuilder xml =
        new StringBuilder(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog \
            https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
            """);
    for (Migration migration : migrations) {
      xml.append("  <include file=\"").append(escape(migration.changelog())).append("\"/>\n");
    }
    return xml.append("</databaseChangeLog>\n").toString();
  }

  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
