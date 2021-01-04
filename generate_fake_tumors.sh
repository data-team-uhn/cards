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

echo -e "\"Patient\"\t\"Tumor\"\t\"Lesion Number\"" > fake_data.csv
for p in `seq 0 9`
do
    for t in `seq 0 12`
    do
        echo -e "P$p\tT$p-$t\t$RANDOM" >> fake_data.csv
    done
done
curl -u admin:admin http://localhost:8080/Forms/ -F ":data=@fake_data.csv" -F ":questionnaire=/Questionnaires/Tumors" -F ":subjectType=/SubjectTypes/Patient" -F ":subjectType=/SubjectTypes/Tumor"
rm fake_data.csv
