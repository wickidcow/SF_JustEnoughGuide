@file:Suppress("VulnerableLibrariesLocal", "UnstableApiUsage")

import java.util.zip.ZipFile

/*
* Copyright (c) 2024-2026 balugaq
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, version 3.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*
*/
plugins {
    java
    alias(libs.plugins.shadow.jar)
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("maven-publish")
    id("signing")
    id("io.github.sgtsilvio.gradle.maven-central-publishing") version "0.5.0"
}

group = "io.github.balugaq"
version = "2.1.74"
// 2.1.74: JEG guide listener first-join parity and safe partial recipe withdrawals.

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://jitpack.io")
    maven("https://repo.aikar.co/content/groups/aikar/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    maven("https://repo.tcoded.com/releases")
    maven("https://mvn.wesjd.net/")
    maven("https://maven.norain.city/snapshots")
    maven("https://repo.alessiodp.com/releases")
    maven("https://repo.jeff-media.com/public")
    exclusiveContent {
        forRepository {
            maven("https://repo.extendedclip.com/releases")
        }
        filter {
            includeGroup("me.clip")
        }
    }
}

dependencies {
    // Paper and Slimefun compile-only dependencies
    compileOnly(libs.paper.api)
    compileOnly(libs.slimefun4)

    // Dependencies shaded into the distributable JAR
    implementation(libs.bstats.bukkit)
    implementation(libs.more.persistent.data.types)
    implementation(libs.anvilgui)
    implementation(libs.folia.lib)
    implementation(libs.libby.bukkit)
    implementation(libs.jetbrains.annotations)
    implementation(libs.jspecify)
    // ACF command framework, shaded into the distributable JAR
    implementation(libs.acf.paper)

    compileOnly(libs.findbugs.annotations)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Search and shared-character dictionaries must work with an empty runtime
    // library cache and no Maven access, including when Pinyin search is disabled.
    implementation(libs.houbb.pinyin)
    implementation(libs.houbb.opencc4j)
    implementation(libs.houbb.heaven)
    implementation(libs.houbb.nlp.common)

    compileOnly(libs.slimefun.translation)
    compileOnly(libs.placeholderapi)

    compileOnly(libs.networks.expansion)
    compileOnly(libs.slime.ae)
    compileOnly(libs.cmi.lib)
    compileOnly(libs.gugu.slimefun.lib)
    compileOnly(libs.emc.tech)
    compileOnly(libs.slime.hud)
    compileOnly(libs.slimefun.recipe)
    compileOnly(libs.ryken.slime.customizer)
    compileOnly(libs.logi.tech)

    // Optional local compile-only JARs
    compileOnly(fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar"))))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<Javadoc>().configureEach {
    // Keep documentation warnings from blocking local builds or releases.
    isFailOnError = false
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        charSet = "UTF-8"
        addStringOption("Xdoclint", "none")
    }
}

// Keep forked JVM tasks on UTF-8 so logs and generated resources remain portable.
tasks.withType<JavaExec>().configureEach {
    systemProperty("file.encoding", "UTF-8")
    systemProperty("sun.stdout.encoding", "UTF-8")
    systemProperty("sun.stderr.encoding", "UTF-8")
}

val searchRuntimeClasspath = configurations.named("runtimeClasspath")

