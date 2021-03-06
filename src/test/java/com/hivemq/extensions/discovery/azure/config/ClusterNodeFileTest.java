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

    private final ClusterNodeAddress clusterNodeAddress = new ClusterNodeAddress("127.0.0.1", 7800);
    private final String nodeId = "ABCD12";

    @Test
    public void test_cluster_node_file_successful_create() {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        assertNotNull(clusterNodeFile);
    }

    @Test
    public void test_cluster_node_file_successful_get_node_address() {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        assertNotNull(clusterNodeFile.getClusterNodeAddress());
    }

    @Test
    public void test_cluster_node_file_successful_get_cluster_id() {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        assertNotNull(clusterNodeFile.getClusterId());
    }

    @Test
    public void test_cluster_node_file_equals() {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        final String clusterNodeFileString = clusterNodeFile.toString();
        final ClusterNodeFile newClusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertTrue(clusterNodeFile.toString().contentEquals(newClusterNodeFile.toString()));
    }

    @Test
    public void test_cluster_node_file_not_equal() {
        final ClusterNodeFile clusterNodeFile1 = new ClusterNodeFile(nodeId + 1, clusterNodeAddress);
        final ClusterNodeFile clusterNodeFile2 = new ClusterNodeFile(nodeId + 2, clusterNodeAddress);
        assertFalse(clusterNodeFile1.toString().contentEquals(clusterNodeFile2.toString()));
    }

    @Test
    public void test_cluster_node_file_nodeId_null() {
        assertThrows(NullPointerException.class, () -> new ClusterNodeFile(null, clusterNodeAddress));
    }

    @Test
    public void test_cluster_node_file_nodeId_blank() {
        assertThrows(IllegalArgumentException.class, () -> new ClusterNodeFile(" ", clusterNodeAddress));
    }

    @Test
    public void test_cluster_node_file_cluster_node_address_null() {
        assertThrows(NullPointerException.class, () -> new ClusterNodeFile(nodeId, null));
    }

    @Test
    public void test_cluster_node_file_expiration_deactivated() {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        assertFalse(clusterNodeFile.isExpired(0));
    }

    @Test
    public void test_cluster_node_file_expired() throws Exception {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        TimeUnit.SECONDS.sleep(2);
        assertTrue(clusterNodeFile.isExpired(1));
    }

    @Test
    public void test_cluster_node_file_not_expired() {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        assertFalse(clusterNodeFile.isExpired(1));
    }

    @Test
    public void test_cluster_node_file_not_expired_sleep() throws Exception {
        final ClusterNodeFile clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        TimeUnit.SECONDS.sleep(1);
        assertFalse(clusterNodeFile.isExpired(2));
    }

    @Test
    public void test_parseClusterNodeFile_success() {
        final ClusterNodeFile clusterNodeFile1 = new ClusterNodeFile(nodeId, clusterNodeAddress);
        final String clusterNodeFile1String = clusterNodeFile1.toString();
        final ClusterNodeFile clusterNodeFile2 = ClusterNodeFile.parseClusterNodeFile(clusterNodeFile1String);
        assertTrue(clusterNodeFile1.toString().contentEquals(clusterNodeFile2.toString()));
    }

    @Test
    public void test_parseClusterNodeFile_false_version() {
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
    public void test_parseClusterNodeFile_false_charset() {
        final String clusterNodeFileString = new String("abcd".getBytes(), StandardCharsets.UTF_16);
        final ClusterNodeFile clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertNull(clusterNodeFile);
    }

    @Test
    public void test_parseClusterNodeFile_version_empty() {
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
    public void test_parseClusterNodeFile_node_id_empty() {
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
    public void test_parseClusterNodeFile_host_empty() {
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
    public void test_parseClusterNodeFile_port_not_number() {
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
    public void test_parseClusterNodeFile_creation_time_not_number() {
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
    public void test_parseClusterNodeFile_too_short() {
        final String clusterNodeFileString = createClusterNodeFileStringTooShort(
                ClusterNodeFile.CONTENT_VERSION,
                Long.toString(System.currentTimeMillis()),
                nodeId,
                clusterNodeAddress.getHost(),
                Integer.toString(clusterNodeAddress.getPort()));

        final ClusterNodeFile clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertNull(clusterNodeFile);
    }

    @Test
    public void test_parseClusterNodeFile_too_long() {
        final String clusterNodeFileString = createClusterNodeFileStringTooLong(
                ClusterNodeFile.CONTENT_VERSION,
                Long.toString(System.currentTimeMillis()),
                nodeId,
                clusterNodeAddress.getHost(),
                Integer.toString(clusterNodeAddress.getPort()));

        final ClusterNodeFile clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertNull(clusterNodeFile);
    }

    @Test
    public void test_parseClusterNodeFile_null() {
        assertThrows(NullPointerException.class, () -> ClusterNodeFile.parseClusterNodeFile(null));
    }

    @Test
    public void test_parseClusterNodeFile_blank() {
        assertThrows(IllegalArgumentException.class, () -> ClusterNodeFile.parseClusterNodeFile("  "));
    }

    public static @NotNull String createClusterNodeFileString(
            final String version,
            final String timeInMillis,
            final String nodeId,
            final String host,
            final String port) {
        final String content =
                version + ClusterNodeFile.CONTENT_SEPARATOR + timeInMillis + ClusterNodeFile.CONTENT_SEPARATOR +
                        nodeId + ClusterNodeFile.CONTENT_SEPARATOR + host + ClusterNodeFile.CONTENT_SEPARATOR + port +
                        ClusterNodeFile.CONTENT_SEPARATOR;

        return new String(Base64.getEncoder().encode(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    private static @NotNull String createClusterNodeFileStringTooLong(
            final String version,
            final String timeInMillis,
            final String nodeId,
            final String host,
            final String port) {
        final String content =
                version + ClusterNodeFile.CONTENT_SEPARATOR + timeInMillis + ClusterNodeFile.CONTENT_SEPARATOR +
                        nodeId + ClusterNodeFile.CONTENT_SEPARATOR + host + ClusterNodeFile.CONTENT_SEPARATOR + port +
                        ClusterNodeFile.CONTENT_SEPARATOR + port + ClusterNodeFile.CONTENT_SEPARATOR;

        return new String(Base64.getEncoder().encode(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    private static @NotNull String createClusterNodeFileStringTooShort(
            final String version,
            final String timeInMillis,
            final String nodeId,
            final String host,
            final String port) {
        final String content =
                version + ClusterNodeFile.CONTENT_SEPARATOR + timeInMillis + ClusterNodeFile.CONTENT_SEPARATOR;

        return new String(Base64.getEncoder().encode(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }
}