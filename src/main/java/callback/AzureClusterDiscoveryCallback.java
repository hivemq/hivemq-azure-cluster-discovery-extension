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

package callback;

import azure.AzureStorageClient;
import com.azure.storage.blob.models.BlobItem;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.annotations.Nullable;
import com.hivemq.extension.sdk.api.services.cluster.ClusterDiscoveryCallback;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterDiscoveryInput;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterDiscoveryOutput;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterNodeAddress;
import config.ClusterNodeFile;
import config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static util.StringUtil.isNullOrBlank;

/**
 * @author Till Seeberger
 */
public class AzureClusterDiscoveryCallback implements ClusterDiscoveryCallback {

    private static final Logger logger = LoggerFactory.getLogger(AzureClusterDiscoveryCallback.class);

    public AzureStorageClient azureStorageClient;

    private @Nullable ClusterNodeFile ownNodeFile;

    public AzureClusterDiscoveryCallback(final @NotNull ConfigReader configReader) {
        azureStorageClient = new AzureStorageClient(configReader);
    }

    @Override
    public void init(
            final @NotNull ClusterDiscoveryInput clusterDiscoveryInput,
            final @NotNull ClusterDiscoveryOutput clusterDiscoveryOutput) {

        try {
            azureStorageClient.createOrUpdate();
        } catch (final Exception ex) {
            logger.error("Initialization of Azure Cluster Discovery Callback failed", ex);
            return;
        }

        clusterDiscoveryOutput.setReloadInterval(azureStorageClient.getStorageConfig()
                .getFileUpdateIntervalInSeconds());

        try {
            if (!azureStorageClient.existsContainer()) {
                logger.info(
                        "Container {} doesn't exist. Creating it.",
                        azureStorageClient.getStorageConfig().getContainerName());
                azureStorageClient.createContainer();
            }

            saveOwnFile(clusterDiscoveryInput.getOwnClusterId(), clusterDiscoveryInput.getOwnAddress());
            clusterDiscoveryOutput.provideCurrentNodes(getNodeAddresses());
        } catch (final Exception e) {
            logger.error("Initialization of Azure Cluster Discovery Callback failed.", e);
        }

    }

    @Override
    public void reload(
            final @NotNull ClusterDiscoveryInput clusterDiscoveryInput,
            final @NotNull ClusterDiscoveryOutput clusterDiscoveryOutput) {

        try {
            azureStorageClient.createOrUpdate();
        } catch (final Exception ex) {
            logger.error("Reload of the Azure Cluster Discovery Callback failed.", ex);
            return;
        }

        clusterDiscoveryOutput.setReloadInterval(azureStorageClient.getStorageConfig()
                .getFileUpdateIntervalInSeconds());

        try {
            if (!azureStorageClient.existsContainer()) {
                logger.info(
                        "Container {} doesn't exist. Creating it.",
                        azureStorageClient.getStorageConfig().getContainerName());
                azureStorageClient.createContainer();
            }

            if (ownNodeFile == null ||
                    ownNodeFile.isExpired(azureStorageClient.getStorageConfig().getFileUpdateIntervalInSeconds())) {
                saveOwnFile(clusterDiscoveryInput.getOwnClusterId(), clusterDiscoveryInput.getOwnAddress());
            }

            clusterDiscoveryOutput.provideCurrentNodes(getNodeAddresses());
        } catch (final Exception e) {
            logger.error("Reload of the Azure Cluster Discovery Callback failed.", e);
        }
    }

    @Override
    public void destroy(final @NotNull ClusterDiscoveryInput clusterDiscoveryInput) {
        try {
            if (ownNodeFile != null) {
                deleteOwnFile(clusterDiscoveryInput.getOwnClusterId());
            }
        } catch (final Exception e) {
            logger.error("Destroy of the Azure Cluster Discovery Callback failed.", e);
        }
    }

    private void saveOwnFile(final @NotNull String ownClusterId, final @NotNull ClusterNodeAddress ownAddress)
            throws RuntimeException {
        final String blobKey = azureStorageClient.getStorageConfig().getFilePrefix() + ownClusterId;
        final ClusterNodeFile newNodeFile = new ClusterNodeFile(ownClusterId, ownAddress);

        azureStorageClient.saveBlob(blobKey, newNodeFile.toString());
        ownNodeFile = newNodeFile;

        logger.debug("Updated own Azure Blob file '{}'.", blobKey);
    }

    private void deleteOwnFile(final @NotNull String ownClusterId) {
        final String blobKey = azureStorageClient.getStorageConfig().getFilePrefix() + ownClusterId;

        azureStorageClient.deleteBlob(blobKey);
        ownNodeFile = null;

        logger.debug("Removed own Azure Blob file '{}'.", blobKey);
    }

    private @NotNull List<ClusterNodeAddress> getNodeAddresses() {

        final List<ClusterNodeAddress> nodeAddresses = new ArrayList<>();

        final List<ClusterNodeFile> nodeFiles;
        try {
            nodeFiles = getNodeFiles();
        } catch (final Exception e) {
            logger.error("Unknown error while reading all node files.", e);
            return nodeAddresses;
        }

        for (final ClusterNodeFile nodeFile : nodeFiles) {

            if (nodeFile.isExpired(azureStorageClient.getStorageConfig().getFileExpirationInSeconds())) {

                logger.debug(
                        "Blob of node with clusterId {} is expired. Blob will be deleted.",
                        nodeFile.getClusterId());

                final String blobKey = azureStorageClient.getStorageConfig().getFilePrefix() + nodeFile.getClusterId();
                azureStorageClient.deleteBlob(blobKey);
            } else {
                nodeAddresses.add(nodeFile.getClusterNodeAddress());
            }
        }

        logger.debug("Found following node addresses with the Azure Cluster Discovery Extension: {}", nodeAddresses);

        return nodeAddresses;
    }

    private @NotNull List<ClusterNodeFile> getNodeFiles() {

        final List<ClusterNodeFile> clusterNodeFiles = new ArrayList<>();

        final Iterator<BlobItem> blobs =
                azureStorageClient.getBlobs(azureStorageClient.getStorageConfig().getFilePrefix());

        blobs.forEachRemaining((BlobItem blob) -> {
            final ClusterNodeFile nodeFile = getNodeFile(blob);
            if (nodeFile != null) {
                clusterNodeFiles.add(nodeFile);
            }
        });

        return clusterNodeFiles;
    }

    private @Nullable ClusterNodeFile getNodeFile(final @NotNull BlobItem blob) {

        final String fileContent;
        try {
            fileContent = azureStorageClient.getBlobContent(blob.getName());
        } catch (UncheckedIOException e) {
            logger.error("An error occurred while downloading the Azure Blob.", e);
            return null;
        }

        if (isNullOrBlank(fileContent)) {
            logger.debug("Azure Blob '{}' has no content. Skipping file.", blob.getName());
            return null;
        }

        final ClusterNodeFile nodeFile = ClusterNodeFile.parseClusterNodeFile(fileContent);
        if (nodeFile == null) {
            logger.debug("Content of the Azure Blob '{}' could not be parsed. Skipping Blob.", blob.getName());
            return null;
        }
        return nodeFile;
    }

}
