plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("io.ktor.plugin") version "3.6.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "se.onemanstudio"
version = "1.0.0-beta"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.openfolder:kotlin-asyncapi-ktor:3.2.4")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-auth")
    implementation("ch.qos.logback:logback-classic:1.6.5")
    implementation("io.ktor:ktor-server-config-yaml")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.20")

    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm:3.6.0")
    implementation("io.ktor:ktor-server-netty-jvm:3.6.0")

    // JSON & Serialization
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.6.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.6.0")

    // Database (Exposed + Postgres/SQLite)
    implementation("org.jetbrains.exposed:exposed-core:0.56.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.56.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.56.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.56.0")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")

    // Security & Utilities
    implementation("io.ktor:ktor-server-auth-jvm:3.6.0")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:3.6.0")
    implementation("io.ktor:ktor-server-cors-jvm:3.6.0")
    implementation("io.ktor:ktor-server-default-headers-jvm:3.6.0")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.6.0")
    implementation("org.mindrot:jbcrypt:0.4")

    implementation("com.maxmind.geoip2:geoip2:5.2.0")

    // User-Agent parsing (uap-core regexes, Apache-2.0)
    implementation("com.github.ua-parser:uap-java:1.6.1")

    // Rate limiting (in-memory cache)
    implementation("com.github.ben-manes.caffeine:caffeine:3.3.0")

    // Email (SMTP)
    implementation("jakarta.mail:jakarta.mail-api:2.1.5")
    implementation("org.eclipse.angus:angus-mail:2.0.5")
}

// Detekt static analysis configuration
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$projectDir/config/detekt/detekt.yml"))
    baseline = file("$projectDir/detekt-baseline.xml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
    }
}

// Test configuration: provide required config via system properties
// so tests work without a .env file (matches CI environment)
tasks.withType<Test> {
    doFirst { file("test-dbs").mkdirs() }
    maxHeapSize = "1g" // each test app re-scans the classpath for the AsyncAPI plugin
    systemProperty("ADMIN_PASSWORD", "testpassword123")
    systemProperty("SERVER_SALT", "a]4k9Bp!2sLq8Fz#7mXr0Wd6Yh3NcEv5JtGu1PxAoKiRnMlHfCjQwSyTbUeOgZd")
    systemProperty("DB_SQLITE_PATH", "test-dbs/ci-test.db")
    // Tests talk plain HTTP; outside development mode the session cookie is Secure-only and never sent back
    systemProperty("KTOR_DEVELOPMENT", "true")
}

// Minify tracker.js (strip comments and collapse whitespace)
tasks.register("minifyTracker") {
    group = "build"
    description = "Minify tracker.js for production"

    val srcFile = file("src/main/resources/tracker/tracker.js")
    val outFile = file("src/main/resources/tracker/tracker.min.js")

    inputs.file(srcFile)
    outputs.file(outFile)

    doLast {
        val source = srcFile.readText()
        val minified = source
            .replace(Regex("/\\*\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "") // strip block comments
            .replace(Regex("//.*"), "")             // strip line comments
            .replace(Regex("\\s*\n\\s*"), "\n")     // collapse line whitespace
            .replace(Regex("\n+"), "\n")             // collapse blank lines
            .trim()
        outFile.writeText(minified)
        println("tracker.min.js: ${minified.length} bytes (from ${source.length} bytes)")
    }
}

tasks.named("processResources") { dependsOn("minifyTracker") }

// Custom task to reset database
tasks.register<JavaExec>("reset") {
    group = "application"
    description = "Reset database: delete all data and re-seed demo"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("se.onemanstudio.db.ResetDatabaseKt")

    doFirst {
        println("=" .repeat(70))
        println("WARNING: This will delete ALL data from the database!")
        println("=" .repeat(70))
        println()
        println("This action will:")
        println("  - Delete all projects and events")
        println("  - Re-seed demo data (Professional Demo project with 1000 events)")
        println()
        println("Press Enter to continue or Ctrl+C to cancel...")

        System.`in`.read()
    }

    doLast {
        println()
        println("=" .repeat(70))
        println("✓ Database reset complete!")
        println("=" .repeat(70))
        println()
        println("Demo project created:")
        println("  - Name: Professional demo")
        println("  - Domain: localhost")
        println("  - API Key: demo-key-123")
        println("  - Events: 1000 sample events")
        println()
    }
}
