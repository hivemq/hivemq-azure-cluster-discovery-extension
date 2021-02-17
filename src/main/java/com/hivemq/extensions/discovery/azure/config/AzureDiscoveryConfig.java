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
import org.aeonbits.owner.Config;

/**
 * @author Till Seeberger
 */
public interface AzureDiscoveryConfig extends Config {

    @Key("connection-string")
    @NotNull String getConnectionString();

    @Key("container-name")
    @DefaultValue("hivemq-discovery")
    @NotNull String getContainerName();

    @Key("file-prefix")
    @DefaultValue("hivemq-node")
    @NotNull String getFilePrefix();

    @Key("file-expiration")
    @DefaultValue("360")
    @NotNull Integer getFileExpirationInSeconds();

    @Key("update-interval")
    @DefaultValue("180")
    @NotNull Integer getFileUpdateIntervalInSeconds();

}
