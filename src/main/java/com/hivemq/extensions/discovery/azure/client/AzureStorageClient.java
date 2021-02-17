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

package com.hivemq.extensions.discovery.azure.client;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.annotations.Nullable;
import com.hivemq.extensions.discovery.azure.config.AzureDiscoveryConfig;
import com.hivemq.extensions.discovery.azure.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;

/**
 * @author Till Seeberger
 */
public class AzureStorageClient {

    private static final Logger log = LoggerFactory.getLogger(AzureStorageClient.class);

    private @NotNull final ConfigReader configReader;
    private @Nullable BlobContainerClient containerClient;
    private @Nullable AzureDiscoveryConfig azureDiscoveryConfig;

    public AzureStorageClient(final @NotNull ConfigReader configReader) {
        this.configReader = configReader;
    }

    public void createOrUpdate() throws IllegalStateException, IllegalArgumentException {

        final AzureDiscoveryConfig newAzureDiscoveryConfig = configReader.readConfiguration();
        if (newAzureDiscoveryConfig == null) {
            throw new IllegalStateException("Configuration of the Azure Cluster Discovery Extension couldn't be loaded.");
        }
        azureDiscoveryConfig = newAzureDiscoveryConfig;

        final String connectionString = azureDiscoveryConfig.getConnectionString();
        final String containerName = azureDiscoveryConfig.getContainerName();

        // Create a BlobServiceClient object which will be used to retrieve the blob container with the HiveMQ node entries
        final BlobServiceClient blobServiceClient =
                new BlobServiceClientBuilder().connectionString(connectionString).buildClient();

        // Create a client for the blob container
        containerClient = blobServiceClient.getBlobContainerClient(containerName);
    }

    public boolean existsContainer() throws RuntimeException {
        try {
            return containerClient.exists();
        } catch (final BlobStorageException blobStorageException) {
            throw new RuntimeException("Azure Storage Container existence check failed with status code " +
                    blobStorageException.getStatusCode() + " and error code " + blobStorageException.getErrorCode());
        }
    }

    public void createContainer() throws RuntimeException {
        try {
            containerClient.create();
            log.trace("Created container {} in Azure Storage Account {}.",
                    containerClient.getBlobContainerName(),
                    containerClient.getAccountName());
        } catch (BlobStorageException error) {
            if (error.getErrorCode().equals(BlobErrorCode.CONTAINER_ALREADY_EXISTS)) {
                log.debug(
                        "Cannot create container {} in Azure Storage Account because the container already exists.",
                        containerClient.getBlobContainerName());
            } else {
                throw new RuntimeException(
                        "Azure Storage Container creation failed with status code " + error.getStatusCode() +
                                " and error code " + error.getErrorCode());
            }
        }
    }

    public void saveBlob(final @NotNull String blobName, final @NotNull String content) throws RuntimeException {
        final BlobClient blobClient = containerClient.getBlobClient(blobName);
        final InputStream blobData = new ByteArrayInputStream(content.getBytes());

        try {
            blobClient.upload(blobData, content.length(), true);
        } catch (final BlobStorageException blobStorageException) {
            throw new RuntimeException(
                    "Azure Storage Blob upload failed with status code " + blobStorageException.getStatusCode() +
                            " and error code " + blobStorageException.getErrorCode());
        }
    }

    public void deleteBlob(final @NotNull String blobName) throws RuntimeException {
        final BlobClient blob = containerClient.getBlobClient(blobName);

        try {
            blob.delete();
        } catch (final BlobStorageException blobStorageException) {
            throw new RuntimeException(
                    "Azure Storage Blob delete failed with status code " + blobStorageException.getStatusCode() +
                            " and error code " + blobStorageException.getErrorCode());
        }
    }

    @NotNull
    public String getBlobContent(final @NotNull String blobName) throws RuntimeException {
        final BlobClient blobClient = containerClient.getBlobClient(blobName);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            blobClient.download(outputStream);
        } catch (final BlobStorageException blobStorageException) {
            throw new RuntimeException(
                    "Azure Storage Blob download failed with status code " + blobStorageException.getStatusCode() +
                            " and error code " + blobStorageException.getErrorCode());
        }

        return outputStream.toString();
    }

    @NotNull
    public Iterator<BlobItem> getBlobs(final @NotNull String filePrefix) throws RuntimeException {

        try {
            return containerClient.listBlobs(new ListBlobsOptions().setPrefix(filePrefix), null).iterator();
        } catch (final BlobStorageException blobStorageException) {
            throw new RuntimeException(
                    "Azure Storage Blobs retrieval failed with status code " + blobStorageException.getStatusCode() +
                            " and error code " + blobStorageException.getErrorCode());
        }
    }

    public @Nullable AzureDiscoveryConfig getStorageConfig() { return azureDiscoveryConfig; }

    public @Nullable ConfigReader getConfigReader() { return configReader; }

    public @Nullable BlobContainerClient getContainerClient() { return containerClient; }

    void setContainerClient(final @NotNull BlobContainerClient containerClient) {
        this.containerClient = containerClient;
    }
}
