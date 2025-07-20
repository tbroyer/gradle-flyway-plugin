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

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

abstract class FlywayWorkAction implements WorkAction<FlywayWorkAction.Parameters> {
  interface Parameters extends WorkParameters {
    Property<String> getUrl();

    Property<String> getUser();

    Property<String> getPassword();

    MapProperty<String, String> getConfiguration();
  }

  @Override
  public void execute() {
    Flyway flyway =
        Flyway.configure()
            .configuration(getParameters().getConfiguration().get())
            .dataSource(
                getParameters().getUrl().get(),
                getParameters().getUser().getOrNull(),
                getParameters().getPassword().getOrNull())
            .load();
    try {
      doExecute(flyway);
    } catch (FlywayException e) {
      // FlywaySqlException's getMessage() is lazy and uses an internal class;
      // letting the exception escape means that when escaping the classloader isolation, when
      // Gradle will call its getMessage() for logging purpose, the helper class won't be available
      // in the classloader,
      // resulting in a ClassNotFoundException when trying to log the error.
      // As a workaround, it's enough to wrap it in another exception: the wrapped exception's
      // getMessage() will only show in the stacktrace and the JVM is smart enough to call it in the
      // appropriate classloader, avoiding the ClassNotFoundException.
      // We do it for all FlywayExceptions, not just FlywaySqlException, in case others have the
      // same issue.
      throw new RuntimeException(e);
    }
  }

  protected abstract void doExecute(Flyway flyway);
}
