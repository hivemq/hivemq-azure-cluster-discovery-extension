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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.azure.storage.blob.models.BlobItem;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.annotations.Nullable;
import com.hivemq.extension.sdk.api.services.cluster.ClusterDiscoveryCallback;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterDiscoveryInput;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterDiscoveryOutput;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterNodeAddress;
import com.hivemq.extensions.discovery.azure.client.AzureStorageClient;
import com.hivemq.extensions.discovery.azure.config.ClusterNodeFile;
import com.hivemq.extensions.discovery.azure.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.hivemq.extensions.discovery.azure.util.StringUtil.isNullOrBlank;

/**
 * @author Till Seeberger
 */
public class AzureClusterDiscoveryCallback implements ClusterDiscoveryCallback {

    private static final @NotNull Logger log = LoggerFactory.getLogger(AzureClusterDiscoveryCallback.class);

    private final @NotNull AzureStorageClient azureStorageClient;
    private @Nullable ClusterNodeFile ownNodeFile;

    public AzureClusterDiscoveryCallback(final @NotNull ConfigReader configReader) {
        azureStorageClient = new AzureStorageClient(configReader);
    }

    AzureClusterDiscoveryCallback(final @NotNull AzureStorageClient azureStorageClient) {
        this.azureStorageClient = azureStorageClient;
    }

    @Override
    public void init(
            final @NotNull ClusterDiscoveryInput clusterDiscoveryInput,
            final @NotNull ClusterDiscoveryOutput clusterDiscoveryOutput) {

        final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger("com.azure").setLevel(Level.OFF);
        loggerContext.getLogger("reactor").setLevel(Level.OFF);

        try {
            azureStorageClient.createOrUpdate();
        } catch (final IllegalStateException | IllegalArgumentException ex) {
            log.warn("Initialization of the Azure Cluster Discovery Callback failed. {}",
                    getRootCause(ex).getMessage());
            return;
        }

        clusterDiscoveryOutput.setReloadInterval(azureStorageClient.getStorageConfig()
                .getFileUpdateIntervalInSeconds());

        try {
            if (!azureStorageClient.existsContainer()) {
                log.info("Azure Blob Storage Container {} doesn't exist. Creating it.",
                        azureStorageClient.getStorageConfig().getContainerName());
                azureStorageClient.createContainer();
            }

            saveOwnFile(clusterDiscoveryInput.getOwnClusterId(), clusterDiscoveryInput.getOwnAddress());
            clusterDiscoveryOutput.provideCurrentNodes(getNodeAddresses());
        } catch (final Exception ex) {
            log.warn("Initialization of the Azure Cluster Discovery Callback failed. {}",
                    getRootCause(ex).getMessage());
        }

    }

    @Override
    public void reload(
            final @NotNull ClusterDiscoveryInput clusterDiscoveryInput,
            final @NotNull ClusterDiscoveryOutput clusterDiscoveryOutput) {

        try {
            azureStorageClient.createOrUpdate();
        } catch (final IllegalStateException | IllegalArgumentException ex) {
            log.warn("Reload of the Azure Cluster Discovery Callback failed. {}", getRootCause(ex).getMessage());
            return;
        }

        clusterDiscoveryOutput.setReloadInterval(azureStorageClient.getStorageConfig()
                .getFileUpdateIntervalInSeconds());

        try {
            if (!azureStorageClient.existsContainer()) {
                log.info("Azure Blob Storage Container {} doesn't exist. Creating it.",
                        azureStorageClient.getStorageConfig().getContainerName());
                azureStorageClient.createContainer();
            }

            if (ownNodeFile == null ||
                    ownNodeFile.isExpired(azureStorageClient.getStorageConfig().getFileUpdateIntervalInSeconds())) {
                saveOwnFile(clusterDiscoveryInput.getOwnClusterId(), clusterDiscoveryInput.getOwnAddress());
            }

            clusterDiscoveryOutput.provideCurrentNodes(getNodeAddresses());
        } catch (final Exception ex) {
            log.warn("Reload of the Azure Cluster Discovery Callback failed. {}", getRootCause(ex).getMessage());
        }
    }

