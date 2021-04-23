#!/bin/bash

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
      [ $FAILURE_HANDLER = "exit_on_failure" ] && exit -1
    fi
done
