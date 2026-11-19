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

import java.io.Serial;

/** A migration declaration that cannot be used, or migrations that cannot be ordered. */
public class MigratoryException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what is wrong, and where
   */
  public MigratoryException(String message) {
    super(message);
  }

  /**
   * Creates the exception.
   *
   * @param message what is wrong, and where
   * @param cause the underlying failure
   */
  public MigratoryException(String message, Throwable cause) {
    super(message, cause);
  }
}
