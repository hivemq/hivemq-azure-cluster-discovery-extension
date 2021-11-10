plugins {
    id("com.hivemq.extension")
    id("com.github.hierynomus.license")
    id("com.github.sgtsilvio.gradle.utf8")
}

group = "com.hivemq.extensions"
description = "HiveMQ Discovery Extension based on usage of Azure Storage Blobs"

hivemqExtension {
    name.set("Azure Cluster Discovery Extension")
    author.set("HiveMQ")
    priority.set(1000)
    startPriority.set(10000)
    sdkVersion.set("${property("hivemq-extension-sdk.version")}")

    resources {
        from("LICENSE")
    }
}

dependencies {
    hivemqProvided("ch.qos.logback:logback-classic:${property("logback.version")}")
    implementation("com.azure:azure-storage-blob:${property("azure-storage-blob.version")}")
    implementation("org.aeonbits.owner:owner-java8:${property("owner.version")}")
}

/* ******************** test ******************** */

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter-api:${property("junit.version")}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.mockito:mockito-inline:${property("mockito.version")}")
    testImplementation("org.awaitility:awaitility:${property("awaitlity.version")}")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

/* ******************** integration test ******************** */

dependencies {
    integrationTestImplementation("org.testcontainers:testcontainers:${property("testcontainers.version")}")
    integrationTestImplementation("org.testcontainers:toxiproxy:${property("testcontainers.version")}")
    integrationTestImplementation("com.hivemq:hivemq-testcontainer-junit5:${property("hivemq-testcontainer.version")}")
}

/* ******************** checks ******************** */

license {
    header = rootDir.resolve("HEADER")
    mapping("java", "SLASHSTAR_STYLE")
}