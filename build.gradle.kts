plugins {
    id("com.hivemq.extension")
    id("com.github.hierynomus.license")
    id("com.github.sgtsilvio.gradle.utf8")
    id("org.asciidoctor.jvm.convert")
}

repositories {
    mavenCentral()
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

/* ******************** resources ******************** */

val prepareAsciidoc by tasks.registering(Sync::class) {
    from("README.adoc").into({ temporaryDir })
}

tasks.asciidoctor {
    dependsOn(prepareAsciidoc)
    sourceDir(prepareAsciidoc.map { it.destinationDir })
}

tasks.hivemqExtensionResources {
    from("LICENSE")
    from("README.adoc") { rename { "README.txt" } }
    from(tasks.asciidoctor)
}

/* ******************** dependencies ******************** */

dependencies {
    implementation("com.azure:azure-storage-blob:${property("azure-storage-blob.version")}")
    implementation("org.aeonbits.owner:owner-java8:${property("owner.version")}")
}


/* ******************** test ******************** */

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:${property("junit.version")}")
    testImplementation("org.mockito:mockito-inline:${property("mockito.version")}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    testLogging {
        events("STARTED", "FAILED", "SKIPPED")
    }
}

/* ******************** integration test ******************** */

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

configurations {
    getByName("integrationTestImplementation").extendsFrom(testImplementation.get())
    getByName("integrationTestRuntimeOnly").extendsFrom(testRuntimeOnly.get())
}

dependencies {
    "integrationTestImplementation"("org.testcontainers:testcontainers:${property("testcontainers.version")}")
    "integrationTestImplementation"("org.testcontainers:toxiproxy:${property("testcontainers.version")}")
    "integrationTestImplementation"("com.hivemq:hivemq-testcontainer-junit5:${property("hivemq-testcontainer.version")}")
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

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.test)
    dependsOn(prepareExtensionTest)
}

tasks.check { dependsOn(integrationTest) }


/* ******************** checks ******************** */

license {
    header = rootDir.resolve("HEADER")
    mapping("java", "SLASHSTAR_STYLE")
}