plugins {
    id("com.hivemq.extension")
    id("com.github.hierynomus.license")
    id("com.github.sgtsilvio.gradle.utf8")
}

/* ******************** metadata ******************** */

group = "com.hivemq.extensions"
description = "HiveMQ Discovery Extension based on usage of Azure Storage Blobs"

hivemqExtension {
    name = "Azure Cluster Discovery Extension"
    author = "HiveMQ"
    priority = 1000
    startPriority = 10000
    sdkVersion = "${property("hivemq-extension-sdk.version")}"
}

/* ******************** dependencies ******************** */

repositories {
    mavenCentral()
}

dependencies {
    hivemqProvided("ch.qos.logback:logback-classic:${property("logback.version")}")
    implementation("com.azure:azure-storage-blob:${property("azure-storage-blob.version")}")
    implementation("org.aeonbits.owner:owner-java8:${property("owner.version")}")
}

/* ******************** resources ******************** */

tasks.hivemqExtensionResources {
    from("LICENSE")
}

/* ******************** test ******************** */

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:${property("junit.version")}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.mockito:mockito-inline:${property("mockito.version")}")
    testImplementation("org.awaitility:awaitility:${property("awaitlity.version")}")
}

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        events("STARTED", "FAILED", "SKIPPED")
    }
}

/* ******************** integration test ******************** */

sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val integrationTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    integrationTestImplementation("org.testcontainers:testcontainers:${property("testcontainers.version")}")
    integrationTestImplementation("org.testcontainers:toxiproxy:${property("testcontainers.version")}")
    integrationTestImplementation("com.hivemq:hivemq-testcontainer-junit5:${property("hivemq-testcontainer.version")}")
}

val prepareExtensionTest by tasks.registering(Sync::class) {
    group = "hivemq extension"
    description = "Prepares the extension for integration testing."

    from(tasks.hivemqExtensionZip.map { zipTree(it.archiveFile) })
    into(buildDir.resolve("hivemq-extension-test"))
}

val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs integration tests."

    testClassesDirs = sourceSets[name].output.classesDirs
    classpath = sourceSets[name].runtimeClasspath
    shouldRunAfter(tasks.test)
    dependsOn(prepareExtensionTest)
}

tasks.check { dependsOn(integrationTest) }


/* ******************** checks ******************** */

license {
    header = rootDir.resolve("HEADER")
    mapping("java", "SLASHSTAR_STYLE")
}