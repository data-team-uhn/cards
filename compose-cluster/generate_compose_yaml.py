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

try:
  import os
  import sys
  import math
  import yaml
  import json
  import psutil
  import shutil
  import hashlib
  import tzlocal
  import argparse
  import zoneinfo
  from OpenSSL import crypto, SSL
except ImportError:
  print("Error: Missing dependencies!")
  print()
  print("On Debian/Ubuntu, these missing dependencies can be installed with:")
  print()
  print("\tapt install python3-yaml python3-psutil python3-tzlocal python3-openssl")
  print()
  print("On any other system, these missing dependencies can be installed with:")
  print()
  print("\tpip3 install -r requirements.txt")
  print()
  print("Although you may wish to do this in a Virtual Environment so as to not affect any of the globally installed Python packages;")
  print()
  print("\tpython3 -m venv venv")
  print("\tsource venv/bin/activate")
  print("\tpip3 install -r requirements.txt")
  print("\tpython3 generate_compose_yaml.py ...")
  print("\tdeactivate")
  print()
  print("Once the Virtual Environment is set up, subsequent uses of generate_compose_yaml.py can be done with:")
  print()
  print("\tsource venv/bin/activate")
  print("\tpython3 generate_compose_yaml.py ...")
  print("\tdeactivate")
  print()
  sys.exit(-1)

from CardsDockerTagProperty import CARDS_DOCKER_TAG
from CloudIAMdemoKeystoreSha256Property import CLOUD_IAM_DEMO_KEYSTORE_SHA256
from ServerMemorySplitConfig import MEMORY_SPLIT_CARDS_JAVA, MEMORY_SPLIT_MONGO_DATA_STORAGE

ADMINER_DOCKER_RELEASE_TAG = "4.8.1"
MINIO_DOCKER_RELEASE_TAG = "RELEASE.2022-09-17T00-09-45Z"

