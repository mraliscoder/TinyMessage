plugins {
    java
    `maven-publish`
}

group = "fi.sulku.hytale"
version = "1.0.2-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/HytaleServer.jar")) // Temp
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}


tasks {
    jar {
        destinationDirectory.set(file("D:\\Pelit\\Hytale Launcher\\HytaleData\\UserData\\Mods"))
    }
}