tasks {
    // The thin Gradle JAR is not a runnable plugin because runtime libraries such
    // as Libby, FoliaLib and ACF are supplied by shadowJar. Publishing only the
    // shaded plugin prevents bundle tooling from accidentally selecting the thin JAR.
    jar {
        enabled = false
    }

    compileJava {
        val compatibilityReport = providers.gradleProperty("jegDeprecationReport")
            .map(String::toBoolean)
            .orElse(false)
            .get()
        if (compatibilityReport) {
            options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
        } else {
            options.compilerArgs.add("-Xlint:-removal")
        }
        // ACF uses -parameters for parameter-name based syntax hints
        options.compilerArgs.add("-parameters")
        options.encoding = "UTF-8"
        options.release = 21
    }

    shadowJar {
        archiveBaseName.set("SF_JustEnoughGuide")
        archiveVersion.set("")
        archiveClassifier.set("")
        archiveFileName.set("SF_JustEnoughGuide${project.version}.jar")

        // Relocations
        relocate("net.Zrips.CMILib", "com.balugaq.jeg.libraries.cmilib")
        relocate("com.tcoded.folialib", "com.balugaq.jeg.libraries.folialib")
        relocate("net.byteflux.libby", "com.balugaq.jeg.libraries.libby")
        relocate("com.jeff_media", "com.balugaq.jeg.libraries.jeff_media")
        relocate("org.bstats", "com.balugaq.jeg.libraries.bstats")
        relocate("net.wesjd.anvilgui", "com.balugaq.jeg.libraries.anvilgui")
        // Shade and relocate ACF to avoid class conflicts with other plugins.
        relocate("co.aikar.commands", "com.balugaq.jeg.libraries.acf.commands")
        relocate("co.aikar.locales", "com.balugaq.jeg.libraries.acf.locales")

        // Keep Houbb's existing namespaces and absolute dictionary paths. The
        // public SearchGroup.getPinyin overload exposes PinyinStyleEnum, and
        // previous Libby loading exposed these same unrelocated classes.

        // Exclude unwanted files
        exclude("META-INF/*")
        exclude("META-INF/maven/**")
        exclude("META-INF/versions/**")

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        mergeServiceFiles()

        doLast {
            val compiled = sourceSets.main.get().output.classesDirs.files.flatMap { root ->
                root.resolve("com/balugaq/jeg").walkTopDown().filter { it.isFile && it.extension == "class" }
                    .map { it.relativeTo(root).invariantSeparatorsPath }.toList()
            }.toSet()
            check(compiled.isNotEmpty()) { "No compiled JEG classes to verify" }
            ZipFile(archiveFile.get().asFile).use { jar ->
                val missing = compiled.filter { jar.getEntry(it) == null }
                check(missing.isEmpty()) { "Missing JEG runtime classes: $missing" }
                check(jar.getEntry("com/balugaq/jeg/utils/clickhandler/OnDisplay\$ItemGroup.class") != null) {
                    "Missing guide item-group renderer"
                }
                val notices = layout.projectDirectory.dir("src/main/resources/licenses/houbb").asFile
                check(notices.resolve("checksums.sha256").isFile && notices.resolve("ATTRIBUTION.txt").isFile) {
                    "Missing bundled search dependency notices"
                }
                for (notice in notices.walkTopDown().filter { it.isFile }) {
                    val name = "licenses/houbb/" + notice.relativeTo(notices).invariantSeparatorsPath
                    val packaged = jar.getEntry(name)
                    check(packaged != null && notice.readBytes().contentEquals(
                        jar.getInputStream(packaged).use { it.readBytes() }
                    )) { "Missing or changed search dependency notice: $name" }
                }
                val expectedLibraries = mapOf(
                    "pinyin" to libs.versions.houbbPinyin.get(),
                    "opencc4j" to libs.versions.houbbOpencc4j.get(),
                    "heaven" to libs.versions.houbbHeaven.get(),
                    "nlp-common" to libs.versions.houbbNlpCommon.get()
                )
                val libraries = searchRuntimeClasspath.get().resolvedConfiguration.resolvedArtifacts
                    .filter { it.moduleVersion.id.group == "com.github.houbb" }
                check(libraries.associate { it.name to it.moduleVersion.id.version } == expectedLibraries) {
                    "Unexpected packaged search dependency versions"
                }
                for (library in libraries) {
                    ZipFile(library.file).use { original ->
                        for (entry in original.entries().asSequence().filter {
                            !it.isDirectory && (it.name.startsWith("com/github/houbb/") || it.name.endsWith(".txt"))
                        }) {
                            val packaged = jar.getEntry(entry.name)
                            check(packaged != null) { "Missing packaged search dependency entry: ${entry.name}" }
                            if (!entry.name.endsWith(".class")) {
                                check(original.getInputStream(entry).use { it.readBytes() }.contentEquals(
                                    jar.getInputStream(packaged).use { it.readBytes() }
                                )) { "Changed packaged search dictionary: ${entry.name}" }
                            }
                        }
                    }
                }
            }
        }
    }

    processResources {
        filesMatching("**/*.yml") {
            expand(project.properties)
        }
        filesMatching("**/*.properties") {
            expand(project.properties)
        }
    }

    build {
        dependsOn(shadowJar)
    }

    runServer {
        dependsOn(shadowJar)
        val run = file(providers.gradleProperty("server.run.dir").orElse("run"))
        runDirectory.set(run)

        doFirst {
            run.resolve("eula.txt").writeText("eula=true")

            val pl = run.resolve("plugins")
            pl.mkdirs()
            copy {
                from(projectDir.resolve("build/libs")) {
                    include("SF_JustEnoughGuide${project.version}.jar")
                }
                into(pl)
            }
        }

        jvmArgs(
            "-Dfile.encoding=UTF-8",
            "-Dsun.jnu.encoding=UTF-8",
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5001",
            "-Dnet.kyori.adventure.text.warn_when_legacy_formatting_detected=false"
        )
        maxHeapSize = "4G"
        minecraftVersion("1.20.1")
    }
}

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.named<Javadoc>("javadoc"))
}