argparser = argparse.ArgumentParser()
argparser.add_argument('--mongo_singular', help='Use a single MongoDB Docker container for data storage', action='store_true')
argparser.add_argument('--mongo_cluster', help='Use a cluster of MongoDB shards and replicas for data storage', action='store_true')
argparser.add_argument('--percona_singular', help='Use a single Docker container of Percona Server for MongoDB for data storage', action='store_true')
argparser.add_argument('--percona_encryption_keyfile', help='Enable encryption-at-rest for the singular Percona Server for MongoDB instance using the provided keyfile')
argparser.add_argument('--percona_encryption_vault_server')
argparser.add_argument('--percona_encryption_vault_port', type=int, default=8200)
argparser.add_argument('--percona_encryption_vault_token_file')
argparser.add_argument('--percona_encryption_vault_secret')
argparser.add_argument('--percona_encryption_vault_disable_tls_for_testing', action='store_true')
argparser.add_argument('--data_db_mount', help='If using --mongo_singular or --percona_singular, mount /data/db to a given location instead of to a Docker volume')
argparser.add_argument('--shards', help='Number of MongoDB shards', default=1, type=int)
argparser.add_argument('--replicas', help='Number of MongoDB replicas per shard (must be an odd number)', default=3, type=int)
argparser.add_argument('--config_replicas', help='Number of MongoDB cluster configuration servers (must be an odd number)', default=3, type=int)
argparser.add_argument('--custom_env_file', help='Enable a custom file with environment variables')
argparser.add_argument('--cards_project', help='The CARDS project to deploy (eg. cards4proms, cards4lfs, etc...')
argparser.add_argument('--demo', help='Enable the Demo Banner, Upgrade Marker Flag, and Demo Forms', action='store_true')
argparser.add_argument('--demo_banner', help='Enable only the Demo Banner', action='store_true')
argparser.add_argument('--dev_docker_image', help='Indicate that the CARDS Docker image being used was built for development, not production.', action='store_true')
argparser.add_argument('--composum', help='Enable Composum for the CARDS admin account', action='store_true')
argparser.add_argument('--debug', help='Debug the CARDS instance on port 5005', action='store_true')
argparser.add_argument('--adminer', help='Add an Adminer Docker container for database interaction via web browser', action='store_true')
argparser.add_argument('--adminer_port', help='If --adminer is specified, bind it to this localhost port [default: 1435]', default=1435, type=int)
argparser.add_argument('--enable_backup_server', help='Add a cards/backup_recorder service to the cluster', action='store_true')
argparser.add_argument('--backup_server_path', help='Host OS path where the backup_recorder container should store its backup files')
argparser.add_argument('--enable_ncr', help='Add a Neural Concept Recognizer service to the cluster', action='store_true')
argparser.add_argument('--oak_filesystem', help='Use the filesystem (instead of MongoDB) as the back-end for Oak/JCR', action='store_true')
argparser.add_argument('--external_mongo', help='Use an external MongoDB instance instead of providing our own', action='store_true')
argparser.add_argument('--external_mongo_uri', help='URI of the external MongoDB instance. Only valid if --external_mongo is specified.')
argparser.add_argument('--external_mongo_dbname', help='Database name of the external MongoDB instance. Only valid if --external_mongo is specified.')
argparser.add_argument('--clarity', help='Enable the clarity-integration CARDS module.', action='store_true')
argparser.add_argument('--mssql', help='Start up a MS-SQL instance with test data', action='store_true')
argparser.add_argument('--expose_mssql', help='If --mssql is specified, forward the SQL service to the specified port (defaults to 1433 if --expose_mssql is specified without a port parameter)', nargs='?', const=1433, type=int)
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
argparser.add_argument('--self_signed_ssl_proxy', help='Generate a self-signed SSL certificate for the proxy to use (used mainly for testing purposes).', action='store_true')
argparser.add_argument('--behind_ssl_termination', help='Listen only for non-encrypted HTTP connections but apply all HTTPS headers (as client connections are made through an upstream SSL-terminating reverse proxy)', action='store_true')
argparser.add_argument('--sling_admin_port', help='The localhost TCP port which should be forwarded to cardsinitial:8080', type=int)
argparser.add_argument('--subnet', help='Manually specify the subnet of IP addresses to be used by the containers in this docker-compose environment (eg. --subnet 172.99.0.0/16)')
argparser.add_argument('--vault_dev_server', help='Add a HashiCorp Vault (development mode) container to the set of services', action='store_true')
argparser.add_argument('--web_port_admin', help='If specified, will listen for connections on this port (and not 8080/443) and forward them to the full-access reverse proxy (permitting logins)', type=int)
argparser.add_argument('--web_port_user', help='If specified, will listen for connections on this port and forward them to the restricted-access reverse proxy (logins not permitted)', type=int)
argparser.add_argument('--web_port_user_root_redirect', help='The client accessing / over --web_port_user will automatically be redirected to this page', default='/Survey.html')
argparser.add_argument('--timezone', help='Specify a timezone (eg. America/Toronto) other than the one of the host system')
args = argparser.parse_args()

MONGO_SHARD_COUNT = args.shards
MONGO_REPLICA_COUNT = args.replicas
CONFIGDB_REPLICA_COUNT = args.config_replicas
ENABLE_BACKUP_SERVER = args.enable_backup_server
ENABLE_NCR = args.enable_ncr
SSL_PROXY = args.ssl_proxy

MISSING_ARG_PROMPTS = {}
MISSING_ARG_PROMPTS['server_address'] = "Enter the public-facing server address for this deployment (eg. localhost:8080): "

def sha256FileHash(filepath):
  hasher = hashlib.sha256()
  with open(filepath, 'rb') as f:
    hasher.update(f.read())
    return hasher.hexdigest()

def getArgValueOrPrompt(arg_name):
  # Initialize this method's static map of arg keys -> values
  if not hasattr(getArgValueOrPrompt, "kv_map"):
    getArgValueOrPrompt.kv_map = {}

  # If this argument has been specified in the command-line, simply return it
  if (arg_name in vars(args)) and (vars(args)[arg_name] is not None):
    return vars(args)[arg_name]
  else:
    # ...otherwise prompt and store the key -> value pair for future use
    if arg_name in getArgValueOrPrompt.kv_map:
      return getArgValueOrPrompt.kv_map[arg_name]
    else:
      if arg_name in MISSING_ARG_PROMPTS:
        val = input(MISSING_ARG_PROMPTS[arg_name])
      else:
        val = input("Please enter a value for the missing --{} argument: ".format(arg_name))
      getArgValueOrPrompt.kv_map[arg_name] = val
      return val

