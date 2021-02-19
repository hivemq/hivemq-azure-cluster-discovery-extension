
# HiveMQ Azure Cluster Discovery Extension
![GitHub release (latest by date)](https://img.shields.io/github/v/release/hivemq/hivemq-azure-cluster-discovery-extension?style=for-the-badge)
![GitHub Workflow Status (branch)](https://img.shields.io/github/workflow/status/hivemq/hivemq-azure-cluster-discovery-extension/Gradle%20Check/master?style=for-the-badge)
![GitHub all releases](https://img.shields.io/github/downloads/hivemq/hivemq-azure-cluster-discovery-extension/total?style=for-the-badge)


**Extension Type**: Integration

**Version**: 1.0.0

**License**: Apache License 2.0

## Purpose

![Extension Overview](img/AzureClusterDiscovery.svg)

This HiveMQ extension allows your HiveMQ cluster nodes to discover each other dynamically by regularly exchanging their information via Azure Blobs in an Azure Blob Storage Container.

HiveMQ instances are added at runtime as soon as they become available by placing their information, on how to connect to them, to the configured Azure Storage Container.
The extension will regularly check the configured Azure Storage Container for files from other HiveMQ nodes.
Additionally, every broker updates its own file on a regular basis to prevent the file from expiring.

## Installation

* Download the extension from the [HiveMQ Website](https://www.hivemq.com/downloads) or from the [GitHub Releases Page](https://github.com/hivemq/hivemq-azure-cluster-discovery-extension/releases).
* Copy the content of the zip file to the `extensions` folder of your HiveMQ nodes.
* Modify the `azDiscovery.properties` file for your needs.
* Change the [Discovery Mechanism](https://www.hivemq.com/docs/latest/hivemq/cluster.html#discovery) of HiveMQ to `extension`.

## Configuration

The information each node writes into the bucket consists of an ip-address and a port.
The ip-address and port are taken from the `external-address` and `external-port` which is configured in the cluster `transport` (config.xml).
If they are not set, the `bind-address` and `bind-port` will be used.

The `azDiscovery.properties` can be reloaded during runtime.

## General Configuration
| Config Name        | Default Value            | Description  |
| ------------- |:-------------:|:-----|
| connection&#x2011;string |    | The required connection string of your Azure Storage Account. See the [Azure Documentation](https://docs.microsoft.com/de-de/com.hivemq.extensions.azure/storage/common/storage-configure-connection-string) for more information.
| container&#x2011;name    | hivemq-discovery      |   The name of the Azure Storage Container in which the Blob for the discovery will be created in. If the Container does not exist yet, it will be created by the extension.
| file&#x2011;prefix       | hivemq-node-      |    An optional file-prefix for the Blob to create, which holds the cluster node information for the discovery. Do not omit this value if you reuse the specified container for other files.
| file&#x2011;expiration   | 360      |    Timeout in seconds after which the created Blob will be deleted by other nodes, if it was not updated in time.
| update&#x2011;interval   | 180      |    Interval in seconds in which the Blob will be updated. Must be less than file-expiration.

### Example Configuration

```properties
connection-string=DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://172.17.0.1:10000/devstoreaccount1
container-name=hivemq-discovery
file-prefix=hivemq-node-
file-expiration=120
update-interval=60
```


## First Steps

* Create an Azure Storage Account.
* Get your Connection String for the Storage Account.
* Place the Connection String into the `azDiscovery.properties` file of your HiveMQ nodes.  
* Start your HiveMQ nodes and verify the discovery.

## Need help?

If you encounter any problems, we are happy to help.
The best place to get in contact is our [Support](http://www.hivemq.com/support/).

## Contributing

If you want to contribute to HiveMQ Azure Cluster Discovery Extension, see the [Contributing Guideline](CONTRIBUTING.md).

## License

The HiveMQ Azure Cluster Discovery Extension is licensed under the `APACHE LICENSE, VERSION 2.0`.
A copy of the license can be found [here](LICENSE).
