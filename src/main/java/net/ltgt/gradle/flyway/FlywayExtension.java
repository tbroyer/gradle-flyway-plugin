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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/** The extension to configure all Flyway tasks in a central place. */
public abstract class FlywayExtension {

  /**
   * The Flyway configuration file (must be a Java properties file encoded in UTF-8)
   *
   * <p>When the {@code java} plugin is applied, it defaults to the {@code db/flyway.conf} from the
   * main source set's resources, after it has been processed by the {@code processResources} task.
   */
  public abstract RegularFileProperty getConfigurationFile();

  /**
   * Additional configuration properties to be merged with the {@link #getConfigurationFile()
   * configuration file} (overriding it).
   */
  public abstract MapProperty<String, String> getConfiguration();

  /**
   * The directories containing migration scripts.
   *
   * <p>When the {@code java} plugin is applied, it defaults to the {@code db/migration/} from the
   * main source set's resources, after it has been processed by the {@code processResources} task.
   */
  public abstract ConfigurableFileCollection getMigrationLocations();

  /** The jdbc url to use to connect to the database. */
  public abstract Property<String> getUrl();

  /** The user to use to connect to the database. */
  public abstract Property<String> getUser();

  /** The password to use to connect to the database. */
  public abstract Property<String> getPassword();
}