def getTimezoneName():
  if args.timezone:
    return args.timezone
  else:
    hosts_localzone = tzlocal.get_localzone()
    if type(hosts_localzone) == zoneinfo.ZoneInfo:
      return hosts_localzone.key
    else:
      return hosts_localzone.zone

#Validate before doing anything else

# Ensure that we have some type of data storage for CARDS
mongo_storage_type_settings = [args.mongo_singular, args.mongo_cluster, args.oak_filesystem, args.external_mongo, args.percona_singular]
if mongo_storage_type_settings.count(True) < 1:
  print("ERROR: A data persistence backend of either --mongo_singular, --mongo_cluster, --oak_filesystem, --external_mongo, or --percona_singular must be specified")
  sys.exit(-1)

if mongo_storage_type_settings.count(True) > 1:
  print("ERROR: Only one data persistence backend can be specified")
  sys.exit(-1)

VAULT_PROVIDED_PERCONA_ENCRYPTION = False
percona_encryption_vault_required_settings = [(args.percona_encryption_vault_server is not None), (args.percona_encryption_vault_token_file is not None), (args.percona_encryption_vault_secret is not None)]
if percona_encryption_vault_required_settings.count(True) == 3:
  # Encryption using HashiCorp Vault is enabled
  VAULT_PROVIDED_PERCONA_ENCRYPTION = True
elif percona_encryption_vault_required_settings.count(True) == 0:
  # Encryption using HashiCorp Vault will not be used
  VAULT_PROVIDED_PERCONA_ENCRYPTION = False
else:
  # Bad configuration
  print("ERROR: In order to use a Percona encryption key provided by Vault, --percona_encryption_vault_server, --percona_encryption_vault_token_file, and --percona_encryption_vault_secret must be specified")
  sys.exit(-1)

if args.mongo_cluster:
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

# Check that the encryption keyfile ownership and permissions are acceptable to Percona
if args.percona_encryption_keyfile is not None:
  if VAULT_PROVIDED_PERCONA_ENCRYPTION:
    print("ERROR: Cannot specify both keyfile-based and Vault-based Percona encryption")
    sys.exit(-1)

  # Check that the file is owned by UID=1001 and has octal permissions of 600
  keyfile_stat = os.stat(args.percona_encryption_keyfile)
  if keyfile_stat.st_uid != 1001:
    print("ERROR: The file specified by --percona_encryption_keyfile must have UID=1001")
    sys.exit(-1)
  if (keyfile_stat.st_mode & 0b111111111) != 0o600:
    print("ERROR: The file specified by --percona_encryption_keyfile must have permissions of rw------- (600)")
    sys.exit(-1)

# Check that the Vault token file ownership and permissions are acceptable to Percona
if args.percona_encryption_vault_token_file is not None:
  # Check that the file is owned by UID=1001 and has octal permissions of 600
  token_file_stat = os.stat(args.percona_encryption_vault_token_file)
  if token_file_stat.st_uid != 1001:
    print("ERROR: The file specified by --percona_encryption_vault_token_file must have UID=1001")
    sys.exit(-1)
  if (token_file_stat.st_mode & 0b111111111) != 0o600:
    print("ERROR: The file specified by --percona_encryption_vault_token_file must have permissions of rw------- (600)")
    sys.exit(-1)

# Percona requires that /data/db have a UID=1001
if (args.data_db_mount is not None) and args.percona_singular:
  data_db_mount_stat = os.stat(args.data_db_mount)
  if data_db_mount_stat.st_uid != 1001:
    print("ERROR: The file specified by --percona_singular must have UID=1001")
    sys.exit(-1)

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

