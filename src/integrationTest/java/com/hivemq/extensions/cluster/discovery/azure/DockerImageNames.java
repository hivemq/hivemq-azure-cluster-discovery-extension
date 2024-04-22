/*
 * Copyright 2021-present HiveMQ GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hivemq.extensions.cluster.discovery.azure;

import org.jetbrains.annotations.NotNull;
import org.testcontainers.utility.DockerImageName;

public final class DockerImageNames {

    public static final @NotNull DockerImageName AZURITE_IMAGE = DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:3.29.0");

    public static final @NotNull DockerImageName TOXIPROXY_IMAGE = DockerImageName.parse("shopify/toxiproxy:2.1.0");

    public static final @NotNull DockerImageName HIVEMQ_IMAGE =
            DockerImageName.parse("hivemq/hivemq4").withTag("latest");

    public DockerImageNames() {
    }
}
