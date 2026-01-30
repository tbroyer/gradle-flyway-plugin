import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.nullaway)
}

group = "net.ltgt.gradle"

dependencies {
    errorprone(libs.errorprone)
    errorprone(libs.nullaway)

    compileOnly(libs.flyway.core)
}

nullaway {
    onlyNullMarked = true
    jspecifyMode = true
}
tasks {
    withType<JavaCompile>().configureEach {
        options.release = 21
        options.compilerArgs.addAll(listOf("-Werror", "-Xlint:all"))
    }
    compileJava {
        options.release = 8
        options.compilerArgs.add("-Xlint:-options")
        options.errorprone {
            // Gradle uses javax.inject in a specific way
            disable("InjectOnConstructorOfAbstractClass")
            disable("JavaxInjectOnAbstractMethod")
        }
    }
    javadoc {
        (options as CoreJavadocOptions).addBooleanOption("Xdoclint:all,-missing", true)
    }
}

testing {
    suites {
        withType(JvmTestSuite::class).configureEach {
            useJUnitJupiter(libs.versions.junit.jupiter)
            dependencies {
                implementation(libs.truth)
            }
            targets.configureEach {
                testTask {
                    testLogging {
                        showExceptions = true
                        showStackTraces = true
                        exceptionFormat = TestExceptionFormat.FULL
                    }
                }
            }
        }

        val test by getting(JvmTestSuite::class) {
            dependencies {
                implementation(project())
            }
        }

        val functionalTest by registering(JvmTestSuite::class) {
            dependencies {
                implementation(gradleTestKit())
                implementation(libs.flyway.core)
                implementation(libs.h2)
            }

            // make plugin-under-test-metadata.properties accessible to TestKit
            gradlePlugin.testSourceSet(sources)

            targets.configureEach {
                testTask {
                    shouldRunAfter(test)

                    val testJavaToolchain = project.findProperty("test.java-toolchain")
                    testJavaToolchain?.also {
                        val launcher =
                            project.javaToolchains.launcherFor {
                                languageVersion.set(JavaLanguageVersion.of(testJavaToolchain.toString()))
                            }
                        val metadata = launcher.get().metadata
                        systemProperty("test.java-version", metadata.languageVersion.asInt())
                        systemProperty("test.java-home", metadata.installationPath.asFile.canonicalPath)
                    }

                    val testGradleVersion = project.findProperty("test.gradle-version")
                    testGradleVersion?.also { systemProperty("test.gradle-version", testGradleVersion) }

                    systemProperty("test.flyway-version", libs.versions.flyway.get())
                    systemProperty("test.h2-version", libs.versions.h2.get())
                }
            }
        }
    }
}
tasks {
    check {
        dependsOn(testing.suites)
    }
}

gradlePlugin {
    website.set("https://github.com/tbroyer/gradle-flyway-plugin")
    vcsUrl.set("https://github.com/tbroyer/gradle-flyway-plugin")
    plugins {
        register("flyway") {
            id = "net.ltgt.flyway"
            implementationClass = "net.ltgt.gradle.flyway.FlywayPlugin"
            displayName = "Gradle plugin for FlywayDB"
            description = "Adds Gradle tasks to run FlywayDB commands"
            tags.addAll("flyway", "flywaydb")
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Gradle plugin for FlywayDB")
            description.set("Adds Gradle tasks to run FlywayDB commands")
            url.set("https://github.com/tbroyer/gradle-flyway-plugin")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                }
            }
            developers {
                developer {
                    name.set("Thomas Broyer")
                    email.set("t.broyer@ltgt.net")
                }
            }
            scm {
                connection.set("https://github.com/tbroyer/gradle-flyway-plugin.git")
                developerConnection.set("scm:git:ssh://github.com:tbroyer/gradle-flyway-plugin.git")
                url.set("https://github.com/tbroyer/gradle-flyway-plugin")
            }
        }
    }
}

spotless {
    kotlinGradle {
        ktlint(libs.versions.ktlint.get())
    }
    java {
        googleJavaFormat(libs.versions.googleJavaFormat.get())
        licenseHeaderFile("LICENSE.header")
    }
}
