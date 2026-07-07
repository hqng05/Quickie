import java.time.Instant

plugins {
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.4.2"
}

// ─── Project metadata ────────────────────────────────────────────────────────

val projectPackage = "tech.qhuyy"

// ─── Git helpers ─────────────────────────────────────────────────────────────

fun gitProvider(vararg args: String): Provider<String> =
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim() }.orElse("unknown")

// Provider<String> — lazy, configuration-cache-safe, serializable
val gitCommitFull    = gitProvider("rev-parse", "HEAD")
val gitCommitShort   = gitProvider("rev-parse", "--short", "HEAD")
val gitCommitMessage = gitProvider("log", "-1", "--pretty=%s")
val gitCommitTime    = gitProvider("log", "-1", "--pretty=%cI")
val gitBranch        = gitProvider("rev-parse", "--abbrev-ref", "HEAD")
val gitDirty         = providers.exec {
    commandLine("git", "status", "--porcelain")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().isNotBlank().toString() }.orElse("false")

// ─── Git properties generation ───────────────────────────────────────────────

val gitPropertiesOutput = layout.buildDirectory.file("generated/resources/git/git.properties")

val generateGitProperties by tasks.registering {
    group       = "build"
    description = "Generates git.properties with commit metadata"

    // Opt out of configuration cache — this task uses non-serializable exec
    // providers and Instant.now(), which the cache cannot persist.
    notCompatibleWithConfigurationCache("Uses providers.exec and Instant.now()")

    // Wire providers as inputs so changes invalidate the task
    inputs.property("git.commit.id",            gitCommitFull)
    inputs.property("git.commit.id.abbrev",      gitCommitShort)
    inputs.property("git.commit.message.short",  gitCommitMessage)
    inputs.property("git.commit.time",           gitCommitTime)
    inputs.property("git.branch",                gitBranch)
    inputs.property("git.dirty",                 gitDirty)

    outputs.file(gitPropertiesOutput)

    doLast {
        val props = mapOf(
            "git.commit.id"            to gitCommitFull.get(),
            "git.commit.id.abbrev"     to gitCommitShort.get(),
            "git.commit.message.short" to gitCommitMessage.get(),
            "git.commit.time"          to gitCommitTime.get(),
            "git.branch"               to gitBranch.get(),
            "git.dirty"                to gitDirty.get(),
            "git.build.time"           to Instant.now().toString(),
        )

        gitPropertiesOutput.get().asFile.apply {
            parentFile.mkdirs()
            writeText(props.entries.joinToString("\n") { "${it.key}=${it.value}" })
        }
    }
}

// ─── Source sets ─────────────────────────────────────────────────────────────

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/resources/git"))
        }
    }
}

// ─── Repositories ────────────────────────────────────────────────────────────

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.helpch.at/releases")
    maven("https://repo.tcoded.com/releases")
    maven("https://repo.rosewooddev.io/repository/public/")
    maven("https://repo.codemc.io/repository/creatorfromhell/")
}

// ─── Dependencies ────────────────────────────────────────────────────────────

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.zaxxer:HikariCP:5.1.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("com.mysql:mysql-connector-j:9.7.0")
    compileOnly("org.xerial:sqlite-jdbc:3.53.2.0")
    compileOnly("dev.jorel:commandapi-paper-core:11.2.0")
    compileOnly("com.google.code.gson:gson:2.10.1")
    // Source: https://mvnrepository.com/artifact/net.kyori/adventure-platform-bukkit
    compileOnly("net.kyori:adventure-platform-bukkit:4.4.1")

    implementation("org.bstats:bstats-bukkit:3.1.0")
    implementation("com.tcoded:FoliaLib:0.5.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

// ─── Kotlin ──────────────────────────────────────────────────────────────────

kotlin {
    jvmToolchain(25)
}

// ─── Process resources ───────────────────────────────────────────────────────

tasks.processResources {
    dependsOn(generateGitProperties)

    val props = mapOf(
        "name"       to project.name,
        "version"    to project.version.toString(),
        "gitCommit"  to gitCommitShort,
        "apiVersion" to "1.21",
    )
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

// ─── Shadow JAR ──────────────────────────────────────────────────────────────

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set(
        providers.provider { "${project.name}-${project.version}+${gitCommitShort.get()}.jar" }
    )

    // Bundle only what we ship; everything else is provided by the server
    dependencies {
        include(dependency("com.tcoded:FoliaLib"))
        include(dependency("org.bstats:bstats-bukkit"))
        include(dependency("org.bstats:bstats-base"))
    }

    // Relocate to avoid classpath conflicts with other plugins
    relocate("com.tcoded.folialib", "$projectPackage.libs.folialib")
    relocate("org.bstats",          "$projectPackage.libs.bstats")

    relocate("kotlin", "$projectPackage.libs.kotlin") {
        exclude("kotlin/kotlin.kotlin_builtins")
        exclude("kotlin/reflect/reflect.kotlin_builtins")
        exclude("META-INF/kotlin*")
    }

    // ── JAR signing / module metadata — invalid inside a fat JAR
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/NOTICE*")
    exclude("META-INF/versions/**")
    exclude("META-INF/maven/**")
    exclude("META-INF/proguard/**")

    // ── Readme / changelog noise from bundled deps
    //    LICENSE (no extension) is preserved — it does not match any pattern below
    exclude("**/*.md")
    exclude("**/*.txt")
    exclude("**/README")
    exclude("**/CHANGELOG")
    exclude("**/CHANGELOG.*")
    exclude("**/CHANGES")
    exclude("**/CHANGES.*")
    exclude("**/CONTRIBUTING")
    exclude("**/CONTRIBUTING.*")
    exclude("**/NOTICE")
}

// ─── Build lifecycle ─────────────────────────────────────────────────────────

tasks.build {
    dependsOn(tasks.shadowJar)
}

// ─── Utility tasks ───────────────────────────────────────────────────────────

tasks.register("printGitInfo") {
    group       = "help"
    description = "Prints current git information (run 'build' first)"
    doLast {
        println("\n=== Git Information ===")
        val file = gitPropertiesOutput.get().asFile
        if (file.exists()) file.readLines().forEach(::println)
        else println("Git properties not generated yet. Run './gradlew build' first.")
    }
}