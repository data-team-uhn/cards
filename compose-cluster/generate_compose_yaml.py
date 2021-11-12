#!/usr/bin/env python
# -*- coding: utf-8 -*-

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

import os
import sys
import yaml
import json
import shutil
import argparse
from CardsDockerTagProperty import CARDS_DOCKER_TAG

argparser = argparse.ArgumentParser()
argparser.add_argument('--shards', help='Number of MongoDB shards', default=1, type=int)
argparser.add_argument('--replicas', help='Number of MongoDB replicas per shard (must be an odd number)', default=3, type=int)
argparser.add_argument('--config_replicas', help='Number of MongoDB cluster configuration servers (must be an odd number)', default=3, type=int)
argparser.add_argument('--custom_env_file', help='Enable a custom file with environment variables')
argparser.add_argument('--cards_project', help='The CARDS project to deploy (eg. cards4care, cards4lfs, etc...')
argparser.add_argument('--dev_docker_image', help='Indicate that the CARDS Docker image being used was built for development, not production.', action='store_true')
argparser.add_argument('--composum', help='Enable Composum for the CARDS admin account', action='store_true')
argparser.add_argument('--enable_ncr', help='Add a Neural Concept Recognizer service to the cluster', action='store_true')
argparser.add_argument('--oak_filesystem', help='Use the filesystem (instead of MongoDB) as the back-end for Oak/JCR', action='store_true')
argparser.add_argument('--external_mongo', help='Use an external MongoDB instance instead of providing our own', action='store_true')
argparser.add_argument('--external_mongo_address', help='Address/Hostname of the external MongoDB instance. Only valid if --external_mongo is specified.')
argparser.add_argument('--external_mongo_dbname', help='Database name of the external MongoDB instance. Only valid if --external_mongo is specified.')
argparser.add_argument('--saml', help='Make the Apache Sling SAML2 Handler OSGi bundle available for SAML-based logins', action='store_true')
argparser.add_argument('--saml_idp_destination', help='URL to redirect to for SAML logins')
argparser.add_argument('--server_address', help='Domain name (or Domain name:port) that the public will use for accessing this CARDS deployment')
argparser.add_argument('--ssl_proxy', help='Protect this service with SSL/TLS (use https:// instead of http://)', action='store_true')
argparser.add_argument('--sling_admin_port', help='The localhost TCP port which should be forwarded to cardsinitial:8080', type=int)
argparser.add_argument('--subnet', help='Manually specify the subnet of IP addresses to be used by the containers in this docker-compose environment (eg. --subnet 172.99.0.0/16)')
args = argparser.parse_args()

MONGO_SHARD_COUNT = args.shards
MONGO_REPLICA_COUNT = args.replicas
CONFIGDB_REPLICA_COUNT = args.config_replicas
ENABLE_NCR = args.enable_ncr
SSL_PROXY = args.ssl_proxy

#Validate before doing anything else
if (MONGO_REPLICA_COUNT % 2) != 1:
  print("ERROR: Replica count must be *odd* to achieve distributed consensus")
  sys.exit(-1)

if (CONFIGDB_REPLICA_COUNT % 2) != 1:
  print("ERROR: Config replica count must be *odd* to achieve distributed consensus")
  sys.exit(-1)

OUTPUT_FILENAME = "docker-compose.yml"

yaml_obj = {}
yaml_obj['version'] = '3'
yaml_obj['volumes'] = {}
yaml_obj['services'] = {}

