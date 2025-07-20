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

import javax.inject.Inject;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.Option;

/** Migrates the schema to the latest version. */
@UntrackedTask(because = "Depends on the database")
public abstract class FlywayMigrate extends MigrationsFlywayTask {
  @Inject
  public FlywayMigrate() {
    super(FlywayMigrateAction.class);
  }

  /** The target version up to which Flyway should consider migrations. */
  @Input
  @Optional
  @Option(
      option = "target",
      description = "The target version up to which Flyway should consider migrations")
  @Override
  public abstract Property<String> getTarget();

  abstract static class FlywayMigrateAction extends FlywayWorkAction {
    @Inject
    public FlywayMigrateAction() {}

    @Override
    protected void doExecute(Flyway flyway) {
      MigrateResult result = flyway.migrate();
      if (!result.success) {
        throw new RuntimeException(result.exceptionObject);
      }
    }
  }
}