def getWiredTigerCacheSizeGB(mongo_node_count=1):
  total_system_memory_bytes = psutil.virtual_memory().total
  total_system_memory_gb = total_system_memory_bytes / (1024 * 1024 * 1024)

  # Total memory allocated for MongoDB - be it a single MongoDB container of a cluster of shards and replicas:
  total_mongo_memory_gb = MEMORY_SPLIT_MONGO_DATA_STORAGE * total_system_memory_gb

  # What should the WIRED_TIGER_CACHE_SIZE_GB value be for the MongoDB node in question?
  wired_tiger_cache_size_gb = total_mongo_memory_gb / mongo_node_count

  # Floor to 2 decimal places
  wired_tiger_cache_size_gb = math.floor(100 * wired_tiger_cache_size_gb) / 100.0

  # The value of WiredTigerCacheSizeGB must range between 0.25GB and 10000GB as per MongoDB documentation
  if (wired_tiger_cache_size_gb < 0.25) or (wired_tiger_cache_size_gb > 10000):
    raise Exception("WiredTigerCacheSizeGB cannot be less than 0.25 or greater than 10000")

  return wired_tiger_cache_size_gb

def getCardsJavaMemoryLimitMB():
  total_system_memory_bytes = psutil.virtual_memory().total
  total_system_memory_mb = total_system_memory_bytes / (1024 * 1024)

  cards_java_memory_limit_mb = MEMORY_SPLIT_CARDS_JAVA * total_system_memory_mb

  # Floor down to the nearest integer MB
  return math.floor(cards_java_memory_limit_mb)

def newListIfEmpty(yaml_object, *keys):
  # Descend down the yaml_object through the keys
  list_parent_object = yaml_object
  for key in keys[0:-1]:
    list_parent_object = list_parent_object[key]
  list_name = keys[-1]
  if list_name in list_parent_object.keys():
    if type(list_parent_object[list_name]) is list:
      return list_parent_object[list_name]
  list_parent_object[list_name] = []
  return list_parent_object[list_name]

OUTPUT_FILENAME = "docker-compose.yml"

yaml_obj = {}
yaml_obj['version'] = '3'
yaml_obj['volumes'] = {}
yaml_obj['services'] = {}

# We're using a cluster of MongoDB shards and replicas for data storage
if args.mongo_cluster:
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

      yaml_obj['services'][service_name]['environment'] = ["WIRED_TIGER_CACHE_SIZE_GB={}".format(getWiredTigerCacheSizeGB(MONGO_SHARD_COUNT * MONGO_REPLICA_COUNT))]

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

if args.mongo_singular:
  # Create the single-container MongoDB
  print("Configuring service: mongo")
  yaml_obj['services']['mongo'] = {}
  yaml_obj['services']['mongo']['image'] = "mongo:4.2-bionic"
  yaml_obj['services']['mongo']['networks'] = {}
  yaml_obj['services']['mongo']['networks']['internalnetwork'] = {}
  yaml_obj['services']['mongo']['networks']['internalnetwork']['aliases'] = ['mongo']

  yaml_obj['services']['mongo']['command'] = "--wiredTigerCacheSizeGB {}".format(getWiredTigerCacheSizeGB(1))

  if args.data_db_mount is None:
    yaml_obj['volumes']['cards-mongo'] = {}
    yaml_obj['volumes']['cards-mongo']['driver'] = "local"

    yaml_obj['services']['mongo']['volumes'] = ["cards-mongo:/data/db"]
  else:
    yaml_obj['services']['mongo']['volumes'] = ["{}:/data/db".format(args.data_db_mount)]

