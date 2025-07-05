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

import com.hivemq.extension.sdk.api.parameter.ExtensionInformation;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStartOutput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopInput;
import com.hivemq.extension.sdk.api.parameter.ExtensionStopOutput;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AzureClusterDiscoveryExtensionMainTest {

    @TempDir
    @NotNull File temporaryFolder;

    private final @NotNull ExtensionStartInput extensionStartInput = mock();
    private final @NotNull ExtensionStartOutput extensionStartOutput = mock();
    private final @NotNull ExtensionStopInput extensionStopInput = mock();
    private final @NotNull ExtensionStopOutput extensionStopOutput = mock();
    private final @NotNull ExtensionInformation extensionInformation = mock();

    private @NotNull AzureClusterDiscoveryExtensionMain azureClusterDiscoveryExtensionMain;

    @BeforeEach
    void setUp() {
        when(extensionStartInput.getExtensionInformation()).thenReturn(extensionInformation);
        when(extensionInformation.getExtensionHomeFolder()).thenReturn(temporaryFolder);
        azureClusterDiscoveryExtensionMain = new AzureClusterDiscoveryExtensionMain();
    }

    @Test
    void test_start_success() {
        azureClusterDiscoveryExtensionMain.extensionStart(extensionStartInput, extensionStartOutput);
        assertThat(azureClusterDiscoveryExtensionMain.azureClusterDiscoveryCallback).isNotNull();
    }

    @Test
    void test_start_failed() {
        when(extensionInformation.getExtensionHomeFolder()).thenThrow(new NullPointerException());

        azureClusterDiscoveryExtensionMain.extensionStart(extensionStartInput, extensionStartOutput);
        assertThat(azureClusterDiscoveryExtensionMain.azureClusterDiscoveryCallback).isNull();
    }

    @Test
    void test_stop_success() {
        assertThatThrownBy(() -> {
            azureClusterDiscoveryExtensionMain.extensionStart(extensionStartInput, extensionStartOutput);
            azureClusterDiscoveryExtensionMain.extensionStop(extensionStopInput, extensionStopOutput);
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void test_stop_no_start_failed() {
        when(extensionInformation.getExtensionHomeFolder()).thenThrow(new NullPointerException());

        azureClusterDiscoveryExtensionMain.extensionStart(extensionStartInput, extensionStartOutput);
        azureClusterDiscoveryExtensionMain.extensionStop(extensionStopInput, extensionStopOutput);
        assertThat(azureClusterDiscoveryExtensionMain.azureClusterDiscoveryCallback).isNull();
    }
}