if not (args.oak_filesystem or args.external_mongo):
  #Create configuration databases
  for i in range(CONFIGDB_REPLICA_COUNT):
    service_name = "config{}".format(i)
    print("Configuring service: {}".format(service_name))
    yaml_obj['services'][service_name] = {}

    yaml_obj['services'][service_name]['build'] = {}
    yaml_obj['services'][service_name]['build']['context'] = "configdb"

    yaml_obj['services'][service_name]['expose'] = ['27017']

    yaml_obj['services'][service_name]['networks'] = {}
    yaml_obj['services'][service_name]['networks']['internalnetwork'] = {}
    yaml_obj['services'][service_name]['networks']['internalnetwork']['aliases'] = [service_name]

    volume_name = "{}-volume".format(service_name)
    yaml_obj['volumes'][volume_name] = {}
    yaml_obj['volumes'][volume_name]['driver'] = "local"

    yaml_obj['services'][service_name]['volumes'] = ["{}:/data/configdb".format(volume_name)]

  #Setup the Mongo shard/replica service containers
  for shard_index in range(MONGO_SHARD_COUNT):
    os.mkdir("shard{}".format(shard_index))
    shutil.copyfile("TEMPLATE_shard/Dockerfile", "shard{}/Dockerfile".format(shard_index))
    mongo_conf = {}
    mongo_conf['sharding'] = {}
    mongo_conf['sharding']['clusterRole'] = "shardsvr"

    mongo_conf['replication'] = {}
    mongo_conf['replication']['replSetName'] = "MongoRS{}".format(shard_index)

    mongo_conf['net'] = {}
    mongo_conf['net']['bindIp'] = '0.0.0.0'
    mongo_conf['net']['port'] = 27017

    with open("shard{}/mongo-shard.conf".format(shard_index), 'w') as f_out:
      f_out.write(yaml.dump(mongo_conf, default_flow_style=False))

    for replica_index in range(MONGO_REPLICA_COUNT):
      service_name = "s{}r{}".format(shard_index, replica_index)
      print("Configuring service: {}".format(service_name))
      yaml_obj['services'][service_name] = {}

      yaml_obj['services'][service_name]['build'] = {}
      yaml_obj['services'][service_name]['build']['context'] = "shard{}".format(shard_index)

      yaml_obj['services'][service_name]['expose'] = ['27017']

      yaml_obj['services'][service_name]['networks'] = {}
      yaml_obj['services'][service_name]['networks']['internalnetwork'] = {}
      yaml_obj['services'][service_name]['networks']['internalnetwork']['aliases'] = [service_name]

      volume_name = "{}-volume".format(service_name)
      yaml_obj['volumes'][volume_name] = {}
      yaml_obj['volumes'][volume_name]['driver'] = "local"

      yaml_obj['services'][service_name]['volumes'] = ["{}:/data/db".format(volume_name)]

  #Setup the router
  print("Configuring service: router")
  yaml_obj['services']['router'] = {}
  yaml_obj['services']['router']['build'] = {}
  yaml_obj['services']['router']['build']['context'] = "mongos"

  yaml_obj['services']['router']['expose'] = ['27017']

  yaml_obj['services']['router']['networks'] = {}
  yaml_obj['services']['router']['networks']['internalnetwork'] = {}
  yaml_obj['services']['router']['networks']['internalnetwork']['aliases'] = ['router', 'mongo']

  yaml_obj['services']['router']['depends_on'] = []
  for i in range(CONFIGDB_REPLICA_COUNT):
    yaml_obj['services']['router']['depends_on'].append("config{}".format(i))

  for shard_index in range(MONGO_SHARD_COUNT):
    for replica_index in range(MONGO_REPLICA_COUNT):
      yaml_obj['services']['router']['depends_on'].append("s{}r{}".format(shard_index, replica_index))

  with open("mongos/mongo-router.conf", 'w') as f_out:
    configdb_str = "ConfigRS/"
    for config_index in range(CONFIGDB_REPLICA_COUNT):
      configdb_str += "config{}:27017,".format(config_index)
    configdb_str = configdb_str.rstrip(',')

    mongo_router_conf = {}
    mongo_router_conf['net'] = {}
    mongo_router_conf['net']['bindIp'] = '0.0.0.0'
    mongo_router_conf['net']['port'] = 27017

    mongo_router_conf['sharding'] = {}
    mongo_router_conf['sharding']['configDB'] = configdb_str

    f_out.write(yaml.dump(mongo_router_conf, default_flow_style=False))

  #Setup the initializer
  print("Configuring service: initializer")
  #Generate the initializations for the shards/replicas
  with open("initializer/initialize_all.sh", 'w') as f_init:
    f_init.write("#!/bin/bash\n")
    f_init.write("/wait_for_mongo.sh config0\n")
    f_init.write('echo "Host config0 is up"\n')

    config_init_doc = {}
    config_init_doc['_id'] = "ConfigRS"
    config_init_doc['configsvr'] = True
    config_init_doc['members'] = []
    for config_index in range(CONFIGDB_REPLICA_COUNT):
      config_init_doc['members'].append({'_id' : config_index, 'host' : 'config{}:27017'.format(config_index)})

    f_init.write("/mongo_rs_initiate.sh config0 '{}'\n".format(json.dumps(config_init_doc)))
    f_init.write('echo "ConfigDB replicas have been configured"\n')

    #Configure replica 0 for each shard
    for shard_index in range(MONGO_SHARD_COUNT):
      f_init.write("/wait_for_mongo.sh s{}r0\n".format(shard_index))
      f_init.write('echo "Host s{}r0 is up"\n'.format(shard_index))

      shard_init_doc = {}
      shard_init_doc['_id'] = "MongoRS{}".format(shard_index)
      shard_init_doc['members'] = []
      for replica_index in range(MONGO_REPLICA_COUNT):
        shard_init_doc['members'].append({'_id' : replica_index, 'host' : "s{}r{}".format(shard_index, replica_index)})

      f_init.write("/mongo_rs_initiate.sh s{}r0 '{}'\n".format(shard_index, json.dumps(shard_init_doc)))
      f_init.write('echo "Shard-{} replicas have been configured"\n'.format(shard_index))

    f_init.write("/wait_for_mongo.sh router\n")
    f_init.write('echo "Host router is up"\n')

    for shard_index in range(MONGO_SHARD_COUNT):
      shard_config = "MongoRS{}/".format(shard_index)
      for replica_index in range(MONGO_REPLICA_COUNT):
        shard_config += "s{}r{}:27017,".format(shard_index, replica_index)
      shard_config = shard_config.rstrip(',')
      f_init.write("/mongo_add_shard.sh router '{}'\n".format(shard_config))

    f_init.write('echo "All shards have been added to the MongoDB cluster..."\n')
    f_init.write('echo "Done."\n')


  #Configure the initializer container
  yaml_obj['services']['initializer'] = {}
  yaml_obj['services']['initializer']['build'] = {}
  yaml_obj['services']['initializer']['build']['context'] = "initializer"

  yaml_obj['services']['initializer']['networks'] = {}
  yaml_obj['services']['initializer']['networks']['internalnetwork'] = {}
  yaml_obj['services']['initializer']['networks']['internalnetwork']['aliases'] = ['initializer']

  yaml_obj['services']['initializer']['depends_on'] = ['router']

