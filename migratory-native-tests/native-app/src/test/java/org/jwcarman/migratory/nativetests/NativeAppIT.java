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
package org.jwcarman.migratory.nativetests;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the app (natively when {@code app.binary} names the binary, otherwise in this JVM) against a
 * fresh database, and checks what it migrated.
 */
class NativeAppIT {

  private static final String BINARY = System.getProperty("app.binary", "");
  private static final AtomicInteger DATABASES = new AtomicInteger();

  private static PostgreSQLContainer postgres;

  @BeforeAll
  static void startDatabase() {
    postgres = new PostgreSQLContainer("postgres:16");
    postgres.start();
  }

  @AfterAll
  static void stopDatabase() {
    postgres.stop();
  }

  @Test
  void migratesLibrariesInOrderBeforeTheApplication() throws Exception {
    String url = newDatabase();

    String log = run(url);

    assertThat(
            jdbc(url)
                .queryForList(
                    "SELECT id FROM databasechangelog ORDER BY orderexecuted", String.class))
        .containsExactly("inventory-1", "orders-1", "app-1");
    if (!BINARY.isBlank()) {
      // the native image read the order baked in at build time
      assertThat(log).contains("Running migrations in order: inventory, orders");
    }
  }

  @Test
  void migratoryEnabledFalseAtRuntimeRunsNothing() throws Exception {
    String url = newDatabase();

    run(url, "--migratory.enabled=false", "--spring.liquibase.enabled=false");

    assertThat(
            jdbc(url)
                .queryForList(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                    String.class))
        .doesNotContain("inventory_item", "order_line");
  }

  private static String run(String url, String... extra) throws Exception {
    List<String> arguments = new ArrayList<>();
    arguments.add("--spring.datasource.url=" + url);
    arguments.add("--spring.datasource.username=" + postgres.getUsername());
    arguments.add("--spring.datasource.password=" + postgres.getPassword());
    arguments.addAll(List.of(extra));
    if (BINARY.isBlank()) {
      SpringApplication.run(NativeApplication.class, arguments.toArray(String[]::new)).close();
      return "";
    }
    File log = new File("target/app-" + DATABASES.get() + ".log");
    List<String> command = new ArrayList<>();
    command.add(new File(BINARY).getAbsolutePath());
    command.addAll(arguments);
    Process process =
        new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start();
    if (!process.waitFor(120, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IllegalStateException("The native app did not exit within 120 seconds");
    }
    String output = Files.readString(log.toPath(), StandardCharsets.UTF_8);
    assertThat(process.exitValue()).as("exit code; output:%n%s", output).isZero();
    return output;
  }

  private static String newDatabase() throws SQLException {
    String name = "native_" + DATABASES.incrementAndGet();
    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + name);
    }
    return postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
  }

  private static JdbcTemplate jdbc(String url) {
    return new JdbcTemplate(
        new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword()));
  }
}
