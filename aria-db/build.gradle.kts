plugins {
    java
    `maven-publish`
}

group = "priv.seventeen.artist.aria"
version = (findProperty("releaseVersion") as String?) ?: "1.0.0"

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
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    implementation("com.h2database:h2:2.2.224")
    implementation("com.mysql:mysql-connector-j:8.2.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

val repoPassword = System.getenv("repo") ?: ""

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "aria-db"

            pom {
                name.set("Aria DB")
                description.set("Database module for Aria scripting language")
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
