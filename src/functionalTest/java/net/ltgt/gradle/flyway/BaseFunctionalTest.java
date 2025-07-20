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

import static java.util.Objects.requireNonNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import org.gradle.api.JavaVersion;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

public abstract class BaseFunctionalTest {
  public static final GradleVersion testGradleVersion =
      Optional.ofNullable(System.getProperty("test.gradle-version"))
          .map(GradleVersion::version)
          .orElseGet(GradleVersion::current);

  public static final JavaVersion testJavaVersion =
      Optional.ofNullable(System.getProperty("test.java-version"))
          .map(JavaVersion::toVersion)
          .orElseGet(JavaVersion::current);
  public static final String testJavaHome =
      System.getProperty("test.java-home", System.getProperty("java.home"));

  public static final String flywayVersion =
      requireNonNull(System.getProperty("test.flyway-version"));

  public static final String h2Version = requireNonNull(System.getProperty("test.h2-version"));

  @TempDir protected Path projectDir;

  protected Properties gradleProperties;

  protected final Path getSettingsFile() {
    return projectDir.resolve("settings.gradle.kts");
  }

  protected final Path getBuildFile() {
    return projectDir.resolve("build.gradle.kts");
  }

  @BeforeEach
  void setupProject() throws Exception {
    gradleProperties = new Properties();
    gradleProperties.setProperty("org.gradle.java.home", testJavaHome);
    gradleProperties.setProperty("org.gradle.configuration-cache", "true");
    Files.writeString(
        getSettingsFile(),
        """
        dependencyResolutionManagement {
            repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
            repositories {
                mavenCentral()
            }
        }
        """);
  }

  protected final BuildResult buildWithArgs(String... args) throws Exception {
    return prepareBuild(args).build();
  }

  protected final BuildResult buildWithArgsAndFail(String... args) throws Exception {
    return prepareBuild(args).buildAndFail();
  }

  protected final GradleRunner prepareBuild(String... args) throws Exception {
    try (var os = Files.newOutputStream(projectDir.resolve("gradle.properties"))) {
      gradleProperties.store(os, null);
    }
    return GradleRunner.create()
        .withGradleVersion(testGradleVersion.getVersion())
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(args)
        .forwardOutput();
  }
}
