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

package com.hivemq.extensions.azure.config;

import com.hivemq.extension.sdk.api.parameter.ExtensionInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.PrintWriter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ConfigReaderTest {

    @Mock
    public ExtensionInformation extensionInformation;

    @TempDir
    File temporaryFolder;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        Mockito.when(extensionInformation.getExtensionHomeFolder()).thenReturn(temporaryFolder);
    }

    @Test
    public void test_readConfiguration_no_file() {
        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        assertNull(configurationReader.readConfiguration());
    }

    @Test
    public void test_readConfiguration_successful() throws Exception {
        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:360");
            printWriter.println("update-interval:180");
        }

        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        assertNotNull(configurationReader.readConfiguration());
    }

    @Test
    public void test_readConfiguration_missing_connection_string() throws Exception {

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:360");
            printWriter.println("update-interval:180");
        }

        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        assertNull(configurationReader.readConfiguration());
    }

    @Test
    public void test_readConfiguration_missing_container_name() throws Exception {

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:360");
            printWriter.println("update-interval:180");
        }

        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        assertNull(configurationReader.readConfiguration());
    }

    @Test
    public void test_readConfiguration_both_intervals_zero_successful() throws Exception {

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:0");
            printWriter.println("update-interval:0");
        }

        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        assertNotNull(configurationReader.readConfiguration());
    }

    @Test
    public void test_readConfiguration_both_intervals_same_value() throws Exception {

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:180");
            printWriter.println("update-interval:180");
        }

        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        assertNull(configurationReader.readConfiguration());
    }

    @Test
    public void test_readConfiguration_update_interval_larger() throws Exception {

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:100");
            printWriter.println("update-interval:300");
        }

        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        assertNull(configurationReader.readConfiguration());
    }

    @Test
    public void test_readConfiguration_update_deactivated() throws Exception {

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:360");
            printWriter.println("update-interval:0");
        }

        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        assertNull(configurationReader.readConfiguration());
    }

    @Test
    public void test_readConfiguration_expiration_deactivated() throws Exception {

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:0");
            printWriter.println("update-interval:180");
        }

        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        assertNull(configurationReader.readConfiguration());
    }

    @Test
    public void test_readConfiguration_missing_expiration() throws Exception {

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:");
            printWriter.println("update-interval:180");
        }

        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        assertNull(configurationReader.readConfiguration());
    }

    @Test
    public void test_readConfiguration_missing_update() throws Exception {

        try (final PrintWriter printWriter = new PrintWriter(new File(temporaryFolder, ConfigReader.STORAGE_FILE))) {
            printWriter.println("connection-string:https://my-connection-string");
            printWriter.println("container-name:hivemq-blob-container");
            printWriter.println("file-prefix:hivemq-cluster");
            printWriter.println("file-expiration:360");
            printWriter.println("update-interval:");
        }

        final ConfigReader configurationReader = new ConfigReader(extensionInformation);
        assertNull(configurationReader.readConfiguration());
    }
}