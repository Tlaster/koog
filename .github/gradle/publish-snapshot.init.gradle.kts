import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

val snapshotRepositoryName = "CentralPortalSnapshots"
val defaultSnapshotRepositoryUrl = "https://central.sonatype.com/repository/maven-snapshots/"

fun Project.snapshotProperty(name: String, envName: String): String? {
    return (findProperty(name) as? String)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envName)?.takeIf { it.isNotBlank() }
}

fun Project.requireSnapshotProperty(name: String, envName: String): String {
    return snapshotProperty(name, envName)
        ?: error("Missing $envName or Gradle property '$name'.")
}

fun Project.isSnapshotPublishableProject(): Boolean {
    return buildFile.exists() && "publishToMaven()" in buildFile.readText()
}

allprojects {
    plugins.withId("maven-publish") {
        if (!isSnapshotPublishableProject()) return@withId

        extensions.configure(PublishingExtension::class.java) {
            repositories {
                maven {
                    name = snapshotRepositoryName
                    url = uri(
                        snapshotProperty("snapshotRepositoryUrl", "SNAPSHOT_REPOSITORY_URL")
                            ?: defaultSnapshotRepositoryUrl
                    )
                    credentials(PasswordCredentials::class.java) {
                        username = requireSnapshotProperty("ossrhUsername", "OSSRH_USERNAME")
                        password = requireSnapshotProperty("ossrhPassword", "OSSRH_PASSWORD")
                    }
                    mavenContent {
                        snapshotsOnly()
                    }
                }
            }
        }

        tasks.withType(PublishToMavenRepository::class.java).configureEach {
            onlyIf("Central Portal snapshot repository only accepts -SNAPSHOT versions") {
                !name.endsWith("To${snapshotRepositoryName}Repository") ||
                    project.version.toString().endsWith("-SNAPSHOT")
            }
        }
    }
}

gradle.projectsEvaluated {
    rootProject.tasks.register("publishAllSnapshotsToCentralPortal") {
        group = "publishing"
        description = "Publishes all Maven publications to the Central Portal snapshot repository."

        doFirst {
            check(rootProject.group.toString() == "moe.tlaster.koog") {
                "Snapshot publishing is expected to use group 'moe.tlaster.koog', actual '${rootProject.group}'."
            }
            check(rootProject.version.toString().endsWith("-SNAPSHOT")) {
                "Snapshot publishing requires a -SNAPSHOT version, actual '${rootProject.version}'."
            }
            rootProject.requireSnapshotProperty("ossrhUsername", "OSSRH_USERNAME")
            rootProject.requireSnapshotProperty("ossrhPassword", "OSSRH_PASSWORD")
            rootProject.requireSnapshotProperty("signingKey", "SIGNING_KEY")
            rootProject.requireSnapshotProperty("signingPassword", "SIGNING_PASSWORD")
        }

        val publishTasks = rootProject.allprojects.flatMap { project ->
            project.tasks.withType(PublishToMavenRepository::class.java)
                .matching { it.name.endsWith("To${snapshotRepositoryName}Repository") }
                .toList()
        }
        dependsOn(publishTasks)
    }
}
