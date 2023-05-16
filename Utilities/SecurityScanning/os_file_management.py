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

# Files that can be found in any Linux distribution
GENERIC_LINUX_FILES = ["/usr/lib/os-release"]

# Files that can be found in any Linux distribution where packages are
# managed by dpkg
DPKG_MANAGED_FILES = ["/var/lib/dpkg/status"]

# Files that are specific to Debian
DEBIAN_SPECIFIC_FILES = ["/etc/debian_version"]

# Files that are specific to Ubuntu
UBUNTU_SPECIFIC_FILES = ["/etc/lsb-release"]

# The complete set of files for various OS
OS_FILE_SET = {}
OS_FILE_SET['debian'] = GENERIC_LINUX_FILES + DPKG_MANAGED_FILES + DEBIAN_SPECIFIC_FILES
OS_FILE_SET['ubuntu'] = GENERIC_LINUX_FILES + DPKG_MANAGED_FILES + UBUNTU_SPECIFIC_FILES
