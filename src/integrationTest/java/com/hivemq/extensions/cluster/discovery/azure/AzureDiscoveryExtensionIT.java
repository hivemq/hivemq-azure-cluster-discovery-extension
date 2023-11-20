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

package com.hivemq.extensions.cluster.discovery.azure;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.hivemq.extensions.cluster.discovery.azure.DockerImageNames.*;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("resource")
class AzureDiscoveryExtensionIT {

    private static final @NotNull String AZURITE_NETWORK_ALIAS = "azurite";
    private static final int AZURITE_PORT = 10000;
    private static final @NotNull String TOXIPROXY_NETWORK_ALIAS = "toxiproxy";
    private static final @NotNull String BLOB_CONTAINER_NAME = "hivemq-discovery";

    private final @NotNull Network network = Network.newNetwork();
    private final @NotNull GenericContainer<?> azureriteContainer = new GenericContainer<>(AZURITE_IMAGE) //
            .withExposedPorts(AZURITE_PORT) //
            .withNetwork(network) //
            .withNetworkAliases(AZURITE_NETWORK_ALIAS);

    @BeforeEach
    void setUp() {
        azureriteContainer.start();
    }

    @AfterEach
    void tearDown() {
        azureriteContainer.stop();
        network.close();
    }

    @Test
    void threeNodesFormCluster() throws IOException, TimeoutException {
        final WaitingConsumer consumer1 = new WaitingConsumer();
        final WaitingConsumer consumer2 = new WaitingConsumer();
        final WaitingConsumer consumer3 = new WaitingConsumer();

        final HiveMQContainer node1 = createHiveMQNode().withLogConsumer(consumer1);
        final HiveMQContainer node2 = createHiveMQNode().withLogConsumer(consumer2);
        final HiveMQContainer node3 = createHiveMQNode().withLogConsumer(consumer3);

        try (node1; node2; node3) {
            node1.start();
            node2.start();
            node3.start();

            consumer1.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 30, SECONDS);
            consumer2.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 30, SECONDS);
            consumer3.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 30, SECONDS);
        }
    }

    @Test
    void twoNodesInCluster_oneNodeStarted_threeNodesInCluster() throws IOException, TimeoutException {
        final WaitingConsumer consumer1 = new WaitingConsumer();
        final WaitingConsumer consumer2 = new WaitingConsumer();
        final WaitingConsumer consumer3 = new WaitingConsumer();

        final HiveMQContainer node1 = createHiveMQNode().withLogConsumer(consumer1);
        final HiveMQContainer node2 = createHiveMQNode().withLogConsumer(consumer2);
        final HiveMQContainer node3 = createHiveMQNode().withLogConsumer(consumer3);

        try (node1; node2; node3) {
            node1.start();
            node2.start();

            consumer1.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 30, SECONDS);
            consumer2.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 30, SECONDS);

            node3.start();

            consumer3.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 300, SECONDS);
        }
    }

    @Test
    void twoNodesInCluster_oneNodeCannotReachAzure_nodeFileDeleted() throws IOException, TimeoutException {
        final ToxiproxyContainer toxiproxy = new ToxiproxyContainer(TOXIPROXY_IMAGE).withNetwork(network).withNetworkAliases(TOXIPROXY_NETWORK_ALIAS);
        try (toxiproxy) {
            toxiproxy.start();

            final ToxiproxyContainer.ContainerProxy proxy = toxiproxy.getProxy(azureriteContainer, AZURITE_PORT);
            final String toxiproxyConnectionString = createAzuriteConnectionString(TOXIPROXY_NETWORK_ALIAS, proxy.getOriginalProxyPort());

            final WaitingConsumer toxicConsumer = new WaitingConsumer();
            final WaitingConsumer normalConsumer = new WaitingConsumer();

            final HiveMQContainer toxicNode =
                    createHiveMQNode(toxiproxyConnectionString).withLogConsumer(toxicConsumer);
            final HiveMQContainer normalNode = createHiveMQNode().withLogConsumer(normalConsumer);

            try (toxicNode; normalNode) {
                toxicNode.start();
                normalNode.start();

                toxicConsumer.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 30, SECONDS);
                normalConsumer.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 30, SECONDS);

                proxy.setConnectionCut(true); // toxicNode now cannot update its node file

                final BlobContainerClient blobContainerClient = new BlobContainerClientBuilder().connectionString(createHostAzuriteConnectionString())
                        .containerName(BLOB_CONTAINER_NAME)
                        .buildClient();

                await().pollInterval(1, SECONDS).atMost(60, SECONDS).until(() -> blobContainerClient.listBlobs().stream().count() == 1);
            }
        }
    }

    @Test
    void threeNodesInCluster_oneNodeStopped_twoNodesInCluster() throws IOException, TimeoutException {
        final WaitingConsumer consumer1 = new WaitingConsumer();
        final WaitingConsumer consumer2 = new WaitingConsumer();
        final WaitingConsumer consumer3 = new WaitingConsumer();

        final HiveMQContainer node1 = createHiveMQNode().withLogConsumer(consumer1);
        final HiveMQContainer node2 = createHiveMQNode().withLogConsumer(consumer2);
        final HiveMQContainer node3 = createHiveMQNode().withLogConsumer(consumer3);

        try (node1; node2; node3) {
            node1.start();
            node2.start();
            node3.start();

            consumer1.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 30, SECONDS);
            consumer2.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 30, SECONDS);
            consumer3.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 30, SECONDS);

            node3.stop();

            consumer1.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 30, SECONDS, 2);
            consumer2.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 30, SECONDS, 2);

            final BlobContainerClient blobContainerClient = new BlobContainerClientBuilder().connectionString(createHostAzuriteConnectionString())
                    .containerName(BLOB_CONTAINER_NAME)
                    .buildClient();

            final List<BlobItem> blobs = blobContainerClient.listBlobs().stream().collect(Collectors.toList());

            assertEquals(3, blobs.size()); // Blob did not yet expire
        }
    }

    @Test
    void wrongConnectionString_reloadRightConnectionString_clusterCreated() throws IOException, TimeoutException {
        final String wrongConnectionString = "DefaultEndpointsProtocol=http;" + //
                "AccountName=devstoreaccount1;" +
                "AccountKey=XXX8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;" +
                "BlobEndpoint=http://" + AZURITE_NETWORK_ALIAS + ":" + AZURITE_PORT + "/devstoreaccount1";

        final WaitingConsumer consumer = new WaitingConsumer();

        final HiveMQContainer reloadingNode = createHiveMQNode(wrongConnectionString).withLogConsumer(consumer);
        final HiveMQContainer normalNode = createHiveMQNode();

        try (reloadingNode; normalNode) {
            reloadingNode.start();
            normalNode.start();

            reloadingNode.copyFileToContainer(
                    Transferable.of(createConfig(createDockerAzuriteConnectionString()).getBytes()),
                    "/opt/hivemq/extensions/hivemq-azure-cluster-discovery-extension/azDiscovery.properties");

            consumer.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 90, SECONDS);
        }
    }

    @Test
    void containerNotExisting_nodeStarted_containerCreated() throws IOException {
        final BlobContainerClient blobContainerClient = new BlobContainerClientBuilder().connectionString(createHostAzuriteConnectionString())
                .containerName(BLOB_CONTAINER_NAME)
                .buildClient();

        assertFalse(blobContainerClient.exists());

        final HiveMQContainer node = createHiveMQNode();

        try (node) {
            node.start();

            assertTrue(blobContainerClient.exists());
        }
    }

    @Test
    void containerExisting_nodeStarted_containerUsed() throws IOException {
        final BlobContainerClient blobContainerClient = new BlobContainerClientBuilder().connectionString(createHostAzuriteConnectionString())
                .containerName(BLOB_CONTAINER_NAME)
                .buildClient();
        final BlobClient blob = blobContainerClient.getBlobClient("blob");
        blobContainerClient.create();
        blob.upload(new ByteArrayInputStream("Test".getBytes()), "Test".getBytes().length);

        final WaitingConsumer consumer = new WaitingConsumer();

        final HiveMQContainer node = createHiveMQNode().withLogConsumer(consumer);

        try (node) {
            node.start();

            final List<BlobItem> blobs = blobContainerClient.listBlobs().stream().collect(Collectors.toList());
            assertEquals(2, blobs.size());
        }
    }

    private @NotNull String createAzuriteConnectionString(final @NotNull String host, final int port) {
        return "DefaultEndpointsProtocol=http;" + //
                "AccountName=devstoreaccount1;" +
                "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;" +
                "BlobEndpoint=http://" + host + ":" + port + "/devstoreaccount1";
    }

    private @NotNull String createDockerAzuriteConnectionString() {
        return createAzuriteConnectionString(AZURITE_NETWORK_ALIAS, AZURITE_PORT);
    }

    private @NotNull String createConfig(final @NotNull String connectionString) {
        return "connection-string=" + connectionString + '\n' + //
                "container-name=" + BLOB_CONTAINER_NAME + '\n' + //
                "file-prefix=hivemq-node-\n" + //
                "file-expiration=15\n" + //
                "update-interval=5";
    }

    private @NotNull String createHostAzuriteConnectionString() {
        return createAzuriteConnectionString("127.0.0.1", azureriteContainer.getMappedPort(AZURITE_PORT));
    }

    private @NotNull HiveMQContainer createHiveMQNode(final @NotNull String connectionString) throws IOException {

        final Path configFile = Files.createTempDirectory("az-extension-test").resolve("azDiscovery.properties");
        Files.writeString(configFile, createConfig(connectionString));

        return new HiveMQContainer(HIVEMQ_IMAGE) //
                .withHiveMQConfig(MountableFile.forClasspathResource("config.xml"))
                .withExtension(MountableFile.forClasspathResource("hivemq-azure-cluster-discovery-extension"))
                .withFileInExtensionHomeFolder(MountableFile.forHostPath(configFile),
                        "hivemq-azure-cluster-discovery-extension")
                .withNetwork(network);
    }

    private @NotNull HiveMQContainer createHiveMQNode() throws IOException {
        return createHiveMQNode(createDockerAzuriteConnectionString());
    }
}