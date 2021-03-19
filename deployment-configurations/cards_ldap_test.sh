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

PROJECT_ROOT=$(realpath ../)

#Generate the docker-compose environment
cp docker-compose.yml $PROJECT_ROOT/compose-cluster
cd $PROJECT_ROOT/compose-cluster
docker-compose build
docker-compose up -d

#Wait for it to start
while true
do
  echo "Waiting for CARDS to start"
  curl --fail http://localhost:8080/system/sling/info.sessionInfo.json && break
  sleep 5
done
echo ""

#Configure LDAP
echo -n 'apply=true' > ldap_args
echo -n '&action=ajaxConfigManager' >> ldap_args
echo -n '&%24location=' >> ldap_args
echo -n '&provider.name=ldap' >> ldap_args
echo -n '&host.name=ldapmock' >> ldap_args
echo -n '&host.port=10389' >> ldap_args
echo -n '&host.ssl=false' >> ldap_args
echo -n '&host.tls=false' >> ldap_args
echo -n '&host.noCertCheck=false' >> ldap_args
echo -n '&bind.dn=uid%3Dadmin%2Cou%3Dsystem' >> ldap_args
echo -n '&bind.password=secret' >> ldap_args
echo -n '&searchTimeout=60s' >> ldap_args
echo -n '&adminPool.maxActive=8' >> ldap_args
echo -n '&adminPool.lookupOnValidate=true' >> ldap_args
echo -n '&adminPool.lookupOnValidate=false' >> ldap_args
echo -n '&userPool.maxActive=8' >> ldap_args
echo -n '&userPool.lookupOnValidate=true' >> ldap_args
echo -n '&userPool.lookupOnValidate=false' >> ldap_args
echo -n '&user.baseDN=dc%3Dwimpi%2Cdc%3Dnet' >> ldap_args
echo -n '&user.objectclass=inetOrgPerson' >> ldap_args
echo -n '&user.idAttribute=samaccountname' >> ldap_args
echo -n '&user.extraFilter=' >> ldap_args
echo -n '&user.makeDnPath=false' >> ldap_args
echo -n '&group.baseDN=ou%3Dgroups%2Co%3Dexample%2Cdc%3Dcom' >> ldap_args
echo -n '&group.objectclass=groupOfUniqueNames' >> ldap_args
echo -n '&group.nameAttribute=cn' >> ldap_args
echo -n '&group.extraFilter=' >> ldap_args
echo -n '&group.makeDnPath=false' >> ldap_args
echo -n '&group.memberAttribute=uniquemember' >> ldap_args
echo -n '&useUidForExtId=false' >> ldap_args
echo -n '&customattributes=' >> ldap_args
echo -n '&propertylist=provider.name' >> ldap_args
echo -n '%2Chost.name' >> ldap_args
echo -n '%2Chost.port' >> ldap_args
echo -n '%2Chost.ssl' >> ldap_args
echo -n '%2Chost.tls' >> ldap_args
echo -n '%2Chost.noCertCheck' >> ldap_args
echo -n '%2Cbind.dn' >> ldap_args
echo -n '%2Cbind.password' >> ldap_args
echo -n '%2CsearchTimeout' >> ldap_args
echo -n '%2CadminPool.maxActive' >> ldap_args
echo -n '%2CadminPool.lookupOnValidate' >> ldap_args
echo -n '%2CuserPool.maxActive' >> ldap_args
echo -n '%2CuserPool.lookupOnValidate' >> ldap_args
echo -n '%2Cuser.baseDN' >> ldap_args
echo -n '%2Cuser.objectclass' >> ldap_args
echo -n '%2Cuser.idAttribute' >> ldap_args
echo -n '%2Cuser.extraFilter' >> ldap_args
echo -n '%2Cuser.makeDnPath' >> ldap_args
echo -n '%2Cgroup.baseDN' >> ldap_args
echo -n '%2Cgroup.objectclass' >> ldap_args
echo -n '%2Cgroup.nameAttribute' >> ldap_args
echo -n '%2Cgroup.extraFilter' >> ldap_args
echo -n '%2Cgroup.makeDnPath' >> ldap_args
echo -n '%2Cgroup.memberAttribute' >> ldap_args
echo -n '%2CuseUidForExtId' >> ldap_args
echo -n '%2Ccustomattributes' >> ldap_args

curl 'http://localhost:8080/system/console/configMgr/org.apache.jackrabbit.oak.security.authentication.ldap.impl.LdapIdentityProvider'\
  -H 'Content-Type: application/x-www-form-urlencoded; charset=UTF-8'\
  -u admin:admin\
  --data-raw $(cat ldap_args)

#Ready to go
echo "Test deployment is now ready at port 8080"
