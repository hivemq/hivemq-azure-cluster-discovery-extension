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

package com.hivemq.extensions.discovery.azure.config;

import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterNodeAddress;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ClusterNodeFileTest {

    private final @NotNull String nodeId = "ABCD12";
    private final @NotNull ClusterNodeAddress clusterNodeAddress = new ClusterNodeAddress("127.0.0.1", 7800);

    @Test
    void getClusterId() {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        assertSame(nodeId, clusterNodeFile.getClusterId());
    }

    @Test
    void getClusterNodeAddress() {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        assertSame(clusterNodeAddress, clusterNodeFile.getClusterNodeAddress());
    }

    @Test
    void whenNodeIdIsNull_thenThrowNPE() {
        assertThrows(NullPointerException.class, () -> new ClusterNodeFile(null, clusterNodeAddress));
    }

    @Test
    void whenNodeIdIsBlank_thenThrowIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new ClusterNodeFile(" ", clusterNodeAddress));
    }

    @Test
    void whenClusterNodeAddressIsNull_thenThrowNPE() {
        assertThrows(NullPointerException.class, () -> new ClusterNodeFile(nodeId, null));
    }

    @Test
    void expiration_deactivated() {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        assertFalse(clusterNodeFile.isExpired(0));
    }

    @Test
    void expired() throws Exception {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        TimeUnit.SECONDS.sleep(2);
        assertTrue(clusterNodeFile.isExpired(1));
    }

    @Test
    void not_expired() {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        assertFalse(clusterNodeFile.isExpired(1));
    }

    @Test
    void not_expired_sleep() throws Exception {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        TimeUnit.SECONDS.sleep(1);
        assertFalse(clusterNodeFile.isExpired(2));
    }

    @Test
    void parseClusterNodeFile_success() {
        final ClusterNodeFile clusterNodeFile1 = new ClusterNodeFile(nodeId, clusterNodeAddress);
        final String clusterNodeFile1String = clusterNodeFile1.toString();
        final ClusterNodeFile clusterNodeFile2 = ClusterNodeFile.parseClusterNodeFile(clusterNodeFile1String);
        assertNotNull(clusterNodeFile2);
        assertEquals(clusterNodeFile1String, clusterNodeFile2.toString());
    }

    @Test
    void parseClusterNodeFile_wrongCharset() {
        final String clusterNodeFileString = new String("abcd".getBytes(), StandardCharsets.UTF_16);
        final ClusterNodeFile clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertNull(clusterNodeFile);
    }

    @Test
    void parseClusterNodeFile_wrongVersion() {
        final String clusterNodeFileString = createClusterNodeFileString(
                "3",
                Long.toString(System.currentTimeMillis()),
                nodeId,
                clusterNodeAddress.getHost(),
                Integer.toString(clusterNodeAddress.getPort()));

        final ClusterNodeFile clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertNull(clusterNodeFile);
    }

    @Test
    void parseClusterNodeFile_emptyVersion() {
        final String clusterNodeFileString = createClusterNodeFileString(
                "",
                Long.toString(System.currentTimeMillis()),
                nodeId,
                clusterNodeAddress.getHost(),
                Integer.toString(clusterNodeAddress.getPort()));

        final ClusterNodeFile clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertNull(clusterNodeFile);
    }

    @Test
    void parseClusterNodeFile_emptyNodeId() {
        final String clusterNodeFileString = createClusterNodeFileString(
                ClusterNodeFile.CONTENT_VERSION,
                Long.toString(System.currentTimeMillis()),
                "",
                clusterNodeAddress.getHost(),
                Integer.toString(clusterNodeAddress.getPort()));

        final ClusterNodeFile clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertNull(clusterNodeFile);
    }

    @Test
    void parseClusterNodeFile_emptyHost() {
        final String clusterNodeFileString = createClusterNodeFileString(
                ClusterNodeFile.CONTENT_VERSION,
                Long.toString(System.currentTimeMillis()),
                nodeId,
                "",
                Integer.toString(clusterNodeAddress.getPort()));

        final ClusterNodeFile clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertNull(clusterNodeFile);
    }

    @Test
    void parseClusterNodeFile_portIsNotANumber() {
        final String clusterNodeFileString = createClusterNodeFileString(
                ClusterNodeFile.CONTENT_VERSION,
                Long.toString(System.currentTimeMillis()),
                nodeId,
                clusterNodeAddress.getHost(),
                "abcd");

        final ClusterNodeFile clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertNull(clusterNodeFile);
    }

    @Test
    void parseClusterNodeFile_creationTimeIsNotANumber() {
        final String clusterNodeFileString = createClusterNodeFileString(
                ClusterNodeFile.CONTENT_VERSION,
                "abcd",
                nodeId,
                clusterNodeAddress.getHost(),
                Integer.toString(clusterNodeAddress.getPort()));

        final ClusterNodeFile clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertNull(clusterNodeFile);
    }

    @Test
    void parseClusterNodeFile_tooShort() {
        final String clusterNodeFileString = encodeClusterNodeFileString(
                ClusterNodeFile.CONTENT_VERSION + ClusterNodeFile.CONTENT_SEPARATOR + System.currentTimeMillis() +
                        ClusterNodeFile.CONTENT_SEPARATOR);

        final ClusterNodeFile clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertNull(clusterNodeFile);
    }

    @Test
    void parseClusterNodeFile_tooLong() {
        final String clusterNodeFileString = encodeClusterNodeFileString(
                ClusterNodeFile.CONTENT_VERSION + ClusterNodeFile.CONTENT_SEPARATOR + System.currentTimeMillis() +
                        ClusterNodeFile.CONTENT_SEPARATOR + nodeId + ClusterNodeFile.CONTENT_SEPARATOR +
                        clusterNodeAddress.getHost() + ClusterNodeFile.CONTENT_SEPARATOR +
                        clusterNodeAddress.getPort() + ClusterNodeFile.CONTENT_SEPARATOR +
                        clusterNodeAddress.getPort());

        final ClusterNodeFile clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertNull(clusterNodeFile);
    }

    @Test
    void parseClusterNodeFile_null() {
        assertThrows(NullPointerException.class, () -> ClusterNodeFile.parseClusterNodeFile(null));
    }

    @Test
    void parseClusterNodeFile_blank() {
        assertThrows(IllegalArgumentException.class, () -> ClusterNodeFile.parseClusterNodeFile("  "));
    }

    public static @NotNull String createClusterNodeFileString(
            final @NotNull String version,
            final @NotNull String timeInMillis,
            final @NotNull String nodeId,
            final @NotNull String host,
            final @NotNull String port) {

        final String content =
                version + ClusterNodeFile.CONTENT_SEPARATOR + timeInMillis + ClusterNodeFile.CONTENT_SEPARATOR +
                        nodeId + ClusterNodeFile.CONTENT_SEPARATOR + host + ClusterNodeFile.CONTENT_SEPARATOR + port;
        return encodeClusterNodeFileString(content);
    }

    private static @NotNull String encodeClusterNodeFileString(final @NotNull String content) {
        return new String(Base64.getEncoder().encode(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }
}