plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    kotlin("jvm") version "2.4.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.9-R0.1-SNAPSHOT")
    compileOnly(files("lib/paper.jar"))
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)
kotlin.jvmToolchain(25)

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        minimize()
    }

    build {
        dependsOn(shadowJar)
    }

    jar {
        enabled = false
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}