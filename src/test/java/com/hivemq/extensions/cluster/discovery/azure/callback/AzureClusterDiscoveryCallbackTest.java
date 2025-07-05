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

package com.hivemq.extensions.cluster.discovery.azure.callback;

import com.azure.storage.blob.models.BlobItem;
import com.hivemq.extension.sdk.api.parameter.ExtensionInformation;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterDiscoveryInput;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterDiscoveryOutput;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterNodeAddress;
import com.hivemq.extensions.cluster.discovery.azure.client.AzureStorageClient;
import com.hivemq.extensions.cluster.discovery.azure.config.AzureDiscoveryConfig;
import com.hivemq.extensions.cluster.discovery.azure.config.ClusterNodeFileTest;
import com.hivemq.extensions.cluster.discovery.azure.config.ConfigReader;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AzureClusterDiscoveryCallbackTest {

    private final @NotNull ExtensionInformation extensionInformation = mock();
    private final @NotNull AzureStorageClient azStorageClient = mock();
    private final @NotNull ClusterDiscoveryInput clusterDiscoveryInput = mock();
    private final @NotNull ClusterDiscoveryOutput clusterDiscoveryOutput = mock();

    private @NotNull AzureClusterDiscoveryCallback azureClusterDiscoveryCallback;
    private @NotNull ConfigReader configurationReader;

    @TempDir
    @NotNull Path temporaryFolder;

    @BeforeEach
    void setUp() throws Exception {
        when(clusterDiscoveryInput.getOwnClusterId()).thenReturn("ABCD12");
        when(clusterDiscoveryInput.getOwnAddress()).thenReturn(new ClusterNodeAddress("127.0.0.1", 7800));

        when(extensionInformation.getExtensionHomeFolder()).thenReturn(temporaryFolder.toFile());

        try (final var printWriter = new PrintWriter(temporaryFolder.resolve(ConfigReader.STORAGE_FILE).toFile())) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:360");
            printWriter.println("update-interval:180");
        }

        configurationReader = new ConfigReader(extensionInformation);
        azureClusterDiscoveryCallback = new AzureClusterDiscoveryCallback(azStorageClient);
        when(azStorageClient.getConfigReader()).thenReturn(configurationReader);

        azStorageClient.getConfigReader().readConfiguration();
        when(azStorageClient.getContainerClient()).thenReturn(mock());

        final AzureDiscoveryConfig azAzureDiscoveryConfig = configurationReader.readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azAzureDiscoveryConfig);
        when(azStorageClient.existsContainer()).thenReturn(true);
    }

    @Test
    void test_init_success() {
        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        when(azStorageClient.getBlobContent(any())).thenReturn(ClusterNodeFileTest.createClusterNodeFileString("3",
                "3",
                "3",
                "3",
                "3"));

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(anyList());
    }

    @Test
    void test_init_provide_current_nodes_exception_getting_node_files() {
        doThrow(RuntimeException.class).when(azStorageClient).getBlobs(any());

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    void test_init_provide_current_nodes_blobexception_getting_node_file() {
        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        doThrow(UncheckedIOException.class).when(azStorageClient).getBlobContent(any());

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    void test_init_provide_current_nodes_exception_getting_node_file() {
        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        doThrow(UncheckedIOException.class).when(azStorageClient).getBlobContent(any());

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    void test_init_provide_current_nodes_empty_blob_item_iterator() {
        when(azStorageClient.getBlobs(any())).thenReturn(Collections.emptyIterator());

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    void test_init_provide_current_nodes_expired_files() throws Exception {
        deleteFilesIn(temporaryFolder);

        try (final var printWriter = new PrintWriter(temporaryFolder.resolve(ConfigReader.STORAGE_FILE).toFile())) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:2");
            printWriter.println("update-interval:1");
        }
        final var azureDiscoveryConfig = new ConfigReader(extensionInformation).readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azureDiscoveryConfig);

        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        when(azStorageClient.getBlobContent(any())).thenReturn(ClusterNodeFileTest.createClusterNodeFileString("3",
                "3",
                "3",
                "3",
                "3"));

        // wait for files to expire
        TimeUnit.SECONDS.sleep(2);

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    void test_init_provide_current_nodes_blob_null() {
        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        when(azStorageClient.getBlobContent(any())).thenReturn(null);

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    void test_init_provide_current_nodes_blob_content_blank() {
        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        when(azStorageClient.getBlobContent(any())).thenReturn(" ");

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    void test_init_provide_current_nodes_parse_failed() {
        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        when(azStorageClient.getBlobContent(any())).thenReturn(ClusterNodeFileTest.createClusterNodeFileString("3",
                "3",
                "3",
                "3",
                "3"));

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    void test_init_save_own_file_failed() {
        doThrow(RuntimeException.class).when(azStorageClient).saveBlob(any(), any());

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput, never()).provideCurrentNodes(anyList());
    }

    @Test
    void test_init_container_does_not_exist_is_created() {
        when(azStorageClient.existsContainer()).thenReturn(false);

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).createContainer();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(anyList());
    }

    @Test
    void test_init_create_failed_config() {
        doThrow(new IllegalStateException("Config is not valid.")).when(azStorageClient).createOrUpdate();

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient, never()).existsContainer();
        verify(azStorageClient).createOrUpdate();

        verify(clusterDiscoveryOutput, never()).provideCurrentNodes(anyList());
    }

    @Test
    void test_init_container_created_success() {
        when(azStorageClient.existsContainer()).thenReturn(false);

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();
        verify(azStorageClient).createContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(anyList());
    }

    @Test
    void test_init_config_invalid() throws Exception {
        deleteFilesIn(temporaryFolder);

        try (final var printWriter = new PrintWriter(temporaryFolder.resolve(ConfigReader.STORAGE_FILE).toFile())) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:360");
            printWriter.println("update-interval:180");
        }
        final var azureDiscoveryConfig = new ConfigReader(extensionInformation).readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azureDiscoveryConfig);
        doThrow(IllegalStateException.class).when(azStorageClient).createOrUpdate();

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient, never()).existsContainer();
        verify(clusterDiscoveryOutput, never()).provideCurrentNodes(anyList());
    }

    @Test
    void test_init_no_config() {
        deleteFilesIn(temporaryFolder);

        azureClusterDiscoveryCallback = new AzureClusterDiscoveryCallback(configurationReader);
        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(clusterDiscoveryOutput, never()).provideCurrentNodes(anyList());
    }

    @Test
    void test_reload_success_same_config() {
        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);
        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(clusterDiscoveryOutput, times(2)).provideCurrentNodes(anyList());
    }

    @Test
    void test_reload_success_new_config() throws Exception {
        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        deleteFilesIn(temporaryFolder);

        try (final var printWriter = new PrintWriter(temporaryFolder.resolve(ConfigReader.STORAGE_FILE).toFile())) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:120");
            printWriter.println("update-interval:60");
        }
        final var azureDiscoveryConfig = new ConfigReader(extensionInformation).readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azureDiscoveryConfig);

        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(clusterDiscoveryOutput, times(2)).provideCurrentNodes(anyList());
    }

    @Test
    void test_reload_new_config_missing_container_is_created() throws Exception {
        deleteFilesIn(temporaryFolder);
        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);
        deleteFilesIn(temporaryFolder);

        try (final var printWriter = new PrintWriter(temporaryFolder.resolve(ConfigReader.STORAGE_FILE).toFile())) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:120");
            printWriter.println("update-interval:60");
        }
        final var azureDiscoveryConfig = new ConfigReader(extensionInformation).readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azureDiscoveryConfig);
        when(azStorageClient.existsContainer()).thenReturn(false);

        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(clusterDiscoveryOutput, times(2)).provideCurrentNodes(anyList());
    }

    @Test
    void test_reload_config_missing_init_success() {
        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        deleteFilesIn(temporaryFolder);

        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(clusterDiscoveryOutput, times(2)).provideCurrentNodes(anyList());
    }

    @Test
    void test_reload_config_still_missing() {
        deleteFilesIn(temporaryFolder);
        when(azStorageClient.getStorageConfig()).thenReturn(null);
        doThrow(IllegalStateException.class).when(azStorageClient).createOrUpdate();

        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(clusterDiscoveryOutput, never()).provideCurrentNodes(anyList());
    }

    @Test
    void test_reload_file_expired() throws Exception {
        deleteFilesIn(temporaryFolder);

        try (final var printWriter = new PrintWriter(temporaryFolder.resolve(ConfigReader.STORAGE_FILE).toFile())) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:5");
            printWriter.println("update-interval:1");
        }
        final var azureDiscoveryConfig = new ConfigReader(extensionInformation).readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azureDiscoveryConfig);

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        // wait for file to expire
        TimeUnit.SECONDS.sleep(1);

        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient, times(2)).saveBlob(any(), any());
        verify(clusterDiscoveryOutput, times(2)).provideCurrentNodes(anyList());
    }

    @Test
    void test_reload_file_exception() throws Exception {
        deleteFilesIn(temporaryFolder);

        try (final var printWriter = new PrintWriter(temporaryFolder.resolve(ConfigReader.STORAGE_FILE).toFile())) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:5");
            printWriter.println("update-interval:1");
        }
        final var azureDiscoveryConfig = new ConfigReader(extensionInformation).readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azureDiscoveryConfig);

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        // wait for file to expire
        TimeUnit.SECONDS.sleep(1);
        doThrow(RuntimeException.class).when(azStorageClient).saveBlob(any(), any());

        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient, times(2)).saveBlob(any(), any());
        verify(clusterDiscoveryOutput, times(1)).provideCurrentNodes(anyList());
    }

    @Test
    void test_destroy_success() {
        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);
        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);
        azureClusterDiscoveryCallback.destroy(clusterDiscoveryInput);

        verify(azStorageClient, times(1)).deleteBlob(any());
    }

    @Test
    void test_destroy_no_own_file() {
        azureClusterDiscoveryCallback.destroy(clusterDiscoveryInput);
        verify(azStorageClient, never()).deleteBlob(any());
    }

    @Test
    void test_destroy_delete_own_file_failed() {
        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);
        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        when(azStorageClient.getBlobContent(any())).thenReturn(ClusterNodeFileTest.createClusterNodeFileString("3",
                "3",
                "3",
                "3",
                "3"));

        doThrow(RuntimeException.class).when(azStorageClient).deleteBlob(any());
        azureClusterDiscoveryCallback.destroy(clusterDiscoveryInput);

        verify(azStorageClient, times(1)).deleteBlob(any());
    }

    private void deleteFilesIn(final @NotNull Path folder) {
        final var files = folder.toFile().listFiles();
        if (files != null) {
            for (final var file : files) {
                deleteFilesIn(file.toPath());
            }
        }
    }

    private @NotNull Iterator<BlobItem> createBlobItemIterator() {
        final var blobItem = new BlobItem();
        blobItem.setName("ABCD12");
        return List.of(blobItem).iterator();
    }
}
