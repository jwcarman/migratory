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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MigrationSorterFuzzTest {

  private static final int MAX_MIGRATIONS = 12;

  @FuzzTest(maxDuration = "2m")
  void any_graph_sorts_to_an_order_honouring_every_edge_or_names_a_cycle(FuzzedDataProvider data) {
    int count = data.consumeInt(0, MAX_MIGRATIONS);
    List<Migration> migrations = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Set<String> after = new HashSet<>();
      Set<String> before = new HashSet<>();
      int edges = data.consumeInt(0, 4);
      for (int e = 0; e < edges; e++) {
        // names past the last migration are targets no one declares, which are ignored
        String target = "m" + data.consumeInt(0, MAX_MIGRATIONS + 2);
        if (!target.equals("m" + i)) {
          (data.consumeBoolean() ? after : before).add(target);
        }
      }
      migrations.add(new Migration("m" + i, "classpath:m" + i + ".xml", after, before, "m" + i));
    }

    List<Migration> sorted;
    try {
      sorted = MigrationSorter.sort(migrations);
    } catch (MigratoryException cycle) {
      assertThat(cycle).hasMessageContaining("cycle");
      return;
    }

    assertThat(sorted).containsExactlyInAnyOrderElementsOf(migrations);
    Map<String, Integer> position = new HashMap<>();
    for (int i = 0; i < sorted.size(); i++) {
      position.put(sorted.get(i).name(), i);
    }
    for (Migration migration : migrations) {
      int at = position.get(migration.name());
      migration.after().stream()
          .filter(position::containsKey)
          .forEach(earlier -> assertThat(position.get(earlier)).isLessThan(at));
      migration.before().stream()
          .filter(position::containsKey)
          .forEach(later -> assertThat(position.get(later)).isGreaterThan(at));
    }
  }
}
