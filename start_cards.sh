#!/bin/sh

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

# All of the actual start logic lives in start_cards.py, shared between this
# wrapper (Linux, macOS, WSL) and start_cards.bat (Windows).
# Keep platform-specific logic in start_cards.py, not in the wrappers.

cd "$(dirname "$0")" || exit 1

if command -v python3 > /dev/null 2>&1
then
  exec python3 start_cards.py "$@"
elif command -v python > /dev/null 2>&1
then
  exec python start_cards.py "$@"
else
  echo "Python 3 is required to start CARDS, but neither 'python3' nor 'python' was found on the PATH." >&2
  exit 1
fi
