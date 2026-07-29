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

# Assembles the Docker build context in target/context:
# - mvnrepo/   the project's local deployment repository (.mvnrepo), holding every project
#              artifact, including every module's feature file
# - artifacts/ placeholder for the self-contained third-party artifact repository, filled in
#              by the slingfeature-maven-plugin `repository` goal when building the
#              production image (-Pproduction)
# - metadata/  build information supporting security audits of production deployments
#
# It also harvests every feature file produced by the current reactor build into
# target/generated-features/, where the `repository` goal picks them up, so that the
# production image embeds the artifacts of ALL the features found in the repository, not
# only the ones merged into the core aggregates. Optional features can then be enabled at
# runtime (e.g. DEV mode, demo features, the ADDITIONAL_SLING_FEATURES environment variable)
# without network access.

set -e

VERSION=$1
MODULE_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$MODULE_DIR/../.." && pwd)
TARGET="$MODULE_DIR/target"
CONTEXT="$TARGET/context"

rm -rf "$CONTEXT" "$TARGET/generated-features"
mkdir -p "$CONTEXT/mvnrepo" "$CONTEXT/artifacts" "$CONTEXT/metadata" "$TARGET/generated-features"

# The project artifact repository, used by the feature launcher to resolve the feature files
# themselves and every project-built bundle
cp -a "$ROOT_DIR/.mvnrepo/." "$CONTEXT/mvnrepo/"

# Harvest every feature file built by the current reactor. The processed (fully substituted)
# feature of each module is left by the slingfeature-maven-plugin in target/slingfeature-tmp/.
# Each harvested copy gets its id rewritten to this module's coordinates, with a classifier
# derived from the original feature id, since that is how the `repository` goal expects
# mid-build features to identify themselves. Only modules tracked by git are harvested, so
# that leftover target directories of modules from other branches are not picked up.
find "$ROOT_DIR" -path '*/target/slingfeature-tmp/*.json' -not -path "$MODULE_DIR/*" \
  | while read -r feature; do
      module_dir=${feature%/target/slingfeature-tmp/*}
      if git -C "$ROOT_DIR" ls-files --error-unmatch "$module_dir/pom.xml" > /dev/null 2>&1; then
        echo "$feature"
      fi
    done \
  | python3 "$MODULE_DIR/harvest_features.py" "io.uhndata.cards:cards-distribution-docker:$VERSION" \
      "$TARGET/generated-features"

# A placeholder making the (possibly empty) directory part of the image in every flavor
cat > "$CONTEXT/artifacts/README.txt" <<EOF
Third-party artifact repository for fully self-contained (production) images.
Empty in developer images, which resolve third-party artifacts from a mounted
~/.m2 repository or over the network on first start.
EOF

# The metadata layer: the dependency inventories and build information used for security
# audits of production deployments
cp "$ROOT_DIR/aggregated-frontend/src/main/frontend/yarn.lock" "$CONTEXT/metadata/yarn.lock"
cp "$ROOT_DIR/modules/homepage/src/main/media/SLING-INF/content/libs/cards/resources/media/default/logo_light_bg.png" \
  "$CONTEXT/metadata/logo.png"
cp "$ROOT_DIR/distribution/slingfeature/target/slingfeature-tmp/feature-core_tar.json" \
  "$CONTEXT/metadata/core_tar.json"
cp "$ROOT_DIR/distribution/slingfeature/target/slingfeature-tmp/feature-core_mongo.json" \
  "$CONTEXT/metadata/core_mongo.json"
cp "$ROOT_DIR/distribution/slingfeature/target/slingfeature-tmp/feature-core_rdb.json" \
  "$CONTEXT/metadata/core_rdb.json"
cat > "$CONTEXT/metadata/build-info.txt" <<EOF
version=$VERSION
commit=$(git -C "$ROOT_DIR" rev-parse HEAD 2>/dev/null || echo unknown)
built=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF

echo "Docker build context assembled in $CONTEXT"