#Configure the initial CARDS container
print("Configuring service: cardsinitial")
yaml_obj['services']['cardsinitial'] = {}
yaml_obj['services']['cardsinitial']['image'] = "cards/cards:{}".format(CARDS_DOCKER_TAG)

yaml_obj['services']['cardsinitial']['networks'] = {}
yaml_obj['services']['cardsinitial']['networks']['internalnetwork'] = {}
yaml_obj['services']['cardsinitial']['networks']['internalnetwork']['aliases'] = ['cardsinitial']

#Create the ./SLING directory and copy the logback.xml file into it
try:
  os.mkdir("SLING")
  shutil.copyfile("../distribution/logback.xml", "SLING/logback.xml")
except FileExistsError:
  print("Warning: SLING directory exists - will leave unmodified.")

yaml_obj['services']['cardsinitial']['volumes'] = ["./SLING:/opt/cards/.cards-data"]
if args.dev_docker_image:
  yaml_obj['services']['cardsinitial']['volumes'].append("{}:/root/.m2:ro".format(os.path.join(os.environ['HOME'], '.m2')))

if args.custom_env_file:
  yaml_obj['services']['cardsinitial']['env_file'] = args.custom_env_file

yaml_obj['services']['cardsinitial']['environment'] = []
yaml_obj['services']['cardsinitial']['environment'].append("INITIAL_SLING_NODE=true")
if not (args.oak_filesystem or args.external_mongo):
  yaml_obj['services']['cardsinitial']['environment'].append("INSIDE_DOCKER_COMPOSE=true")
yaml_obj['services']['cardsinitial']['environment'].append("CARDS_RELOAD=${CARDS_RELOAD:-}")
if args.oak_filesystem:
  yaml_obj['services']['cardsinitial']['environment'].append("OAK_FILESYSTEM=true")

if not (args.oak_filesystem or args.external_mongo):
  yaml_obj['services']['cardsinitial']['depends_on'] = ['router']

if args.sling_admin_port:
  yaml_obj['services']['cardsinitial']['ports'] = ["127.0.0.1:{}:8080".format(args.sling_admin_port)]

if args.cards_project:
  yaml_obj['services']['cardsinitial']['environment'].append("CARDS_PROJECT={}".format(args.cards_project))

if args.composum:
  yaml_obj['services']['cardsinitial']['environment'].append("DEV=true")

if args.saml:
  yaml_obj['services']['cardsinitial']['environment'].append("SAML_AUTH_ENABLED=true")
  yaml_obj['services']['cardsinitial']['volumes'].append("./samlKeystore.p12:/opt/cards/samlKeystore.p12:ro")

#Configure the NCR container (if enabled) - only one for now
if ENABLE_NCR:
  print("Configuring service: neuralcr")
  yaml_obj['services']['neuralcr'] = {}
  yaml_obj['services']['neuralcr']['image'] = "ccmsk/neuralcr"

  yaml_obj['services']['neuralcr']['volumes'] = ["./NCR_MODEL:/root/opt/ncr/model_params:ro"]

  yaml_obj['services']['neuralcr']['networks'] = {}
  yaml_obj['services']['neuralcr']['networks']['internalnetwork'] = {}
  yaml_obj['services']['neuralcr']['networks']['internalnetwork']['aliases'] = ['neuralcr']

