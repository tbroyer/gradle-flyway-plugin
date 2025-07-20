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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.Correspondence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.ltgt.gradle.flyway.tasks.FlywayClean;
import net.ltgt.gradle.flyway.tasks.FlywayMigrate;
import net.ltgt.gradle.flyway.tasks.FlywayRepair;
import net.ltgt.gradle.flyway.tasks.FlywayTask;
import net.ltgt.gradle.flyway.tasks.MigrationsFlywayTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlywayPluginTest {

  private Project project;

  @BeforeEach
  void setup(@TempDir Path projectDir) throws Exception {
    project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();
    Files.writeString(projectDir.resolve("settings.gradle.kts"), "");
  }

  @Test
  void test() {
    project.getPluginManager().apply(FlywayPlugin.class);

    assertThat(project.getExtensions().findByName("flyway")).isInstanceOf(FlywayExtension.class);
    var flywayExtension = project.getExtensions().getByType(FlywayExtension.class);
    assertThat(flywayExtension.getUrl().isPresent()).isFalse();
    assertThat(flywayExtension.getUser().isPresent()).isFalse();
    assertThat(flywayExtension.getPassword().isPresent()).isFalse();
    assertThat(flywayExtension.getConfiguration().getOrNull()).isEmpty();
    assertThat(flywayExtension.getConfigurationFile().isPresent()).isFalse();
    assertThat(flywayExtension.getMigrationLocations().getFrom()).isEmpty();

    var flywayConfiguration = project.getConfigurations().findByName("flyway");
    assertThat(flywayConfiguration).isInstanceOf(DependencyScopeConfiguration.class);
    assertThat(flywayConfiguration.getExtendsFrom()).isEmpty();

    var flywayClasspathConfiguration = project.getConfigurations().findByName("flywayClasspath");
    assertThat(flywayClasspathConfiguration).isInstanceOf(ResolvableConfiguration.class);
    assertThat(flywayClasspathConfiguration.getExtendsFrom()).containsExactly(flywayConfiguration);

    project
        .getTasks()
        .withType(FlywayTask.class)
        .forEach(
            flywayTask -> {
              assertThat(flywayTask.getClasspath().getFrom())
                  .comparingElementsUsing(
                      Correspondence.transforming(
                          input -> input instanceof Provider<?> provider ? provider.get() : input,
                          "resolving providers"))
                  .containsExactly(flywayClasspathConfiguration);
              assertThat(flywayTask.getConfiguration().getOrNull()).isEmpty();
              assertThat(flywayTask.getUrl().isPresent()).isFalse();
              assertThat(flywayTask.getUser().isPresent()).isFalse();
              assertThat(flywayTask.getPassword().isPresent()).isFalse();
              assertThat(flywayTask.getConfigurationFile().isPresent()).isFalse();
              if (flywayTask instanceof MigrationsFlywayTask migrationsFlywayTask) {
                assertThat(migrationsFlywayTask.getMigrationLocations().getFrom())
                    .containsExactly(flywayExtension.getMigrationLocations());
              }
              if (flywayTask instanceof FlywayMigrate flywayMigrate) {
                assertThat(flywayMigrate.getTarget().isPresent()).isFalse();
              }
            });

    assertThat(project.getTasks().findByName("flywayClean")).isInstanceOf(FlywayClean.class);
    assertThat(project.getTasks().findByName("flywayMigrate")).isInstanceOf(FlywayMigrate.class);
    assertThat(project.getTasks().findByName("flywayRepair")).isInstanceOf(FlywayRepair.class);
  }

  @Test
  void javaProject() {
    project.getPluginManager().apply(FlywayPlugin.class);
    project.getPluginManager().apply(JavaPlugin.class);

    var flywayExtension = project.getExtensions().getByType(FlywayExtension.class);
    assertThat(flywayExtension.getUrl().isPresent()).isFalse();
    assertThat(flywayExtension.getUser().isPresent()).isFalse();
    assertThat(flywayExtension.getPassword().isPresent()).isFalse();
    assertThat(flywayExtension.getConfiguration().getOrNull()).isEmpty();
    assertThat(flywayExtension.getConfigurationFile().getAsFile().getOrNull())
        .isEqualTo(project.file("build/resources/main/db/flyway.conf"));
    assertThat(flywayExtension.getMigrationLocations().getFiles())
        .containsExactly(project.file("build/resources/main/db/migration"));

    project
        .getTasks()
        .withType(FlywayTask.class)
        .forEach(
            flywayTask -> {
              assertThat(flywayTask.getTaskDependencies().getDependencies(flywayTask))
                  .contains(project.getTasks().getByName(JvmConstants.PROCESS_RESOURCES_TASK_NAME));
            });
  }

  @Test
  void wiresTasksToExtension() {
    project.getPluginManager().apply(FlywayPlugin.class);

    var flywayExtension = project.getExtensions().getByType(FlywayExtension.class);
    flywayExtension.getUrl().set("url");
    flywayExtension.getUser().set("user");
    flywayExtension.getPassword().set("password");
    flywayExtension.getConfiguration().put("key", "value");
    flywayExtension.getConfigurationFile().set(project.file("flyway.conf"));
    flywayExtension.getMigrationLocations().from(project.file("migrations"));

    // Create a new custom task to check it's wired to the extension too
    project.getTasks().register("flywayMigrateData", FlywayMigrate.class);

    project
        .getTasks()
        .withType(FlywayTask.class)
        .forEach(
            flywayTask -> {
              assertThat(flywayTask.getUrl().getOrNull()).isEqualTo("url");
              assertThat(flywayTask.getUser().getOrNull()).isEqualTo("user");
              assertThat(flywayTask.getPassword().getOrNull()).isEqualTo("password");
              assertThat(flywayTask.getConfiguration().getOrNull()).containsExactly("key", "value");
              assertThat(flywayTask.getConfigurationFile().getAsFile().getOrNull())
                  .isEqualTo(project.file("flyway.conf"));
              if (flywayTask instanceof MigrationsFlywayTask migrationsFlywayTask) {
                assertThat(migrationsFlywayTask.getMigrationLocations().getFiles())
                    .containsExactly(project.file("migrations"));
              }
            });
  }

  // This test shouldn't be necessary, had the documentation been clearer
  @Test
  void configuringTaskComplementsExtensionForMultivaluedProperties() {
    project.getPluginManager().apply(FlywayPlugin.class);

    var flywayExtension = project.getExtensions().getByType(FlywayExtension.class);
    flywayExtension.getConfiguration().put("extension", "extension");
    flywayExtension.getMigrationLocations().from(project.file("extension"));

    project
        .getTasks()
        .withType(FlywayTask.class)
        .forEach(
            flywayTask -> {
              flywayTask.getConfiguration().put("task", "task");
              if (flywayTask instanceof MigrationsFlywayTask migrationsFlywayTask) {
                migrationsFlywayTask.getMigrationLocations().from(project.file("task"));
              }

              assertThat(flywayTask.getConfiguration().getOrNull())
                  .containsExactly("extension", "extension", "task", "task");
              if (flywayTask instanceof MigrationsFlywayTask migrationsFlywayTask) {
                assertThat(migrationsFlywayTask.getMigrationLocations().getFiles())
                    .containsExactly(project.file("extension"), project.file("task"));
              }
            });

    // Adding entries to the extension after configuring the task
    flywayExtension.getConfiguration().put("new-key", "extension");
    flywayExtension.getMigrationLocations().from(project.file("other-extension"));

    project
        .getTasks()
        .withType(FlywayTask.class)
        .forEach(
            flywayTask -> {
              assertThat(flywayTask.getConfiguration().getOrNull())
                  .containsExactly(
                      "extension", "extension", "task", "task", "new-key", "extension");

              // Overriding an entry from the extension with a new value
              flywayTask.getConfiguration().put("new-key", "task");

              assertThat(flywayTask.getConfiguration().getOrNull())
                  .containsExactly("extension", "extension", "task", "task", "new-key", "task");

              if (flywayTask instanceof MigrationsFlywayTask migrationsFlywayTask) {
                assertThat(migrationsFlywayTask.getMigrationLocations().getFiles())
                    .containsExactly(
                        project.file("extension"),
                        project.file("task"),
                        project.file("other-extension"));
              }
            });

    // Resetting the extension values
    flywayExtension.getConfiguration().set(Map.of("reset-extension", "extension"));
    flywayExtension.getMigrationLocations().setFrom(project.file("reset-extension"));

    project
        .getTasks()
        .withType(FlywayTask.class)
        .forEach(
            flywayTask -> {
              assertThat(flywayTask.getConfiguration().getOrNull())
                  .containsExactly(
                      "reset-extension", "extension", "task", "task", "new-key", "task");

              if (flywayTask instanceof MigrationsFlywayTask migrationsFlywayTask) {
                assertThat(migrationsFlywayTask.getMigrationLocations().getFiles())
                    .containsExactly(project.file("reset-extension"), project.file("task"));
              }
            });
  }
}
