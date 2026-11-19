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

import org.jwcarman.migratory.core.MigratoryException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Reports a {@link MigratoryException} (a bad declaration, a cycle, two migrations with one name)
 * as Spring Boot's short startup failure report instead of a stack trace.
 */
public class MigratoryFailureAnalyzer extends AbstractFailureAnalyzer<MigratoryException> {

  /** Creates the analyzer; Spring Boot instantiates it. */
  public MigratoryFailureAnalyzer() {
    // no state
  }

  @Override
  protected FailureAnalysis analyze(Throwable rootFailure, MigratoryException cause) {
    return new FailureAnalysis(
        cause.getMessage(),
        "Fix the declaration or library named above"
            + " (https://jwcarman.github.io/migratory/guides/declaring-migrations/), or set"
            + " migratory.enabled=false and include the libraries' changelogs in the"
            + " application's own.",
        cause);
  }
}
