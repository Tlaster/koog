import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension

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

fun Project.configureSnapshotSigningIfPossible() {
    val signing = extensions.findByType(SigningExtension::class.java) ?: return
    val publishing = extensions.findByType(PublishingExtension::class.java) ?: return
    val signingKey = snapshotProperty("signingKey", "SIGNING_KEY")
        ?.replace("\\n", "\n")
        ?: return
    val signingPassword = requireSnapshotProperty("signingPassword", "SIGNING_PASSWORD")

    signing.useInMemoryPgpKeys(signingKey, signingPassword)
    publishing.publications.withType(MavenPublication::class.java).configureEach {
        signing.sign(this)
    }
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

        configureSnapshotSigningIfPossible()

        tasks.withType(PublishToMavenRepository::class.java).configureEach {
            onlyIf("Central Portal snapshot repository only accepts -SNAPSHOT versions") {
                !name.endsWith("To${snapshotRepositoryName}Repository") ||
                    project.version.toString().endsWith("-SNAPSHOT")
            }
            if (name.endsWith("To${snapshotRepositoryName}Repository")) {
                dependsOn(tasks.withType(Sign::class.java))
            }
        }
    }

    plugins.withId("signing") {
        configureSnapshotSigningIfPossible()
    }
}

gradle.projectsEvaluated {
    rootProject.tasks.register("publishAllSnapshotsToCentralPortal") {
        group = "publishing"
        description = "Publishes all Maven publications to the Central Portal snapshot repository."

        doFirst {
            check(rootProject.group.toString() == "moe.tlaster") {
                "Snapshot publishing is expected to use group 'moe.tlaster', actual '${rootProject.group}'."
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
