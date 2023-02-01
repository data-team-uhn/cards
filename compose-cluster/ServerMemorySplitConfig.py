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

# Let us allocate memory as follows:

# 25% for the CARDS Java process
MEMORY_SPLIT_CARDS_JAVA = 0.25

# 60% for MongoDB data storage nodes (either a single container or the set of shards and replicas)
MEMORY_SPLIT_MONGO_DATA_STORAGE = 0.60

# 15% for other processes
MEMORY_SPLIT_OTHER_PROCESSES = 0.15
