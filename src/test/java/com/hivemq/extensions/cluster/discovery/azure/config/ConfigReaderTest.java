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

import com.hivemq.extension.sdk.api.parameter.ExtensionInformation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigReaderTest {

    private @NotNull ExtensionInformation extensionInformation;

    @BeforeEach
    void setUp(final @TempDir @NotNull File tempDir) {
        extensionInformation = mock(ExtensionInformation.class);
        when(extensionInformation.getExtensionHomeFolder()).thenReturn(tempDir);
    }

    @Test
    void test_readConfiguration_no_file() {
        final var configurationReader = new ConfigReader(extensionInformation);
        assertThat(configurationReader.readConfiguration()).isNull();
    }

    @Test
    void test_readConfiguration_successful() throws Exception {
        Files.writeString(extensionInformation.getExtensionHomeFolder().toPath().resolve(ConfigReader.STORAGE_FILE),
                "connection-string:https://my-connection-string\n" + //
                        "container-name:hivemq-blob-container\n" + //
                        "file-prefix:hivemq-cluster\n" + //
                        "file-expiration:360\n" + //
                        "update-interval:180");

        final var configurationReader = new ConfigReader(extensionInformation);
        assertThat(configurationReader.readConfiguration()).isNotNull();
    }

    @Test
    void test_readConfiguration_missing_connection_string() throws Exception {
        Files.writeString(extensionInformation.getExtensionHomeFolder().toPath().resolve(ConfigReader.STORAGE_FILE),
                "connection-string:\n" +//
                        "container-name:hivemq-blob-container\n" + //
                        "file-prefix:hivemq-cluster\n" + //
                        "file-expiration:360\n" + //
                        "update-interval:180");

        final var configurationReader = new ConfigReader(extensionInformation);
        assertThat(configurationReader.readConfiguration()).isNull();
    }

    @Test
    void test_readConfiguration_missing_container_name() throws Exception {
        Files.writeString(extensionInformation.getExtensionHomeFolder().toPath().resolve(ConfigReader.STORAGE_FILE),
                "connection-string:https://my-connection-string\n" + //
                        "container-name:\n" + //
                        "file-prefix:hivemq-cluster\n" + //
                        "file-expiration:360\n" + //
                        "update-interval:180\n");

        final var configurationReader = new ConfigReader(extensionInformation);
        assertThat(configurationReader.readConfiguration()).isNull();
    }

    @Test
    void test_readConfiguration_both_intervals_zero_successful() throws Exception {
        Files.writeString(extensionInformation.getExtensionHomeFolder().toPath().resolve(ConfigReader.STORAGE_FILE),
                "connection-string:https://my-connection-string\n" + //
                        "container-name:hivemq-blob-container\n" + //
                        "file-prefix:hivemq-cluster\n" + //
                        "file-expiration:0\n" + //
                        "update-interval:0\n");

        final var configurationReader = new ConfigReader(extensionInformation);
        assertThat(configurationReader.readConfiguration()).isNotNull();
    }

    @Test
    void test_readConfiguration_both_intervals_same_value() throws Exception {
        Files.writeString(extensionInformation.getExtensionHomeFolder().toPath().resolve(ConfigReader.STORAGE_FILE),
                "connection-string:https://my-connection-string\n" + //
                        "container-name:hivemq-blob-container\n" + //
                        "file-prefix:hivemq-cluster\n" + //
                        "file-expiration:180\n" + //
                        "update-interval:180\n");

        final var configurationReader = new ConfigReader(extensionInformation);
        assertThat(configurationReader.readConfiguration()).isNull();
    }

    @Test
    void test_readConfiguration_update_interval_larger() throws Exception {
        Files.writeString(extensionInformation.getExtensionHomeFolder().toPath().resolve(ConfigReader.STORAGE_FILE),
                "connection-string:https://my-connection-string\n" + //
                        "container-name:hivemq-blob-container\n" + //
                        "file-prefix:hivemq-cluster\n" + //
                        "file-expiration:100\n" + //
                        "update-interval:300\n");

        final var configurationReader = new ConfigReader(extensionInformation);
        assertThat(configurationReader.readConfiguration()).isNull();
    }

    @Test
    void test_readConfiguration_update_deactivated() throws Exception {
        Files.writeString(extensionInformation.getExtensionHomeFolder().toPath().resolve(ConfigReader.STORAGE_FILE),
                "connection-string:https://my-connection-string\n" + //
                        "container-name:hivemq-blob-container\n" + //
                        "file-prefix:hivemq-cluster\n" + //
                        "file-expiration:360\n" + //
                        "update-interval:0\n");

        final var configurationReader = new ConfigReader(extensionInformation);
        assertThat(configurationReader.readConfiguration()).isNull();
    }

    @Test
    void test_readConfiguration_expiration_deactivated() throws Exception {
        Files.writeString(extensionInformation.getExtensionHomeFolder().toPath().resolve(ConfigReader.STORAGE_FILE),
                "connection-string:https://my-connection-string\n" + //
                        "container-name:hivemq-blob-container\n" + //
                        "file-prefix:hivemq-cluster\n" + //
                        "file-expiration:0\n" + //
                        "update-interval:180\n");

        final var configurationReader = new ConfigReader(extensionInformation);
        assertThat(configurationReader.readConfiguration()).isNull();
    }

    @Test
    void test_readConfiguration_missing_expiration() throws Exception {
        Files.writeString(extensionInformation.getExtensionHomeFolder().toPath().resolve(ConfigReader.STORAGE_FILE),
                "connection-string:https://my-connection-string\n" + //
                        "container-name:hivemq-blob-container\n" + //
                        "file-prefix:hivemq-cluster\n" + //
                        "file-expiration:\n" + //
                        "update-interval:180\n");

        final var configurationReader = new ConfigReader(extensionInformation);
        assertThat(configurationReader.readConfiguration()).isNull();
    }

    @Test
    void test_readConfiguration_missing_update() throws Exception {
        Files.writeString(extensionInformation.getExtensionHomeFolder().toPath().resolve(ConfigReader.STORAGE_FILE),
                "connection-string:https://my-connection-string\n" + //
                        "container-name:hivemq-blob-container\n" + //
                        "file-prefix:hivemq-cluster\n" + //
                        "file-expiration:360\n" + //
                        "update-interval:\n");

        final var configurationReader = new ConfigReader(extensionInformation);
        assertThat(configurationReader.readConfiguration()).isNull();
    }
}
