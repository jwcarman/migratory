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
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.core.Ordered;

/** The detector, loaded from spring.factories, orders migratory first and never needs Liquibase. */
class MigratoryDatabaseInitializerDetectorTest {

  @Test
  void findsNothingWhenLiquibaseIsMissing() {
    assertThat(
            new MigratoryDatabaseInitializerDetector(new FilteredClassLoader("liquibase."))
                .detect(new DefaultListableBeanFactory()))
        .isEmpty();
  }

  @Test
  void comesBeforeEveryOtherDetector() {
    assertThat(new MigratoryDatabaseInitializerDetector().getOrder())
        .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
  }
}
