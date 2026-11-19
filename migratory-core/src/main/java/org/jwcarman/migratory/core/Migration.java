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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A library's declared migration: a Liquibase changelog under a unique name, with the names of the
 * migrations it runs after and before.
 *
 * @param name the migration's unique name
 * @param changelog the changelog's {@code classpath:} location
 * @param after the names of the migrations this one runs after
 * @param before the names of the migrations this one runs before
 * @param source where the declaration was read from, for error messages
 */
public record Migration(
    String name, String changelog, Set<String> after, Set<String> before, String source) {

  /**
   * Creates a migration, copying {@code after} and {@code before} into unmodifiable sets that keep
   * their order.
   */
  public Migration {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(changelog, "changelog");
    Objects.requireNonNull(source, "source");
    after = Collections.unmodifiableSet(new LinkedHashSet<>(after));
    before = Collections.unmodifiableSet(new LinkedHashSet<>(before));
  }

  /**
   * The changelog's path without its {@code classpath:} prefix, as a class loader resource name.
   *
   * @return the resource name
   */
  public String resourceName() {
    return changelog.substring(MigrationDeclarations.CLASSPATH_PREFIX.length());
  }
}
