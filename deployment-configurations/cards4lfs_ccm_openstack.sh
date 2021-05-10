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

# Prerequisites:
# - SSL certificates and keys in compose-cluster/SSL_CONFIG
#   - certificate.crt
#   - certificatekey.key
#   - certificatechain.crt
#
# - Docker images
#   - lfs/lfs
#   - ccmsk/neuralcr (version with HP preloaded model)
#

DOCKER_COMPOSE_SUBNET='172.19.0.0/16'
DOCKER_COMPOSE_HOST_IP='172.19.0.1'

MONGO_DB_VOLUME_MOUNT=$(realpath ~/cards4lfs_mongodb)
mkdir $MONGO_DB_VOLUME_MOUNT

PROJECT_ROOT=$(realpath ../)

#We will use a single mongoDB instance for data persistence
docker pull mongo:4.2-bionic || exit -1
docker run -p 27017:27017 -v $MONGO_DB_VOLUME_MOUNT:/data/db --name mongolfs -d mongo:4.2-bionic || exit -1

#Wait for mongoDB to start
while true
do
  echo "Waiting for mongoDB to start"
  sleep 5
  docker exec mongolfs mongo --eval 'db.runCommand("ping").ok' && break
done

#Generate the docker-compose environment
cd $PROJECT_ROOT/compose-cluster
echo "ADDITIONAL_RUN_MODES=lfs,dev" > custom_env.env
echo "BIOPORTAL_APIKEY=$BIOPORTAL_APIKEY" >> custom_env.env
CARDS_EXT_MONGO_AUTH='' python3 generate_compose_yaml.py \
  --external_mongo \
  --external_mongo_address $DOCKER_COMPOSE_HOST_IP \
  --external_mongo_dbname sling \
  --enable_ncr \
  --ssl_proxy \
  --sling_admin_port 8081 \
  --custom_env_file custom_env.env \
  --subnet $DOCKER_COMPOSE_SUBNET

#Add the docker-compose.override.yml file and any other other needed resources
cp $PROJECT_ROOT/deployment-configurations/resources/cards4lfs_ccm_openstack/docker-compose.override.yml .
cp $PROJECT_ROOT/deployment-configurations/resources/cards4lfs_ccm_openstack/registered_models.py ncr_registered_models.py

docker-compose build || exit -1
docker-compose up -d lfsinitial || exit -1

#All configurations on CARDS will be done through this URL
export CARDS_URL=http://localhost:8081

#Wait for CARDS to start
while true
do
  echo "Waiting for CARDS to start"
  curl --fail $CARDS_URL/system/sling/info.sessionInfo.json && break
  sleep 5
done
echo ""

#Set the admin password
cd $PROJECT_ROOT/Utilities/Administration
if [ -z "$CARDS_DEPLOYMENT_ADMIN_PASSWORD" ]
then
  echo "CARDS_DEPLOYMENT_ADMIN_PASSWORD unspecified, generating a random one"
  export CARDS_DEPLOYMENT_ADMIN_PASSWORD=$(openssl rand -hex 8)
  echo ""
  echo "**********************************************************"
  echo -n "Admin password for this deployment is: "
  printenv CARDS_DEPLOYMENT_ADMIN_PASSWORD
  echo "**********************************************************"
  echo ""
fi
printenv CARDS_DEPLOYMENT_ADMIN_PASSWORD | python3 set_sling_password.py || exit -1

#Install the required vocabularies
echo "Installing vocabularies..."
cd $PROJECT_ROOT/Utilities/Administration
export ADMIN_PASSWORD=$CARDS_DEPLOYMENT_ADMIN_PASSWORD

echo "Installing HP vocabulary..."
python3 install_vocabulary.py --bioportal_id HP || exit -1

echo "Installing HANCESTRO vocabulary..."
python3 install_vocabulary.py --bioportal_id HANCESTRO || exit -1

#Download the required NCR models
echo "Downloading NCR models..."
mkdir $PROJECT_ROOT/compose-cluster/NCR_MODEL
cd $PROJECT_ROOT/Utilities/NCR-Downloader

#pmc_model_new.bin
if [ ! -f $PROJECT_ROOT/compose-cluster/NCR_MODEL/pmc_model_new.bin ]
then
  python3 download_model.py \
    --download pmc_model_new.bin \
    --savedir $PROJECT_ROOT/compose-cluster/NCR_MODEL || exit -1
fi

#HP - Human Phenotype Ontology
if [ ! -d $PROJECT_ROOT/compose-cluster/NCR_MODEL/HP ]
then
  python3 download_model.py \
    --download HP \
    --savedir $PROJECT_ROOT/compose-cluster/NCR_MODEL || exit -1
fi

#Set up LDAP
#cd $PROJECT_ROOT/Utilities/Administration
#TODO: Set the environment variables accordingly
#python3 configure_ldap.py || exit -1

#Start up everything else
cd $PROJECT_ROOT/compose-cluster
docker-compose up -d

#Wait for NeuralCR to start
#while true
#do
#  echo "Waiting for NeuralCR to start"
#  #It is safe to use --insecure here as we are only connecting to localhost
#  curl --fail --insecure https://127.0.0.1/ncr/models/ && break
#  sleep 5
#done

#Install the required NCR models
#echo "Installing NCR models..."
#cd $PROJECT_ROOT/Utilities/Administration

#echo "Installing HP NCR model"
#python3 install_neuralcr_model.py \
#  --model_name HP \
#  --model_type neural \
#  --param_dir $PROJECT_ROOT/compose-cluster/NCR_MODEL/HP \
#  --fasttext $PROJECT_ROOT/compose-cluster/NCR_MODEL/pmc_model_new.bin \
#  --threshold 0.6 || exit -1

#Ready to go
echo "cards4lfs_ccm_openstack deployment is now ready at https://lfs.ccm.sickkids.ca"
