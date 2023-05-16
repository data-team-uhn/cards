# Using _Percona Server for MongoDB_ for encrypted-at-rest data storage

1. Download a _Percona Server for MongoDB_ image.

```bash
docker pull percona/percona-server-mongodb:4.4
```

2.1. For testing purposes, we can setup Percona to _not_ use encryption-at-rest.

```bash
mkdir PERCONA_DATA
sudo chown 1001 PERCONA_DATA
docker run --rm -p 27017:27017 -v $(realpath PERCONA_DATA):/data/db:rw -d percona/percona-server-mongodb:4.4
```

2.2 For testing purposes, we can setup Percona to use encryption-at-rest with the encryption key provided by a file.

```bash
mkdir PERCONA_DATA
sudo chown 1001 PERCONA_DATA
mkdir PERCONA_CRYPTO
openssl rand -base64 32 > PERCONA_CRYPTO/mongodb-keyfile
sudo chown 1001 PERCONA_CRYPTO
sudo chown 1001 PERCONA_CRYPTO/mongodb-keyfile
sudo chmod 600 PERCONA_CRYPTO/mongodb-keyfile
docker run --rm -p 27017:27017 -v $(realpath PERCONA_DATA):/data/db:rw -v $(realpath PERCONA_CRYPTO):/PERCONA_CRYPTO:ro -d percona/percona-server-mongodb:4.4 --enableEncryption --encryptionKeyFile /PERCONA_CRYPTO/mongodb-keyfile
```

2.3 To simulate a production environment, we can launch a development
instance of HashiCorp Vault to provide keys to Percona.

```bash
# Create a network where Percona can communicate with Vault
docker network create vaultnet

# Launch a development instance of the Vault Docker container and make note of the "Root Token"
docker run --rm --name vault --network vaultnet -p 8200:8200 -it vault
```

... then in another terminal ...

```bash
mkdir PERCONA_DATA
sudo chown 1001 PERCONA_DATA
mkdir PERCONA_CRYPTO

# Write the Vault "Root Token" to PERCONA_CRYPTO/vault.token

sudo chown 1001 PERCONA_CRYPTO
sudo chown 1001 PERCONA_CRYPTO/vault.token
sudo chmod 600 PERCONA_CRYPTO/vault.token

docker run --rm --network vaultnet -p 27017:27017 -v $(realpath PERCONA_DATA):/data/db:rw -v $(realpath PERCONA_CRYPTO):/PERCONA_CRYPTO:ro -d percona/percona-server-mongodb:4.4 --enableEncryption --vaultServerName vault --vaultPort 8200 --vaultTokenFile /PERCONA_CRYPTO/vault.token --vaultSecret secret/data/mongoencrypt --vaultDisableTLSForTesting
```

2.4 In a real production instance, the `--vaultDisableTLSForTesting`
should **never** be used and instead a valid TLS certificate should be
provided for a production Vault server.

3. Generate a Docker Compose environment that uses the Percona instance for data storage.

```bash
cd compose-cluster
CARDS_EXT_MONGO_AUTH='' python3 generate_compose_yaml.py --external_mongo --external_mongo_address 172.99.0.1 --external_mongo_dbname sling --dev_docker_image --subnet 172.99.0.0/16
```

4. Start the Docker Compose environment

```bash
docker-compose build
docker-compose up -d
```

Tests
-----

- We can inspect the data that is stored in the `PERCONA_DATA` directory
by mounting it to an Alpine Linux container and exploring it as `UID=1001`.

```bash
docker run --rm -u 1001 -v $(realpath PERCONA_DATA):/data:ro -it alpine:3.17

cd /data

# Should only return data for unencrypted Percona
strings * | grep cards

# Should only return data for unencrypted Percona
strings * | grep 'encrypted=false'

# Should only return data for encrypted Percona
strings * | grep 'encrypted=true'
```

- After starting Percona with the encryption-at-rest key provided by
Vault, visit `http://localhost:8200` and login with the _Root Token_. A
randomly generated _mongoencrypt_ secret should be present under
`secret/mongoencrypt`.

- Restarting CARDS and Percona should persist all data in all use cases.
