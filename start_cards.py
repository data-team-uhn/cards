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

"""Starts CARDS via the Apache Sling feature launcher.

This is the single, cross-platform implementation of the start process; `start_cards.sh`
(Linux, macOS, WSL) and `start_cards.bat` (Windows) are thin wrappers around it.
Keep platform-specific behavior here, not in the wrappers.
"""

import sys

# Guard before the Python-3-only imports below, so that an outdated interpreter (e.g. a
# `python` that is Python 2) produces a clear message instead of a raw ImportError.
if sys.version_info < (3, 6):
    sys.exit('Python 3.6 or later is required to start CARDS, but this is Python %d.%d.'
             % (sys.version_info[0], sys.version_info[1]))

import base64
import filecmp
import hashlib
import os
import platform
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

from pathlib import Path

BIND_TESTS = 2
BIND_TEST_SPACING = 30

TERMINAL_NOCOLOR = '\033[0m'
TERMINAL_RED = '\033[0;31m'
TERMINAL_GREEN = '\033[0;32m'
TERMINAL_YELLOW = '\033[0;33m'

JAVA_DEBUGGING_FLAGS = ('-Xdebug -Xnoagent -Djava.compiler=NONE'
                        ' -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005')

GROUP = 'io.uhndata.cards'

CLOUD_IAM_KEYCLOAK_ENDPOINT = 'https://lemur-15.cloud-iam.com/auth/realms/uhn-cards-test/protocol/saml'

ROOT = Path(__file__).resolve().parent

IS_WINDOWS = os.name == 'nt'
IS_MACOS = platform.system() == 'Darwin'
# Matches both WSL1 ("...-Microsoft") and WSL2 ("...-microsoft-standard") kernel releases
IS_WSL = 'microsoft' in platform.release().lower()

# Optional feature sets, keyed by the flag that enables them. Each entry lists
# `artifact` / `artifact/classifier` specs, added to the launcher as one `-f` argument.
OPTIONAL_FEATURES = {
    '--clarity': ['cards-clarity-integration'],
    '--dev': ['cards/composum'],
    '--demo': ['cards-modules-demo-banner', 'cards-modules-upgrade-marker', 'cards-dataentry/forms_demo'],
    '--locking': ['cards-locking'],
    '--slack_notifications': ['cards-slack-notifications'],
    '--keycloak_demo': ['cards-keycloakdemo-saml-support'],
    '--cloud-iam_demo': ['cards-cloud-iam-demo-saml-support'],
    '--uhn_ad_fs': ['cards-uhn-saml-support'],
    '--test': ['cards-modules-test-forms', 'cards-email-notifications', 'cards-patient-portal',
               'cards-clinician-dashboard', 'cards-locking', 'cards-jwt-token-authentication',
               'cards-chromosome', 'cards-identifier', 'cards-contact-info', 'cards-dicom',
               'cards-selectable-area', 'cards-vocabularies', 'cards-pedigree'],
}

