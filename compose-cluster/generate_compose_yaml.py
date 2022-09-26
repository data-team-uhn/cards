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
import math
import yaml
import json
import psutil
import shutil
import hashlib
import argparse
from OpenSSL import crypto, SSL
from CardsDockerTagProperty import CARDS_DOCKER_TAG
from CloudIAMdemoKeystoreSha256Property import CLOUD_IAM_DEMO_KEYSTORE_SHA256
from ServerMemorySplitConfig import MEMORY_SPLIT_CARDS_JAVA, MEMORY_SPLIT_MONGO_SHARDS_REPLICAS

MINIO_DOCKER_RELEASE_TAG = "RELEASE.2022-09-17T00-09-45Z"

argparser = argparse.ArgumentParser()
argparser.add_argument('--shards', help='Number of MongoDB shards', default=1, type=int)
argparser.add_argument('--replicas', help='Number of MongoDB replicas per shard (must be an odd number)', default=3, type=int)
argparser.add_argument('--config_replicas', help='Number of MongoDB cluster configuration servers (must be an odd number)', default=3, type=int)
argparser.add_argument('--custom_env_file', help='Enable a custom file with environment variables')
argparser.add_argument('--cards_project', help='The CARDS project to deploy (eg. cards4proms, cards4lfs, etc...')
argparser.add_argument('--dev_docker_image', help='Indicate that the CARDS Docker image being used was built for development, not production.', action='store_true')
argparser.add_argument('--composum', help='Enable Composum for the CARDS admin account', action='store_true')
argparser.add_argument('--enable_backup_server', help='Add a cards/backup_recorder service to the cluster', action='store_true')
argparser.add_argument('--backup_server_path', help='Host OS path where the backup_recorder container should store its backup files')
argparser.add_argument('--enable_ncr', help='Add a Neural Concept Recognizer service to the cluster', action='store_true')
argparser.add_argument('--oak_filesystem', help='Use the filesystem (instead of MongoDB) as the back-end for Oak/JCR', action='store_true')
argparser.add_argument('--external_mongo', help='Use an external MongoDB instance instead of providing our own', action='store_true')
argparser.add_argument('--external_mongo_uri', help='URI of the external MongoDB instance. Only valid if --external_mongo is specified.')
argparser.add_argument('--external_mongo_dbname', help='Database name of the external MongoDB instance. Only valid if --external_mongo is specified.')
argparser.add_argument('--saml', help='Make the Apache Sling SAML2 Handler OSGi bundle available for SAML-based logins', action='store_true')
argparser.add_argument('--saml_idp_destination', help='URL to redirect to for SAML logins')
argparser.add_argument('--saml_cloud_iam_demo', help='Enable SAML authentication with CARDS via the Cloud-IAM.com demo', action='store_true')
argparser.add_argument('--server_address', help='Domain name (or Domain name:port) that the public will use for accessing this CARDS deployment')
argparser.add_argument('--smtps', help='Enable SMTPS emailing functionality', action='store_true')
argparser.add_argument('--smtps_localhost_proxy', help='Run an SSL termination proxy so that the CARDS container may connect to the host\'s SMTP server at localhost:25', action='store_true')
argparser.add_argument('--smtps_test_container', help='Enable the mock SMTPS (cards/postfix-docker) container for viewing CARDS-sent emails.', action='store_true')
argparser.add_argument('--smtps_test_mail_path', help='Host OS path where the email mbox file from smtps_test_container is stored')
argparser.add_argument('--s3_test_container', help='Add a MinIO S3 Bucket Docker container for testing S3 data exports', action='store_true')
argparser.add_argument('--ssl_proxy', help='Protect this service with SSL/TLS (use https:// instead of http://)', action='store_true')
argparser.add_argument('--sling_admin_port', help='The localhost TCP port which should be forwarded to cardsinitial:8080', type=int)
argparser.add_argument('--subnet', help='Manually specify the subnet of IP addresses to be used by the containers in this docker-compose environment (eg. --subnet 172.99.0.0/16)')
args = argparser.parse_args()

MONGO_SHARD_COUNT = args.shards
MONGO_REPLICA_COUNT = args.replicas
CONFIGDB_REPLICA_COUNT = args.config_replicas
ENABLE_BACKUP_SERVER = args.enable_backup_server
ENABLE_NCR = args.enable_ncr
SSL_PROXY = args.ssl_proxy

def sha256FileHash(filepath):
  hasher = hashlib.sha256()
  with open(filepath, 'rb') as f:
    hasher.update(f.read())
    return hasher.hexdigest()