if args.external_mongo:
  if args.external_mongo_address:
    ext_mongo_address = args.external_mongo_address
  else:
    ext_mongo_address = input("Enter the address of the MongoDB server (ip, hostname, or domain name, optionally followed by port, e.g. mongo.localdomain:27017): ")

  if args.external_mongo_address and 'CARDS_EXT_MONGO_AUTH' in os.environ:
    ext_mongo_credentials = os.environ['CARDS_EXT_MONGO_AUTH']
  else:
    ext_mongo_credentials = input("Enter the username:password for the MongoDB server (leave blank for no password): ")

  if args.external_mongo_dbname:
    ext_mongo_db_name = args.external_mongo_dbname
  else:
    ext_mongo_db_name = input("Enter the Sling storage database name on the MongoDB server (default: sling): ")

  yaml_obj['services']['cardsinitial']['environment'].append("EXTERNAL_MONGO_ADDRESS={}".format(ext_mongo_address))
  if len(ext_mongo_credentials) != 0:
    yaml_obj['services']['cardsinitial']['environment'].append("MONGO_AUTH={}".format(ext_mongo_credentials))

  if len(ext_mongo_db_name) != 0:
    yaml_obj['services']['cardsinitial']['environment'].append("CUSTOM_MONGO_DB_NAME={}".format(ext_mongo_db_name))


#Configure the proxy container
print("Configuring service: proxy")
yaml_obj['services']['proxy'] = {}
yaml_obj['services']['proxy']['build'] = {}
yaml_obj['services']['proxy']['build']['context'] = "proxy"

if SSL_PROXY:
  yaml_obj['services']['proxy']['ports'] = ["443:443"]
else:
  yaml_obj['services']['proxy']['ports'] = ["8080:80"]

yaml_obj['services']['proxy']['networks'] = {}
yaml_obj['services']['proxy']['networks']['internalnetwork'] = {}
yaml_obj['services']['proxy']['networks']['internalnetwork']['aliases'] = ['proxy']

yaml_obj['services']['proxy']['depends_on'] = ['cardsinitial']
if ENABLE_NCR:
  yaml_obj['services']['proxy']['depends_on'].append('neuralcr')

if SSL_PROXY:
  if args.saml:
    shutil.copyfile("proxy/https_saml_000-default.conf", "proxy/000-default.conf")
  else:
    shutil.copyfile("proxy/https_000-default.conf", "proxy/000-default.conf")
  #Volume mount the SSL certificate and key
  yaml_obj['services']['proxy']['volumes'] = []
  yaml_obj['services']['proxy']['volumes'].append("./SSL_CONFIG/certificate.crt:/etc/cert/certificate.crt:ro")
  yaml_obj['services']['proxy']['volumes'].append("./SSL_CONFIG/certificatekey.key:/etc/cert/certificatekey.key:ro")
  yaml_obj['services']['proxy']['volumes'].append("./SSL_CONFIG/certificatechain.crt:/etc/cert/certificatechain.crt:ro")
else:
  if args.saml:
    shutil.copyfile("proxy/http_saml_000-default.conf", "proxy/000-default.conf")
  else:
    shutil.copyfile("proxy/http_000-default.conf", "proxy/000-default.conf")

if args.saml:
  yaml_obj['services']['proxy']['environment'] = []

  if args.saml_idp_destination:
    idp_url = args.saml_idp_destination
  else:
    idp_url = input("Enter the SAML2 IdP destination: ")

  if args.server_address:
    cards_server_address = args.server_address
  else:
    cards_server_address = input("Enter the public-facing server address for this deployment (eg. localhost:8080): ")

  yaml_obj['services']['proxy']['environment'].append("SAML_IDP_DESTINATION={}".format(idp_url))
  yaml_obj['services']['proxy']['environment'].append("CARDS_HOST_AND_PORT={}".format(cards_server_address))

#Setup the internal network
print("Configuring the internal network")
yaml_obj['networks'] = {}
yaml_obj['networks']['internalnetwork'] = {}
if args.subnet:
  yaml_obj['networks']['internalnetwork']['ipam'] = {}
  yaml_obj['networks']['internalnetwork']['ipam']['driver'] = 'default'
  yaml_obj['networks']['internalnetwork']['ipam']['config'] = [{'subnet': args.subnet}]

#Save it
with open(OUTPUT_FILENAME, 'w') as f_out:
  f_out.write(yaml.dump(yaml_obj, default_flow_style=False))

print("Done!")