if args.percona_singular:
  # Create the single-container Percona Server for MongoDB
  print("Configuring service: percona")
  yaml_obj['services']['percona'] = {}
  yaml_obj['services']['percona']['image'] = "percona/percona-server-mongodb:4.4"
  yaml_obj['services']['percona']['networks'] = {}
  yaml_obj['services']['percona']['networks']['internalnetwork'] = {}
  yaml_obj['services']['percona']['networks']['internalnetwork']['aliases'] = ['percona', 'mongo']

  yaml_obj['services']['percona']['command'] = "--wiredTigerCacheSizeGB {}".format(getWiredTigerCacheSizeGB(1))
  if args.percona_encryption_keyfile is not None:
    yaml_obj['services']['percona']['command'] += " --enableEncryption --encryptionKeyFile /percona_keyfile"
  elif VAULT_PROVIDED_PERCONA_ENCRYPTION:
    yaml_obj['services']['percona']['command'] += " --enableEncryption --vaultServerName {} --vaultPort {} --vaultTokenFile /vault.token --vaultSecret {}".format(args.percona_encryption_vault_server, args.percona_encryption_vault_port, args.percona_encryption_vault_secret)
    if args.percona_encryption_vault_disable_tls_for_testing:
      yaml_obj['services']['percona']['command'] += "  --vaultDisableTLSForTesting"

  if args.data_db_mount is None:
    yaml_obj['volumes']['cards-percona'] = {}
    yaml_obj['volumes']['cards-percona']['driver'] = "local"

    yaml_obj['services']['percona']['volumes'] = ["cards-percona:/data/db"]
  else:
    yaml_obj['services']['percona']['volumes'] = ["{}:/data/db".format(args.data_db_mount)]

  if args.percona_encryption_keyfile is not None:
    yaml_obj['services']['percona']['volumes'].append("{}:/percona_keyfile:ro".format(args.percona_encryption_keyfile))
  elif VAULT_PROVIDED_PERCONA_ENCRYPTION:
    yaml_obj['services']['percona']['volumes'].append("{}:/vault.token:ro".format(args.percona_encryption_vault_token_file))

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
if os.path.exists("/etc/localtime"):
  yaml_obj['services']['cardsinitial']['volumes'].append("/etc/localtime:/etc/localtime:ro")
if args.dev_docker_image:
  yaml_obj['services']['cardsinitial']['volumes'].append("{}:/root/.m2:ro".format(os.path.join(os.environ['HOME'], '.m2')))

if args.custom_env_file:
  yaml_obj['services']['cardsinitial']['env_file'] = args.custom_env_file

yaml_obj['services']['cardsinitial']['environment'] = []
yaml_obj['services']['cardsinitial']['environment'].append("INITIAL_SLING_NODE=true")

# If we're using a cluster of MongoDB shards and replicas for data storage,
# ensure that CARDS does not start before the cluster is ready.
if args.mongo_cluster:
  yaml_obj['services']['cardsinitial']['environment'].append("INSIDE_DOCKER_COMPOSE=true")

yaml_obj['services']['cardsinitial']['environment'].append("CARDS_RELOAD=${CARDS_RELOAD:-}")
if args.oak_filesystem:
  yaml_obj['services']['cardsinitial']['environment'].append("OAK_FILESYSTEM=true")

if args.mongo_cluster:
  yaml_obj['services']['cardsinitial']['depends_on'] = ['router']

if args.mongo_singular:
  yaml_obj['services']['cardsinitial']['depends_on'] = ['mongo']

if args.percona_singular:
  yaml_obj['services']['cardsinitial']['depends_on'] = ['percona']

if args.mongo_cluster or args.mongo_singular or args.percona_singular:
  # We must also limit the memory given to the CARDS Java process as the
  # internal MongoDB setup will also use a significant amount of memory.
  yaml_obj['services']['cardsinitial']['environment'].append("CARDS_JAVA_MEMORY_LIMIT_MB={}".format(getCardsJavaMemoryLimitMB()))

if args.sling_admin_port:
  newListIfEmpty(yaml_obj, 'services', 'cardsinitial', 'ports').append("127.0.0.1:{}:8080".format(args.sling_admin_port))

if args.debug:
  newListIfEmpty(yaml_obj, 'services', 'cardsinitial', 'ports').append("127.0.0.1:5005:5005")

if args.cards_project:
  yaml_obj['services']['cardsinitial']['environment'].append("CARDS_PROJECT={}".format(args.cards_project))

if args.composum:
  yaml_obj['services']['cardsinitial']['environment'].append("DEV=true")