#Validate before doing anything else
if (MONGO_REPLICA_COUNT % 2) != 1:
  print("ERROR: Replica count must be *odd* to achieve distributed consensus")
  sys.exit(-1)

if (CONFIGDB_REPLICA_COUNT % 2) != 1:
  print("ERROR: Config replica count must be *odd* to achieve distributed consensus")
  sys.exit(-1)

if args.smtps_localhost_proxy:
  if not args.subnet:
    print("ERROR: A --subnet must be explicitly specified when using --smtps_localhost_proxy")
    sys.exit(-1)

if args.smtps_test_container:
  if not args.smtps_test_mail_path:
    print("ERROR: A --smtps_test_mail_path must be specified when using --smtps_test_container")
    sys.exit(-1)

if ENABLE_BACKUP_SERVER:
  if not args.backup_server_path:
    print("ERROR: A --backup_server_path must be specified with using --enable_backup_server")
    sys.exit(-1)

if args.saml:
  if "samlKeystore.p12" not in os.listdir('.'):
    print("ERROR: samlKeystore.p12 is required but not found.")
    sys.exit(-1)

if args.saml_cloud_iam_demo:
  if CLOUD_IAM_DEMO_KEYSTORE_SHA256 != sha256FileHash("samlKeystore.p12"):
    print("")
    print("=============================== Warning ==============================")
    print("The SHA256 hash of samlKeystore.p12 does not match the expected value.")
    print("SAML authentication with Cloud-IAM.com may not work.")
    print("======================================================================")
    print("")

def getDockerHostIP(subnet):
  network_address = subnet.split('/')[0]
  network_address_octets = network_address.split('.')
  network_address_octets[-1] = '1'
  return '.'.join(network_address_octets)

def generateSmtpsProxyConfigFile(docker_host_ip):
  with open("smtps_localhost_proxy/nginx.conf.template", 'r') as f:
    nginx_template = f.read()
  nginx_config = nginx_template.replace("${DOCKER_HOST_IP}", docker_host_ip)
  with open("smtps_localhost_proxy/nginx.conf", 'w') as f:
    f.write(nginx_config)

def generateSelfSignedCert():
  k = crypto.PKey()
  k.generate_key(crypto.TYPE_RSA, 4096)
  cert = crypto.X509()
  cert.get_subject().C = "NT"
  cert.get_subject().ST = "stateOrProvinceName"
  cert.get_subject().L = "localityName"
  cert.get_subject().O = "organizationName"
  cert.get_subject().OU = "organizationUnitName"
  cert.get_subject().CN = "commonName"
  cert.get_subject().emailAddress = "emailAddress"
  cert.set_serial_number(0)
  cert.gmtime_adj_notBefore(0)
  cert.gmtime_adj_notAfter(100*365*24*60*60)
  cert.set_issuer(cert.get_subject())
  cert.set_pubkey(k)
  cert.sign(k, 'sha256')
  pem_key = crypto.dump_privatekey(crypto.FILETYPE_PEM, k).decode('utf-8')
  pem_cert = crypto.dump_certificate(crypto.FILETYPE_PEM, cert).decode('utf-8')
  return pem_key, pem_cert

def getPathToProjectResourcesDirectory(project_name):
  CARDS4_PREFIX = "cards4"
  resourcesPathMap = {}

  # If an entry for this project_name exists in resourcesPathMap use it instead of anything else
  if project_name in resourcesPathMap:
    return resourcesPathMap[project_name]

  if not project_name.startswith(CARDS4_PREFIX):
    return None

  project_id = project_name[len(CARDS4_PREFIX):]
  return "../{}-resources/".format(project_id)

def getPathToConfDirectory(project_name):
  project_resources_dir = getPathToProjectResourcesDirectory(project_name)
  if project_resources_dir is not None:
    return os.path.join(project_resources_dir, "clinical-data/src/main/resources/SLING-INF/content/libs/cards/conf/")

  return None

def getPathToMediaContentDirectory(project_name):
  project_resources_dir = getPathToProjectResourcesDirectory(project_name)
  if project_resources_dir is not None:
    return os.path.join(project_resources_dir, "clinical-data/src/main/media/SLING-INF/content")

  return None

