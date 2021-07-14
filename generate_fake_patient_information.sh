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

echo -e "\"Patient\"" > fake_data.csv
for p in `seq 0 40`
do
  echo -e "P$p" >> fake_data.csv
done
curl -u admin:admin http://localhost:8080/Forms/ -F ":data=@fake_data.csv" -F ":questionnaire=/Questionnaires/Patient information" -F ":subjectType=/SubjectTypes/Patient"
rm fake_data.csv
