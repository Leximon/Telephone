plugins {
    id("com.github.johnrengelman.shadow") version "7.1.0"
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.serialization") version "1.8.0"
}

group = project.properties["group"] as String
version = project.properties["version"] as String

repositories {
    mavenCentral()
    maven("https://jitpack.io/")
    maven("https://repo.leximon.dev/releases")
    maven("https://m2.dv8tion.net/releases")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")

    implementation("org.litote.kmongo:kmongo-coroutine:4.8.0")
    implementation("io.github.reactivecircus.cache4k:cache4k:0.9.0")
    implementation("ch.qos.logback:logback-classic:1.4.5")
    implementation("net.dv8tion:Telephone-JDA:5.0.0-beta.5.1_DEV") // use a slightly modified version of the JDA
    implementation("com.github.minndevelopment:jda-ktx:0.10.0-beta.1")
    implementation("com.sedmelluq:lavaplayer:1.3.77")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to project.properties["mainClassName"] as String
        )
    }
}

tasks.shadowJar {
    archiveBaseName.set("Telephone")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
}
