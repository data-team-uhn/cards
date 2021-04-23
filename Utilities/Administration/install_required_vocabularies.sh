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

FAILURE_HANDLER=$1

python3 list_required_vocabularies.py | while read -r vocab
do
  #Check if vocab.owl exists
  if [ -n $VOCABULARY_FILES_PATH ] && [ -f $VOCABULARY_FILES_PATH/$vocab.owl ]
  then
    python3 install_vocabulary.py \
      --vocabulary_file $VOCABULARY_FILES_PATH/$vocab.owl \
      --vocabulary_id $vocab \
      --vocabulary_name $vocab \
      --vocabulary_version 1.0 || exit -1

  #Check if vocab.obo exists
  elif [ -n $VOCABULARY_FILES_PATH ] && [ -f $VOCABULARY_FILES_PATH/$vocab.obo ]
  then
    python3 install_vocabulary.py \
      --vocabulary_file $VOCABULARY_FILES_PATH/$vocab.obo \
      --vocabulary_id $vocab \
      --vocabulary_name $vocab \
      --vocabulary_version 1.0 || exit -1
  else
    #Try to install from BioPortal
    python3 install_vocabulary.py --bioportal_id $vocab
    #If BioPortal installation fails, either exit or just log an error
    if [ $? -ne 0 ]
    then
      echo "*ERROR* Could not install $vocab"
      [ "$FAILURE_HANDLER" = "exit_on_failure" ] && exit -1
    fi
  fi
done
