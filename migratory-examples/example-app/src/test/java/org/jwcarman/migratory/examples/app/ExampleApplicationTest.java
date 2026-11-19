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
package org.jwcarman.migratory.examples.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.lockservice.LockServiceFactory;

@SpringBootTest
@Testcontainers
class ExampleApplicationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

  @Autowired private JdbcClient jdbc;

  @Autowired private DataSource dataSource;

  @Test
  void librariesMigrateInOrderBeforeTheApplication() {
    assertThat(
            jdbc.sql("SELECT id FROM databasechangelog ORDER BY orderexecuted")
                .query(String.class)
                .list())
        .containsExactly("inventory-1", "orders-1", "app-1");
  }

  @Test
  void liquibaseLocksWithAPostgresSessionLock() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      Database database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));

      // liquibase-sessionlock replaces the DATABASECHANGELOGLOCK row with a PostgreSQL advisory
      // lock, which the database releases when a crashed instance's session ends
      assertThat(LockServiceFactory.getInstance().getLockService(database).getClass().getName())
          .isEqualTo("com.github.blagerweij.sessionlock.PGLockService");
    }
  }
}
