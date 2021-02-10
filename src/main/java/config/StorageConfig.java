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

package config;

import com.hivemq.extension.sdk.api.annotations.NotNull;
import org.aeonbits.owner.Config;

/**
 * @author Till Seeberger
 */
public interface StorageConfig extends Config {

    @Key("connection-string")
    @NotNull String getConnectionString();

    @Key("container-name")
    @NotNull String getContainerName();

    @Key("file-prefix")
    @NotNull
    @DefaultValue("")
    String getFilePrefix();

    @Key("file-expiration")
    @NotNull
    @DefaultValue("360")
    Integer getFileExpirationInSeconds();

    @Key("update-interval")
    @NotNull
    @DefaultValue("180")
    Integer getFileUpdateIntervalInSeconds();

}
