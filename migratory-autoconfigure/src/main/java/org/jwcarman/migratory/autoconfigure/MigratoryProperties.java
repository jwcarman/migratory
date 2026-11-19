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

import org.springframework.boot.context.properties.ConfigurationProperties;

/** migratory's settings. */
@ConfigurationProperties("migratory")
public class MigratoryProperties {

  /**
   * Whether migratory runs the migrations libraries declare. When false, the application includes
   * their changelogs in its own. Read when the migrations would run, so it works the same in a
   * native image.
   */
  private boolean enabled = true;

  /**
   * Whether migratory's run drops everything in the default schema of the database it runs against
   * first, the application's tables and the history table included, so every start builds the
   * libraries' tables and then the application's again. For development databases only. Leave
   * {@code spring.liquibase.drop-first} off: it fails startup while migratory has migrations to
   * run.
   */
  private boolean dropFirst;

  /** Creates the settings with their defaults. */
  public MigratoryProperties() {
    // defaults only
  }

  /**
   * Whether migratory runs the declared migrations.
   *
   * @return true unless turned off
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Turns migratory on or off.
   *
   * @param enabled whether migratory runs the declared migrations
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Whether migratory's run drops everything in the default schema first.
   *
   * @return true when every start rebuilds the schema
   */
  public boolean isDropFirst() {
    return dropFirst;
  }

  /**
   * Makes migratory's run drop everything in the schema first, or not.
   *
   * @param dropFirst whether every start rebuilds the schema
   */
  public void setDropFirst(boolean dropFirst) {
    this.dropFirst = dropFirst;
  }
}
