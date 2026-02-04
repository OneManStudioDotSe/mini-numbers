plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.1.0" // <--- MAKE SURE THIS IS HERE
    id("io.ktor.plugin") version "3.4.0"
}

group = "se.onemanstudio"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.openfolder:kotlin-asyncapi-ktor:3.1.3")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-auth")
    implementation("ch.qos.logback:logback-classic:1.5.13")
    implementation("io.ktor:ktor-server-config-yaml")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.3.0")

    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm:3.4.0")
    implementation("io.ktor:ktor-server-netty-jvm:3.4.0")

    // JSON & Serialization
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.4.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.4.0")

    // Database (Exposed + Postgres/SQLite)
    implementation("org.jetbrains.exposed:exposed-core:0.56.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.56.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.56.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.56.0")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.postgresql:postgresql:42.7.7") // Or sqlite-jdbc
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")

    // Security & Utilities
    implementation("io.ktor:ktor-server-auth-jvm:3.4.0")
    implementation("io.ktor:ktor-server-cors-jvm:3.4.0")

    implementation("com.maxmind.geoip2:geoip2:5.0.1")

    // User-Agent parsing
    implementation("eu.bitwalker:UserAgentUtils:1.21")
}
