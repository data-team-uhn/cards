#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

ALL_ARGUMENTS=$@

# HTTP + Singular Percona + Encryption-At-Rest with Vault provided key
python3 generate_compose_yaml.py $ALL_ARGUMENTS \
  --dev_docker_image \
  --composum \
  --percona_singular \
  --percona_encryption_vault_server vault \
  --percona_encryption_vault_token_file ./vault_dev_token.token \
  --percona_encryption_vault_secret secret/data/perconaencrypt \
  --percona_encryption_vault_disable_tls_for_testing \
  --vault_dev_server || { echo "ERROR: Failed to generate a Docker Compose environment. Exiting."; exit -1; }

docker-compose build

# Bring up Vault first
docker-compose up -d vault_dev

# Wait for Vault to become available
while true
do
  docker-compose exec vault_dev vault status -address=http://localhost:8200 && break
  sleep 5
done
echo "Vault is up"

# Set the perconaencrypt Vault secret to some pre-defined test value
docker-compose exec -e "VAULT_ADDR=http://127.0.0.1:8200" -e "VAULT_TOKEN=vault_dev_token" vault_dev vault kv put secret/perconaencrypt value="gKpfqVcH85jI6K97OVdKdoH8CCXHPa5OkWduWfWsOBw="

# Bring everything else up
docker-compose up -d

# Wait for CARDS to become available
while true
do
  curl --fail http://localhost:8080/system/sling/info.sessionInfo.json && break
  sleep 5
done
