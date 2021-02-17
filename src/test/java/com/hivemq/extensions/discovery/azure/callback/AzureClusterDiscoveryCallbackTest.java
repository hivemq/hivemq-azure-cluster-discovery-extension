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

package com.hivemq.extensions.discovery.azure.callback;

import com.hivemq.extensions.discovery.azure.client.AzureStorageClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.hivemq.extension.sdk.api.parameter.ExtensionInformation;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterDiscoveryInput;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterDiscoveryOutput;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterNodeAddress;
import com.hivemq.extensions.discovery.azure.config.ClusterNodeFileTest;
import com.hivemq.extensions.discovery.azure.config.ConfigReader;
import com.hivemq.extensions.discovery.azure.config.AzureDiscoveryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

public class AzureClusterDiscoveryCallbackTest {

    @TempDir
    File temporaryFolder;

    @Mock
    public ExtensionInformation extensionInformation;

    @Mock
    public AzureStorageClient azStorageClient;

    @Mock
    BlobContainerClient blobContainerClient;

    @Mock
    public ClusterDiscoveryInput clusterDiscoveryInput;

    @Mock
    public ClusterDiscoveryOutput clusterDiscoveryOutput;

    private AzureClusterDiscoveryCallback azureClusterDiscoveryCallback;
    private ConfigReader configurationReader;
    private AzureDiscoveryConfig azAzureDiscoveryConfig;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        when(clusterDiscoveryInput.getOwnClusterId()).thenReturn("ABCD12");
        when(clusterDiscoveryInput.getOwnAddress()).thenReturn(new ClusterNodeAddress("127.0.0.1", 7800));

