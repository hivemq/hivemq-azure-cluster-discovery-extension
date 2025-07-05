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

package com.hivemq.extensions.cluster.discovery.azure.config;

import com.hivemq.extension.sdk.api.services.cluster.parameter.ClusterNodeAddress;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClusterNodeFileTest {

    private final @NotNull String nodeId = "ABCD12";
    private final @NotNull ClusterNodeAddress clusterNodeAddress = new ClusterNodeAddress("127.0.0.1", 7800);

    @Test
    void getClusterId() {
        final var clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        assertThat(clusterNodeFile.getClusterId()).isSameAs(nodeId);
    }

    @Test
    void getClusterNodeAddress() {
        final var clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        assertThat(clusterNodeFile.getClusterNodeAddress()).isSameAs(clusterNodeAddress);
    }

    @Test
    void whenNodeIdIsNull_thenThrowNPE() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> new ClusterNodeFile(null,
                clusterNodeAddress)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void whenNodeIdIsBlank_thenThrowIllegalArgument() {
        assertThatThrownBy(() -> new ClusterNodeFile(" ",
                clusterNodeAddress)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whenClusterNodeAddressIsNull_thenThrowNPE() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> new ClusterNodeFile(nodeId, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void expiration_deactivated() {
        final var clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        assertThat(clusterNodeFile.isExpired(0)).isFalse();
    }

    @Test
    void expired() throws Exception {
        final var clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        TimeUnit.SECONDS.sleep(2);
        assertThat(clusterNodeFile.isExpired(1)).isTrue();
    }

    @Test
    void not_expired() {
        final var clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        assertThat(clusterNodeFile.isExpired(1)).isFalse();
    }

    @Test
    void not_expired_sleep() throws Exception {
        final var clusterNodeFile = new ClusterNodeFile(nodeId, clusterNodeAddress);
        TimeUnit.SECONDS.sleep(1);
        assertThat(clusterNodeFile.isExpired(2)).isFalse();
    }

    @Test
    void parseClusterNodeFile_success() {
        final var clusterNodeFile1 = new ClusterNodeFile(nodeId, clusterNodeAddress);
        final var clusterNodeFile1String = clusterNodeFile1.toString();
        final var clusterNodeFile2 = ClusterNodeFile.parseClusterNodeFile(clusterNodeFile1String);
        assertThat(clusterNodeFile2).isNotNull();
        assertThat(clusterNodeFile2.toString()).isEqualTo(clusterNodeFile1String);
    }

    @Test
    void parseClusterNodeFile_wrongCharset() {
        final var clusterNodeFileString = new String("abcd".getBytes(), StandardCharsets.UTF_16);
        final var clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertThat(clusterNodeFile).isNull();
    }

    @Test
    void parseClusterNodeFile_wrongVersion() {
        final var clusterNodeFileString = createClusterNodeFileString("3",
                Long.toString(System.currentTimeMillis()),
                nodeId,
                clusterNodeAddress.getHost(),
                Integer.toString(clusterNodeAddress.getPort()));

        final var clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertThat(clusterNodeFile).isNull();

    }

    @Test
    void parseClusterNodeFile_emptyVersion() {
        final String clusterNodeFileString = createClusterNodeFileString("",
                Long.toString(System.currentTimeMillis()),
                nodeId,
                clusterNodeAddress.getHost(),
                Integer.toString(clusterNodeAddress.getPort()));

        final ClusterNodeFile clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertThat(clusterNodeFile).isNull();
    }

    @Test
    void parseClusterNodeFile_emptyNodeId() {
        final var clusterNodeFileString = createClusterNodeFileString(ClusterNodeFile.CONTENT_VERSION,
                Long.toString(System.currentTimeMillis()),
                "",
                clusterNodeAddress.getHost(),
                Integer.toString(clusterNodeAddress.getPort()));

        final var clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertThat(clusterNodeFile).isNull();
    }

    @Test
    void parseClusterNodeFile_emptyHost() {
        final var clusterNodeFileString = createClusterNodeFileString(ClusterNodeFile.CONTENT_VERSION,
                Long.toString(System.currentTimeMillis()),
                nodeId,
                "",
                Integer.toString(clusterNodeAddress.getPort()));

        final var clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertThat(clusterNodeFile).isNull();
    }

    @Test
    void parseClusterNodeFile_portIsNotANumber() {
        final var clusterNodeFileString = createClusterNodeFileString(ClusterNodeFile.CONTENT_VERSION,
                Long.toString(System.currentTimeMillis()),
                nodeId,
                clusterNodeAddress.getHost(),
                "abcd");

        final var clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertThat(clusterNodeFile).isNull();
    }

    @Test
    void parseClusterNodeFile_creationTimeIsNotANumber() {
        final var clusterNodeFileString = createClusterNodeFileString(ClusterNodeFile.CONTENT_VERSION,
                "abcd",
                nodeId,
                clusterNodeAddress.getHost(),
                Integer.toString(clusterNodeAddress.getPort()));

        final var clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertThat(clusterNodeFile).isNull();
    }

    @Test
    void parseClusterNodeFile_tooShort() {
        final var clusterNodeFileString = encodeClusterNodeFileString(ClusterNodeFile.CONTENT_VERSION +
                ClusterNodeFile.CONTENT_SEPARATOR +
                System.currentTimeMillis() +
                ClusterNodeFile.CONTENT_SEPARATOR);

        final var clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertThat(clusterNodeFile).isNull();
    }

    @Test
    void parseClusterNodeFile_tooLong() {
        final var clusterNodeFileString = encodeClusterNodeFileString(ClusterNodeFile.CONTENT_VERSION +
                ClusterNodeFile.CONTENT_SEPARATOR +
                System.currentTimeMillis() +
                ClusterNodeFile.CONTENT_SEPARATOR +
                nodeId +
                ClusterNodeFile.CONTENT_SEPARATOR +
                clusterNodeAddress.getHost() +
                ClusterNodeFile.CONTENT_SEPARATOR +
                clusterNodeAddress.getPort() +
                ClusterNodeFile.CONTENT_SEPARATOR +
                clusterNodeAddress.getPort());

        final var clusterNodeFile = ClusterNodeFile.parseClusterNodeFile(clusterNodeFileString);
        assertThat(clusterNodeFile).isNull();
    }

    @Test
    void parseClusterNodeFile_null() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> ClusterNodeFile.parseClusterNodeFile(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void parseClusterNodeFile_blank() {
        assertThatThrownBy(() -> ClusterNodeFile.parseClusterNodeFile("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    public static @NotNull String createClusterNodeFileString(
            final @NotNull String version,
            final @NotNull String timeInMillis,
            final @NotNull String nodeId,
            final @NotNull String host,
            final @NotNull String port) {
        final var content = version +
                ClusterNodeFile.CONTENT_SEPARATOR +
                timeInMillis +
                ClusterNodeFile.CONTENT_SEPARATOR +
                nodeId +
                ClusterNodeFile.CONTENT_SEPARATOR +
                host +
                ClusterNodeFile.CONTENT_SEPARATOR +
                port;
        return encodeClusterNodeFileString(content);
    }

    private static @NotNull String encodeClusterNodeFileString(final @NotNull String content) {
        return new String(Base64.getEncoder().encode(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }
}
