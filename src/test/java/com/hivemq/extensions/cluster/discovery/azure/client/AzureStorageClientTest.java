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

package com.hivemq.extensions.cluster.discovery.azure.client;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.hivemq.extension.sdk.api.parameter.ExtensionInformation;
import com.hivemq.extensions.cluster.discovery.azure.config.ClusterNodeFileTest;
import com.hivemq.extensions.cluster.discovery.azure.config.ConfigReader;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AzureStorageClientTest {

    private @NotNull ExtensionInformation extensionInformation;
    private @NotNull AzureStorageClient azStorageClient;

    @TempDir
    @NotNull File temporaryFolder;

    @BeforeEach
    void setUp() throws IOException {
        extensionInformation = mock(ExtensionInformation.class);

        when(extensionInformation.getExtensionHomeFolder()).thenReturn(temporaryFolder);

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println(
                    "connection-string:DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;QueueEndpoint=http://127.0.0.1:10001/devstoreaccount1;");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:360");
            printWriter.println("update-interval:180");
        }

        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        azStorageClient = new AzureStorageClient(configurationReader);
    }

    @Test
    void test_create_successful() {
        azStorageClient.createOrUpdate();
        assertNotNull(azStorageClient.getStorageConfig());
        assertNotNull(azStorageClient.getContainerClient());
    }

    @Test
    void test_container_exists() {
        azStorageClient.createOrUpdate();
        final BlobContainerClient containerClient = Mockito.mock(BlobContainerClient.class);
        azStorageClient.setContainerClient(containerClient);

        when(containerClient.exists()).thenReturn(true);
        final boolean containerExists = azStorageClient.existsContainer();

        assertTrue(containerExists);
    }

    @Test
    void test_container_does_not_exist() {
        azStorageClient.createOrUpdate();
        final BlobContainerClient containerClient = Mockito.mock(BlobContainerClient.class);
        azStorageClient.setContainerClient(containerClient);

        when(containerClient.exists()).thenReturn(false);
        final boolean containerExists = azStorageClient.existsContainer();

        assertFalse(containerExists);
    }

    @Test
    void test_create_no_config_file() {
        deleteFilesInTemporaryFolder();
        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        azStorageClient = new AzureStorageClient(configurationReader);

        assertThrows(IllegalStateException.class, () -> azStorageClient.createOrUpdate());
    }

    @Test
    void test_create_invalid_config() throws IOException {
        deleteFilesInTemporaryFolder();
        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("");
        }
        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        azStorageClient = new AzureStorageClient(configurationReader);
        assertThrows(IllegalStateException.class, () -> azStorageClient.createOrUpdate());

    }

    private void deleteFilesInTemporaryFolder() {
        for (final File file : Objects.requireNonNull(temporaryFolder.listFiles())) {
            assertTrue(file.delete());
        }
    }

    @Test
    void test_saveBlob_success() {
        final BlobClient blobClient = mock(BlobClient.class);

        azStorageClient.createOrUpdate();

        final BlobContainerClient containerClient = Mockito.mock(BlobContainerClient.class);
        azStorageClient.setContainerClient(containerClient);

        when(containerClient.getBlobClient(any())).thenReturn(blobClient);

        doNothing().when(blobClient).upload(any(), anyLong(), anyBoolean());

        azStorageClient.saveBlob("abcd", "test");
    }

    @Test
    void test_getBlobContent_success() {
        final BlobClient blobClient = mock(BlobClient.class);
        azStorageClient.createOrUpdate();

        final BlobContainerClient containerClient = Mockito.mock(BlobContainerClient.class);
        azStorageClient.setContainerClient(containerClient);

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        doAnswer(invocation -> {
            final OutputStream outputStream = invocation.getArgument(0);
            outputStream.write(ClusterNodeFileTest.createClusterNodeFileString("3", "3", "3", "3", "3").getBytes());
            return null;
        }).when(blobClient).downloadStream(any());

        final String blobContent = azStorageClient.getBlobContent("abcd");
        assertNotNull(blobContent);
        assertFalse(blobContent.isEmpty());
    }

    @Test
    void test_deleteObject_success() {
        final BlobClient blobClient = mock(BlobClient.class);
        azStorageClient.createOrUpdate();

        final BlobContainerClient containerClient = Mockito.mock(BlobContainerClient.class);
        azStorageClient.setContainerClient(containerClient);

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);

        doNothing().when(blobClient).delete();
        azStorageClient.deleteBlob("abcd");
    }
}
