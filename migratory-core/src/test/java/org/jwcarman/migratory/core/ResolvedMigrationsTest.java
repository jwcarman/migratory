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
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ResolvedMigrationsTest {

  private static List<Migration> roundTrip(List<Migration> migrations) {
    String text = ResolvedMigrations.write(migrations);
    return ResolvedMigrations.read(
        new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1)));
  }

  @Test
  void roundTrips() {
    List<Migration> sorted =
        List.of(
            new Migration(
                "who-jdbc",
                "classpath:org/jwcarman/who/jdbc/db/who-jdbc-changelog.xml",
                Set.of(),
                Set.of(),
                "jar:file:/libs/who-jdbc.jar!/META-INF/migratory.properties"),
            new Migration(
                "roux",
                "classpath:org/jwcarman/roux/db/roux-changelog.xml",
                Set.of("who-jdbc"),
                Set.of(),
                "jar:file:/libs/roux.jar!/META-INF/migratory.properties"));

    List<Migration> read = roundTrip(sorted);

    assertThat(read)
        .extracting(Migration::name, Migration::changelog, Migration::source)
        .containsExactly(
            tuple(
                "who-jdbc",
                "classpath:org/jwcarman/who/jdbc/db/who-jdbc-changelog.xml",
                "jar:file:/libs/who-jdbc.jar!/META-INF/migratory.properties"),
            tuple(
                "roux",
                "classpath:org/jwcarman/roux/db/roux-changelog.xml",
                "jar:file:/libs/roux.jar!/META-INF/migratory.properties"));
    assertThat(read.get(1).after()).isEmpty();
  }

  @Test
  void damagedCountFails() {
    assertThatThrownBy(() -> read("count=x\n"))
        .isInstanceOf(MigratoryException.class)
        .hasMessageContaining(ResolvedMigrations.LOCATION);
  }

  @Test
  void missingEntryFails() {
    // the reason is the cause's; the failure report appends it once
    assertThatThrownBy(() -> read("count=1\n0.changelog=classpath:a/c.xml\n"))
        .isInstanceOf(MigratoryException.class)
        .hasMessage(ResolvedMigrations.LOCATION + " is damaged")
        .cause()
        .hasMessage("0.name is missing");
  }

  @Test
  void malformedEscapeFails() {
    assertThatThrownBy(() -> read("count=\\uZZZZ\n"))
        .isInstanceOf(MigratoryException.class)
        .hasMessageContaining(ResolvedMigrations.LOCATION);
  }

  private static List<Migration> read(String text) {
    return ResolvedMigrations.read(
        new ByteArrayInputStream(text.getBytes(StandardCharsets.ISO_8859_1)));
  }

  @Test
  void emptyRoundTrips() {
    assertThat(roundTrip(List.of())).isEmpty();
  }
}
