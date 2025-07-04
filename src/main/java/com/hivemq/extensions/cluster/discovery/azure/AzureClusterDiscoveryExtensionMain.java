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

import com.hivemq.extension.sdk.api.ExtensionMain;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartOutput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopOutput;
import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extensions.cluster.discovery.azure.callback.AzureClusterDiscoveryCallback;
import com.hivemq.extensions.cluster.discovery.azure.config.ConfigReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Till Seeberger
 */
public class AzureClusterDiscoveryExtensionMain implements ExtensionMain {

    private static final @NotNull Logger log = LoggerFactory.getLogger(AzureClusterDiscoveryExtensionMain.class);

    @Nullable AzureClusterDiscoveryCallback azureClusterDiscoveryCallback;

    @Override
    public void extensionStart(
            final @NotNull ExtensionStartInput extensionStartInput,
            final @NotNull ExtensionStartOutput extensionStartOutput) {
        try {
            final var configReader = new ConfigReader(extensionStartInput.getExtensionInformation());
            azureClusterDiscoveryCallback = new AzureClusterDiscoveryCallback(configReader);
            Services.clusterService().addDiscoveryCallback(azureClusterDiscoveryCallback);
            log.debug("Registered Azure Cluster Discovery Callback successfully.");
        } catch (final UnsupportedOperationException e) {
            extensionStartOutput.preventExtensionStartup(e.getMessage());
        } catch (final Exception e) {
            extensionStartOutput.preventExtensionStartup("Unknown error while starting the extension" +
                    ((e.getMessage() != null) ? ": " + e.getMessage() : ""));
        }
    }

    @Override
    public void extensionStop(
            final @NotNull ExtensionStopInput extensionStopInput,
            final @NotNull ExtensionStopOutput extensionStopOutput) {
        if (azureClusterDiscoveryCallback != null) {
            Services.clusterService().removeDiscoveryCallback(azureClusterDiscoveryCallback);
        }
    }
}
