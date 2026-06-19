/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.forms.internal.parse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts the Docling Python HTTP daemon when CARDS boots so PDF models stay warm between requests.
 * <p>
 * If a healthy daemon is already listening at {@code cards.docling.daemon.url}, this component does
 * not start a second process. Disable auto-start with {@code cards.docling.daemon.autostart=false}.
 * </p>
 *
 * @version $Id$
 */
@Component(immediate = true)
public class DoclingDaemonLauncher
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DoclingDaemonLauncher.class);

    private static final String DEFAULT_DAEMON_SCRIPT = "Utilities/Parsing/docling_daemon.py";

    private static final String DEFAULT_DAEMON_URL = "http://127.0.0.1:18765";

    private static final String DAEMON_URL_PROPERTY = "cards.docling.daemon.url";

    private static final String DAEMON_SCRIPT_PROPERTY = "cards.docling.daemon.script";

    private static final String DAEMON_PYTHON_PROPERTY = "cards.docling.daemon.python";

    private static final String PYTHON_COMMAND_PROPERTY = "cards.docling.python";

    private static final String AUTOSTART_PROPERTY = "cards.docling.daemon.autostart";

    private static final String DEFAULT_PYTHON_COMMAND = "python";

    private static final long HEALTH_TIMEOUT_SECONDS = 5L;

    private static final long STARTUP_TIMEOUT_SECONDS = 180L;

    private static final long STARTUP_POLL_MILLIS = 500L;

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 15L;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(HEALTH_TIMEOUT_SECONDS))
        .build();

    private Process daemonProcess;

    private boolean ownsProcess;

    /**
     * Start the daemon when auto-start is enabled and no healthy daemon is already running.
     */
    @Activate
    public void activate()
    {
        if (!isAutoStartEnabled()) {
            LOGGER.info("Docling daemon auto-start is disabled");
            return;
        }

        final String daemonUrl = resolveDaemonUrl();
        if (isDaemonHealthy(daemonUrl)) {
            LOGGER.info("Docling daemon already running at {}", daemonUrl);
            return;
        }

        try {
            this.daemonProcess = startDaemonProcess(daemonUrl);
            this.ownsProcess = true;
            if (waitForHealthyDaemon(daemonUrl)) {
                LOGGER.info("Docling daemon started at {}", daemonUrl);
            } else {
                LOGGER.error("Docling daemon failed to become healthy at {}", daemonUrl);
                stopOwnedProcess();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to start Docling daemon: {}", e.getMessage());
            stopOwnedProcess();
        }
    }

    /**
     * Stop the daemon process started by this component.
     */
    @Deactivate
    public void deactivate()
    {
        if (!this.ownsProcess) {
            return;
        }

        final String daemonUrl = resolveDaemonUrl();
        requestDaemonShutdown(daemonUrl);
        stopOwnedProcess();
    }

    private Process startDaemonProcess(final String daemonUrl) throws IOException
    {
        final URI uri = URI.create(daemonUrl);
        final int port = uri.getPort() > 0 ? uri.getPort() : 18765;
        final String host = StringUtils.defaultIfBlank(uri.getHost(), "127.0.0.1");
        final String pythonCmd = resolvePythonCommand();
        final String scriptPath = resolveDaemonScriptPath();

        LOGGER.info("Starting Docling daemon with '{}' '{}' --host {} --port {}", pythonCmd, scriptPath, host, port);
        return new ProcessBuilder(
            pythonCmd,
            scriptPath,
            "--host", host,
            "--port", String.valueOf(port))
            .redirectErrorStream(true)
            .start();
    }

    private boolean waitForHealthyDaemon(final String daemonUrl)
    {
        final long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(STARTUP_TIMEOUT_SECONDS);
        while (System.currentTimeMillis() < deadline) {
            if (isDaemonHealthy(daemonUrl)) {
                return true;
            }
            if (this.daemonProcess != null && !this.daemonProcess.isAlive()) {
                LOGGER.error("Docling daemon process exited during startup with code {}",
                    this.daemonProcess.exitValue());
                return false;
            }
            sleepQuietly(STARTUP_POLL_MILLIS);
        }
        return false;
    }

    private void requestDaemonShutdown(final String daemonUrl)
    {
        try {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(daemonUrl + "/shutdown"))
                .timeout(Duration.ofSeconds(HEALTH_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            LOGGER.debug("Docling daemon shutdown request failed: {}", e.getMessage());
        }
    }

    private void stopOwnedProcess()
    {
        if (this.daemonProcess == null) {
            return;
        }

        try {
            if (this.daemonProcess.isAlive()) {
                final boolean finished = this.daemonProcess.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    this.daemonProcess.destroyForcibly();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.daemonProcess.destroyForcibly();
        } finally {
            this.daemonProcess = null;
            this.ownsProcess = false;
        }
    }

    private static boolean isDaemonHealthy(final String daemonUrl)
    {
        try {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(daemonUrl + "/health"))
                .timeout(Duration.ofSeconds(HEALTH_TIMEOUT_SECONDS))
                .GET()
                .build();
            final HttpResponse<String> response =
                HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("\"ready\": true");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isAutoStartEnabled()
    {
        final String configured = System.getProperty(AUTOSTART_PROPERTY);
        if (StringUtils.isBlank(configured)) {
            return true;
        }
        return Boolean.parseBoolean(configured);
    }

    private static String resolveDaemonUrl()
    {
        final String configured = System.getProperty(DAEMON_URL_PROPERTY);
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        return DEFAULT_DAEMON_URL;
    }

    private static String resolveDaemonScriptPath()
    {
        final String configured = System.getProperty(DAEMON_SCRIPT_PROPERTY);
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        return DEFAULT_DAEMON_SCRIPT;
    }

    private static String resolvePythonCommand()
    {
        final String daemonPython = System.getProperty(DAEMON_PYTHON_PROPERTY);
        if (StringUtils.isNotBlank(daemonPython)) {
            return daemonPython;
        }
        final String configured = System.getProperty(PYTHON_COMMAND_PROPERTY);
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        return DEFAULT_PYTHON_COMMAND;
    }

    private static void sleepQuietly(final long millis)
    {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
