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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Enumeration;

import org.junit.jupiter.api.Test;
import org.jwcarman.migratory.autoconfigure.MigratoryAutoConfiguration.DeclarationsPresentCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** The missing-Liquibase check applies only when some library declares a migration. */
class DeclarationsPresentConditionTest {

  private final DeclarationsPresentCondition condition = new DeclarationsPresentCondition();

  @Test
  void matchesWhenADeclarationIsOnTheClassPath() {
    assertThat(condition.matches(context(getClass().getClassLoader()), metadata())).isTrue();
  }

  @Test
  void doesNotMatchWithoutAClassLoader() {
    assertThat(condition.matches(context(null), metadata())).isFalse();
  }

  @Test
  void aClassPathThatCannotBeSearchedFails() {
    ClassLoader broken =
        new ClassLoader(getClass().getClassLoader()) {
          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            throw new IOException("cannot search " + name);
          }
        };

    ConditionContext context = context(broken);
    AnnotatedTypeMetadata metadata = metadata();

    assertThatThrownBy(() -> condition.matches(context, metadata))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("cannot search");
  }

  private static ConditionContext context(ClassLoader classLoader) {
    ConditionContext context = mock(ConditionContext.class);
    when(context.getClassLoader()).thenReturn(classLoader);
    return context;
  }

  private static AnnotatedTypeMetadata metadata() {
    return mock(AnnotatedTypeMetadata.class);
  }
}