if args.debug:
  yaml_obj['services']['cardsinitial']['environment'].append("DEBUG=true")

if args.demo:
  yaml_obj['services']['cardsinitial']['environment'].append("DEMO=true")

if args.demo_banner:
  yaml_obj['services']['cardsinitial']['environment'].append("DEMO_BANNER=true")

if args.clarity:
  yaml_obj['services']['cardsinitial']['environment'].append("CLARITY_IMPORT_ENABLED=true")

if args.saml:
  yaml_obj['services']['cardsinitial']['environment'].append("SAML_AUTH_ENABLED=true")
  yaml_obj['services']['cardsinitial']['volumes'].append("./samlKeystore.p12:/opt/cards/samlKeystore.p12:ro")

if args.saml_cloud_iam_demo:
  yaml_obj['services']['cardsinitial']['environment'].append("SAML_CLOUD_IAM_DEMO=true")

if args.smtps:
  yaml_obj['services']['cardsinitial']['environment'].append("SMTPS_ENABLED=true")
  yaml_obj['services']['cardsinitial']['environment'].append("SLING_COMMONS_CRYPTO_PASSWORD=password")

  yaml_obj['services']['cardsinitial']['environment'].append("CARDS_HOST_AND_PORT={}".format(getArgValueOrPrompt('server_address')))

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

if SSL_PROXY or args.behind_ssl_termination:
  yaml_obj['services']['cardsinitial']['environment'].append("BEHIND_SSL_PROXY=true")

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
  if args.web_port_admin is not None:
    yaml_obj['services']['proxy']['ports'] = ["{}:443".format(args.web_port_admin)]
  else:
    yaml_obj['services']['proxy']['ports'] = ["443:443"]
  if args.web_port_user is not None:
    yaml_obj['services']['proxy']['ports'].append("{}:444".format(args.web_port_user))
else:
  if args.web_port_admin is not None:
    yaml_obj['services']['proxy']['ports'] = ["{}:80".format(args.web_port_admin)]
  else:
    yaml_obj['services']['proxy']['ports'] = ["8080:80"]
  if args.web_port_user is not None:
    yaml_obj['services']['proxy']['ports'].append("{}:90".format(args.web_port_user))

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
yaml_obj['services']['proxy']['environment'].append("WEB_PORT_USER_ROOT_REDIRECT={}".format(args.web_port_user_root_redirect))

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
elif args.behind_ssl_termination:
  if args.saml:
    shutil.copyfile("proxy/terminated-ssl_saml_000-default.conf", "proxy/000-default.conf")
  else:
    shutil.copyfile("proxy/terminated-ssl_000-default.conf", "proxy/000-default.conf")
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

  yaml_obj['services']['proxy']['environment'].append("SAML_IDP_DESTINATION={}".format(idp_url))
  yaml_obj['services']['proxy']['environment'].append("CARDS_HOST_AND_PORT={}".format(getArgValueOrPrompt('server_address')))

# Generate a self-signed SSL certificate for the proxy, if instructed to do so
if args.self_signed_ssl_proxy:
  print("Generating a self-signed SSL certificate for the proxy")
  proxy_ssl_key, proxy_ssl_cert = generateSelfSignedCert()
  with open("./SSL_CONFIG/certificatekey.key", 'w') as f_proxy_ssl_key:
    f_proxy_ssl_key.write(proxy_ssl_key)
  with open("./SSL_CONFIG/certificate.crt", 'w') as f_proxy_ssl_cert:
    f_proxy_ssl_cert.write(proxy_ssl_cert)
  with open("./SSL_CONFIG/certificatechain.crt", 'w') as f_proxy_ssl_cert:
    f_proxy_ssl_cert.write(proxy_ssl_cert)

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

if args.adminer:
  print("Configuring service: adminer")
  yaml_obj['services']['adminer'] = {}
  yaml_obj['services']['adminer']['image'] = "adminer:" + ADMINER_DOCKER_RELEASE_TAG
  yaml_obj['services']['adminer']['networks'] = {}
  yaml_obj['services']['adminer']['networks']['internalnetwork'] = {}
  yaml_obj['services']['adminer']['networks']['internalnetwork']['aliases'] = ['adminer']
  yaml_obj['services']['adminer']['ports'] = ["127.0.0.1:{}:8080".format(args.adminer_port)]

