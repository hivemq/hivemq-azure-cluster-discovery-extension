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
import org.aeonbits.owner.ConfigFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class ConfigReader {

    public static final @NotNull String CONFIG_PATH = "conf/config.properties";
    public static final @NotNull String LEGACY_CONFIG_PATH = "azDiscovery.properties";

    private static final @NotNull Logger logger = LoggerFactory.getLogger(ConfigReader.class);

    private final @NotNull ConfigResolver configResolver;

    public ConfigReader(final @NotNull ExtensionInformation extensionInformation) {
        configResolver = new ConfigResolver(extensionInformation.getExtensionHomeFolder().toPath(),
                "Azure Cluster Discovery Extension",
                CONFIG_PATH,
                LEGACY_CONFIG_PATH);
    }

    private static boolean isValid(final @NotNull AzureDiscoveryConfig azureDiscoveryConfig) {
        final var connectionString = azureDiscoveryConfig.getConnectionString();
        if (isNullOrBlank(connectionString)) {
            logger.warn("The Connection String in the configuration file was empty.");
            return false;
        }
        final var containerName = azureDiscoveryConfig.getContainerName();
        if (isNullOrBlank(containerName)) {
            logger.warn("The Container Name in the configuration file was empty.");
            return false;
        }
        final long fileExpirationInSeconds;
        try {
            fileExpirationInSeconds = azureDiscoveryConfig.getFileExpirationInSeconds();
        } catch (final UnsupportedOperationException e) {
            logger.warn("The File Expiration Interval in the configuration file was not valid. {}.", e.getMessage());
            return false;
        }
        if (fileExpirationInSeconds < 0) {
            logger.warn("The File Expiration Interval in the configuration file was negative.");
            return false;
        }
        final long fileUpdateIntervalInSeconds;
        try {
            fileUpdateIntervalInSeconds = azureDiscoveryConfig.getFileUpdateIntervalInSeconds();
        } catch (final UnsupportedOperationException e) {
            logger.warn("The File Update Interval in the configuration file was not valid. {}.", e.getMessage());
            return false;
        }
        if (fileUpdateIntervalInSeconds < 0) {
            logger.warn("The File Update Interval in the configuration file was negative.");
            return false;
        }
        if (!(fileUpdateIntervalInSeconds == 0 && fileExpirationInSeconds == 0)) {
            if (fileUpdateIntervalInSeconds == fileExpirationInSeconds) {
                logger.warn("The File Update Interval is the same as the File Expiration Interval.");
                return false;
            }
            if (fileUpdateIntervalInSeconds == 0) {
                logger.warn("The File Update Interval is deactivated but the File Expiration Interval is set.");
                return false;
            }
            if (fileExpirationInSeconds == 0) {
                logger.warn("The File Expiration Interval is deactivated but the File Update Interval is set.");
                return false;
            }
            if (!(fileUpdateIntervalInSeconds < fileExpirationInSeconds)) {
                logger.warn("The File Update Interval is larger than the File Expiration Interval.");
                return false;
            }
        }
        return true;
    }

    public static boolean isNullOrBlank(final @Nullable String value) {
        return value == null || value.isBlank();
    }

    public @Nullable AzureDiscoveryConfig readConfiguration() {
        final var propertiesFile = configResolver.get().toFile();
        if (!propertiesFile.exists()) {
            logger.warn("Could not find '{}'. Please verify that the properties file is located under '{}'.",
                    propertiesFile.getName(),
                    propertiesFile.getParentFile());
            return null;
        }
        if (!propertiesFile.canRead()) {
            logger.warn(
                    "Could not read '{}'. Please verify that the user running HiveMQ has reading permissions for it.",
                    propertiesFile.getAbsolutePath());
            return null;
        }
        try (final var inputStream = new FileInputStream(propertiesFile)) {
            logger.debug("Reading properties file '{}'.", propertiesFile.getAbsolutePath());
            final var properties = new Properties();
            properties.load(inputStream);
            final var azureDiscoveryConfig = ConfigFactory.create(AzureDiscoveryConfig.class, properties);
            if (!isValid(azureDiscoveryConfig)) {
                logger.warn("The Configuration of the Azure Storage Cluster Discovery Extension is not valid.");
                return null;
            }
            logger.trace("Read properties file '{}' successfully.", propertiesFile.getAbsolutePath());
            return azureDiscoveryConfig;

        } catch (final FileNotFoundException e) {
            logger.warn("Could not find the properties file '{}'", propertiesFile.getAbsolutePath());
        } catch (final IOException e) {
            logger.warn("An error occurred while reading the properties file {}. {}",
                    propertiesFile.getAbsolutePath(),
                    e.getMessage());
        }
        return null;
    }
}
