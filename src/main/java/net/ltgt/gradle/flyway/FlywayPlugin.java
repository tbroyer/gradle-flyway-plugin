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

import java.io.File;
import javax.inject.Inject;
import net.ltgt.gradle.flyway.tasks.FlywayClean;
import net.ltgt.gradle.flyway.tasks.FlywayMigrate;
import net.ltgt.gradle.flyway.tasks.FlywayRepair;
import net.ltgt.gradle.flyway.tasks.FlywayTask;
import net.ltgt.gradle.flyway.tasks.MigrationsFlywayTask;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ResolvableConfiguration;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

abstract class FlywayPlugin implements Plugin<Project> {

  private final JavaToolchainService javaToolchainService;

  @Inject
  public FlywayPlugin(JavaToolchainService javaToolchainService) {
    this.javaToolchainService = javaToolchainService;
  }

  @Override
  public void apply(Project project) {
    @SuppressWarnings("UnstableApiUsage")
    NamedDomainObjectProvider<ResolvableConfiguration> flywayClasspathConfiguration =
        registerConfigurations(project);

    FlywayExtension flywayExtension = registerExtension(project);

    registerTasks(project, flywayClasspathConfiguration, flywayExtension);

    project
        .getPluginManager()
        .withPlugin("java-base", appliedPlugin -> configureToolchain(project));

    project
        .getPluginManager()
        .withPlugin("java", appliedPlugin -> configureJavaDefaults(project, flywayExtension));
  }

  @SuppressWarnings("UnstableApiUsage")
  private NamedDomainObjectProvider<ResolvableConfiguration> registerConfigurations(
      Project project) {
    NamedDomainObjectProvider<DependencyScopeConfiguration> flywayConfiguration =
        project.getConfigurations().dependencyScope("flyway");
    return project
        .getConfigurations()
        .resolvable(
            "flywayClasspath",
            configuration -> configuration.extendsFrom(flywayConfiguration.get()));
  }

  private FlywayExtension registerExtension(Project project) {
    FlywayExtension flywayExtension =
        project.getExtensions().create("flyway", FlywayExtension.class);
    flywayExtension.getUrl().convention(project.getProviders().gradleProperty("flyway.url"));
    flywayExtension.getUser().convention(project.getProviders().gradleProperty("flyway.user"));
    flywayExtension
        .getPassword()
        .convention(project.getProviders().gradleProperty("flyway.password"));
    flywayExtension
        .getConfiguration()
        .set(project.getProviders().gradlePropertiesPrefixedBy("flyway."));
    return flywayExtension;
  }

  private void registerTasks(
      Project project,
      @SuppressWarnings("UnstableApiUsage")
          NamedDomainObjectProvider<ResolvableConfiguration> flywayClasspathConfiguration,
      FlywayExtension flywayExtension) {
    registerTask(
        "flywayMigrate",
        FlywayMigrate.class,
        "Migrates the schema to the latest version.",
        project,
        flywayClasspathConfiguration,
        flywayExtension);
    registerTask(
        "flywayRepair",
        FlywayRepair.class,
        "Repairs the schema history table.",
        project,
        flywayClasspathConfiguration,
        flywayExtension);
    registerTask(
        "flywayClean",
        FlywayClean.class,
        "Drops all objects in the configured schemas.",
        project,
        flywayClasspathConfiguration,
        flywayExtension);
  }

  @SuppressWarnings("UnstableApiUsage")
  private void registerTask(
      String taskName,
      Class<? extends FlywayTask> flywayTaskClass,
      String description,
      Project project,
      NamedDomainObjectProvider<ResolvableConfiguration> flywayClasspathConfiguration,
      FlywayExtension flywayExtension) {
    project
        .getTasks()
        .register(
            taskName,
            flywayTaskClass,
            task -> {
              task.setGroup("Flyway");
              task.setDescription(description);

              task.getClasspath().from(flywayClasspathConfiguration);
              task.getUrl().convention(flywayExtension.getUrl());
              task.getUser().convention(flywayExtension.getUser());
              task.getPassword().convention(flywayExtension.getPassword());
              task.getConfigurationFile().convention(flywayExtension.getConfigurationFile());
              task.getConfiguration().set(flywayExtension.getConfiguration());
              if (task instanceof MigrationsFlywayTask) {
                ((MigrationsFlywayTask) task)
                    .getMigrationLocations()
                    .from(flywayExtension.getMigrationLocations());
              }
            });
  }

  private void configureToolchain(Project project) {
    JavaToolchainSpec toolchain =
        project.getExtensions().getByType(JavaPluginExtension.class).getToolchain();
    project
        .getTasks()
        .withType(FlywayTask.class)
        .configureEach(
            task -> task.getJavaLauncher().convention(javaToolchainService.launcherFor(toolchain)));
  }

  private void configureJavaDefaults(Project project, FlywayExtension flywayExtension) {
    flywayExtension.getConfigurationFile().convention(getResourceFile(project, "db/flyway.conf"));
    flywayExtension.getMigrationLocations().from(getResourceFile(project, "db/migration"));
  }

  private Provider<RegularFile> getResourceFile(Project project, String path) {
    // XXX: using the task only to have a provider with a build dependency on the task
    SourceSet mainSourceSet = getMainSourceSet(project);
    return project
        .getLayout()
        .file(
            project
                .getTasks()
                .named(mainSourceSet.getProcessResourcesTaskName())
                .map(
                    processResources ->
                        new File(mainSourceSet.getOutput().getResourcesDir(), path)));
  }

  private SourceSet getMainSourceSet(Project project) {
    return project
        .getExtensions()
        .getByType(SourceSetContainer.class)
        .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
  }
}
