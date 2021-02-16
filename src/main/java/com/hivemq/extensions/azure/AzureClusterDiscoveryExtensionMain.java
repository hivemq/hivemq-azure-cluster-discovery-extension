package com.hivemq.extensions.azure;/*
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

import com.hivemq.extensions.azure.callback.AzureClusterDiscoveryCallback;
import com.hivemq.extension.sdk.api.ExtensionMain;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartOutput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopOutput;
import com.hivemq.extension.sdk.api.services.Services;
import com.hivemq.extensions.azure.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Till Seeberger
 */
public class AzureClusterDiscoveryExtensionMain implements ExtensionMain {

    private static final Logger logger = LoggerFactory.getLogger(AzureClusterDiscoveryExtensionMain.class);

    AzureClusterDiscoveryCallback azureClusterDiscoveryCallback;

    @Override
    public void extensionStart(
            @NotNull ExtensionStartInput extensionStartInput,
            @NotNull ExtensionStartOutput extensionStartOutput) {

        try {
            final ConfigReader configReader = new ConfigReader(extensionStartInput.getExtensionInformation());
            azureClusterDiscoveryCallback = new AzureClusterDiscoveryCallback(configReader);
            Services.clusterService().addDiscoveryCallback(azureClusterDiscoveryCallback);
            logger.debug("Registered Azure Cluster Discovery Callback successfully.");
        } catch (final UnsupportedOperationException e) {
            extensionStartOutput.preventExtensionStartup(e.getMessage());
        } catch (final Exception e) {
            logger.error("Not able to start Azure Cluster Discovery Extension.", e);
            extensionStartOutput.preventExtensionStartup("Exception caught at extension start.");
        }
    }

    @Override
    public void extensionStop(
            @NotNull ExtensionStopInput extensionStopInput,
            @NotNull ExtensionStopOutput extensionStopOutput) {
        if (azureClusterDiscoveryCallback != null) {
            Services.clusterService().removeDiscoveryCallback(azureClusterDiscoveryCallback);
        }
    }
}