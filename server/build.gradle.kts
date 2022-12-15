plugins {
    java
    id("io.ratpack.ratpack-java") version "1.9.0"
}

group = "com.github.avenderov"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val nettyVersion = "4.1.86.Final"

dependencies {
    implementation(platform("io.netty:netty-bom:${nettyVersion}"))

    runtimeOnly("io.netty:netty-resolver-dns-native-macos:${nettyVersion}:osx-aarch_64")
    implementation("ch.qos.logback:logback-classic:1.2.11")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
