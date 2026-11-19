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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/** The generated root changelog is served at its location, and everything else is delegated. */
class RootChangelogResourceLoaderTest {

  private static final String ROOT = "<databaseChangeLog/>";

  private final RootChangelogResourceLoader loader =
      new RootChangelogResourceLoader(new DefaultResourceLoader(), ROOT);

  @Test
  void servesTheRootAtItsLocation() throws IOException {
    Resource root = loader.getResource(RootChangelogResourceLoader.ROOT_LOCATION);

    assertThat(root.exists()).isTrue();
    assertThat(root.getFilename()).isEqualTo("root-changelog.xml");
    assertThat(root.contentLength()).isEqualTo(ROOT.length());
    assertThat(root.getDescription()).isEqualTo("migratory's generated root changelog");
    assertThat(root.getURI()).hasToString(RootChangelogResourceLoader.ROOT_LOCATION);
    try (InputStream in = root.getInputStream()) {
      assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(ROOT);
    }
  }

  @Test
  void servesTheRootWhateverPrefixOrLeadingSlashNamesIt() throws IOException {
    Resource root = loader.getResource(RootChangelogResourceLoader.ROOT_LOCATION);

    assertThat(loader.getResources("classpath*:/" + RootChangelogResourceLoader.ROOT_PATH))
        .containsExactly(root);
    assertThat(loader.getResource("//" + RootChangelogResourceLoader.ROOT_PATH)).isSameAs(root);
  }

  @Test
  void theRootEqualsOnlyItself() {
    Resource root = loader.getResource(RootChangelogResourceLoader.ROOT_LOCATION);
    Resource other =
        new RootChangelogResourceLoader(new DefaultResourceLoader(), ROOT)
            .getResource(RootChangelogResourceLoader.ROOT_LOCATION);

    assertThat(root).isEqualTo(root).isNotEqualTo(other).hasSameHashCodeAs(root);
  }

  @Test
  void delegatesEveryOtherLocation() throws IOException {
    assertThat(loader.getResource("classpath:app-changelog/empty.yaml").exists()).isTrue();
    assertThat(loader.getResources("classpath*:app-changelog/empty.yaml")).hasSize(1);
    assertThat(loader.getClassLoader()).isSameAs(new DefaultResourceLoader().getClassLoader());
  }
}
