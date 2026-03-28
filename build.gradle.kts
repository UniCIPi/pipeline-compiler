plugins {
    kotlin("jvm") version "2.1.10"
    application
}

application {
    mainClass.set("com.bringauto.pipelinecompiler.MainKt") // Adjust to your actual class path
}

group = "com.bringauto.pipelinecompiler"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
    testImplementation(kotlin("test"))
    implementation("dev.turingcomplete:text-case-converter:2.0.0")
    implementation("dev.turingcomplete:text-case-converter-kotlin-extension:2.0.0")
    implementation("com.squareup:kotlinpoet:2.1.0")
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    implementation("com.cronutils:cron-utils:9.2.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}