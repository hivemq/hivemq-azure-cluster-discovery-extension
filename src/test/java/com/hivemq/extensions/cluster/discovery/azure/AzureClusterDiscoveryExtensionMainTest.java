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

import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.parameter.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AzureClusterDiscoveryExtensionMainTest {

    @TempDir
    @NotNull File temporaryFolder;

    private @NotNull ExtensionStartInput extensionStartInput;
    private @NotNull ExtensionStartOutput extensionStartOutput;
    private @NotNull ExtensionStopInput extensionStopInput;
    private @NotNull ExtensionStopOutput extensionStopOutput;
    private @NotNull ExtensionInformation extensionInformation;
    private @NotNull AzureClusterDiscoveryExtensionMain azureClusterDiscoveryExtensionMain;

    @BeforeEach
    void setUp() {
        extensionStartInput = mock(ExtensionStartInput.class);
        extensionStartOutput = mock(ExtensionStartOutput.class);
        extensionStopInput = mock(ExtensionStopInput.class);
        extensionStopOutput = mock(ExtensionStopOutput.class);
        extensionInformation = mock(ExtensionInformation.class);

        when(extensionStartInput.getExtensionInformation()).thenReturn(extensionInformation);
        when(extensionInformation.getExtensionHomeFolder()).thenReturn(temporaryFolder);
        azureClusterDiscoveryExtensionMain = new AzureClusterDiscoveryExtensionMain();
    }

    @Test
    void test_start_success() {
        azureClusterDiscoveryExtensionMain.extensionStart(extensionStartInput, extensionStartOutput);
        assertNotNull(azureClusterDiscoveryExtensionMain.azureClusterDiscoveryCallback);
    }

    @Test
    void test_start_failed() {
        when(extensionInformation.getExtensionHomeFolder()).thenThrow(new NullPointerException());
        azureClusterDiscoveryExtensionMain.extensionStart(extensionStartInput, extensionStartOutput);
        assertNull(azureClusterDiscoveryExtensionMain.azureClusterDiscoveryCallback);
    }

    @Test
    void test_stop_success() {
        assertThrows(RuntimeException.class, () -> {
            azureClusterDiscoveryExtensionMain.extensionStart(extensionStartInput, extensionStartOutput);
            azureClusterDiscoveryExtensionMain.extensionStop(extensionStopInput, extensionStopOutput);
        });
    }

    @Test
    void test_stop_no_start_failed() {
        when(extensionInformation.getExtensionHomeFolder()).thenThrow(new NullPointerException());
        azureClusterDiscoveryExtensionMain.extensionStart(extensionStartInput, extensionStartOutput);
        azureClusterDiscoveryExtensionMain.extensionStop(extensionStopInput, extensionStopOutput);
        assertNull(azureClusterDiscoveryExtensionMain.azureClusterDiscoveryCallback);
    }
}