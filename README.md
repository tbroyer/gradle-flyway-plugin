# flyway-gradle-plugin

This plugin is an alternative to [Flyway](https://www.red-gate.com/products/flyway/community/)'s [own Gradle plugin](https://plugins.gradle.org/plugin/org.flywaydb.flyway), that isn't actively maintained (as of July 2025, some issues have been open for two years, related to not using _lazy_ configuration APIs, warnings of things that will break with Gradle 9, and incompatibilities with the configuration cache).

It's tailored for my own needs:

 * Flyway is embedded in the application, that will be responsible for applying the migrations in production (no separate Flyway CLI or Flyway Desktop)
 * Flyway configuration (except for the datasource) is loaded from a (UTF-8 encoded) properties file
 * Gradle tasks are only used for development, against a local database, that developers are encouraged to wipe and rebuild at will ; data is either managed using migrations (possibly repeatable migrations, in an additional directory used only for development), or by restoring a dump
 * Migrations are only implemented as SQL scripts (no Java-based migrations)

That being said, the plugin might be used in different situations: those needs mostly inform priorities (but also the DSL, and default values).

## Compatibility

 * Gradle >= 8.5 (including Gradle 9)
 * Java >= 8 (though it's only tested with Java >= 17)

It's built against the latest stable version of Flyway Community, but should work with earlier versions (as long as Flyway is backwards compatible).

## Usage

1. Apply the plugin:

   ```kotlin
   plugins {
       id("net.ltgt.flyway") version "…"
   }
   ```

2. Add dependencies to Flyway and your JDBC driver:

   ```kotlin
   dependencies {
       flyway("org.flywaydb:flyway-core:$flywayVersion")
       flyway("org.flywaydb:flyway-database-postgresql:$flywayVersion")
       flyway("org.postgresql:postgresql:$postgresqlVersion")
   }
   ```

3. Configure the plugin (see [below](#configuration "Configuration") for the full list of properties):

   ```kotlin
   flyway {
       url = "jdbc:postgresql:database"
       user = "user"
       password = "password"
   }
   ```

   The `url`, `user` and `password` properties default to using the `flyway.url`, `flyway.user` and `flyway.password` Gradle properties, so you can put those in your `gradle.properties` file if you prefer and/or pass them on the command-line with `-P`.

   The `configuration` property defaults to all the Gradle properties whose name starts with `flyway.`. Note that because this is a convention value, any explicit configuration in the script (like in the snippet above) will entirely replace the whole map.  
   You can reinstate those values yourself using `providers.gradlePropertiesPrefixedWith("flyway.")`.

   If you're also applying the `java` plugin, then the `configurationFile` and `migrationLocations` default respectively to the `db/flyway.conf` and `db/migration/` from the main source set (after being processed by the `processResources` task).

   You can override the configuration from `configurationFile` with the `configuration` property.

   Note that the configuration file is read as a properties file; TOML files aren't supported.

4. You can now use the `flywayMigrate`, `flywayClean`, etc. tasks.

   Each task inherits its default configuration from the `flyway` extension configured above. The `url`, `user`, `password`, and (for `flywayMigrate`) `target` properties can also be overridden from the command line:

   ```shell
   ./gradlew flywayMigrate --target=42
   ```
   
## Available tasks

* `flywayMigrate` migrates the schema to the latest version.
* `flywayClean` drops all objects in the configured schemas.
* `flywayRepair` repairs the schema history table (rarely used, but sometimes useful).

## Migrating from `org.flywaydb.flyway`

Configuration of the plugin is widely different, as this plugin doesn't expose a full DSL for the Flyway configuration, but only the `configuration` map and a handful of other properties.  
I used to load the configuration file as a `java.util.Properties` right from my build script to configure properties on the `flyway` extension (the `schemas` one mainly), so I built this right into the plugin as a better alternative.

From a developer's point of view, once the project is configured, tasks are named the same so it shouldn't change much, except that the set of available tasks is narrower.

## Configuration

 Property | Default value | Command-line option | Description 
:---------|:--------------|:--------------------|:------------
`url`                | The `flyway.url` Gradle property | `--url` | The jdbc url to use to connect to the database.
`user`               | The `flyway.user` Gradle property | `--user` | The user to use to connect to the database.
`password`           | The `flyway.password` Gradle property | `--password` | The password to use to connect to the database.
`migrationLocations` | `db/migration/` in the _main_ source set, if the `java` plugin is applied | | The directories containing migration scripts.
`configurationFile`  | `db/flyway.conf` in the _main_ source set, if the `java` plugin is applied | | The configuration file to use; must be a properties file encoded in UTF-8.
`configuration`      | All the Gradle properties whose name start with `flyway.` | | The configuration properties, overwriting the ones from the `configurationFile`.
`target`             | | `--target` | (Only on tasks where it makes sense, i.e. `flywayMigrate`) The target version up to which Flyway should consider migrations.
`javaLauncher`       | Based on the project's `java.toolchain`, if the `java-base` plugin is applied | | (only on tasks, not the extension) The java executable used to run Flyway.
