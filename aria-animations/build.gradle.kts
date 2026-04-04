plugins {
    java
    `maven-publish`
}

group = "priv.seventeen.artist.aria"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":"))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

val repoPassword = System.getenv("repo") ?: ""

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "aria-animations"

            pom {
                name.set("Aria Animations")
                description.set("Animation easing objects for Aria scripting language")
            }
        }
    }
    repositories {
        maven {
            url = uri(property("mavenRepoUrl") as String)
            isAllowInsecureProtocol = true
            credentials {
                username = property("mavenRepoUser") as String
                password = repoPassword
            }
        }
    }
}
