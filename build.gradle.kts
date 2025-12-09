plugins {
    kotlin("jvm") version "1.9.23"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.example.mcpanel"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.19.2-R0.1-SNAPSHOT")

    implementation("io.ktor:ktor-server-core-jvm:2.3.8")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.8")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.8")
    implementation("io.ktor:ktor-serialization-gson-jvm:2.3.8")
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.8")
    implementation("io.ktor:ktor-server-cors-jvm:2.3.8")

    implementation("com.google.code.gson:gson:2.11.0")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks {
    shadowJar {
        archiveBaseName.set("PanelPlugin")
        archiveClassifier.set("")
        archiveVersion.set(version.toString())

        // relocations to avoid classpath conflicts
        relocate("io.ktor", "com.example.mcpanel.libs.ktor")
        relocate("com.google.gson", "com.example.mcpanel.libs.gson")
        relocate("ch.qos.logback", "com.example.mcpanel.libs.logback")
    }

    build {
        dependsOn(shadowJar)
    }
}
