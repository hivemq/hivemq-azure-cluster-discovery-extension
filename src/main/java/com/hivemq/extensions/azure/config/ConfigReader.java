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

import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.annotations.Nullable;
import com.hivemq.extension.sdk.api.parameter.ExtensionInformation;
import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

public class ConfigReader {

    public static final String STORAGE_FILE = "azDiscovery.properties";

    private static final Logger logger = LoggerFactory.getLogger(ConfigReader.class);

    private final @NotNull File extensionHomeFolder;

    public ConfigReader(final @NotNull ExtensionInformation extensionInformation) {
        extensionHomeFolder = extensionInformation.getExtensionHomeFolder();
    }

    public @Nullable StorageConfig readConfiguration() {

        final File propertiesFile = new File(extensionHomeFolder, STORAGE_FILE);

        if (!propertiesFile.exists()) {
            logger.error(
                    "Could not find '{}'. Please verify that the properties file is located under '{}'.",
                    STORAGE_FILE,
                    extensionHomeFolder);
            return null;
        }

        if (!propertiesFile.canRead()) {
            logger.error(
                    "Could not read '{}'. Please verify that the user running HiveMQ has reading permissions for it.",
                    propertiesFile.getAbsolutePath());
            return null;
        }

        try (final InputStream inputStream = new FileInputStream(propertiesFile)) {

            logger.debug("Reading properties file '{}'.", propertiesFile.getAbsolutePath());
            final Properties properties = new Properties();
            properties.load(inputStream);

            final StorageConfig storageConfig = ConfigFactory.create(StorageConfig.class, properties);
            if (!isValid(storageConfig)) {
                logger.error("Configuration of the Azure Storage Cluster Discovery Extension is not valid!");
                return null;
            }
            logger.trace("Read properties file '{}' successfully.", propertiesFile.getAbsolutePath());
            return storageConfig;

        } catch (final FileNotFoundException e) {
            logger.error("Could not find the properties file '{}'", propertiesFile.getAbsolutePath());
        } catch (final IOException e) {
            logger.error("An error occurred while reading the properties file {}", propertiesFile.getAbsolutePath(), e);
        }

        return null;
    }

    private static boolean isValid(final @NotNull StorageConfig storageConfig) {

        final String connectionString = storageConfig.getConnectionString();
        if (isNullOrBlank(connectionString)) {
            logger.error("Azure Storage Cluster Discovery Extension - Connection String is empty!");
            return false;
        }

        final String containerName = storageConfig.getContainerName();
        if (isNullOrBlank(containerName)) {
            logger.error("Azure Storage Cluster Discovery Extension - Container Name is empty!");
            return false;
        }

        final long fileExpirationInSeconds;
        try {
            fileExpirationInSeconds = storageConfig.getFileExpirationInSeconds();
        } catch (final UnsupportedOperationException e) {
            logger.error("Azure Storage Cluster Discovery Extension- File expiration interval is not set!");
            return false;
        }
        if (fileExpirationInSeconds < 0) {
            logger.error("Azure Storage Cluster Discovery Extension - File expiration interval is negative!");
            return false;
        }

        final long fileUpdateIntervalInSeconds;
        try {
            fileUpdateIntervalInSeconds = storageConfig.getFileUpdateIntervalInSeconds();
        } catch (final UnsupportedOperationException e) {
            logger.error("Azure Storage Cluster Discovery Extension - File update interval is not set!");
            return false;
        }
        if (fileUpdateIntervalInSeconds < 0) {
            logger.error("Azure Storage Cluster Discovery Extension - File update interval is negative!");
            return false;
        }

        if (!(fileUpdateIntervalInSeconds == 0 && fileExpirationInSeconds == 0)) {

            if (fileUpdateIntervalInSeconds == fileExpirationInSeconds) {
                logger.error(
                        "Azure Storage Cluster Discovery Extension - File update interval is the same as the expiration interval!");
                return false;
            }

            if (fileUpdateIntervalInSeconds == 0) {
                logger.error(
                        "Azure Storage Cluster Discovery Extension - File update interval is deactivated but expiration is set!");
                return false;
            }

            if (fileExpirationInSeconds == 0) {
                logger.error(
                        "Azure Storage Cluster Discovery Extension - File expiration is deactivated but update interval is set!");
                return false;
            }

            if (!(fileUpdateIntervalInSeconds < fileExpirationInSeconds)) {
                logger.error(
                        "Azure Storage Cluster Discovery Extension - File update interval is larger than expiration interval!");
                return false;
            }
        }


        return true;
    }

    public static boolean isNullOrBlank(final @Nullable String value) {
        return value == null || value.isBlank();
    }
}
