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

"""Post-processing cleanup for generated markdown output."""

import re

_EMPTY_HEADING = re.compile(r"^#{1,6}\s*_?\s*$")
_GARBAGE_LINE = re.compile(r"^(\|{2,}|_{2,}|\.{3,})\s*$")


def clean_markdown(md: str) -> str:
    """Collapse blank lines, remove empty headings, and strip decorative garbage lines."""
    if not md:
        return md or ""
    lines = [
        line
        for line in md.split("\n")
        if not _EMPTY_HEADING.match(line) and not _GARBAGE_LINE.match(line)
    ]
    return re.sub(r"\n{3,}", "\n\n", "\n".join(lines)).strip()
