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

# Generate the project-specific HTTP 503 custom error page
nodejs /render_503_page.js

# Generate the apache_non_cards_all_user_routes.conf Apache config file
nodejs /generate_non_cards_routes.js /apache_common_conf/apache_non_cards_all_user_routes.conf

# Start the nodeJS configurable proxy
nodejs /http_proxy.js &

# Start the Apache httpd proxy
apachectl -D FOREGROUND
