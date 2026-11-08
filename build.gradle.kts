import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    java
    id("org.springframework.boot") version "4.1.2"
}

group = "io.moviesites"
version = "1.0.0"
description = "A self-hosted folder-based video platform"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
    val temporaryDirectory = layout.projectDirectory.dir(".cache/tmp").asFile
    systemProperty("java.io.tmpdir", temporaryDirectory.absolutePath)
    jvmArgs("-XX:-UsePerfData")
    doFirst { temporaryDirectory.mkdirs() }
}

tasks.processResources {
    exclude("**/*.mp4", "**/*.mov", "**/*.mkv", "**/*.webm")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.bootJar {
    archiveFileName = "moviesites.jar"
}

tasks.jar {
    enabled = false
}
