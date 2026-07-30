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

# Docling processing daemon.
#
# Serves POST /parse, which takes the document bytes and returns the Markdown plus its chunk
# tree. That endpoint touches no shared filesystem, which is what makes this container usable
# from a JVM running elsewhere: the older /convert and /chunk endpoints pass absolute paths,
# and a caller's paths do not exist in here (on a Windows host they cannot be made to).
#
# Build, from the repo root:
#   mvn install -Pdocker -pl modules/documents/processing
# or, without Maven:
#   docker build -t iap/docling:latest modules/documents/processing
#
# Run, assuming a non-dockerized IAP install:
#   docker run --rm -p 127.0.0.1:18765:18765 --cpus=4 --memory=8g iap/docling:latest
#
# The 127.0.0.1 prefix on the published port is not optional decoration: the daemon has no
# authentication, so it is the only thing keeping the network off /parse and /shutdown. A bare
# "-p 18765:18765" binds every host interface and exposes both to anyone who can route here.
# docker-compose.yml publishes no port at all and lets IAP reach the daemon over the Compose
# network instead.

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
    # Where the baked weights live. download_models() saves into DOCLING_ARTIFACTS_PATH,
    # and Docling reads them back from that same layout at runtime.
    DOCLING_ARTIFACTS_PATH=/opt/models/docling \
    HF_HOME=/opt/models/hf

WORKDIR /app

RUN pip install --no-cache-dir \
        "docling>=2.115" \
        pypdf \
        psutil

# Bake the model weights into the image. Without this the first conversion tries to reach
# huggingface.co and fails on any host with no egress or with TLS interception — the container
# would start healthy and then fail every real request. Downloading at build time also makes
# startup deterministic and lets the runtime stay fully offline (see the ENV below).
RUN python -c "import os; from pathlib import Path; from docling.utils.model_downloader import download_models; download_models(output_dir=Path(os.environ['DOCLING_ARTIFACTS_PATH']))" \
    && find /opt/models -type d -name '.locks' -prune -exec rm -rf {} +

# Now that we have the models downloaded, we can turn HF offline
ENV TRANSFORMERS_OFFLINE=1 \
    HF_HUB_OFFLINE=1

COPY src/main/python/ /app/

# Unprivileged: the daemon only ever writes its own upload temp files.
RUN useradd --create-home --uid 10001 docling \
    && chown -R docling:docling /app /opt/models
USER docling

EXPOSE 18765

HEALTHCHECK --interval=30s --timeout=5s --start-period=180s --retries=3 \
    CMD curl -fsS http://127.0.0.1:18765/health || exit 1

# 0.0.0.0 is required here and cannot be tightened to 127.0.0.1: Docker forwards a published port
# to the container's eth0 address, so a daemon listening only on the container's loopback would
# refuse every forwarded connection. Confine it from the host side instead, by publishing to
# 127.0.0.1 (see the run example above). No volume is needed: /parse takes the document in the
# request body and returns the result, so the daemon accepts no paths.
ENTRYPOINT ["python", "docling_daemon.py", "--host", "0.0.0.0", "--port", "18765"]
