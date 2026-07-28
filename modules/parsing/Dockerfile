# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# Docling parsing daemon.
#
# Serves POST /parse, which takes the document bytes and returns the Markdown plus its chunk
# tree. That endpoint touches no shared filesystem, which is what makes this container usable
# from a JVM running elsewhere: the older /convert and /chunk endpoints pass absolute paths,
# and a caller's paths do not exist in here (on a Windows host they cannot be made to).
#
# Build:
#   docker build -t cards-docling -f modules/parsing/Dockerfile modules/parsing
# Run:
#   docker run --rm -p 18765:18765 --cpus=4 --memory=8g \
#     -e DOCLING_AUTH_TOKEN=change-me cards-docling

FROM python:3.13-slim AS base

# Docling's PDF pipeline pulls in native imaging/OpenMP libraries that the slim image lacks.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libgl1 \
        libglib2.0-0 \
        libgomp1 \
        curl \
    && rm -rf /var/lib/apt/lists/*

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    # One thread per process: the daemon's own ProcessPoolExecutor owns the parallelism, and
    # letting each worker also thread out oversubscribes the container's CPU quota.
    OMP_NUM_THREADS=1 \
    DOCLING_NUM_THREADS=1 \
    # Keep the model cache on a path that exists at build time so the weights baked in below
    # are the ones found at runtime.
    HF_HOME=/opt/models \
    TRANSFORMERS_OFFLINE=1 \
    HF_HUB_OFFLINE=1

WORKDIR /app

RUN pip install --no-cache-dir \
        "docling>=2.115" \
        pypdf \
        psutil \
        tiktoken

# Bake the model weights into the image. Without this the first conversion tries to reach
# huggingface.co and fails on any host with no egress or with TLS interception — the container
# would start healthy and then fail every real request. Downloading at build time also makes
# startup deterministic and lets the runtime stay fully offline (HF_HUB_OFFLINE above).
RUN python -c "from docling.utils.model_downloader import download_models; download_models()" \
    && find /opt/models -type d -name '.locks' -prune -exec rm -rf {} +

COPY src/main/python/ /app/

# Unprivileged: the daemon only ever writes its own upload temp files.
RUN useradd --create-home --uid 10001 docling \
    && chown -R docling:docling /app /opt/models
USER docling

EXPOSE 18765

# /health needs no auth, so it works as a container probe even with a token configured.
HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=3 \
    CMD curl -fsS http://127.0.0.1:18765/health || exit 1

# 0.0.0.0 is required to be reachable from outside the container. That is safe only because
# the endpoints are token-protected — set DOCLING_AUTH_TOKEN. --parse-output-dir is
# deliberately omitted: /chunk needs a filesystem shared with the caller, so it stays disabled
# here and /parse is used instead.
ENTRYPOINT ["python", "docling_daemon.py", "--host", "0.0.0.0", "--port", "18765"]
