plugins {
    id("com.hivemq.extension")
    id("com.github.hierynomus.license")
    id("io.github.sgtsilvio.gradle.defaults")
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
    integrationTestCompileOnly("org.jetbrains:annotations:${property("jetbrains-annotations.version")}")
    integrationTestImplementation("org.awaitility:awaitility:${property("awaitlity.version")}")
    integrationTestImplementation("org.testcontainers:testcontainers:${property("testcontainers.version")}")
    integrationTestImplementation("org.testcontainers:toxiproxy:${property("testcontainers.version")}")
    integrationTestImplementation("org.testcontainers:hivemq:${property("testcontainers.version")}")
    integrationTestImplementation("com.azure:azure-storage-blob:${property("azure-storage-blob.version")}")
}

/* ******************** checks ******************** */

license {
    header = rootDir.resolve("HEADER")
    mapping("java", "SLASHSTAR_STYLE")
}