publishing {
    repositories {
        maven {
            name = "Central"
            url = uri("https://central.sonatype.com/api/v1/publisher")
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.named("shadowJar"))
            // Maven Central requires sources and javadoc artifacts.
            artifact(sourcesJar)
            artifact(javadocJar)

            pom {
                name = "JustEnoughGuide"
                description = "A Slimefun addon for Minecraft that significantly enhances the functionality and user experience of the original Slimefun guide book."
                url = "https://github.com/balugaq/JustEnoughGuide"
                licenses {
                    license {
                        name = "GNU General Public License v3.0 or later"
                        url  = "https://www.gnu.org/licenses/gpl-3.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "balugaq"
                        name = "balugaq"
                        email = "balugaq@qq.com"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/balugaq/JustEnoughGuide.git"
                    developerConnection = "scm:git:ssh://github.com/balugaq/JustEnoughGuide.git"
                    url = "https://github.com/balugaq/JustEnoughGuide"
                }
            }
        }
    }
}

// Isolate the packaged search runtime from every server, Gradle dependency and
// Libby cache. HTTP(S) is refused inside this process. These fixture classes are
// outside the main source set and are never included in the distributable JAR.
val compilePackagedPinyinProbe = tasks.register<JavaCompile>("compilePackagedPinyinProbe") {
    source("tests/packaged-pinyin/PackagedPinyinProbe.java")
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("classes/packaged-pinyin-probe"))
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    options.release.set(21)
    options.encoding = "UTF-8"
}

val verifyPackagedPinyinRuntime = tasks.register<JavaExec>("verifyPackagedPinyinRuntime") {
    group = "verification"
    description = "Verify packaged search classes and dictionaries with HTTP(S) acquisition refused"
    dependsOn(tasks.shadowJar, compilePackagedPinyinProbe)
    classpath = files(compilePackagedPinyinProbe.flatMap { it.destinationDirectory })
    mainClass.set("io.github.wickidcow.jegtest.PackagedPinyinProbe")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    args(tasks.shadowJar.flatMap { it.archiveFile }.get().asFile.absolutePath,
        file("tests/packaged-pinyin/dictionaries.sha256").absolutePath)
}

tasks.check { dependsOn(verifyPackagedPinyinRuntime) }

val compilePinyinStartupFixture = tasks.register<JavaCompile>("compilePinyinStartupFixture") {
    source(fileTree("tests/pinyin-startup") { include("*.java") })
    classpath = sourceSets.main.get().compileClasspath
    destinationDirectory.set(layout.buildDirectory.dir("classes/pinyin-startup-fixture"))
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(25)) })
    options.release.set(21)
    options.encoding = "UTF-8"
}

val pinyinStartupProbeJar = tasks.register<Jar>("pinyinStartupProbeJar") {
    dependsOn(compilePinyinStartupFixture)
    archiveFileName.set("JEGPinyinProbe.jar")
    destinationDirectory.set(layout.buildDirectory.dir("pinyin-fixtures"))
    from(compilePinyinStartupFixture.flatMap { it.destinationDirectory }) {
        include("**/PinyinStartupProbe*.class")
    }
    from("tests/pinyin-startup/plugin.yml")
}

val pinyinAcquisitionBlockerJar = tasks.register<Jar>("pinyinAcquisitionBlockerJar") {
    dependsOn(compilePinyinStartupFixture)
    archiveFileName.set("JEGAcquisitionBlocker.jar")
    destinationDirectory.set(layout.buildDirectory.dir("pinyin-fixtures"))
    from(compilePinyinStartupFixture.flatMap { it.destinationDirectory }) {
        include("**/AcquisitionBlocker*.class")
    }
    manifest { attributes("Premain-Class" to "io.github.wickidcow.jegtest.AcquisitionBlocker") }
}

tasks.check { dependsOn(pinyinStartupProbeJar, pinyinAcquisitionBlockerJar) }

// Signing configuration
signing {
    // Read signing material from Gradle properties or environment variables only when supplied.
    val signingKey = providers.gradleProperty("signingKey")
        .orElse(providers.systemProperty("signingKey"))
        .orElse(providers.environmentVariable("SIGNING_KEY"))
        .orNull

    val signingPassword = providers.gradleProperty("signingPassword")
        .orElse(providers.systemProperty("signingPassword"))
        .orElse(providers.environmentVariable("SIGNING_PASSWORD"))
        .orNull
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    } else {
        // No signing key was supplied; local/development builds remain unsigned.
    }
}