HELP = """Usage: ./start_cards.sh [OPTIONS] [-- LAUNCHER_ARGS]     (Linux / macOS / WSL)
       start_cards.bat [OPTIONS] [-- LAUNCHER_ARGS]      (Windows)

Starts CARDS via the Apache Sling feature launcher.

Options:
  -p, --port <port>              Port for CARDS to bind to (default: 8080).
      --permissions <scheme>     Permissions scheme to run with: `open` (the
                                 default), `trusted`, or `ownership`.
  -P, --project <project[,...]>  Launch one or more CARDS *projects*. Each
                                 `<name>` resolves to the `cards4<name>`
                                 artifact and its dependency features (the
                                 `cards4` prefix is optional).
      --mongo                    Use a MongoDB document store for the repository
                                 instead of the default file-based (TAR/segment)
                                 store. Requires a running MongoDB instance.
      --debug                    Enable Java remote debugging (JDWP) on port
                                 `5005`. Startup pauses until a debugger
                                 attaches - connect with `jdb -attach 5005` (or
                                 your IDE).
      --dev                      Include the Composum content browser, at
                                 `/bin/browser.html`.
      --test                     Additionally load the test questionnaires and
                                 the features they exercise.
      --demo                     Include the demo warning banner and demo forms.
      --clarity                  Enable the Clarity integration.
      --locking                  Enable the record locking / sign-off abilities.
      --slack_notifications      Enable Slack notifications.
      --saml                     Enable SAML authentication, and run a header
                                 rewriting proxy next to CARDS (on port 9090, or
                                 8080 with `--cloud-iam_demo`) for SAML plus
                                 local Sling login. Implies
                                 `--permissions trusted`.
      --keycloak_demo            Use a local Keycloak demo instance as the SAML
                                 identity provider.
      --cloud-iam_demo           Use the Cloud-IAM.com demo as the SAML identity
                                 provider.
      --uhn_ad_fs                Use the UHN AD FS as the SAML identity
                                 provider.
  -h, --help                     Show this help message and exit.

Notes:
  - Any argument not listed above is passed through to the Sling feature
    launcher. For the arguments it accepts, see the Apache Sling Feature
    Launcher documentation:
    https://github.com/apache/sling-org-apache-sling-feature-launcher"""


def banner(color, *lines):
    width = max(len(line) for line in lines)
    horizontal = color + '*' * (width + 8) + TERMINAL_NOCOLOR
    spacer = color + '*' + ' ' * (width + 6) + '*' + TERMINAL_NOCOLOR
    print(horizontal)
    print(spacer)
    for line in lines:
        print(color + '*   ' + line.ljust(width) + '   *' + TERMINAL_NOCOLOR)
    print(spacer)
    print(horizontal)


def handle_cards_java_fail(bind_port):
    banner(TERMINAL_RED, 'The CARDS Java process has failed at port %d' % bind_port)
    sys.exit(1)


def handle_tcp_bind_fail(bind_port):
    banner(TERMINAL_RED, 'Unable to bind to TCP port %d' % bind_port)
    sys.exit(1)


def get_cards_version():
    with open(str(ROOT / 'pom.xml'), encoding='utf-8') as pom:
        for line in pom:
            if '<version>' in line:
                return line.split('>', 1)[1].split('<', 1)[0]
    sys.exit('Unable to determine CARDS_VERSION from pom.xml')


def feature_url(spec, cards_version):
    """Turn an `artifact` or `artifact/classifier` spec into a launcher `mvn:` feature URL."""
    artifact, _, classifier = spec.partition('/')
    url = 'mvn:%s/%s/%s/slingosgifeature' % (GROUP, artifact, cards_version)
    return '%s/%s' % (url, classifier) if classifier else url


def psutil_usable():
    """The robust bind test needs psutil, which does not work correctly on WSL and macOS."""
    if IS_WSL or IS_MACOS:
        return False
    try:
        import psutil  # noqa: F401 pylint: disable=unused-import
        return True
    except ImportError:
        return False


