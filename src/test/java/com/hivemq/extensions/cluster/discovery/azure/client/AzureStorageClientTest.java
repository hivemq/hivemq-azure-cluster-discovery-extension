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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AzureStorageClientTest {

    private @NotNull ExtensionInformation extensionInformation;
    private @NotNull AzureStorageClient azStorageClient;
    private @NotNull Path configPath;

    @TempDir
    private @NotNull Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        extensionInformation = mock(ExtensionInformation.class);
        when(extensionInformation.getExtensionHomeFolder()).thenReturn(tempDir.toFile());

        configPath = tempDir.resolve(ConfigReader.CONFIG_PATH);
        Files.createDirectories(configPath.getParent());

        Files.writeString(configPath, """
                connection-string:DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;QueueEndpoint=http://127.0.0.1:10001/devstoreaccount1;
                container-name:hivemq-blob-container
                file-prefix:hivemq-cluster
                file-expiration:360
                update-interval:180
                """);

        final var configurationReader = new ConfigReader(extensionInformation);
        azStorageClient = new AzureStorageClient(configurationReader);
    }

    @Test
    void test_create_successful() {
        azStorageClient.createOrUpdate();
        assertThat(azStorageClient.getStorageConfig()).isNotNull();
        assertThat(azStorageClient.getContainerClient()).isNotNull();
    }

    @Test
    void test_container_exists() {
        azStorageClient.createOrUpdate();
        final var containerClient = Mockito.mock(BlobContainerClient.class);
        azStorageClient.setContainerClient(containerClient);

        when(containerClient.exists()).thenReturn(true);
        assertThat(azStorageClient.existsContainer()).isTrue();
    }

    @Test
    void test_container_does_not_exist() {
        azStorageClient.createOrUpdate();
        final var containerClient = Mockito.mock(BlobContainerClient.class);
        azStorageClient.setContainerClient(containerClient);

        when(containerClient.exists()).thenReturn(false);
        assertThat(azStorageClient.existsContainer()).isFalse();
    }

    @Test
    void test_create_no_config_file() throws IOException {
        Files.deleteIfExists(configPath);
        Files.deleteIfExists(configPath.getParent());

        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        azStorageClient = new AzureStorageClient(configurationReader);

        assertThatThrownBy(() -> azStorageClient.createOrUpdate()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void test_create_invalid_config() throws IOException {
        Files.writeString(configPath, "");

        final var configurationReader = new ConfigReader(extensionInformation);
        azStorageClient = new AzureStorageClient(configurationReader);

        assertThatThrownBy(() -> azStorageClient.createOrUpdate()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void test_saveBlob_success() {
        final var blobClient = mock(BlobClient.class);
        azStorageClient.createOrUpdate();

        final var containerClient = Mockito.mock(BlobContainerClient.class);
        azStorageClient.setContainerClient(containerClient);

        when(containerClient.getBlobClient(any())).thenReturn(blobClient);
        doNothing().when(blobClient).upload(any(), anyLong(), anyBoolean());

        azStorageClient.saveBlob("abcd", "test");
    }

    @Test
    void test_getBlobContent_success() {
        final var blobClient = mock(BlobClient.class);
        azStorageClient.createOrUpdate();

        final var containerClient = Mockito.mock(BlobContainerClient.class);
        azStorageClient.setContainerClient(containerClient);

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        doAnswer(invocation -> {
            final OutputStream outputStream = invocation.getArgument(0);
            outputStream.write(ClusterNodeFileTest.createClusterNodeFileString("3", "3", "3", "3", "3").getBytes());
            return null;
        }).when(blobClient).downloadStream(any());

        final var blobContent = azStorageClient.getBlobContent("abcd");
        assertThat(blobContent).isNotEmpty();
    }

    @Test
    void test_deleteObject_success() {
        final var blobClient = mock(BlobClient.class);
        azStorageClient.createOrUpdate();

        final var containerClient = Mockito.mock(BlobContainerClient.class);
        azStorageClient.setContainerClient(containerClient);

        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        doNothing().when(blobClient).delete();

        azStorageClient.deleteBlob("abcd");
    }
}
