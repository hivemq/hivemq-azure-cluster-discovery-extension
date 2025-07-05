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

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.hivemq.extensions.cluster.discovery.azure.config.AzureDiscoveryConfig;
import com.hivemq.extensions.cluster.discovery.azure.config.ConfigReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * @author Till Seeberger
 */
public class AzureStorageClient {

    private static final @NotNull Logger log = LoggerFactory.getLogger(AzureStorageClient.class);

    private final @NotNull ConfigReader configReader;

    private @Nullable BlobContainerClient containerClient;
    private @Nullable AzureDiscoveryConfig azureDiscoveryConfig;

    public AzureStorageClient(final @NotNull ConfigReader configReader) {
        this.configReader = configReader;
    }

    public void createOrUpdate() throws IllegalStateException, IllegalArgumentException {
        final var newAzureDiscoveryConfig = configReader.readConfiguration();
        if (newAzureDiscoveryConfig == null) {
            if (azureDiscoveryConfig != null) {
                log.warn(
                        "Configuration of the Azure Cluster Discovery Extension couldn't be loaded. Using last valid configuration.");
            } else {
                throw new IllegalStateException(
                        "Configuration of the Azure Cluster Discovery Extension couldn't be loaded.");
            }
        } else {
            azureDiscoveryConfig = newAzureDiscoveryConfig;
        }

        final var connectionString = azureDiscoveryConfig.getConnectionString();
        final var containerName = azureDiscoveryConfig.getContainerName();

        // create a BlobServiceClient object which will be used to retrieve the blob container with the HiveMQ node entries
        final var blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();

        // create a client for the blob container
        containerClient = blobServiceClient.getBlobContainerClient(containerName);
    }

    public boolean existsContainer() throws RuntimeException {
        try {
            return containerClient.exists();
        } catch (final BlobStorageException blobStorageException) {
            throw new RuntimeException("Azure Storage Container existence check failed with status code " +
                    blobStorageException.getStatusCode() +
                    " and error code " +
                    blobStorageException.getErrorCode() +
                    ".");
        }
    }

    public void createContainer() throws RuntimeException {
        try {
            containerClient.create();
            log.trace("Created container {} in Azure Storage Account {}.",
                    containerClient.getBlobContainerName(),
                    containerClient.getAccountName());
        } catch (final BlobStorageException error) {
            if (error.getErrorCode().equals(BlobErrorCode.CONTAINER_ALREADY_EXISTS)) {
                log.debug("Cannot create container {} in Azure Storage Account because the container already exists.",
                        containerClient.getBlobContainerName());
            } else {
                throw new RuntimeException("Azure Storage Container creation failed with status code " +
                        error.getStatusCode() +
                        " and error code " +
                        error.getErrorCode() +
                        ".");
            }
        }
    }

    public void saveBlob(final @NotNull String blobName, final @NotNull String content) throws RuntimeException {
        final var blobClient = containerClient.getBlobClient(blobName);
        final var blobData = new ByteArrayInputStream(content.getBytes());
        try {
            blobClient.upload(blobData, content.length(), true);
        } catch (final BlobStorageException blobStorageException) {
            throw new RuntimeException("Azure Storage Blob upload failed with status code " +
                    blobStorageException.getStatusCode() +
                    " and error code " +
                    blobStorageException.getErrorCode() +
                    ".");
        }
    }

    public void deleteBlob(final @NotNull String blobName) throws RuntimeException {
        final var blobClient = containerClient.getBlobClient(blobName);
        try {
            blobClient.delete();
        } catch (final BlobStorageException blobStorageException) {
            throw new RuntimeException("Azure Storage Blob delete failed with status code " +
                    blobStorageException.getStatusCode() +
                    " and error code " +
                    blobStorageException.getErrorCode() +
                    ".");
        }
    }

    public @NotNull String getBlobContent(final @NotNull String blobName) throws RuntimeException {
        final var blobClient = containerClient.getBlobClient(blobName);
        final var outputStream = new ByteArrayOutputStream();
        try {
            blobClient.downloadStream(outputStream);
        } catch (final BlobStorageException blobStorageException) {
            throw new RuntimeException("Azure Storage Blob download failed with status code " +
                    blobStorageException.getStatusCode() +
                    " and error code " +
                    blobStorageException.getErrorCode() +
                    ".");
        }
        return outputStream.toString();
    }

    public @NotNull Iterator<BlobItem> getBlobs(final @NotNull String filePrefix) throws RuntimeException {
        try {
            return containerClient.listBlobs(new ListBlobsOptions().setPrefix(filePrefix), null).iterator();
        } catch (final BlobStorageException blobStorageException) {
            throw new RuntimeException("Azure Storage Blobs retrieval failed with status code " +
                    blobStorageException.getStatusCode() +
                    " and error code " +
                    blobStorageException.getErrorCode() +
                    ".");
        }
    }

    public @Nullable AzureDiscoveryConfig getStorageConfig() {
        return azureDiscoveryConfig;
    }

    public @NotNull ConfigReader getConfigReader() {
        return configReader;
    }

    public @Nullable BlobContainerClient getContainerClient() {
        return containerClient;
    }

    void setContainerClient(final @NotNull BlobContainerClient containerClient) {
        this.containerClient = containerClient;
    }
}
