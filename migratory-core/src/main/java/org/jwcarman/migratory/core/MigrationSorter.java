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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Orders migrations so each runs after every migration it names in {@code after}, and before every
 * one it names in {@code before}. Migrations ready at the same time run in order of name, so the
 * order depends only on the declarations. Names that no migration has are ignored.
 */
public final class MigrationSorter {

  private MigrationSorter() {}

  /**
   * Sorts migrations.
   *
   * @param migrations the migrations, uniquely named, in any order
   * @return the migrations in the order they run
   * @throws MigratoryException when the declarations form a cycle
   */
  public static List<Migration> sort(Collection<Migration> migrations) {
    Map<String, Migration> byName = new TreeMap<>();
    migrations.forEach(migration -> byName.put(migration.name(), migration));

    Map<String, List<String>> next = new HashMap<>();
    Map<String, Integer> incoming = new TreeMap<>();
    byName
        .keySet()
        .forEach(
            name -> {
              next.put(name, new ArrayList<>());
              incoming.put(name, 0);
            });
    for (Migration migration : byName.values()) {
      for (String earlier : migration.after()) {
        edge(earlier, migration.name(), byName, next, incoming);
      }
      for (String later : migration.before()) {
        edge(migration.name(), later, byName, next, incoming);
      }
    }

    PriorityQueue<String> ready = new PriorityQueue<>();
    incoming.forEach(
        (name, count) -> {
          if (count == 0) {
            ready.add(name);
          }
        });
    List<Migration> order = new ArrayList<>();
    while (!ready.isEmpty()) {
      String name = ready.poll();
      order.add(byName.get(name));
      for (String waiting : next.get(name)) {
        if (incoming.merge(waiting, -1, Integer::sum) == 0) {
          ready.add(waiting);
        }
      }
    }
    if (order.size() < byName.size()) {
      TreeSet<String> stuck = new TreeSet<>(byName.keySet());
      order.forEach(migration -> stuck.remove(migration.name()));
      throw new MigratoryException(
          "Migrations "
              + String.join(", ", stuck)
              + " cannot be ordered: their after and before declarations form a cycle");
    }
    return List.copyOf(order);
  }

  private static void edge(
      String from,
      String to,
      Map<String, Migration> byName,
      Map<String, List<String>> next,
      Map<String, Integer> incoming) {
    if (byName.containsKey(from) && byName.containsKey(to) && !next.get(from).contains(to)) {
      next.get(from).add(to);
      incoming.merge(to, 1, Integer::sum);
    }
  }
}