    @Override
    public void destroy(final @NotNull ClusterDiscoveryInput clusterDiscoveryInput) {
        try {
            if (ownNodeFile != null) {
                deleteOwnFile(clusterDiscoveryInput.getOwnClusterId());
            }
        } catch (final RuntimeException ex) {
            log.warn("Destroy of the Azure Cluster Discovery Callback failed. {}", getRootCause(ex).getMessage());
        }

    }

    private void saveOwnFile(final @NotNull String ownClusterId, final @NotNull ClusterNodeAddress ownAddress)
            throws RuntimeException {
        final String blobKey = azureStorageClient.getStorageConfig().getFilePrefix() + ownClusterId;
        final ClusterNodeFile newNodeFile = new ClusterNodeFile(ownClusterId, ownAddress);

        azureStorageClient.saveBlob(blobKey, newNodeFile.toString());
        ownNodeFile = newNodeFile;

        log.debug("Updated own Azure Blob file '{}'.", blobKey);
    }

    private void deleteOwnFile(final @NotNull String ownClusterId) throws RuntimeException {
        final String blobKey = azureStorageClient.getStorageConfig().getFilePrefix() + ownClusterId;

        azureStorageClient.deleteBlob(blobKey);
        ownNodeFile = null;

        log.debug("Removed own Azure Blob file '{}'.", blobKey);
    }

    private @NotNull List<ClusterNodeAddress> getNodeAddresses() {

        final List<ClusterNodeAddress> nodeAddresses = new ArrayList<>();

        final List<ClusterNodeFile> nodeFiles;
        try {
            nodeFiles = getNodeFiles();
        } catch (final Exception e) {
            log.debug("Unknown error while reading all node files.", e);
            return nodeAddresses;
        }

        for (final ClusterNodeFile nodeFile : nodeFiles) {

            if (nodeFile.isExpired(azureStorageClient.getStorageConfig().getFileExpirationInSeconds())) {

                log.debug("Azure Blob of node with clusterId {} is expired. Blob will be deleted.",
                        nodeFile.getClusterId());

                final String blobKey = azureStorageClient.getStorageConfig().getFilePrefix() + nodeFile.getClusterId();

                try {
                    azureStorageClient.deleteBlob(blobKey);
                } catch (final Exception ex) {
                    log.warn("Could not delete expired Azure Blob file '{}'. {}", blobKey, getRootCause(ex));
                }
            } else {
                nodeAddresses.add(nodeFile.getClusterNodeAddress());
            }
        }

        log.debug("Found following node addresses with the Azure Cluster Discovery Extension: {}", nodeAddresses);

        return nodeAddresses;
    }

    private @NotNull List<ClusterNodeFile> getNodeFiles() {

        final List<ClusterNodeFile> clusterNodeFiles = new ArrayList<>();

        try {
            final Iterator<BlobItem> blobs =
                    azureStorageClient.getBlobs(azureStorageClient.getStorageConfig().getFilePrefix());
            blobs.forEachRemaining((BlobItem blob) -> {
                final ClusterNodeFile nodeFile = getNodeFile(blob);
                if (nodeFile != null) {
                    clusterNodeFiles.add(nodeFile);
                }
            });
        } catch (final Exception ex) {
            log.warn("Could not get Azure Blobs. {}", getRootCause(ex).getMessage());
        }

        return clusterNodeFiles;
    }

    private @Nullable ClusterNodeFile getNodeFile(final @NotNull BlobItem blob) {

        final String fileContent;
        try {
            fileContent = azureStorageClient.getBlobContent(blob.getName());
        } catch (RuntimeException e) {
            log.warn("An error occurred while downloading the Azure Blob. {}", getRootCause(e).getMessage());
            return null;
        }

        if (isNullOrBlank(fileContent)) {
            log.debug("Azure Blob '{}' has no content. Skipping file.", blob.getName());
            return null;
        }

        final ClusterNodeFile nodeFile = ClusterNodeFile.parseClusterNodeFile(fileContent);
        if (nodeFile == null) {
            log.debug("Content of the Azure Blob '{}' could not be parsed. Skipping Blob.", blob.getName());
            return null;
        }
        return nodeFile;
    }

    private @NotNull Throwable getRootCause(final @NotNull Throwable e) {
        Throwable cause;
        Throwable result = e;

        while (null != (cause = result.getCause()) && (result != cause)) {
            result = cause;
        }
        return result;
    }
}
