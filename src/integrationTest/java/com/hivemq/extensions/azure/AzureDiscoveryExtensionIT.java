package com.hivemq.extensions.azure;/*
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

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.testcontainer.junit5.HiveMQTestContainerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class AzureDiscoveryExtensionIT {

    private final @NotNull Network network = Network.newNetwork();

    private final static String AZURITE_IMAGE_NAME = "mcr.microsoft.com/azure-storage/azurite:3.10.0";
    private static final DockerImageName TOXIPROXY_IMAGE = DockerImageName.parse("shopify/toxiproxy:2.1.0");
    private static final String TOXIPROXY_NETWORK_ALIAS = "toxiproxy";
    private final static int AZURITE_EXPOSED_PORT = 10000;
    private GenericContainer<?> azureriteContainer;
    private int azureritePort;
    private String azureriteDockerConnectionString;
    private String azureriteUserConnectionString;

    @BeforeEach
    void setUp() {
        azureriteContainer =
                new GenericContainer<>(AZURITE_IMAGE_NAME)
                        .withExposedPorts(AZURITE_EXPOSED_PORT)
                        .withNetwork(network);
        azureriteContainer.start();
        azureritePort = azureriteContainer.getMappedPort(10000);
        azureriteDockerConnectionString =
                "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://172.17.0.1:" +
                        azureritePort + "/devstoreaccount1";
        azureriteUserConnectionString =
                "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:" +
                        azureritePort + "/devstoreaccount1";
    }

    @Test
    public void threeNodesFormCluster() throws IOException, TimeoutException {
        WaitingConsumer consumer1 = new WaitingConsumer();
        WaitingConsumer consumer2 = new WaitingConsumer();
        WaitingConsumer consumer3 = new WaitingConsumer();

        final HiveMQTestContainerExtension node1 = createHiveMQNode().withLogConsumer(consumer1);
        final HiveMQTestContainerExtension node2 = createHiveMQNode().withLogConsumer(consumer2);
        final HiveMQTestContainerExtension node3 = createHiveMQNode().withLogConsumer(consumer3);
        node1.start();
        node2.start();
        node3.start();

        consumer1.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 30, SECONDS);
        consumer2.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 30, SECONDS);
        consumer3.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 30, SECONDS);
    }

    @Test
    public void twoNodesInCluster_oneNodeStarted_threeNodesInCluster() throws IOException, TimeoutException {
        WaitingConsumer consumer1 = new WaitingConsumer();
        WaitingConsumer consumer2 = new WaitingConsumer();
        WaitingConsumer consumer3 = new WaitingConsumer();

        final HiveMQTestContainerExtension node1 = createHiveMQNode().withLogConsumer(consumer1);
        final HiveMQTestContainerExtension node2 = createHiveMQNode().withLogConsumer(consumer2);
        final HiveMQTestContainerExtension node3 = createHiveMQNode().withLogConsumer(consumer3);
        node1.start();
        node2.start();

        consumer1.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 30, SECONDS);
        consumer2.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 30, SECONDS);

        node3.start();

        consumer3.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 300, SECONDS);
    }

    @Test
    public void twoNodesInCluster_OneNodeCannotReachAzure_nodeFileDeleted()
            throws IOException, TimeoutException {

        final ToxiproxyContainer toxiproxy = new ToxiproxyContainer(TOXIPROXY_IMAGE).withNetwork(network)
                .withNetworkAliases(TOXIPROXY_NETWORK_ALIAS);
        toxiproxy.start();

        final ToxiproxyContainer.ContainerProxy proxy = toxiproxy.getProxy(azureriteContainer, 10000);
        final String toxiProxyConnectionString =
                "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://172.17.0.1:" +
                        proxy.getProxyPort() + "/devstoreaccount1";

        final WaitingConsumer toxicConsumer = new WaitingConsumer();
        final WaitingConsumer normalConsumer = new WaitingConsumer();
        final HiveMQTestContainerExtension toxicNode =
                createHiveMQNode(toxiProxyConnectionString).withLogConsumer(toxicConsumer);
        final HiveMQTestContainerExtension normalNode = createHiveMQNode().withLogConsumer(normalConsumer);

        toxicNode.start();
        normalNode.start();

        toxicConsumer.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 30, SECONDS);
        normalConsumer.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 30, SECONDS);

        proxy.setConnectionCut(true); // toxicNode now cannot update its node file

        final BlobServiceClient blobServiceClient =
                new BlobServiceClientBuilder().connectionString(azureriteUserConnectionString).buildClient();
        final BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient("hivemq-discovery");

        await().pollInterval(1, SECONDS).atMost(60, SECONDS).until( () -> blobContainerClient.listBlobs().stream().collect(Collectors.toList()).size() == 1);


    }

    @Test
    public void threeNodesInCluster_oneNodeStopped_twoNodeInCluster() throws IOException, TimeoutException {
        final WaitingConsumer consumer1 = new WaitingConsumer();
        final WaitingConsumer consumer2 = new WaitingConsumer();
        final WaitingConsumer consumer3 = new WaitingConsumer();

        final HiveMQTestContainerExtension node1 = createHiveMQNode().withLogConsumer(consumer1);
        final HiveMQTestContainerExtension node2 = createHiveMQNode().withLogConsumer(consumer2);
        final HiveMQTestContainerExtension node3 = createHiveMQNode().withLogConsumer(consumer3);
        node1.start();
        node2.start();
        node3.start();

        consumer1.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 30, SECONDS);
        consumer2.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 30, SECONDS);
        consumer3.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 3"), 30, SECONDS);

        node3.stop();

        consumer1.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 30, SECONDS, 2);
        consumer2.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 30, SECONDS, 2);


        final BlobServiceClient blobServiceClient =
                new BlobServiceClientBuilder().connectionString(azureriteUserConnectionString).buildClient();
        final BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient("hivemq-discovery");

        final List<BlobItem> blobs = blobContainerClient.listBlobs().stream().collect(Collectors.toList());

        assertEquals(3, blobs.size()); // Blob did not yet expire
    }

    @Test
    void wrongConnectionString_reloadRightConnectionString_clusterCreated()
            throws IOException, TimeoutException, InterruptedException {
        WaitingConsumer consumer = new WaitingConsumer();

        final String wrongConnectionString =
                "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=XXX8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://172.17.0.1:" +
                        azureritePort + "/devstoreaccount1";
        final HiveMQTestContainerExtension reloadingNode =
                createHiveMQNode(wrongConnectionString).withLogConsumer(consumer);
        final HiveMQTestContainerExtension normalNode = createHiveMQNode();
        reloadingNode.start();
        normalNode.start();


        final String extensionConfigPath = "src/integrationTest/resources/azDiscovery.properties";
        final String replacedConfig = Files.readString(Path.of(extensionConfigPath))
                .replace("connection-string=", "connection-string=" + azureriteDockerConnectionString);
        reloadingNode.copyFileToContainer(
                Transferable.of(replacedConfig.getBytes()),
                "/opt/hivemq/extensions/hivemq-azure-cluster-discovery-extension/azDiscovery.properties");


        reloadingNode.execInContainer("echo '" + replacedConfig +
                "' > /opt/hivemq/extensions/hivemq-azure-cluster-discovery-extension/azDiscovery.properties");

        consumer.waitUntil(frame -> frame.getUtf8String().contains("Cluster size = 2"), 300, SECONDS);
    }

    @Test
    public void containerNotExisting_nodeStarted_ContainerCreated() throws IOException {
        final BlobServiceClient blobServiceClient =
                new BlobServiceClientBuilder().connectionString(azureriteUserConnectionString).buildClient();
        final BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient("hivemq-discovery");

        assertFalse(blobContainerClient.exists());

        final HiveMQTestContainerExtension node = createHiveMQNode();
        node.start();


        assertTrue(blobContainerClient.exists());
    }

    @Test
    public void containerExisting_nodeStarted_ContainerUsed() throws IOException {
        final BlobServiceClient blobServiceClient =
                new BlobServiceClientBuilder().connectionString(azureriteUserConnectionString).buildClient();
        final BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient("hivemq-discovery");
        final BlobClient blob =
                blobContainerClient.getBlobClient("blob");// A blob to make sure that the container was not recreated
        blobContainerClient.create();
        blob.upload(new ByteArrayInputStream("Test".getBytes()), "Test".getBytes().length);

        final WaitingConsumer consumer = new WaitingConsumer();
        final HiveMQTestContainerExtension node = createHiveMQNode().withLogConsumer(consumer);
        node.start();

        final List<BlobItem> blobs = blobContainerClient.listBlobs().stream().collect(Collectors.toList());
        assertEquals(2, blobs.size());
    }


    /* Helpers */

    @NotNull
    private HiveMQTestContainerExtension createHiveMQNode(final @NotNull String connectionString) throws IOException {

        final Path extensionTempPath = Files.createTempDirectory("az-extension-test");
        FileUtils.copyDirectory(new File("build/hivemq-extension-test"), extensionTempPath.toFile());
        final Path extensionDir = extensionTempPath.resolve("hivemq-azure-cluster-discovery-extension");

        final String extensionConfigPath = "src/integrationTest/resources/azDiscovery.properties";
        final String replacedConfig = Files.readString(Path.of(extensionConfigPath))
                .replace("connection-string=", "connection-string=" + connectionString);
        Files.writeString(extensionDir.resolve("azDiscovery.properties"), replacedConfig);

        return new HiveMQTestContainerExtension("hivemq/hivemq4", "latest")
                .withExtension(extensionDir.toFile())
                .withHiveMQConfig(new File("src/integrationTest/resources/config.xml"))
                .withNetwork(network)
                .waitingFor(Wait.forLogMessage(".*Started HiveMQ in.*\\n", 1));
    }

    @NotNull
    private HiveMQTestContainerExtension createHiveMQNode() throws IOException {
        return createHiveMQNode(azureriteDockerConnectionString);
    }
}
