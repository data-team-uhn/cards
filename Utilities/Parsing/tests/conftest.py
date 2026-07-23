#
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.
#

"""Shared pytest setup for the parsing tests.

The modules under test (``markdown_cleanup``, ``toc_and_appendix_detection``,
``chunker``, ``docling_batch_sizing``) live one directory up, beside this ``tests/``
folder, and are imported by their bare module name. Put that directory on ``sys.path``
so the tests run the same way whether launched by Maven, by ``pytest`` from the
``Parsing`` folder, or from anywhere else.
"""

import sys
from pathlib import Path

PARSING_ROOT = Path(__file__).resolve().parent.parent

if str(PARSING_ROOT) not in sys.path:
    sys.path.insert(0, str(PARSING_ROOT))
