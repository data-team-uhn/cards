#!/usr/bin/env python
# -*- coding: utf-8 -*-

import os
import sys
import yaml
import json
import shutil

#Print help if args not given correctly
if len(sys.argv) != 3:
	print("Usage: python3 generate_compose_yaml.py MONGO_SHARD_COUNT MONGO_REPLICA_COUNT")
	sys.exit(-1)

MONGO_SHARD_COUNT = int(sys.argv[1])
MONGO_REPLICA_COUNT = int(sys.argv[2])

#Validate before doing anything else
if (MONGO_REPLICA_COUNT % 2) != 1:
	print("ERROR: Replica count must be *odd* to achieve distributed consensus")
	sys.exit(-1)

OUTPUT_FILENAME = "docker-compose.yml"

yaml_obj = {}
yaml_obj['version'] = '3'
yaml_obj['services'] = {}

#Create 3 configuration databases
for i in range(3):
	service_name = "config{}".format(i)
	print("Configuring service: {}".format(service_name))
	yaml_obj['services'][service_name] = {}
	
	yaml_obj['services'][service_name]['build'] = {}
	yaml_obj['services'][service_name]['build']['context'] = "configdb"
	
	yaml_obj['services'][service_name]['expose'] = ['27017']
	
	yaml_obj['services'][service_name]['networks'] = {}
	yaml_obj['services'][service_name]['networks']['internalnetwork'] = {}
	yaml_obj['services'][service_name]['networks']['internalnetwork']['aliases'] = [service_name]
	
	

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
for i in range(3):
	yaml_obj['services']['router']['depends_on'].append("config{}".format(i))

for shard_index in range(MONGO_SHARD_COUNT):
	for replica_index in range(MONGO_REPLICA_COUNT):
		yaml_obj['services']['router']['depends_on'].append("s{}r{}".format(shard_index, replica_index))

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
	config_init_doc['members'].append({'_id' : 0, 'host' : 'config0:27017'})
	config_init_doc['members'].append({'_id' : 1, 'host' : 'config1:27017'})
	config_init_doc['members'].append({'_id' : 2, 'host' : 'config2:27017'})
	
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

#Configure the initial LFS container
print("Configuring service: lfsinitial")
yaml_obj['services']['lfsinitial'] = {}
yaml_obj['services']['lfsinitial']['image'] = "lfs/lfs"

yaml_obj['services']['lfsinitial']['networks'] = {}
yaml_obj['services']['lfsinitial']['networks']['internalnetwork'] = {}
yaml_obj['services']['lfsinitial']['networks']['internalnetwork']['aliases'] = ['lfsinitial']

yaml_obj['services']['lfsinitial']['ports'] = ["8080:8080"]

yaml_obj['services']['lfsinitial']['environment'] = []
yaml_obj['services']['lfsinitial']['environment'].append("INITIAL_SLING_NODE=true")
yaml_obj['services']['lfsinitial']['environment'].append("INSIDE_DOCKER_COMPOSE=true")

yaml_obj['services']['lfsinitial']['depends_on'] = ['router']

#Setup the internal network
print("Configuring the internal network")
yaml_obj['networks'] = {}
yaml_obj['networks']['internalnetwork'] = {}

#Save it
with open(OUTPUT_FILENAME, 'w') as f_out:
	f_out.write(yaml.dump(yaml_obj, default_flow_style=False))

print("Done!")
