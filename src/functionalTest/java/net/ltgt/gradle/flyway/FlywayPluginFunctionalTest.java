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
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.math.BigInteger;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.sql.SQLSyntaxErrorException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class FlywayPluginFunctionalTest extends BaseFunctionalTest {

  @RegisterExtension DatabaseExtension database = new DatabaseExtension(() -> projectDir);

  @Test
  void test() throws Exception {
    Files.writeString(
        getBuildFile(),
        // language=kts
        """
        plugins {
            id("net.ltgt.flyway")
        }

        dependencies {
          flyway("org.flywaydb:flyway-core:%1$s")
          flyway("com.h2database:h2:%2$s")
        }

        flyway {
            url = "%3$s"
            migrationLocations.from(file("migrations"))
        }
        """
            .formatted(flywayVersion, h2Version, database.getURL()));
    var migrationDir = projectDir.resolve("migrations");
    Files.createDirectory(migrationDir);
    Files.writeString(migrationDir.resolve("V0__create_test_table.sql"), "CREATE TABLE TEST;");

    var flyway =
        Flyway.configure()
            .dataSource(database.getURL(), null, null)
            .locations(Location.FILESYSTEM_PREFIX + migrationDir.toString())
            .load();

    BuildResult result;

    assertThat(flyway.info().current()).isNull();
    assertTableDoesNotExist("TEST");

    result = buildWithArgs("flywayMigrate");
    assertThat(requireNonNull(result.task(":flywayMigrate")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    assertThat(flyway.info().current().getVersion().getMajor()).isEqualTo(BigInteger.ZERO);
    assertTableExists("TEST");

    // Re-running the task, it's never up-to-date, and doesn't fail
    result = buildWithArgs("flywayMigrate");
    assertThat(requireNonNull(result.task(":flywayMigrate")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    // Change the migration script
    Files.writeString(migrationDir.resolve("V0__create_test_table.sql"), "CREATE TABLE TEST_2;");

    assumeFalse(flyway.validateWithResult().validationSuccessful);

    result = buildWithArgsAndFail("flywayMigrate");
    assertThat(requireNonNull(result.task(":flywayMigrate")).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(result.getOutput()).contains("Migrations have failed validation");

    result = buildWithArgs("flywayRepair");
    assertThat(requireNonNull(result.task(":flywayRepair")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    assertThat(flyway.info().current().getVersion().getMajor()).isEqualTo(BigInteger.ZERO);
    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    assertTableExists("TEST");
    assertTableDoesNotExist("TEST_2");

    result = buildWithArgs("flywayMigrate");
    assertThat(requireNonNull(result.task(":flywayMigrate")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    result = buildWithArgs("flywayClean", "-Pflyway.cleanDisabled=false");
    assertThat(requireNonNull(result.task(":flywayClean")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    assertThat(flyway.info().current()).isNull();
    assertTableDoesNotExist("TEST");
    assertTableDoesNotExist("TEST_2");
  }

  // This also tests that the task doesn't fail if the configuration file doesn't exist
  @Test
  void javaProject() throws Exception {
    Files.writeString(
        getBuildFile(),
        // language=kts
        """
        plugins {
            id("net.ltgt.flyway")
            `java-library`
        }

        dependencies {
          flyway("org.flywaydb:flyway-core:%1$s")
          flyway("com.h2database:h2:%2$s")
        }

        flyway {
            url = "%3$s"
        }

        tasks {
            processResources {
                expand("table" to "TEST")
            }
        }
        """
            .formatted(flywayVersion, h2Version, database.getURL()));
    Files.createDirectories(projectDir.resolve("src/main/resources/db/migration"));
    Files.writeString(
        projectDir.resolve("src/main/resources/db/migration/V0.sql"), "CREATE TABLE ${table};");

    var result = buildWithArgs("flywayMigrate");
    assertThat(requireNonNull(result.task(":processResources")).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    assertTableExists("TEST");
  }

  @Test
  void tasksWithoutMigrationsAreSkipped() throws Exception {
    Files.writeString(
        getBuildFile(),
        // language=kts
        """
        plugins {
            id("net.ltgt.flyway")
        }

        dependencies {
          flyway("org.flywaydb:flyway-core:%1$s")
          flyway("com.h2database:h2:%2$s")
        }

        flyway {
            url = "%3$s"
        }
        """
            .formatted(flywayVersion, h2Version, database.getURL()));

    BuildResult result = buildWithArgs("flywayMigrate", "flywayRepair");

    assertThat(requireNonNull(result.task(":flywayMigrate")).getOutcome())
        .isEqualTo(TaskOutcome.NO_SOURCE);
    assertThat(requireNonNull(result.task(":flywayRepair")).getOutcome())
        .isEqualTo(TaskOutcome.NO_SOURCE);
  }

  private void assertTableExists(String table) throws Exception {
    try (var conn = DriverManager.getConnection(database.getURL());
        var stmt = conn.createStatement()) {
      // Do nothing with the results: if the query passed, it means the table exists
      stmt.executeQuery("SELECT * FROM %s;".formatted(table)).close();
    }
  }

  private void assertTableDoesNotExist(String table) throws Exception {
    try (var conn = DriverManager.getConnection(database.getURL());
        var stmt = conn.createStatement()) {
      assertThrows(
          SQLSyntaxErrorException.class,
          // Do nothing with the results: if the query passed (and it shouldn't),
          // it means the table exists
          () -> stmt.executeQuery("SELECT * FROM %s;".formatted(table)).close());
    }
  }
}
