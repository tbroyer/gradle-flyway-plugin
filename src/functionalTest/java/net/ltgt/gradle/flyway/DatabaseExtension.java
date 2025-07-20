/*
 * Copyright © 2025 Thomas Broyer
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
package net.ltgt.gradle.flyway;

import java.nio.file.Path;
import java.util.function.Supplier;
import org.h2.tools.Server;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

class DatabaseExtension implements BeforeEachCallback, AfterEachCallback {
  private final Supplier<Path> projectDir;

  private Server server;

  public DatabaseExtension(Supplier<Path> projectDir) {
    this.projectDir = projectDir;
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    server = Server.createTcpServer("-ifNotExists", "-baseDir", projectDir.get().toString());
    server.start();
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    server.stop();
  }

  public String getURL() {
    return "jdbc:h2:%s/test".formatted(server.getURL());
  }
}