def getLogoByResourcesDirectory(project_name):
  path_to_media_json = os.path.join(getPathToConfDirectory(project_name), "Media.json")
  if not os.path.exists(path_to_media_json):
    print("Warning: {} does not exist.".format(path_to_media_json))
    return None

  with open(path_to_media_json, 'r') as f_json:
    try:
      media_config = json.load(f_json)
    except json.decoder.JSONDecodeError:
      print("Warning: {} contains invalid JSON.".format(path_to_media_json))
      return None

  if "logoLight" not in media_config:
    print("Warning: 'logoLight' was not specified in {}.".format(path_to_media_json))
    return None

  path_to_media_sling_content_directory = getPathToMediaContentDirectory(project_name)

  logo_light_path = media_config["logoLight"].lstrip("/")
  logo_light_path = os.path.join(path_to_media_sling_content_directory, logo_light_path)

  if not os.path.exists(logo_light_path):
    print("Warning: The path {} in {} does not exist.".format(logo_light_path, path_to_media_json))
    return None

  return logo_light_path

def getCardsProjectLogoPath(project_name):
  if project_name is not None:
    # Try to see if a {project_id}-resources directory exists that can be used for obtaining the logo
    projectLogoPath = getLogoByResourcesDirectory(project_name)
    if projectLogoPath is not None:
      return projectLogoPath

  # If all else fails, use the default CARDS logo
  projectLogoPath = "../modules/homepage/src/main/media/SLING-INF/content/libs/cards/resources/media/default/logo_light_bg.png"
  return projectLogoPath

def getApplicationNameByResourcesDirectory(project_name):
  path_to_appname_json = os.path.join(getPathToConfDirectory(project_name), "AppName.json")
  if not os.path.exists(path_to_appname_json):
    print("Warning: {} does not exist.".format(path_to_appname_json))
    return None

  with open(path_to_appname_json, 'r') as f_json:
    try:
      appname_config = json.load(f_json)
    except json.decoder.JSONDecodeError:
      print("Warning: {} contains invalid JSON.".format(appname_config))
      return None

  if "AppName" not in appname_config:
    print("Warning: 'AppName' was not specified in {}.".format(appname_config))
    return None

  if type(appname_config['AppName']) != str:
    print("Warning: 'AppName' in {} is of wrong data-type.".format(path_to_appname_json))
    return None

  return appname_config['AppName']

def getCardsApplicationName(project_name):
  if project_name is not None:
    # Try to see if a {project_id}-resources directory exists that can be used for obtaining the logo
    projectAppName = getApplicationNameByResourcesDirectory(project_name)
    if projectAppName is not None:
      return projectAppName

  # If all else fails, use the generic CARDS name
  return "CARDS"

def getWiredTigerCacheSizeGB():
  total_system_memory_bytes = psutil.virtual_memory().total
  total_system_memory_gb = total_system_memory_bytes / (1024 * 1024 * 1024)

  # Total memory given to the complete set of MongoDB shards and replicas:
  total_mongo_shard_replica_memory_gb = MEMORY_SPLIT_MONGO_SHARDS_REPLICAS * total_system_memory_gb

  # Memory given to each MongoDB shard / replica
  memory_per_shard_replica_gb = total_mongo_shard_replica_memory_gb / (MONGO_SHARD_COUNT * MONGO_REPLICA_COUNT)

  # Floor to 2 decimal places
  memory_per_shard_replica_gb = math.floor(100 * memory_per_shard_replica_gb) / 100.0

  # The value of WiredTigerCacheSizeGB must range between 0.25GB and 10000GB as per MongoDB documentation
  if (memory_per_shard_replica_gb < 0.25) or (memory_per_shard_replica_gb > 10000):
    raise Exception("WiredTigerCacheSizeGB cannot be less than 0.25 or greater than 10000")

  return memory_per_shard_replica_gb

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

      yaml_obj['services'][service_name]['environment'] = ["WIRED_TIGER_CACHE_SIZE_GB={}".format(getWiredTigerCacheSizeGB())]

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
yaml_obj['services']['cardsinitial']['volumes'].append("./SSL_CONFIG/cards_certs/:/load_certs:ro")
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

if args.saml_cloud_iam_demo:
  yaml_obj['services']['cardsinitial']['environment'].append("SAML_CLOUD_IAM_DEMO=true")

if args.smtps:
  yaml_obj['services']['cardsinitial']['environment'].append("SMTPS_ENABLED=true")
  yaml_obj['services']['cardsinitial']['environment'].append("SLING_COMMONS_CRYPTO_PASSWORD=password")

