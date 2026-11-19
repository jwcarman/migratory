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

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class MigrationDeclarationsParseTest {

  private static final String SOURCE = "jar:file:/libs/who-rbac.jar!/META-INF/migratory.properties";

  private static Properties properties(String text) throws IOException {
    Properties properties = new Properties();
    properties.load(new StringReader(text));
    return properties;
  }

  private static Migration parse(String text) throws IOException {
    return MigrationDeclarations.parse(properties(text), SOURCE);
  }

  @Test
  void parsesAllKeys() throws IOException {
    Migration migration =
        parse(
            """
            name=who-rbac
            changelog=classpath:org/jwcarman/who/rbac/db/who-rbac-changelog.xml
            after=who-jdbc
            """);

    assertThat(migration.name()).isEqualTo("who-rbac");
    assertThat(migration.changelog())
        .isEqualTo("classpath:org/jwcarman/who/rbac/db/who-rbac-changelog.xml");
    assertThat(migration.after()).containsExactly("who-jdbc");
    assertThat(migration.before()).isEmpty();
    assertThat(migration.source()).isEqualTo(SOURCE);
  }

  @Test
  void toleratesWhitespaceAndTrailingCommas() throws IOException {
    Migration migration =
        parse(
            "name = roux \r\nchangelog = classpath:roux/db/changelog.xml \r\nafter= a , b ,\r\nbefore=,c\r\n");

    assertThat(migration.name()).isEqualTo("roux");
    assertThat(migration.changelog()).isEqualTo("classpath:roux/db/changelog.xml");
    assertThat(migration.after()).containsExactly("a", "b");
    assertThat(migration.before()).containsExactly("c");
  }

  @Test
  void rejectsMissingName() {
    assertFails("changelog=classpath:db/x.xml", "name");
  }

  @Test
  void rejectsBlankChangelog() {
    assertFails("name=roux\nchangelog=  ", "changelog");
  }

  @Test
  void rejectsBadName() {
    assertFails("name=Who_RBAC\nchangelog=classpath:db/x.xml", "Who_RBAC");
  }

  @Test
  void rejectsUnknownKey() {
    assertFails("name=roux\nchangelog=classpath:db/x.xml\nafer=who-rbac", "afer");
  }

  @Test
  void rejectsNonClasspathChangelog() {
    assertFails("name=roux\nchangelog=file:/x.xml", "classpath:");
  }

  @Test
  void rejectsLeadingSlashInChangelog() {
    assertThatThrownBy(() -> parse("name=roux\nchangelog=classpath:/org/x.xml"))
        .hasMessageContaining("without the leading slash")
        .hasMessageNotContaining("empty");
  }

  @Test
  void rejectsDotSegmentsInChangelog() {
    assertFails("name=roux\nchangelog=classpath:db/../roux.xml", "must not contain . or ..");
    assertFails("name=roux\nchangelog=classpath:./roux.xml", "must not contain . or ..");
  }

  @Test
  void rejectsEmptySegmentsInChangelog() {
    assertFails("name=roux\nchangelog=classpath:db//roux.xml", "empty segment");
    assertFails("name=roux\nchangelog=classpath:db/", "empty segment");
  }

  @Test
  void rejectsChangelogAtClassPathRoot() {
    // a native image includes the changelog's directory; at the root that would be everything
    assertFails("name=roux\nchangelog=classpath:roux.xml", "directory");
  }

  @Test
  void rejectsSelfReference() {
    assertFails("name=roux\nchangelog=classpath:db/x.xml\nafter=roux", "itself");
  }

  @Test
  void rejectsBeforeItself() {
    assertFails("name=roux\nchangelog=classpath:db/x.xml\nbefore=roux", "itself");
  }

  @Test
  void rejectsChangelogWithNoPath() {
    assertFails("name=roux\nchangelog=classpath:", "has no path after classpath:");
  }

  private static void assertFails(String text, String reason) {
    assertThatThrownBy(() -> parse(text))
        .isInstanceOf(MigratoryException.class)
        .hasMessageContaining(SOURCE)
        .hasMessageContaining(reason);
  }
}
