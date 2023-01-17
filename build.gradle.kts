import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.21"
    kotlin("plugin.serialization") version "1.7.20"
    application
}

group = "de.leximon"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io/")
    maven("https://repo.tts-craft.de/releases")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation(kotlin("stdlib-jdk8"))
//    implementation("org.mongodb:mongodb-driver-sync:4.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    implementation("org.litote.kmongo:kmongo:4.8.0")

    implementation("ch.qos.logback:logback-classic:1.4.5")
    implementation("net.dv8tion:JDA:5.0.0-beta.1")
    implementation("com.github.minndevelopment:jda-ktx:17eb77a138ba356a3b0439afeddf77d4520c7c60")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}