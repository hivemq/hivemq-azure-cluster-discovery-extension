HIVEMQ_VERSION=$1
STORAGE_ACCESS_KEY=$2
STORAGE_CONTAINER_NAME=$3

EXTENSION_PROPERTIES_PATH="/opt/hivemq/extensions/hivemq-azure-cluster-discovery-extension/azDiscovery.properties"
HIVEMQ_DOWNLOAD_LINK="https://www.hivemq.com/releases/hivemq-${HIVEMQ_VERSION}.zip"

# TODO Change this to the github release download link
EXTENSION_DOWNLOAD_LINK="https://hivemqstorage.blob.core.windows.net/azure-extension/hivemq-azure-cluster-discovery-extension-1.0.0.zip?sp=r&st=2021-02-16T15:15:03Z&se=2021-02-16T23:15:03Z&spr=https&sv=2019-12-12&sr=b&sig=RbVc6Re9dOYVmMUvRoiNHUVCkor18Dfq6Pz3El3c8Vc%3D"

sudo apt-get update -y
sudo apt-get install -y openjdk-11-jdk
sudo apt-get -y install unzip

# Install HiveMQ 
cd /opt 
sudo wget --content-disposition $HIVEMQ_DOWNLOAD_LINK
sudo unzip "hivemq-${HIVEMQ_VERSION}.zip"
sudo ln -s "/opt/hivemq-${HIVEMQ_VERSION}" /opt/hivemq
sudo useradd -d /opt/hivemq hivemq
sudo chown -R hivemq:hivemq "/opt/hivemq-${HIVEMQ_VERSION}"
sudo chown -R hivemq:hivemq /opt/hivemq
cd /opt/hivemq
sudo chmod +x ./bin/run.sh
sudo cp /opt/hivemq/bin/init-script/hivemq.service /etc/systemd/system/hivemq.service

echo "<?xml version=\"1.0\"?>
<hivemq>

    <listeners>
        <tcp-listener>
            <port>1883</port>
            <bind-address>0.0.0.0</bind-address>
        </tcp-listener>
    </listeners>

    <cluster>
        <enabled>true</enabled>

        <transport>
            <tcp>
                <bind-address>0.0.0.0</bind-address>
                <bind-port>7800</bind-port>
            </tcp>
        </transport>

        <discovery>
            <extension/>
        </discovery>

    </cluster>

     <control-center>
        <enabled>true</enabled>
        <listeners>
            <http>
                <port>8080</port>
                <bind-address>0.0.0.0</bind-address>
            </http>
        </listeners>
    </control-center>

</hivemq>" | sudo tee /opt/hivemq/conf/config.xml

# Install extension

cd /opt/hivemq/extensions
sudo wget --content-disposition $EXTENSION_DOWNLOAD_LINK -O azure-extension.zip
sudo unzip azure-extension.zip 
echo "connection-string=${STORAGE_ACCESS_KEY}
container-name=hivemq-discovery
# An optional file-prefix for the Blob to create, which holds the cluster node information for the discovery. (default: hivemq-node)
# Do not omit this value if you reuse the specified container for other files.
file-prefix=hivemq-node-
# Timeout in seconds after which the created Blob will be deleted by other nodes, if it was not updated in time. (default: 360)
file-expiration=360
# Interval in seconds in which the Blob will be updated. Must be less than file-expiration. (default: 180)
update-interval=180" | sudo tee $EXTENSION_PROPERTIES_PATH


sudo systemctl enable hivemq
sudo systemctl start hivemq
