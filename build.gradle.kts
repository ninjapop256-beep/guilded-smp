import java.security.MessageDigest

plugins {
    java
}

group = "com.guildedsmp"
version = "1.0.0"
description = "Guilded SMP core plugin"

val paperApiVersion = project.property("paperApiVersion") as String
val mockBukkitVersion = project.property("mockBukkitVersion") as String
val mockBukkitPaperApiVersion = project.property("mockBukkitPaperApiVersion") as String
val junitVersion = project.property("junitVersion") as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    testImplementation("io.papermc.paper:paper-api:$mockBukkitPaperApiVersion")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.2:$mockBukkitVersion")
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    // Paper 26.2 requires Java 25. Any JDK 25+ can build this; bytecode targets 25.
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveFileName.set("GuildedSMP-${project.version}.jar")
}

tasks.test {
    useJUnitPlatform()
    // MockBukkit loads the plugin through a ByteBuddy proxy injected into its class loader.
    // On JDK 26 that needs sun.misc.Unsafe memory access and an open java.lang. Test JVM only;
    // the plugin itself never uses Unsafe or reflection.
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--sun-misc-unsafe-memory-access=allow",
        "-Dnet.bytebuddy.experimental=true",
        // ByteBuddy 1.18 disables its Unsafe-based injection on Java 26 unless net.bytebuddy.safe=false
        // (its error message says "true", but the code checks for false).
        "-Dnet.bytebuddy.safe=false",
    )
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

// ---------------------------------------------------------------------------
// Resource pack packaging (original Guilded SMP assets live in /resourcepack)
// ---------------------------------------------------------------------------
val resourcePackZip = tasks.register<Zip>("resourcePackZip") {
    group = "build"
    description = "Packages the Guilded SMP resource pack as a separate ZIP."
    from(layout.projectDirectory.dir("resourcepack"))
    archiveFileName.set("GuildedSMP-ResourcePack-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    // Deterministic archive so the SHA-1 only changes when the content changes.
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val resourcePackSha1 = tasks.register("resourcePackSha1") {
    group = "build"
    description = "Writes the SHA-1 of the resource pack ZIP (needed for server.properties / config)."
    dependsOn(resourcePackZip)
    val zipFile = resourcePackZip.flatMap { it.archiveFile }
    val out = layout.buildDirectory.file("distributions/GuildedSMP-ResourcePack-${project.version}.zip.sha1")
    inputs.file(zipFile)
    outputs.file(out)
    doLast {
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = zipFile.get().asFile.readBytes()
        val hex = digest.digest(bytes).joinToString("") { "%02x".format(it) }
        out.get().asFile.writeText(hex + "\n")
        logger.lifecycle("Resource pack SHA-1: $hex")
    }
}

tasks.register<Copy>("dist") {
    group = "build"
    description = "Copies the plugin JAR and resource pack ZIP (+SHA-1) into /dist."
    dependsOn(tasks.jar, resourcePackSha1)
    from(tasks.jar.flatMap { it.archiveFile })
    from(layout.buildDirectory.dir("distributions"))
    into(layout.projectDirectory.dir("dist"))
}

tasks.build {
    dependsOn(resourcePackSha1)
}
