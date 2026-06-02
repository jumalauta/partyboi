import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlin_version = "2.3.21"
val logback_version: String = "1.5.34"
val postgres_version: String = "42.7.11"
val h2_version: String = "2.4.240"
val kotlinx_html_version: String = "0.12.0"
val flyway_version: String = "12.7.0"
val arrow_version: String = "2.2.2.1"

plugins {
    kotlin("jvm") version "2.3.21"
    id("io.ktor.plugin") version "3.5.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
}

group = "com.example"
version = "0.0.1"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers") }
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation-jvm")
    implementation("org.postgresql:postgresql:$postgres_version")
    implementation("com.h2database:h2:$h2_version")
    implementation("com.github.seratch:kotliquery:1.9.1")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.flywaydb:flyway-core:$flyway_version")
    implementation("org.flywaydb:flyway-database-postgresql:$flyway_version")
    implementation("io.ktor:ktor-server-html-builder-jvm")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:$kotlinx_html_version")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-compression-jvm")
    implementation("io.ktor:ktor-server-host-common-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-auto-head-response-jvm")
    implementation("io.ktor:ktor-server-sessions-jvm")
    implementation("io.ktor:ktor-server-sessions")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-rate-limit")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-network-jvm")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.arrow-kt:arrow-core:$arrow_version")
    implementation("io.arrow-kt:arrow-fx-coroutines:$arrow_version")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.sksamuel.scrimage:scrimage-core:4.6.2")
    implementation("org.jetbrains:markdown:0.7.3")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
    implementation("io.github.g0dkar:qrcode-kotlin:4.5.0")
    implementation("com.github.docker-java:docker-java:3.7.1")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.7.1")
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("it.skrape:skrapeit:1.2.2")
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
}

kotlin {
    jvmToolchain(21)
}

tasks.register("generateBuildInfo") {
    val outputDir = layout.buildDirectory.dir("generated/resources/build-info")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        val timestamp = ProcessBuilder("date", "-u", "+%Y-%m-%dT%H:%M:%SZ")
            .start().inputStream.bufferedReader().readText().trim()
        dir.resolve("build-info.properties").writeText("build.timestamp=$timestamp\n")
    }
}

tasks.named("processResources") {
    dependsOn("generateBuildInfo")
}

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("generated/resources/build-info"))
    }
    create("syncHarness") {
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}

val syncHarnessImplementation: Configuration by configurations.getting {
    extendsFrom(configurations["implementation"])
}
val syncHarnessRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations["runtimeOnly"])
}

dependencies {
    syncHarnessImplementation("io.ktor:ktor-client-core")
    syncHarnessImplementation("io.ktor:ktor-client-cio")
    syncHarnessImplementation("io.ktor:ktor-client-content-negotiation-jvm")
    syncHarnessImplementation("io.ktor:ktor-client-encoding")
    syncHarnessImplementation("io.ktor:ktor-serialization-kotlinx-json")
    syncHarnessImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    syncHarnessImplementation("org.jsoup:jsoup:1.22.2")
    syncHarnessImplementation("io.arrow-kt:arrow-core:$arrow_version")
    syncHarnessImplementation(kotlin("stdlib-jdk8"))
}

tasks.register<JavaExec>("syncHarness") {
    group = "verification"
    description = "Run the two-instance sync end-to-end harness against a docker-compose stack."
    mainClass.set("party.jml.partyboi.syncharness.MainKt")
    classpath = sourceSets["syncHarness"].runtimeClasspath
    standardInput = System.`in`
}

tasks.named("shadowJar", com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class.java) {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xannotation-default-target=param-property"))
}