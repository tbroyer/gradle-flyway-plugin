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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.ltgt.gradle.flyway.FlywayExtension;
import org.flywaydb.core.api.CoreLocationPrefix;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.Option;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

/** The base class for all Flyway tasks. */
@UntrackedTask(because = "Depends on the database")
public abstract class FlywayTask extends DefaultTask {
  private final Class<? extends FlywayWorkAction> workActionClass;

  FlywayTask(Class<? extends FlywayWorkAction> workActionClass) {
    this.workActionClass = workActionClass;
  }

  @Inject
  protected abstract WorkerExecutor getWorkerExecutor();

  /**
   * The classpath for executing Flyway.
   *
   * <p>Defaults to the {@code flywayClasspath} configuration, itself extending the {@code flyway}
   * configuration.
   */
  @Classpath
  public abstract ConfigurableFileCollection getClasspath();

  /**
   * The jdbc url to use to connect to the database.
   *
   * <p>Defaults to the project-level {@code flyway} extension's {@link FlywayExtension#getUrl()
   * url} property.
   */
  @Input
  @Option(option = "url", description = "Configures the database JDBC URL")
  public abstract Property<String> getUrl();

  /**
   * The user to use to connect to the database.
   *
   * <p>Defaults to the project-level {@code flyway} extension's {@link FlywayExtension#getUser()
   * user} property.
   */
  @Input
  @Optional
  @Option(option = "user", description = "Configures the database user")
  public abstract Property<String> getUser();

  /**
   * The password to use to connect to the database.
   *
   * <p>Defaults to the project-level {@code flyway} extension's {@link
   * FlywayExtension#getPassword() password} property.
   */
  @Input
  @Optional
  @Option(option = "password", description = "Configures the database password")
  public abstract Property<String> getPassword();

  /**
   * The Flyway configuration file (must be a Java properties file encoded in UTF-8, might not
   * exist)
   *
   * <p>Defaults to the project-level {@code flyway} extension's {@link
   * FlywayExtension#getConfigurationFile() configurationFile} property.
   */
  @Internal
  public abstract RegularFileProperty getConfigurationFile();

  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  @Optional
  Provider<RegularFile> getActualConfigurationFile() {
    return getConfigurationFile().filter(regularFile -> regularFile.getAsFile().exists());
  }

  /**
   * Additional configuration properties to be merged with the {@link #getConfigurationFile()
   * configuration file} (overriding it).
   *
   * <p>Defaults to the project-level {@code flyway} extension's {@link
   * FlywayExtension#getConfiguration() configuration} property.
   */
  @Input
  public abstract MapProperty<String, String> getConfiguration();

  /**
   * The directories containing migration scripts.
   *
   * <p>Declared here as internal as an implementation detail to make it easier to implement the
   * task action.
   */
  @Internal
  protected abstract ConfigurableFileCollection getMigrationLocations();

  /**
   * The target version up to which Flyway should consider migrations.
   *
   * <p>Declared here as internal as an implementation detail to make it easier to implement the
   * task action.
   */
  @Internal
  protected abstract Property<String> getTarget();

  /**
   * Configures the java executable to be used to run Flyway.
   *
   * <p>If it is the same as the one used to run Gradle, then Flyway will run <i>in-process</i>.
   *
   * <p>When the {@code java-base} plugin is applied, it defaults to using the toolchain {@link
   * JavaPluginExtension#getToolchain() configured at the project level}.
   */
  @Nested
  @Optional
  public abstract Property<JavaLauncher> getJavaLauncher();

  @TaskAction
  void run() {
    final WorkQueue workQueue;
    JavaLauncher javaLauncher = getJavaLauncher().getOrNull();
    if (javaLauncher == null || javaLauncher.getMetadata().isCurrentJvm()) {
      workQueue =
          getWorkerExecutor()
              .classLoaderIsolation(spec -> spec.getClasspath().from(getClasspath()));
    } else {
      workQueue =
          getWorkerExecutor()
              .processIsolation(
                  spec -> {
                    spec.getClasspath().from(getClasspath());
                    spec.forkOptions(
                        forkOptions -> forkOptions.setExecutable(javaLauncher.getExecutablePath()));
                  });
    }
    workQueue.submit(
        workActionClass,
        params -> {
          params.getUrl().set(getUrl().get());
          params.getUser().set(getUser());
          params.getPassword().set(getPassword());
          // Merge configuration files with properties
          File configurationFile = getConfigurationFile().getAsFile().getOrNull();
          if (configurationFile != null && configurationFile.exists()) {
            params.getConfiguration().putAll(loadConfigurationFile(configurationFile.toPath()));
          }
          params.getConfiguration().putAll(getConfiguration().get());
          // Set specific properties
          params
              .getConfiguration()
              .put(
                  "flyway.locations",
                  getMigrationLocations().getFiles().stream()
                      .map(f -> CoreLocationPrefix.FILESYSTEM_PREFIX + f.getPath())
                      .collect(Collectors.joining(",")));
          if (getTarget().isPresent()) {
            params.getConfiguration().put("flyway.target", getTarget().get());
          }
        });
  }

  private Map<String, String> loadConfigurationFile(Path configurationFile) {
    Properties properties = new Properties();
    try (BufferedReader r = Files.newBufferedReader(configurationFile)) {
      properties.load(r);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    @SuppressWarnings("unchecked")
    Map<String, String> asMap = (Map<String, String>) (Map<?, ?>) properties;
    return Collections.unmodifiableMap(asMap);
  }
}
