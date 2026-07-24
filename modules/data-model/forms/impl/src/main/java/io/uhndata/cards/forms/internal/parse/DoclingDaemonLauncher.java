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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * <p>
 * The {@code @Activate} method returns immediately; stdout draining and health polling run on
 * background threads owned by an internal {@link ExecutorService} so the OSGi SCR thread is never
 * blocked during model loading (which can take several minutes on a cold host).
 * </p>
 *
 * @version $Id$
 */
@Component(immediate = true)
public class DoclingDaemonLauncher
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DoclingDaemonLauncher.class);

    private static final String DEFAULT_DAEMON_SCRIPT = "modules/parsing/src/main/python/docling_daemon.py";

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

    /** HTTP client used for /health and /shutdown calls; created in activate, closed in deactivate. */
    private HttpClient httpClient;

    /** The daemon process started by this component, if any. Volatile for cross-thread visibility. */
    private volatile Process daemonProcess;

    /** True iff this component started the daemon process and is responsible for stopping it. */
    private volatile boolean ownsProcess;

    /**
     * Set to true at the start of deactivate() so background tasks can detect shutdown
     * and stop polling without needing to wait for the executor to deliver an interrupt.
     */
    private volatile boolean deactivated;

    /** ExecutorService that owns the stdout-drainer and startup-poller background tasks. */
    private ExecutorService backgroundExecutor;

    /**
     * Start the daemon when auto-start is enabled and no healthy daemon is already running.
     * Returns immediately; stdout draining and health polling run on background threads.
     */
    @Activate
    public void activate()
    {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HEALTH_TIMEOUT_SECONDS))
            .build();
        this.backgroundExecutor = Executors.newCachedThreadPool(runnable -> {
            final Thread thread = new Thread(runnable, "docling-daemon-launcher");
            thread.setDaemon(true);
            return thread;
        });

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
            drainProcessStdout(this.daemonProcess);
            scheduleDaemonStartupCheck(daemonUrl);
        } catch (IOException e) {
            LOGGER.error("Failed to start Docling daemon: {}", e.getMessage());
        }
    }

    /**
     * Stop the daemon process started by this component and release all resources.
     */
    @Deactivate
    public void deactivate()
    {
        this.deactivated = true;
        if (this.backgroundExecutor != null) {
            this.backgroundExecutor.shutdownNow();
        }
        if (!this.ownsProcess) {
            closeHttpClient();
            return;
        }

        final String daemonUrl = resolveDaemonUrl();
        requestDaemonShutdown(daemonUrl);
        stopOwnedProcess();
        closeHttpClient();
    }

    private Process startDaemonProcess(final String daemonUrl) throws IOException
    {
        final URI uri = URI.create(daemonUrl);
        final int port = uri.getPort() > 0 ? uri.getPort() : 18765;
        final String host = StringUtils.defaultIfBlank(uri.getHost(), "127.0.0.1");
        final String pythonCmd = resolvePythonCommand();
        final String scriptPath = resolveDaemonScriptPath();

        LOGGER.info("Starting Docling daemon with '{}' '{}' --host {} --port {} --parse-output-dir {}",
            pythonCmd, scriptPath, host, port, ParsedMarkdownStore.resolveBaseOutputDir());
        return new ProcessBuilder(
            pythonCmd,
            scriptPath,
            "--host", host,
            "--port", String.valueOf(port),
            "--parse-output-dir", ParsedMarkdownStore.resolveBaseOutputDir().toString())
            .redirectErrorStream(true)
            .start();
    }

    /**
     * Submit a background task that continuously reads and logs the daemon process's stdout.
     * This prevents the OS pipe buffer from filling up and blocking the daemon during model loading.
     *
     * @param process the daemon process whose stdout is drained
     */
    private void drainProcessStdout(final Process process)
    {
        this.backgroundExecutor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    LOGGER.debug("Docling daemon: {}", line);
                    line = reader.readLine();
                }
            } catch (IOException e) {
                LOGGER.debug("Docling daemon stdout closed: {}", e.getMessage());
            }
        });
    }

    /**
     * Submit a background task that polls the daemon's /health endpoint until it becomes ready,
     * the process exits, the timeout is reached, or deactivation is signalled.
     *
     * @param daemonUrl the base URL of the daemon
     */
    private void scheduleDaemonStartupCheck(final String daemonUrl)
    {
        this.backgroundExecutor.submit(() -> {
            final long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(STARTUP_TIMEOUT_SECONDS);
            while (!this.deactivated && System.currentTimeMillis() < deadline) {
                if (isDaemonHealthy(daemonUrl)) {
                    LOGGER.info("Docling daemon started at {}", daemonUrl);
                    return;
                }
                final Process process = this.daemonProcess;
                if (process != null && !process.isAlive()) {
                    LOGGER.error("Docling daemon process exited during startup with code {}",
                        process.exitValue());
                    return;
                }
                sleepQuietly(STARTUP_POLL_MILLIS);
            }
            if (!this.deactivated) {
                LOGGER.error("Docling daemon failed to become healthy within {} seconds at {}",
                    STARTUP_TIMEOUT_SECONDS, daemonUrl);
            }
        });
    }

    private void requestDaemonShutdown(final String daemonUrl)
    {
        final HttpClient client = this.httpClient;
        if (client == null) {
            return;
        }
        try {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(daemonUrl + "/shutdown"))
                .timeout(Duration.ofSeconds(HEALTH_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
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
                    this.daemonProcess.descendants().forEach(ProcessHandle::destroyForcibly);
                    this.daemonProcess.destroyForcibly();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.daemonProcess.descendants().forEach(ProcessHandle::destroyForcibly);
            this.daemonProcess.destroyForcibly();
        } finally {
            this.daemonProcess = null;
            this.ownsProcess = false;
        }
    }

    private void closeHttpClient()
    {
        if (this.httpClient != null) {
            try {
                this.httpClient.close();
            } catch (RuntimeException e) {
                LOGGER.debug("Error closing Docling HTTP client: {}", e.getMessage());
            } finally {
                this.httpClient = null;
            }
        }
    }

    private boolean isDaemonHealthy(final String daemonUrl)
    {
        final HttpClient client = this.httpClient;
        if (client == null) {
            return false;
        }
        try {
            final HttpRequest request = HttpRequest.newBuilder(URI.create(daemonUrl + "/health"))
                .timeout(Duration.ofSeconds(HEALTH_TIMEOUT_SECONDS))
                .GET()
                .build();
            final HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());
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