if args.smtps_localhost_proxy:
  yaml_obj['services']['cardsinitial']['environment'].append("SMTPS_HOST=smtps_localhost_proxy")
  yaml_obj['services']['cardsinitial']['environment'].append("SMTPS_LOCALHOST_PROXY=true")
  yaml_obj['services']['cardsinitial']['volumes'].append("./SSL_CONFIG/smtps_certificate.crt:/etc/cert/smtps_certificate.crt:ro")
  yaml_obj['services']['cardsinitial']['extra_hosts'] = {}
  yaml_obj['services']['cardsinitial']['extra_hosts']['smtps_localhost_proxy'] = getDockerHostIP(args.subnet)

if args.smtps_test_container:
  yaml_obj['services']['cardsinitial']['environment'].append("SMTPS_HOST=smtps_test_container")
  yaml_obj['services']['cardsinitial']['environment'].append("SMTPS_LOCAL_TEST_CONTAINER=true")

if args.s3_test_container:
  yaml_obj['services']['cardsinitial']['environment'].append("S3_ENDPOINT_URL=http://minio:9000")
  yaml_obj['services']['cardsinitial']['environment'].append("S3_ENDPOINT_REGION=us-west-1")
  yaml_obj['services']['cardsinitial']['environment'].append("S3_BUCKET_NAME=uhn")
  yaml_obj['services']['cardsinitial']['environment'].append("AWS_KEY=minioadmin")
  yaml_obj['services']['cardsinitial']['environment'].append("AWS_SECRET=minioadmin")

if ENABLE_BACKUP_SERVER:
  print("Configuring service: backup_recorder")

  yaml_obj['services']['cardsinitial']['environment'].append("BACKUP_WEBHOOK_URL=http://backup_recorder:8012")

  yaml_obj['services']['backup_recorder'] = {}
  yaml_obj['services']['backup_recorder']['image'] = "cards/expressjs"

  yaml_obj['services']['backup_recorder']['volumes'] = ["{}:/backup".format(args.backup_server_path)]
  yaml_obj['services']['backup_recorder']['volumes'].append("{}:/backup_recorder.js:ro".format(os.path.realpath("../Utilities/Administration/Backup-To-JSON/backup_recorder.js")))

  yaml_obj['services']['backup_recorder']['environment'] = ["LISTEN_HOST=0.0.0.0"]
  yaml_obj['services']['backup_recorder']['environment'].append("HOST_USER={}".format(os.environ['USER']))
  yaml_obj['services']['backup_recorder']['environment'].append("HOST_UID={}".format(os.getuid()))

  yaml_obj['services']['backup_recorder']['networks'] = {}
  yaml_obj['services']['backup_recorder']['networks']['internalnetwork'] = {}
  yaml_obj['services']['backup_recorder']['networks']['internalnetwork']['aliases'] = ['backup_recorder']

  yaml_obj['services']['backup_recorder']['depends_on'] = ['cardsinitial']
  yaml_obj['services']['backup_recorder']['command'] = ["nodejs", "/backup_recorder.js", "/backup"]

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
  if args.external_mongo_uri:
    ext_mongo_uri = args.external_mongo_uri
  else:
    ext_mongo_uri = input("Enter the URI of the MongoDB server (ip, hostname, or domain name, optionally followed by port, e.g. mongo.localdomain:27017): ")

  if args.external_mongo_uri and 'CARDS_EXT_MONGO_AUTH' in os.environ:
    ext_mongo_credentials = os.environ['CARDS_EXT_MONGO_AUTH']
  else:
    ext_mongo_credentials = input("Enter the username:password for the MongoDB server (leave blank for no password): ")

  if args.external_mongo_dbname:
    ext_mongo_db_name = args.external_mongo_dbname
  else:
    ext_mongo_db_name = input("Enter the Sling storage database name on the MongoDB server (default: sling): ")

  yaml_obj['services']['cardsinitial']['environment'].append("EXTERNAL_MONGO_URI={}".format(ext_mongo_uri))
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

#Add the appropriate CARDS logo (eg. DATAPRO, HERACLES, etc...) for the selected project
shutil.copyfile(getCardsProjectLogoPath(args.cards_project), "./proxy/proxyerror/logo.png")

#Specify the Application Name of the CARDS project to the proxy
yaml_obj['services']['proxy']['environment'] = []
yaml_obj['services']['proxy']['environment'].append("CARDS_APP_NAME={}".format(getCardsApplicationName(args.cards_project)))

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
  if args.saml_idp_destination:
    idp_url = args.saml_idp_destination
  elif args.saml_cloud_iam_demo:
    idp_url = "https://lemur-15.cloud-iam.com/auth/realms/cards-saml-test/protocol/saml"
  else:
    idp_url = input("Enter the SAML2 IdP destination: ")

  if args.server_address:
    cards_server_address = args.server_address
  else:
    cards_server_address = input("Enter the public-facing server address for this deployment (eg. localhost:8080): ")

  yaml_obj['services']['proxy']['environment'].append("SAML_IDP_DESTINATION={}".format(idp_url))
  yaml_obj['services']['proxy']['environment'].append("CARDS_HOST_AND_PORT={}".format(cards_server_address))

