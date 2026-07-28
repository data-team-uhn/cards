#!/usr/bin/env python3
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

"""Harvest feature files for the self-contained artifact repository.

Reads a list of feature file paths on standard input (one per line), and copies each into the
output directory with its feature id rewritten to the given project coordinates plus a
classifier derived from the feature's original id. This is the naming the
slingfeature-maven-plugin expects for features generated during the build, letting its
`repository` goal embed the artifacts of every feature produced by the reactor.

Usage: find ... | harvest_features.py <groupId:artifactId:version> <outputDirectory>
"""

import json
import os
import sys

group_id, artifact_id, version = sys.argv[1].split(":")
output_directory = sys.argv[2]

for line in sys.stdin:
    path = line.strip()
    if not path:
        continue
    with open(path, "r") as feature_file:
        feature = json.load(feature_file)
    # Derive a unique, readable classifier from the original feature id:
    # groupId:artifactId:type[:classifier]:version -> artifactId[-classifier]
    original_id = feature.get("id", "")
    parts = original_id.split(":")
    if len(parts) >= 4:
        classifier = parts[1] + ("-" + parts[3] if len(parts) >= 5 else "")
    else:
        # Fall back to a name derived from the file's location
        classifier = os.path.basename(os.path.dirname(os.path.dirname(os.path.dirname(path))))
    classifier = classifier.replace(".", "-")
    # Rewrite the id to the harvesting project's coordinates, keeping everything else
    rewritten = {"id": "%s:%s:slingosgifeature:%s:%s" % (group_id, artifact_id, classifier, version)}
    rewritten.update((key, value) for key, value in feature.items() if key != "id")
    with open(os.path.join(output_directory, classifier + ".json"), "w") as output:
        json.dump(rewritten, output, indent=2)
    print("Harvested %s as %s" % (original_id or path, classifier))
