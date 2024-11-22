plugins {
    alias(libs.plugins.hivemq.extension)
    alias(libs.plugins.defaults)
    alias(libs.plugins.oci)
    alias(libs.plugins.license)
}

group = "com.hivemq.extensions"
description = "HiveMQ Discovery Extension based on usage of Azure Storage Blobs"

hivemqExtension {
    name = "Azure Cluster Discovery Extension"
    author = "HiveMQ"
    priority = 1000
    startPriority = 10000
    sdkVersion = libs.versions.hivemq.extensionSdk

    resources {
        from("LICENSE")
    }
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    hivemqProvided(libs.logback.classic)
    implementation(libs.azure.storage.blob)
    implementation(libs.owner.java8)
}

oci {
    registries {
        dockerHub {
            optionalCredentials()
        }
        registry("mcr") {
            url = uri("https://mcr.microsoft.com")
            exclusiveContent {
                includeGroup("azure-storage")
            }
        }
    }
}

@Suppress("UnstableApiUsage")
testing {
    suites {
        withType<JvmTestSuite> {
            useJUnitJupiter(libs.versions.junit.jupiter)
        }
        "test"(JvmTestSuite::class) {
            dependencies {
                compileOnly(libs.jetbrains.annotations)
                implementation(libs.mockito)
            }
        }
        "integrationTest"(JvmTestSuite::class) {
            dependencies {
                compileOnly(libs.jetbrains.annotations)
                implementation(libs.awaitility)
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.hivemq)
                implementation(libs.testcontainers.toxiproxy)
                implementation(libs.gradleOci.junitJupiter)
                implementation(libs.azure.storage.blob)
                runtimeOnly(libs.logback.classic)
            }
            oci.of(this) {
                imageDependencies {
                    runtime("hivemq:hivemq4:latest") { isChanging = true }
                    runtime("azure-storage:azurite:3.33.0").tag("latest")
                    runtime("shopify:toxiproxy:2.1.0").tag("latest")
                }
            }
        }
    }
}

license {
    header = rootDir.resolve("HEADER")
    mapping("java", "SLASHSTAR_STYLE")
}