#Configure the SMTPS localhost proxy container (if enabled)
if args.smtps_localhost_proxy:
  print("Configuring service: smtps_localhost_proxy")
  yaml_obj['services']['smtps_localhost_proxy'] = {}
  yaml_obj['services']['smtps_localhost_proxy']['image'] = "nginx"
  yaml_obj['services']['smtps_localhost_proxy']['network_mode'] = 'host'
  # envsubst the nginx.conf.template file and volume mount it
  generateSmtpsProxyConfigFile(getDockerHostIP(args.subnet))
  yaml_obj['services']['smtps_localhost_proxy']['volumes'] = []
  yaml_obj['services']['smtps_localhost_proxy']['volumes'].append("./smtps_localhost_proxy/nginx.conf:/etc/nginx/nginx.conf:ro")
  yaml_obj['services']['smtps_localhost_proxy']['volumes'].append("./SSL_CONFIG/smtps_certificate.crt:/etc/cert/smtps_certificate.crt:ro")
  yaml_obj['services']['smtps_localhost_proxy']['volumes'].append("./SSL_CONFIG/smtps_certificatekey.key:/etc/cert/smtps_certificatekey.key:ro")

#Configure the SMTPS test container (if enabled)
if args.smtps_test_container:
  print("Configuring service: smtps_test_container")
  yaml_obj['services']['smtps_test_container'] = {}
  yaml_obj['services']['smtps_test_container']['image'] = "cards/postfix-docker"
  yaml_obj['services']['smtps_test_container']['networks'] = {}
  yaml_obj['services']['smtps_test_container']['networks']['internalnetwork'] = {}
  yaml_obj['services']['smtps_test_container']['networks']['internalnetwork']['aliases'] = ['smtps_test_container']
  yaml_obj['services']['smtps_test_container']['environment'] = []
  yaml_obj['services']['smtps_test_container']['environment'].append("HOST_USER={}".format(os.environ['USER']))
  yaml_obj['services']['smtps_test_container']['environment'].append("HOST_UID={}".format(os.getuid()))
  yaml_obj['services']['smtps_test_container']['environment'].append("HOST_GID={}".format(os.getgid()))
  yaml_obj['services']['smtps_test_container']['volumes'] = []
  yaml_obj['services']['smtps_test_container']['volumes'].append("./SSL_CONFIG/cards_certs/smtps_certificate.crt:/HOSTPERM_cert.pem:ro")
  yaml_obj['services']['smtps_test_container']['volumes'].append("./SSL_CONFIG/smtps_certificatekey.key:/HOSTPERM_certkey.pem:ro")
  yaml_obj['services']['smtps_test_container']['volumes'].append("{}:/var/spool/mail".format(args.smtps_test_mail_path))
  print("Generating a self-signed SSL certificate for smtps_test_container")
  smtps_key, smtps_cert = generateSelfSignedCert()
  with open("./SSL_CONFIG/smtps_certificatekey.key", 'w') as f_smtps_key:
    f_smtps_key.write(smtps_key)
  with open("./SSL_CONFIG/cards_certs/smtps_certificate.crt", 'w') as f_smtps_cert:
    f_smtps_cert.write(smtps_cert)

if args.s3_test_container:
  print("Configuring service: s3_test_container")
  yaml_obj['services']['s3_test_container'] = {}
  yaml_obj['services']['s3_test_container']['image'] = "minio/minio:" + MINIO_DOCKER_RELEASE_TAG
  yaml_obj['services']['s3_test_container']['networks'] = {}
  yaml_obj['services']['s3_test_container']['networks']['internalnetwork'] = {}
  yaml_obj['services']['s3_test_container']['networks']['internalnetwork']['aliases'] = ['minio']
  yaml_obj['services']['s3_test_container']['ports'] = ["127.0.0.1:9001:9001"]
  yaml_obj['services']['s3_test_container']['environment'] = []
  yaml_obj['services']['s3_test_container']['environment'].append("MINIO_ROOT_USER=minioadmin")
  yaml_obj['services']['s3_test_container']['environment'].append("MINIO_ROOT_PASSWORD=minioadmin")
  yaml_obj['services']['s3_test_container']['command'] = ["server", "/data", "--console-address", ":9001"]

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