if args.mssql:
  print("Configuring service: ms-sql")
  yaml_obj['services']['mssql'] = {}
  yaml_obj['services']['mssql']['image'] = 'mcr.microsoft.com/mssql/server:2022-latest'
  yaml_obj['services']['mssql']['networks'] = {}
  yaml_obj['services']['mssql']['networks']['internalnetwork'] = {}
  yaml_obj['services']['mssql']['networks']['internalnetwork']['aliases'] = ['mssql']
  yaml_obj['services']['mssql']['environment'] = ['ACCEPT_EULA=Y', 'MSSQL_SA_PASSWORD=testPassword_']
  yaml_obj['services']['cardsinitial']['environment'].append("CLARITY_SQL_SERVER=mssql:1433")
  yaml_obj['services']['cardsinitial']['environment'].append('CLARITY_SQL_USERNAME=sa')
  yaml_obj['services']['cardsinitial']['environment'].append('CLARITY_SQL_PASSWORD=testPassword_')
  yaml_obj['services']['cardsinitial']['environment'].append('CLARITY_SQL_ENCRYPT=false')
  yaml_obj['services']['cardsinitial']['environment'].append('CLARITY_SQL_SCHEMA=path')
  if args.cards_project == 'cards4prems':
    yaml_obj['services']['cardsinitial']['environment'].append('CLARITY_SQL_TABLE=CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS')
    yaml_obj['services']['cardsinitial']['environment'].append('CLARITY_EVENT_TIME_COLUMN=HOSP_DISCHARGE_DTTM')
  elif args.cards_project == 'cards4proms':
    yaml_obj['services']['cardsinitial']['environment'].append('CLARITY_SQL_TABLE=PatientVisitActivity_for_DATA-PRO')
    yaml_obj['services']['cardsinitial']['environment'].append('CLARITY_EVENT_TIME_COLUMN=ENCOUNTER_DATE')
  if args.expose_mssql:
    yaml_obj['services']['mssql']['ports'] = ['127.0.0.1:{}:1433'.format(args.expose_mssql)]

if args.vault_dev_server:
  print("Configuring service: vault_dev")
  yaml_obj['services']['vault_dev'] = {}
  yaml_obj['services']['vault_dev']['image'] = 'vault:1.12.2'
  yaml_obj['services']['vault_dev']['networks'] = {}
  yaml_obj['services']['vault_dev']['networks']['internalnetwork'] = {}
  yaml_obj['services']['vault_dev']['networks']['internalnetwork']['aliases'] = ['vault_dev', 'vault']
  yaml_obj['services']['vault_dev']['environment'] = ['VAULT_DEV_ROOT_TOKEN_ID=vault_dev_token']
  yaml_obj['services']['vault_dev']['ports'] = ['127.0.0.1:8200:8200']

#Setup the internal network
print("Configuring the internal network")
yaml_obj['networks'] = {}
yaml_obj['networks']['internalnetwork'] = {}
if args.subnet:
  yaml_obj['networks']['internalnetwork']['ipam'] = {}
  yaml_obj['networks']['internalnetwork']['ipam']['driver'] = 'default'
  yaml_obj['networks']['internalnetwork']['ipam']['config'] = [{'subnet': args.subnet}]

# Configuration items that should be added to *all* services
for service_name in yaml_obj['services']:
  # Timezone
  if 'environment' in yaml_obj['services'][service_name]:
    yaml_obj['services'][service_name]['environment'].append("TZ={}".format(getTimezoneName()))
  else:
    yaml_obj['services'][service_name]['environment'] = ["TZ={}".format(getTimezoneName())]

  # Automatic restart policy
  yaml_obj['services'][service_name]['restart'] = "unless-stopped"

#Save it
with open(OUTPUT_FILENAME, 'w') as f_out:
  f_out.write(yaml.dump(yaml_obj, default_flow_style=False))

print("Done!")
