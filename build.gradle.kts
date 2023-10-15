plugins {
    alias(libs.plugins.hivemq.extension)
    alias(libs.plugins.defaults)
    alias(libs.plugins.license)
}

group = "com.hivemq.extensions"
description = "HiveMQ Discovery Extension based on usage of Azure Storage Blobs"

hivemqExtension {
    name.set("Azure Cluster Discovery Extension")
    author.set("HiveMQ")
    priority.set(1000)
    startPriority.set(10000)
    sdkVersion.set(libs.versions.hivemq.extensionSdk)

    resources {
        from("LICENSE")
    }
}

dependencies {
    hivemqProvided(libs.logback.classic)
    implementation(libs.azure.storage.blob)
    implementation(libs.owner.java8)
}

/* ******************** test ******************** */

dependencies {
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockito)
    testImplementation(libs.awaitility)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

/* ******************** integration test ******************** */

dependencies {
    integrationTestCompileOnly(libs.jetbrains.annotations)
    integrationTestImplementation(libs.awaitility)
    integrationTestImplementation(libs.testcontainers)
    integrationTestImplementation(libs.testcontainers.toxiproxy)
    integrationTestImplementation(libs.testcontainers.hivemq)
    integrationTestImplementation(libs.azure.storage.blob)
}

/* ******************** checks ******************** */

license {
    header = rootDir.resolve("HEADER")
    mapping("java", "SLASHSTAR_STYLE")
}
