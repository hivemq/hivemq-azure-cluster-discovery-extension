package com.hivemq.extensions.discovery.azure;

import com.hivemq.extension.sdk.api.annotations.NotNull;
import org.testcontainers.utility.DockerImageName;

public final class DockerImageNames {

    public static final @NotNull DockerImageName AZURITE_IMAGE =
            DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:3.14.3");

    public static final @NotNull DockerImageName TOXIPROXY_IMAGE = DockerImageName.parse("shopify/toxiproxy:2.1.0");

    public static final @NotNull DockerImageName HIVEMQ_IMAGE =
            DockerImageName.parse("hivemq/hivemq4").withTag("latest");

    public DockerImageNames() {
    }
}
