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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MigrationSorterTest {

  static Migration migration(String name, Set<String> after, Set<String> before) {
    return new Migration(name, "classpath:" + name + "/changelog.xml", after, before, name);
  }

  static Migration after(String name, String... after) {
    return migration(name, Set.of(after), Set.of());
  }

  private static List<String> names(List<Migration> migrations) {
    return migrations.stream().map(Migration::name).toList();
  }

  @Test
  void independentMigrationsSortByName() {
    assertThat(names(MigrationSorter.sort(List.of(after("c"), after("a"), after("b")))))
        .containsExactly("a", "b", "c");
  }

  @Test
  void afterIsHonoured() {
    List<Migration> migrations =
        List.of(
            after("roux", "who-rbac"),
            after("who-rbac", "who-jdbc"),
            after("who-apikey", "who-jdbc"),
            after("who-jdbc"));

    assertThat(names(MigrationSorter.sort(migrations)))
        .containsExactly("who-jdbc", "who-apikey", "who-rbac", "roux");
  }

  @Test
  void beforeIsHonoured() {
    List<Migration> migrations = List.of(after("a"), migration("b", Set.of(), Set.of("a")));

    assertThat(names(MigrationSorter.sort(migrations))).containsExactly("b", "a");
  }

  @Test
  void unknownTargetsAreIgnored() {
    List<Migration> migrations =
        List.of(after("b", "missing"), migration("a", Set.of(), Set.of("absent")));

    assertThat(names(MigrationSorter.sort(migrations))).containsExactly("a", "b");
  }

  @Test
  void cycleFailsNamingMembers() {
    List<Migration> migrations = List.of(after("a", "b"), after("b", "a"), after("c"));

    assertThatThrownBy(() -> MigrationSorter.sort(migrations))
        .isInstanceOf(MigratoryException.class)
        .hasMessageStartingWith("Migrations a, b cannot be ordered");
  }

  @Test
  void emptyIsEmpty() {
    assertThat(MigrationSorter.sort(List.of())).isEmpty();
  }

  @Test
  void orderDoesNotDependOnInputOrder() {
    List<Migration> migrations =
        new ArrayList<>(
            List.of(
                after("who-jdbc"),
                after("who-rbac", "who-jdbc"),
                after("who-apikey", "who-jdbc"),
                after("roux", "who-rbac"),
                after("other"),
                migration("early", Set.of(), Set.of("who-jdbc"))));
    List<String> expected = names(MigrationSorter.sort(migrations));
    Random random = new Random(42);

    for (int i = 0; i < 100; i++) {
      Collections.shuffle(migrations, random);
      assertThat(names(MigrationSorter.sort(migrations))).isEqualTo(expected);
    }
  }
}