def port_available(port):
    """Simple bind test: check that the port is available right now.

    Probing the loopback interface is enough to detect a conflicting local server,
    without ever opening a socket reachable from other machines.
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        # On WSL and Windows, SO_REUSEADDR would allow rebinding a port that is actually in use
        if not (IS_WSL or IS_WINDOWS):
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(('127.0.0.1', port))
        sock.listen()
        return True
    except OSError:
        return False
    finally:
        sock.close()


def is_listening(port, pid):
    """Robust bind test: check that the process (or a descendant) is listening on the port.

    Descendants matter on Windows, where launcher.bat runs Java as a child process;
    on Linux the shell launcher exec()s Java, keeping the same pid.
    """
    import psutil
    try:
        pids = {pid} | {child.pid for child in psutil.Process(pid).children(recursive=True)}
    except psutil.NoSuchProcess:
        return False
    for conn in psutil.net_connections(kind='tcp'):
        if conn.status == psutil.CONN_LISTEN and conn.pid in pids and conn.laddr.port == port:
            return True
    return False


def get_error_log_last_modified():
    try:
        return os.path.getmtime(str(ROOT / '.cards-data' / 'logs' / 'error.log'))
    except OSError:
        return 0.0


def require_value(argv, i, name):
    if i >= len(argv):
        sys.exit('Missing value for the %s option' % name)
    return argv[i]


def has_test_run_mode(argv):
    """Check whether the launcher arguments request the `test` run mode."""
    for i, arg in enumerate(argv):
        if arg.startswith('-D') and len(arg) > 2:
            prop = arg[2:]
        elif arg == '-D' and i + 1 < len(argv):
            prop = argv[i + 1]
        else:
            continue
        key, _, value = prop.partition('=')
        if key == 'sling.run.modes' and 'test' in value.split(','):
            return True
    return False


def parse_args(argv, cards_version):
    options = {
        'bind_port': 8080,
        'permissions': os.environ.get('PERMISSIONS', ''),
        'permissions_explicit': False,
        'projects': [],
        'storage': 'tar',
        'debug': False,
        'test': has_test_run_mode(argv),
        'saml': False,
        'cloud_iam_demo': False,
        'feature_args': [],
        'passthrough': [],
    }
    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg in ('-h', '--help'):
            print(HELP)
            sys.exit(0)
        elif arg in ('-p', '--port'):
            i += 1
            value = require_value(argv, i, arg)
            try:
                options['bind_port'] = int(value)
            except ValueError:
                sys.exit('Invalid port: %s' % value)
        elif arg == '--permissions':
            i += 1
            options['permissions'] = require_value(argv, i, arg)
            options['permissions_explicit'] = True
        elif arg in ('-P', '--project'):
            i += 1
            options['projects'] += require_value(argv, i, arg).split(',')
        elif arg == '--mongo':
            options['storage'] = 'mongo'
        elif arg == '--debug':
            options['debug'] = True
        elif arg == '--saml':
            options['saml'] = True
            options['feature_args'] += [
                '-f', feature_url('cards-saml-support/base', cards_version),
                '-C', 'io.dropwizard.metrics:metrics-core:ALL',
                '-f', feature_url('cards-fetch-requires-saml-login', cards_version)]
        elif arg in OPTIONAL_FEATURES:
            options['test'] = options['test'] or arg == '--test'
            options['cloud_iam_demo'] = options['cloud_iam_demo'] or arg == '--cloud-iam_demo'
            options['feature_args'] += [
                '-f', ','.join(feature_url(spec, cards_version) for spec in OPTIONAL_FEATURES[arg])]
        elif arg == '--':
            options['passthrough'] += argv[i + 1:]
            break
        else:
            options['passthrough'].append(arg)
        i += 1
    # SAML authentication only makes sense with a permissions scheme that restricts access
    if options['saml'] and not options['permissions_explicit']:
        options['permissions'] = 'trusted'
    return options


def get_dependency_features(env, features_file):
    helper = str(ROOT / 'distribution' / 'docker' / 'get_project_dependency_features.py')
    result = subprocess.run([sys.executable, helper, str(features_file)], env=env,
                            stdout=subprocess.PIPE, universal_newlines=True, check=False)
    return [feature for feature in result.stdout.strip().split(',') if feature]


def feature_resolution_env(cards_version, project_version, permissions, project_name):
    return dict(os.environ, CARDS_VERSION=cards_version, PROJECT_NAME=project_name,
                PROJECT_VERSION=project_version, PERMISSIONS=permissions)


def resolve_project_features(projects, cards_version, project_version, permissions):
    mvn = shutil.which('mvn')
    if mvn is None:
        sys.exit('Maven (mvn) is required to resolve --project features, but was not found on the PATH')
    features = []
    for project in projects:
        # Support both "cards4project" and just "project"
        name = project[len('cards4'):] if project.startswith('cards4') else project
        project = 'cards4' + name
        env = feature_resolution_env(cards_version, project_version, permissions, project)
        features.append('mvn:%s/%s/%s/slingosgifeature' % (GROUP, project, project_version))
        features += get_dependency_features(env, ROOT / 'distribution' / 'docker' / 'sling-features.json')
        dependency_dir = tempfile.mkdtemp()
        try:
            subprocess.run([mvn, '--quiet', '--non-recursive', 'dependency:copy',
                            '-Dartifact=%s:%s-docker-packaging:%s:dependencies'
                            % (GROUP, name, project_version),
                            '-DoutputDirectory=%s' % dependency_dir],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
            dependencies_file = os.path.join(dependency_dir,
                                             '%s-docker-packaging-%s.dependencies' % (name, project_version))
            if os.path.isfile(dependencies_file):
                features += get_dependency_features(env, dependencies_file)
        finally:
            shutil.rmtree(dependency_dir, ignore_errors=True)
    return features


def http_ok(url, auth=None):
    request = urllib.request.Request(url)
    if auth:
        credentials = base64.b64encode(('%s:%s' % auth).encode('utf-8')).decode('ascii')
        request.add_header('Authorization', 'Basic ' + credentials)
    try:
        with urllib.request.urlopen(request, timeout=5):
            return True
    except (urllib.error.URLError, OSError):
        return False


def check_smtps_configuration(launcher_args):
    """Check the configuration that sending emails over SMTPS depends on."""
    if not any('mvn:%s/cards-email-notifications/' % GROUP in arg for arg in launcher_args):
        return
    if not os.environ.get('SLING_COMMONS_CRYPTO_PASSWORD'):
        banner(TERMINAL_YELLOW,
               'The SLING_COMMONS_CRYPTO_PASSWORD enviroment variable is missing.',
               "Using the default value of 'password'.")
        os.environ['SLING_COMMONS_CRYPTO_PASSWORD'] = 'password'
    mailcap = Path.home() / '.mailcap'
    reference_mailcap = ROOT / 'distribution' / 'docker' / 'mailcap'
    if not mailcap.is_file():
        banner(TERMINAL_RED,
               'The file ~/.mailcap is missing. Exiting.',
               'You can create ~/.mailcap by running: cp distribution/docker/mailcap ~/.mailcap')
        sys.exit(1)
    if not filecmp.cmp(str(mailcap), str(reference_mailcap), shallow=False):
        banner(TERMINAL_YELLOW,
               'Warning: The file ~/.mailcap differs from what is expected.',
               'Sending emails may not work properly!')


def verify_cloud_iam_keystore():
    """Check samlKeystore.p12 against the checksum recorded for the Cloud-IAM.com demo."""
    checksum_file = (ROOT / 'Utilities' / 'Administration' / 'SAML'
                     / 'cloud-iam_demo_samlKeystore.p12.sha256sum')
    try:
        with open(str(checksum_file), encoding='utf-8') as checksums:
            expected, _, keystore_name = checksums.read().strip().partition('  ')
        keystore = checksum_file.parent / keystore_name.strip()
        with open(str(keystore), 'rb') as contents:
            actual = hashlib.sha256(contents.read()).hexdigest()
    except OSError:
        actual = None
    if actual == expected:
        banner(TERMINAL_GREEN, 'Setup Cloud-IAM.com Demo as a SAML IdP for CARDS')
    else:
        banner(TERMINAL_YELLOW,
               'Invalid Sha256 hash for samlKeystore.p12 for Cloud-IAM.com demo.',
               'SAML authentication via Cloud-IAM.com IdP may not work.')


def install_hancestro(cards_url):
    if not os.environ.get('BIOPORTAL_APIKEY'):
        banner(TERMINAL_RED, 'BIOPORTAL_APIKEY not specified, skipping HANCESTRO installation.')
        return
    admin_password = os.environ.get('ADMIN_PASSWORD', 'admin')
    if http_ok(cards_url + '/Vocabularies/HANCESTRO.json', auth=('admin', admin_password)):
        print('HANCESTRO already installed')
        return
    installer = str(ROOT / 'Utilities' / 'Administration' / 'install_vocabulary.py')
    result = subprocess.run([sys.executable, installer, '--bioportal_id', 'HANCESTRO'], check=False)
    if result.returncode == 0:
        banner(TERMINAL_GREEN, 'Installed HANCESTRO')
    else:
        banner(TERMINAL_RED, 'HANCESTRO install failed')


def start_headermod_proxy(listen_port, bind_port, keycloak_endpoint):
    """Start the proxy serving SAML and local Sling login on a single port."""
    node = shutil.which('nodejs') or shutil.which('node')
    if node is None:
        banner(TERMINAL_RED, 'Node.js is required for SAML authentication, but was not found on the PATH')
        sys.exit(1)
    command = [node, str(ROOT / 'Utilities' / 'Development' / 'keycloak_headermod_http_proxy.js'),
               '--listen-port=%d' % listen_port, '--cards-port=%d' % bind_port]
    if keycloak_endpoint:
        command.append('--keycloak-endpoint=%s' % keycloak_endpoint)
    return subprocess.Popen(command, cwd=str(ROOT))


def stop(process, interrupted=False):
    """Stop a launched process; `interrupted` means it already received the console Ctrl+C."""
    if process is None or process.poll() is not None:
        return
    if IS_WINDOWS:
        if interrupted:
            # Ctrl+C was delivered to the whole console process group, Java included, so
            # let it shut the repository down cleanly before resorting to a force kill.
            try:
                process.wait(timeout=30)
                return
            except subprocess.TimeoutExpired:
                pass
        # launcher.bat runs Java as a child process, so take down the whole process tree
        subprocess.run(['taskkill', '/T', '/F', '/PID', str(process.pid)],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
    else:
        process.terminate()
    process.wait()


def monitor_startup(process, cards_url, bind_port, use_psutil, debug, error_log_time_origin):
    if debug:
        banner(TERMINAL_YELLOW,
               'Please connect JDB to localhost:5005 to continue with startup.',
               'jdb -attach 5005')
        # As soon as we see CARDS writing to .cards-data/logs/error.log, we
        # can conclude that JDB has attached to the Java process.
        while get_error_log_last_modified() <= error_log_time_origin:
            time.sleep(5)
            print('Waiting for JDB attachment...')

    # Check to see if CARDS was able to bind to the TCP port
    # This is the more robust test that works only if psutil is usable
    if use_psutil:
        for bind_test in range(BIND_TESTS + 1):
            if bind_test == BIND_TESTS:
                stop(process)
                handle_tcp_bind_fail(bind_port)
            time.sleep(BIND_TEST_SPACING)
            if is_listening(bind_port, process.pid):
                break
            # If the CARDS Java process has terminated, stop this script altogether
            if process.poll() is not None:
                handle_cards_java_fail(bind_port)
        banner(TERMINAL_GREEN, 'CARDS Socket BIND: OK')
    else:
        banner(TERMINAL_YELLOW, 'CARDS Socket BIND: OK - used suboptimal bind test')

    while True:
        print('Waiting for CARDS to start')
        # If the CARDS Java process has terminated, stop this script altogether
        if process.poll() is not None:
            handle_cards_java_fail(bind_port)
        if http_ok(cards_url + '/system/sling/info.sessionInfo.json'):
            break
        time.sleep(5)


def build_launcher_args(options, cards_version, project_version):
    # Allow referring to the current version with the literal token `VERSION` in launcher arguments
    launcher_args = [arg.replace('VERSION', cards_version) for arg in options['passthrough']]
    launcher_args += options['feature_args']
    if options['projects']:
        features = resolve_project_features(options['projects'], cards_version, project_version,
                                            options['permissions'])
    else:
        features = get_dependency_features(
            feature_resolution_env(cards_version, project_version, options['permissions'], ''),
            ROOT / 'distribution' / 'docker' / 'sling-features.json')
    if features:
        launcher_args += ['-f', ','.join(features)]
    return launcher_args


def main(argv):
    if IS_WINDOWS:
        os.system('')  # enables ANSI color code processing in the Windows console

    cards_version = get_cards_version()
    project_version = os.environ.get('PROJECT_VERSION') or cards_version

    options = parse_args(argv, cards_version)
    print('CARDS_VERSION', cards_version)
    bind_port = options['bind_port']
    cards_url = 'http://localhost:%d' % bind_port
    # install_vocabulary.py reads the address of the instance from the environment
    os.environ['CARDS_URL'] = cards_url

    use_psutil = psutil_usable()
    # Without psutil, simply check that the port is available now,
    # and therefore will likely be available in the very near future
    if not use_psutil and not port_available(bind_port):
        handle_tcp_bind_fail(bind_port)

    launcher_args = build_launcher_args(options, cards_version, project_version)
    check_smtps_configuration(launcher_args)

    headermod_proxy_port = None
    if options['saml']:
        headermod_proxy_port = 8080 if options['cloud_iam_demo'] else 9090
        if headermod_proxy_port == bind_port:
            banner(TERMINAL_RED,
                   'Error: CARDS and keycloak_headermod_http_proxy.js cannot be bound to the same port.')
            sys.exit(1)

    launcher = (ROOT / 'distribution' / 'target' / 'dependency' / 'org.apache.sling.feature.launcher'
                / 'bin' / ('launcher.bat' if IS_WINDOWS else 'launcher'))
    if not launcher.is_file():
        sys.exit('The Sling feature launcher is missing at %s - run `mvn install` first' % launcher)

    java_opts = '-Djdk.xml.entityExpansionLimit=0 -Dorg.osgi.service.http.port=%d' % bind_port
    if options['debug']:
        java_opts = JAVA_DEBUGGING_FLAGS + ' ' + java_opts
    env = dict(os.environ, JAVA_OPTS=java_opts)

    # Path.as_uri() produces the platform-correct form (file:///home/... or file:///C:/...)
    repository_urls = ','.join([
        (ROOT / '.mvnrepo').as_uri(),
        (Path.home() / '.m2' / 'repository').as_uri(),
        'https://repo.maven.apache.org/maven2',
        'https://repository.apache.org/content/groups/snapshots',
    ])

    error_log_time_origin = get_error_log_last_modified()

    command = [str(launcher),
               '-u', repository_urls,
               '-p', '.cards-data',
               '-c', '.cards-data/cache',
               '-f', 'mvn:%s/cards/%s/slingosgifeature/core_%s' % (GROUP, cards_version, options['storage'])]
    command += launcher_args
    process = subprocess.Popen(command, env=env, cwd=str(ROOT))
    proxy = None

    try:
        monitor_startup(process, cards_url, bind_port, use_psutil, options['debug'], error_log_time_origin)

        if options['test']:
            install_hancestro(cards_url)
        if not os.environ.get('GOOGLE_APIKEY'):
            banner(TERMINAL_YELLOW,
                   'GOOGLE_APIKEY not specified',
                   'Certain question types may have limited functionality')

        keycloak_endpoint = os.environ.get('KEYCLOAK_HEADERMOD_HTTP_PROXY_KEYCLOAK_ENDPOINT')
        if options['cloud_iam_demo']:
            verify_cloud_iam_keystore()
            keycloak_endpoint = keycloak_endpoint or CLOUD_IAM_KEYCLOAK_ENDPOINT
        if options['saml']:
            proxy = start_headermod_proxy(headermod_proxy_port, bind_port, keycloak_endpoint)
            banner(TERMINAL_GREEN, 'Started CARDS at port %d' % bind_port,
                   'Use port %d for SAML + local Sling login.' % headermod_proxy_port)
        else:
            banner(TERMINAL_GREEN, 'Started CARDS at port %d' % bind_port)

        # Stop this script if the CARDS process terminates
        process.wait()
    except KeyboardInterrupt:
        if proxy is not None:
            print('Shutting down keycloak_headermod_http_proxy.js')
            stop(proxy, interrupted=True)
        print('Shutting down CARDS')
        stop(process, interrupted=True)
        return 0
    stop(proxy)
    if process.returncode != 0:
        handle_cards_java_fail(bind_port)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
