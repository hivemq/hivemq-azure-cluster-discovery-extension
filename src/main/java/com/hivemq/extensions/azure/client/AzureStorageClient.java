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

package com.hivemq.extensions.azure.client;

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
import com.hivemq.extensions.azure.config.ConfigReader;
import com.hivemq.extensions.azure.config.StorageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;

/**
 * @author Till Seeberger
 */
public class AzureStorageClient {

    private static final Logger log = LoggerFactory.getLogger(AzureStorageClient.class);

    public @NotNull ConfigReader configReader;
    public BlobContainerClient containerClient;
    public StorageConfig storageConfig;

    public AzureStorageClient(final @NotNull ConfigReader configReader) {
        this.configReader = configReader;
    }

    public void createOrUpdate() {

        final StorageConfig newStorageConfig = configReader.readConfiguration();
        if (newStorageConfig == null) {
            throw new IllegalStateException("Configuration of the Azure Cluster Discovery Extension couldn't be loaded.");
        }
        storageConfig = newStorageConfig;

        final String connectionString = storageConfig.getConnectionString();
        final String containerName = storageConfig.getContainerName();

        // Create a BlobServiceClient object which will be used to retrieve the blob container with the HiveMQ node entries
        final BlobServiceClient blobServiceClient =
                new BlobServiceClientBuilder().connectionString(connectionString).buildClient();

        // Create a client for the blob container
        containerClient = blobServiceClient.getBlobContainerClient(containerName);
    }

    public boolean existsContainer() {
        return containerClient.exists();
    }

    public void createContainer() throws BlobStorageException {
        try {
            containerClient.create();
            log.trace(
                    "Created container {} in Azure Storage Account {}.",
                    containerClient.getBlobContainerName(),
                    containerClient.getAccountName());
        } catch (BlobStorageException error) {
            if (error.getErrorCode().equals(BlobErrorCode.CONTAINER_ALREADY_EXISTS)) {
                log.debug(
                        "Cannot create container {} in Azure Storage Account because the container already exists.",
                        containerClient.getBlobContainerName());
            } else {
                throw error;
            }
        }
    }

    public void saveBlob(final @NotNull String blobName, final @NotNull String content) throws RuntimeException {
        final BlobClient blobClient = containerClient.getBlobClient(blobName);
        final InputStream blobData = new ByteArrayInputStream(content.getBytes());

        blobClient.upload(blobData, content.length(), true);
    }

    public void deleteBlob(final @NotNull String blobName) throws RuntimeException {
        final BlobClient blob = containerClient.getBlobClient(blobName);
        blob.delete();
    }

    @NotNull
    public String getBlobContent(final @NotNull String blobName) throws UncheckedIOException {
        final BlobClient blobClient = containerClient.getBlobClient(blobName);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        blobClient.download(outputStream);

        return outputStream.toString();
    }

    @NotNull
    public Iterator<BlobItem> getBlobs(final @NotNull String filePrefix) throws RuntimeException {
        return containerClient.listBlobs(new ListBlobsOptions().setPrefix(filePrefix), null).iterator();
    }

    @Nullable
    public StorageConfig getStorageConfig() { return storageConfig; }
}
