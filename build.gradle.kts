plugins {
    java
    `maven-publish`
    id("me.champeau.jmh") version "0.7.2"
    jacoco
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
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")

    implementation("org.jline:jline:3.25.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

jmh {
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(1)
    benchmarkMode.set(listOf("avgt"))
    timeUnit.set("ms")
}

val repoPassword = System.getenv("repo") ?: ""

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "aria"

            pom {
                name.set("Aria")
                description.set("Lightweight embeddable scripting language for the JVM")
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

tasks.register("deploy") {
    group = "aria"
    description = "Publish all modules to Maven repository"
    dependsOn(":publish", ":aria-db:publish", ":aria-lsp:publish", ":aria-animations:publish")
}
