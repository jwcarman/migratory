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
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * A native image builds {@code java.net.URL}s for the schema a changelog names, and refuses a
 * protocol it was not built with: changelogs name it over http or https, so both are enabled.
 */
class NativeSchemaLocationTest {

  @Test
  void nativeImagesAcceptHttpAndHttpsSchemaLocations() throws IOException {
    Properties properties = new Properties();
    try (InputStream in =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "META-INF/native-image/org.jwcarman.migratory/migratory-autoconfigure/native-image.properties")) {
      assertThat(in).isNotNull();
      properties.load(in);
    }

    assertThat(properties.getProperty("Args")).contains("--enable-url-protocols=http,https");
  }
}
