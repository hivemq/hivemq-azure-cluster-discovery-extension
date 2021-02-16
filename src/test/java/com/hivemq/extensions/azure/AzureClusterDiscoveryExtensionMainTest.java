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

import com.hivemq.extension.sdk.api.parameter.*;
import com.hivemq.extensions.azure.AzureClusterDiscoveryExtensionMain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class AzureClusterDiscoveryExtensionMainTest {

    @TempDir
    File temporaryFolder;

    @Mock
    ExtensionStartInput extensionStartInput;

    @Mock
    ExtensionStartOutput extensionStartOutput;

    @Mock
    ExtensionStopInput extensionStopInput;

    @Mock
    ExtensionStopOutput extensionStopOutput;

    @Mock
    ExtensionInformation extensionInformation;

    private AzureClusterDiscoveryExtensionMain azureClusterDiscoveryExtensionMain;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(extensionStartInput.getExtensionInformation()).thenReturn(extensionInformation);
        when(extensionInformation.getExtensionHomeFolder()).thenReturn(temporaryFolder);
        azureClusterDiscoveryExtensionMain = new AzureClusterDiscoveryExtensionMain();
    }

    @Test
    public void test_start_success() {
        azureClusterDiscoveryExtensionMain.extensionStart(extensionStartInput, extensionStartOutput);
        assertNotNull(azureClusterDiscoveryExtensionMain.azureClusterDiscoveryCallback);
    }

    @Test
    public void test_start_failed() {
        when(extensionInformation.getExtensionHomeFolder()).thenThrow(new NullPointerException());
        azureClusterDiscoveryExtensionMain.extensionStart(extensionStartInput, extensionStartOutput);
        assertNull(azureClusterDiscoveryExtensionMain.azureClusterDiscoveryCallback);
    }

    @Test
    public void test_stop_success() {
        assertThrows(RuntimeException.class, () -> {
            azureClusterDiscoveryExtensionMain.extensionStart(extensionStartInput, extensionStartOutput);
            azureClusterDiscoveryExtensionMain.extensionStop(extensionStopInput, extensionStopOutput);
        });
    }

    @Test
    public void test_stop_no_start_failed() {
        when(extensionInformation.getExtensionHomeFolder()).thenThrow(new NullPointerException());
        azureClusterDiscoveryExtensionMain.extensionStart(extensionStartInput, extensionStartOutput);
        azureClusterDiscoveryExtensionMain.extensionStop(extensionStopInput, extensionStopOutput);
        assertNull(azureClusterDiscoveryExtensionMain.azureClusterDiscoveryCallback);
    }
}