        when(extensionInformation.getExtensionHomeFolder()).thenReturn(temporaryFolder);

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:360");
            printWriter.println("update-interval:180");
        }

        configurationReader = new ConfigReader(extensionInformation);
        azureClusterDiscoveryCallback = new AzureClusterDiscoveryCallback(configurationReader);
        when(azStorageClient.getConfigReader()).thenReturn(configurationReader);
        azureClusterDiscoveryCallback.setAzureStorageClient(azStorageClient);

        azStorageClient.getConfigReader().readConfiguration();
        when(azStorageClient.getContainerClient()).thenReturn(blobContainerClient);

        azAzureDiscoveryConfig = configurationReader.readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azAzureDiscoveryConfig);
        when(azStorageClient.existsContainer()).thenReturn(true);
    }

    @Test
    public void test_init_success() {
        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        when(azStorageClient.getBlobContent(any())).thenReturn(ClusterNodeFileTest.createClusterNodeFileString(
                "3",
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
    public void test_init_provide_current_nodes_exception_getting_node_files() {
        doThrow(RuntimeException.class).when(azStorageClient).getBlobs(any());

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    public void test_init_provide_current_nodes_blobexception_getting_node_file() {
        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        doThrow(UncheckedIOException.class).when(azStorageClient).getBlobContent(any());

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    public void test_init_provide_current_nodes_exception_getting_node_file() {
        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        doThrow(UncheckedIOException.class).when(azStorageClient).getBlobContent(any());

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    public void test_init_provide_current_nodes_empty_blob_item_iterator() {
        when(azStorageClient.getBlobs(any())).thenReturn(createEmptyBlobItemIterator());

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    public void test_init_provide_current_nodes_expired_files() throws Exception {
        deleteFilesInTemporaryFolder();

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:2");
            printWriter.println("update-interval:1");
        }
        final AzureDiscoveryConfig azureDiscoveryConfig = new ConfigReader(extensionInformation).readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azureDiscoveryConfig);

        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        when(azStorageClient.getBlobContent(any())).thenReturn(ClusterNodeFileTest.createClusterNodeFileString(
                "3",
                "3",
                "3",
                "3",
                "3"));

        // Wait for files to expire
        TimeUnit.SECONDS.sleep(2);

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    public void test_init_provide_current_nodes_blob_null() {
        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        when(azStorageClient.getBlobContent(any())).thenReturn(null);

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    public void test_init_provide_current_nodes_blob_content_blank() {
        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        when(azStorageClient.getBlobContent(any())).thenReturn(" ");

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(new ArrayList<>());
    }

    @Test
    public void test_init_provide_current_nodes_parse_failed() {
        when(azStorageClient.getBlobs(any())).thenReturn(createBlobItemIterator());
        when(azStorageClient.getBlobContent(any())).thenReturn(ClusterNodeFileTest.createClusterNodeFileString(
                "3",
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
    public void test_init_save_own_file_failed() {
        doThrow(RuntimeException.class).when(azStorageClient).saveBlob(any(), any());

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput, never()).provideCurrentNodes(anyList());
    }

    @Test
    public void test_init_container_does_not_exist_is_created() {

        when(azStorageClient.existsContainer()).thenReturn(false);

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).createContainer();
        verify(azStorageClient).existsContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(anyList());
    }

    @Test
    public void test_init_create_failed_config() {

        doThrow(new IllegalStateException("Config is not valid.")).when(azStorageClient).createOrUpdate();

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient, never()).existsContainer();
        verify(azStorageClient).createOrUpdate();

        verify(clusterDiscoveryOutput, never()).provideCurrentNodes(anyList());
    }

    @Test
    public void test_init_container_created_success() {

        when(azStorageClient.existsContainer()).thenReturn(false);

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient).createOrUpdate();
        verify(azStorageClient).existsContainer();
        verify(azStorageClient).createContainer();

        verify(clusterDiscoveryOutput).provideCurrentNodes(anyList());
    }

    @Test
    public void test_init_config_invalid() throws Exception {

        deleteFilesInTemporaryFolder();

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:360");
            printWriter.println("update-interval:180");
        }
        final AzureDiscoveryConfig azureDiscoveryConfig = new ConfigReader(extensionInformation).readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azureDiscoveryConfig);
        doThrow(IllegalStateException.class).when(azStorageClient).createOrUpdate();

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient, never()).existsContainer();
        verify(clusterDiscoveryOutput, never()).provideCurrentNodes(anyList());
    }

    @Test
    public void test_init_no_config() {
        temporaryFolder.delete();

        azureClusterDiscoveryCallback = new AzureClusterDiscoveryCallback(configurationReader);
        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(clusterDiscoveryOutput, never()).provideCurrentNodes(anyList());
    }

    @Test
    public void test_reload_success_same_config() {

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);
        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(clusterDiscoveryOutput, times(2)).provideCurrentNodes(anyList());
    }

    @Test
    public void test_reload_success_new_config() throws Exception {
        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        deleteFilesInTemporaryFolder();

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:120");
            printWriter.println("update-interval:60");
        }
        final AzureDiscoveryConfig azureDiscoveryConfig = new ConfigReader(extensionInformation).readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azureDiscoveryConfig);

        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(clusterDiscoveryOutput, times(2)).provideCurrentNodes(anyList());
    }

    @Test
    public void test_reload_new_config_missing_container_is_created() throws Exception {
        temporaryFolder.delete();
        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);
        deleteFilesInTemporaryFolder();

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:120");
            printWriter.println("update-interval:60");
        }
        final AzureDiscoveryConfig azureDiscoveryConfig = new ConfigReader(extensionInformation).readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azureDiscoveryConfig);
        when(azStorageClient.existsContainer()).thenReturn(false);

        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(clusterDiscoveryOutput, times(2)).provideCurrentNodes(anyList());
    }

    @Test
    public void test_reload_config_missing_init_success() {
        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        deleteFilesInTemporaryFolder();

        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(clusterDiscoveryOutput, times(2)).provideCurrentNodes(anyList());
    }

    @Test
    public void test_reload_config_still_missing() {
        temporaryFolder.delete();
        when(azStorageClient.getStorageConfig()).thenReturn(null);
        doThrow(IllegalStateException.class).when(azStorageClient).createOrUpdate();

        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(clusterDiscoveryOutput, never()).provideCurrentNodes(anyList());
    }

    @Test
    public void test_reload_file_expired() throws Exception {
        deleteFilesInTemporaryFolder();

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:5");
            printWriter.println("update-interval:1");
        }
        final AzureDiscoveryConfig azureDiscoveryConfig = new ConfigReader(extensionInformation).readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azureDiscoveryConfig);

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        // Wait for file to expire
        TimeUnit.SECONDS.sleep(1);

        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient, times(2)).saveBlob(any(), any());
        verify(clusterDiscoveryOutput, times(2)).provideCurrentNodes(anyList());
    }

    @Test
    public void test_reload_file_exception() throws Exception {
        deleteFilesInTemporaryFolder();

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:5");
            printWriter.println("update-interval:1");
        }
        final AzureDiscoveryConfig azureDiscoveryConfig = new ConfigReader(extensionInformation).readConfiguration();
        when(azStorageClient.getStorageConfig()).thenReturn(azureDiscoveryConfig);

        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);

        // Wait for file to expire
        TimeUnit.SECONDS.sleep(1);
        doThrow(RuntimeException.class).when(azStorageClient).saveBlob(any(), any());

        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        verify(azStorageClient, times(2)).saveBlob(any(), any());
        verify(clusterDiscoveryOutput, times(1)).provideCurrentNodes(anyList());
    }

    @Test
    public void test_destroy_success() {
        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);
        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);
        azureClusterDiscoveryCallback.destroy(clusterDiscoveryInput);

        verify(azStorageClient, times(1)).deleteBlob(any());
    }

    @Test
    public void test_destroy_no_own_file() {
        azureClusterDiscoveryCallback.destroy(clusterDiscoveryInput);
        verify(azStorageClient, never()).deleteBlob(any());
    }

    @Test
    public void test_destroy_delete_own_file_failed() {
        azureClusterDiscoveryCallback.init(clusterDiscoveryInput, clusterDiscoveryOutput);
        azureClusterDiscoveryCallback.reload(clusterDiscoveryInput, clusterDiscoveryOutput);

        when(azStorageClient.getBlobContent(any())).thenReturn(ClusterNodeFileTest.createClusterNodeFileString(
                "3",
                "3",
                "3",
                "3",
                "3"));

        doThrow(RuntimeException.class).when(azStorageClient).deleteBlob(any());
        azureClusterDiscoveryCallback.destroy(clusterDiscoveryInput);

        verify(azStorageClient, times(1)).deleteBlob(any());
    }

    private void deleteFilesInTemporaryFolder() {
        final String root = temporaryFolder.getAbsolutePath();
        // deletes also root folder
        temporaryFolder.delete();
        // restore root folder
        new File(root).mkdir();
    }

    Iterator<BlobItem> createBlobItemIterator() {
        final BlobItem blobItem = new BlobItem();
        blobItem.setName("ABCD12");
        return List.of(blobItem).iterator();
    }

    Iterator<BlobItem> createEmptyBlobItemIterator() {
        return Collections.emptyIterator();
    }

}