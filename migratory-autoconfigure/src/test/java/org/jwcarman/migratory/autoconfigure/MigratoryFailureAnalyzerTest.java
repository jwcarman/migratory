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

import org.junit.jupiter.api.Test;
import org.jwcarman.migratory.core.MigratoryException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.FailureHandler;

class MigratoryFailureAnalyzerTest {

  @Test
  void describesAMigratoryFailureWithItsOwnMessage() {
    MigratoryException failure = new MigratoryException("Two migrations are named 'a': x and y");

    FailureAnalysis analysis =
        new MigratoryFailureAnalyzer()
            .analyze(new BeanCreationException("migratoryLiquibase", "failed", failure));

    assertThat(analysis.getDescription()).isEqualTo("Two migrations are named 'a': x and y");
    assertThat(analysis.getAction()).contains("migratory.enabled=false");
    assertThat(analysis.getCause()).isSameAs(failure);
  }

  @Test
  void isRegisteredWithSpringBoot() {
    assertThat(
            SpringFactoriesLoader.forDefaultResourceLocation(getClass().getClassLoader())
                // Boot's own analyzers need constructor arguments this test does not give
                .load(FailureAnalyzer.class, FailureHandler.handleMessage((message, e) -> {})))
        .hasAtLeastOneElementOfType(MigratoryFailureAnalyzer.class);
  }
}
