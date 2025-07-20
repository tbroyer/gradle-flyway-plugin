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
package net.ltgt.gradle.flyway.tasks;

import net.ltgt.gradle.flyway.FlywayExtension;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;

/** The base class for Flyway tasks using migration scripts. */
public abstract class MigrationsFlywayTask extends FlywayTask {
  MigrationsFlywayTask(Class<? extends FlywayWorkAction> workActionClass) {
    super(workActionClass);
  }

  /**
   * The directories containing migration scripts.
   *
   * <p>Defaults to the project-level {@code flyway} extension's {@link
   * FlywayExtension#getMigrationLocations() migrationLocations} property.
   */
  @InputFiles
  @PathSensitive(PathSensitivity.NAME_ONLY)
  @SkipWhenEmpty
  @IgnoreEmptyDirectories
  @Override
  public abstract ConfigurableFileCollection getMigrationLocations();
}
