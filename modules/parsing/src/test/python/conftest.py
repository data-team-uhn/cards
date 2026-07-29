# Copyright 2026 DATA @ UHN. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""Shared pytest setup for the parsing tests.

The modules under test (``markdown_cleanup``, ``toc_and_appendix_detection``,
``chunker``, ``docling_batch_sizing``) live in ``src/main/python`` and are imported by
their bare module name. This file sits in ``src/test/python``. Put the source directory
on ``sys.path`` so the tests run the same way whether launched by Maven or by ``pytest``
from anywhere else.
"""

import sys
from pathlib import Path

# .../src/test/python/conftest.py -> parents[2] is .../src
PYTHON_SOURCE_ROOT = Path(__file__).resolve().parents[2] / "main" / "python"

if str(PYTHON_SOURCE_ROOT) not in sys.path:
    sys.path.insert(0, str(PYTHON_SOURCE_ROOT))
