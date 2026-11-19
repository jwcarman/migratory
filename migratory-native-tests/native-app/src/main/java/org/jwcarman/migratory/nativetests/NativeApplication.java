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
package org.jwcarman.migratory.nativetests;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts, which runs the example libraries' migrations and then this app's own changelog, and
 * exits: it has no web server.
 */
@SpringBootApplication
public class NativeApplication {

  /**
   * Runs the app.
   *
   * @param args the data source and any other settings
   */
  public static void main(String[] args) {
    SpringApplication.run(NativeApplication.class, args).close();
  }
}
