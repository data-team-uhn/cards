#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
"""

import argparse
import datetime

import urllib3
urllib3.disable_warnings()

import requests
from http.cookies import SimpleCookie

argparser = argparse.ArgumentParser()
argparser.add_argument('--https', action='store_true')
argparser.add_argument('--saml', action='store_true')
args = argparser.parse_args()

class SampleClient:
  def __init__(self, admin_port, user_port, use_ssl=False):
    self.admin_port = admin_port
    self.user_port = user_port
    self.protocol = "http"
    self.admin_port_urlpart = ""
    self.user_port_urlpart = ""
    if use_ssl:
      self.protocol = "https"
      if self.admin_port != 443:
        self.admin_port_urlpart = ":{}".format(self.admin_port)
      if self.user_port != 443:
        self.user_port_urlpart = ":{}".format(self.user_port)
    else:
      self.admin_port_urlpart = ":{}".format(self.admin_port)
      self.user_port_urlpart = ":{}".format(self.user_port)
    self.client_headers = {'User-Agent': 'Mozilla/5.0'}

  def admin_url_for(self, path):
    return self.protocol + "://localhost" + self.admin_port_urlpart + path

  def user_url_for(self, path):
    return self.protocol + "://localhost" + self.user_port_urlpart + path


  def adminGET(self, path, auth=None, allow_redirects=True):
    return requests.get(self.admin_url_for(path), auth=auth, allow_redirects=allow_redirects, headers=self.client_headers, verify=False)

  def userGET(self, path, auth=None, allow_redirects=True):
    return requests.get(self.user_url_for(path), auth=auth, allow_redirects=allow_redirects, headers=self.client_headers, verify=False)

  def adminPOST(self, path, data=None, auth=None, allow_redirects=True):
    return requests.post(self.admin_url_for(path), data=data, auth=auth, allow_redirects=allow_redirects, headers=self.client_headers, verify=False)

  def userPOST(self, path, data=None, auth=None, allow_redirects=True):
    return requests.post(self.user_url_for(path), data=data, auth=auth, allow_redirects=allow_redirects, headers=self.client_headers, verify=False)

if (args.https):
  testClient = SampleClient(443, 444, use_ssl=True)
else:
  testClient = SampleClient(8080, 8090)

def check_standard_http_headers():
  # Check that the `X-Frame-Options "DENY"` HTTP header is present
  r = testClient.adminGET("/")
  assert 'X-Frame-Options' in r.headers, "FAIL: X-Frame-Options not in HTTP response header"
  assert r.headers['X-Frame-Options'] == 'DENY, SAMEORIGIN', "FAIL: X-Frame-Options header != DENY"
  print("PASS: (admin port) X-Frame-Options=DENY Test")

  r = testClient.userGET("/")
  assert 'X-Frame-Options' in r.headers, "FAIL: X-Frame-Options not in HTTP response header"
  assert r.headers['X-Frame-Options'] == 'DENY, SAMEORIGIN', "FAIL: X-Frame-Options header != DENY"
  print("PASS: (user port) X-Frame-Options=DENY Test")


def check_https_hsts():
  # Check that the `Strict-Transport-Security "max-age=31536000"` HTTP header is present
  r = testClient.adminGET("/")
  assert 'Strict-Transport-Security' in r.headers, "FAIL: Strict-Transport-Security not in HTTP response header"
  assert r.headers['Strict-Transport-Security'] == "max-age=31536000", "FAIL: Incorrect HSTS HTTP header on admin port"
  print("PASS: Correct HSTS HTTP header on admin port")

  r = testClient.userGET("/")
  assert 'Strict-Transport-Security' in r.headers, "FAIL: Strict-Transport-Security not in HTTP response header"
  assert r.headers['Strict-Transport-Security'] == "max-age=31536000", "FAIL: Incorrect HSTS HTTP header on user port"
  print("PASS: Correct HSTS HTTP header on user port")


def check_basic_http_auth():
  # Check that Basic HTTP Authentication through the admin port works
  r = testClient.adminGET("/thisIsAPathToSomethingThatDoesntExist", auth=('admin', 'admin'), allow_redirects=False)
  assert r.status_code == 404, "FAIL: Unable to do basic HTTP authentication through admin port"
  print("PASS: Basic Auth (permitted) through admin port Test")

  # Check that Basic HTTP Authentication through the user port DOES NOT work
  r = testClient.userGET("/thisIsAPathToSomethingThatDoesntExist", auth=('admin', 'admin'), allow_redirects=False)
  assert r.status_code == 302, "FAIL: Was able to do basic HTTP authentication through user port"
  print("PASS: Basic Auth (denied) through user port Test")


def check_session_cookie_auth():
  # Check that users can login and obtain a session cookie through the admin port
  r = testClient.adminPOST("/j_security_check", data={'j_username': 'admin', 'j_password': 'admin', 'j_validate': 'true'})
  assert r.status_code == 200, "FAIL: Client was not able to login on the admin port"
  print("PASS: Session cookie auth (permitted) through the admin port Test")

  # Check that users cannot login and obtain a session cookie through the user port
  r = testClient.userPOST("/j_security_check", data={'j_username': 'admin', 'j_password': 'admin', 'j_validate': 'true'})
  assert r.status_code == 403, "FAIL: Client on user port accessing /j_security_check was not returned HTTP 403 as expected"
  print("PASS: Session cookie auth (denied) through user port Test")


def check_secure_cookie():
  # Checks that the login session cookie has the "secure" attribute set, so that it will only ever be sent over HTTPS
  r = testClient.adminPOST("/j_security_check", data={'j_username': 'admin', 'j_password': 'admin', 'j_validate': 'true'})
  assert 'Set-Cookie' in r.headers, "FAIL: Set-Cookie HTTP header not present"
  assert r.headers['Set-Cookie'].endswith(';Secure'), "FAIL: Secure flag for Set-Cookie not set"
  print("PASS: Session cookie has 'Secure' flag set")

def check_ncr_routing():
  # Check that routing to /ncr/ works
  r = testClient.adminGET("/ncr/annotate")
  assert r.text == "ACCESS DENIED", "FAIL: Unexpected response from NCR"
  print("PASS: Client on admin port can use NCR")

  r = testClient.userGET("/ncr/annotate")
  assert r.text == "ACCESS DENIED", "FAIL: Unexpected response from NCR"
  print("PASS: Client on user port can use NCR")


def check_root_redirect(withSaml=False):
  # Check that requests to / from the admin port are
  r = testClient.adminGET("/", allow_redirects=False)
  assert r.status_code == 302, "FAIL: Request to / on the admin port did not return HTTP 302"
  assert 'Location' in r.headers, "FAIL: Location HTTP header was not present"
  if withSaml:
    assert r.headers['Location'] == testClient.admin_url_for('/login')
    print("PASS: Client on admin port was redirected from / to /login")
  else:
    assert r.headers['Location'] == testClient.admin_url_for('/login?resource=%2F')
    print("PASS: Client on admin port was redirected from / to /login?resource=%2F")

  # Check that requests to / from the user port are redirected to /Survey.html
  r = testClient.userGET("/", allow_redirects=False)
  assert r.status_code == 301, "FAIL: Request to / on the user port did not return HTTP 301"
  assert 'Location' in r.headers, "FAIL: Location HTTP header was not present"
  assert r.headers['Location'] == testClient.user_url_for('/Survey.html')
  print("PASS: Client on user port was redirected from / to /Survey.html")

def check_saml_consumer_accessibility():
  # Check that clients connecting to the admin port CAN access /sp/consumer
  expected_saml_error_message = "org.apache.sling.auth.saml2.SAML2RuntimeException:"
  expected_saml_error_message += " org.opensaml.messaging.decoder.MessageDecodingException:"
  expected_saml_error_message += " No SAML message present in request"
  r = testClient.adminPOST("/sp/consumer")
  assert (expected_saml_error_message in r.text), "FAIL: Client on admin port cannot reach CARDS SAML endpoint"
  print("PASS: Client on admin port can access the CARDS SAML consumer endpoint")

  r = testClient.userPOST("/sp/consumer")
  assert r.status_code == 403, "FAIL: Client on user port can access the CARDS SAML consumer endpoint"
  print("PASS: Client on user port cannot access the CARDS SAML consumer endpoint")

def check_goto_saml_login():
  # Check that clients accessing /goto_saml_login on the admin port are redirected to the IdP login page
  r = testClient.adminGET("/goto_saml_login", allow_redirects=False)
  assert r.status_code == 302, "FAIL: Request to /goto_saml_login on the admin port did not return HTTP 302"
  assert 'Location' in r.headers, "FAIL: Location HTTP header was not present"
  assert r.headers['Location'].startswith("https://lemur-15.cloud-iam.com/auth/realms/cards-saml-test/protocol/saml?SAMLRequest=")
  print("PASS: Client accessing /goto_saml_login on the admin port was redirected to the IdP login page")

  assert 'Cache-Control' in r.headers, "FAIL: Cache-Control header is not present"
  assert r.headers['Cache-Control'] == 'no-store'
  print("PASS: /goto_saml_login set the 'Cache-Control' HTTP header to 'no-store'");

  # Check that clients accessing /goto_saml_login on the user port are redirected to /
  r = testClient.userGET("/goto_saml_login", allow_redirects=False)
  assert r.status_code == 302, "FAIL: Request to /goto_saml_login on the user port did not return HTTP 302"
  assert 'Location' in r.headers, "FAIL: Location HTTP header was not present"
  assert r.headers['Location'] == '/'
  print("PASS: /goto_saml_login redirected the client on the user port to /")


def check_fetch_requires_saml_login():
  # Check that clients accessing /fetch_requires_saml_login.html on the admin port are redirected to the SAML IdP login page
  r = testClient.adminGET("/fetch_requires_saml_login.html", allow_redirects=False)
  assert r.status_code == 302, "FAIL: Request to /fetch_requires_saml_login.html on the admin port did not return HTTP 302"
  assert 'Location' in r.headers, "FAIL: Location HTTP header was not present"
  assert r.headers['Location'].startswith("https://lemur-15.cloud-iam.com/auth/realms/cards-saml-test/protocol/saml?SAMLRequest=")
  print("PASS: Client accessing /fetch_requires_saml_login.html on the admin port was redirected to the IdP login page")

  # Check that the Cache-Control header is appropriately set
  assert 'Cache-Control' in r.headers, "FAIL: Cache-Control header is not present"
  assert r.headers['Cache-Control'] == 'no-store'
  print("PASS: /fetch_requires_saml_login.html set the 'Cache-Control' HTTP header to 'no-store'")

  # Check that clients accessing /fetch_requires_saml_login.html on the user port are redirected to /
  r = testClient.userGET("/fetch_requires_saml_login.html", allow_redirects=False)
  assert r.status_code == 302, "FAIL: Request to /fetch_requires_saml_login.html on the user port did not return HTTP 302"
  assert 'Location' in r.headers, "FAIL: Location HTTP header was not present"
  assert r.headers['Location'] == '/'
  print("PASS: Client accessing /fetch_requires_saml_login.html on the user port was redirected to /")


def check_system_sling_logout():
  r = testClient.adminGET("/system/sling/logout", allow_redirects=False)
  assert r.status_code == 302, "FAIL: Request to /system/sling/logout on the admin port did not return HTTP 302"
  assert 'Set-Cookie' in r.headers, "FAIL: Set-Cookie HTTP header was not present"
  assert r.headers['Set-Cookie'] == "sling.formauth=; Path=/; Expires=Thu, 01-Jan-1970 00:00:00 GMT; Max-Age=0; HttpOnly", "FAIL: Incorrect cookie set by /system/sling/logout"
  print("PASS: /system/sling/logout sets the appropriate cookies")

def check_system_console_logout():
  r = testClient.adminGET("/system/console/logout", allow_redirects=False)
  assert r.status_code == 302, "FAIL: Request to /system/console/logout on the admin port did not return HTTP 302"
  assert 'Set-Cookie' in r.headers, "FAIL: Set-Cookie HTTP header was not present"
  assert r.headers['Set-Cookie'] == "sling.formauth=; Path=/; Expires=Thu, 01-Jan-1970 00:00:00 GMT; Max-Age=0; HttpOnly", "FAIL: Incorrect cookie set by /system/console/logout"
  print("PASS: /system/console/logout sets the appropriate cookies")


def get_cookie_expiration_datetime(set_cookie_header, cookie_name):
  for name, obj in SimpleCookie(set_cookie_header).items():
    if name == cookie_name and 'expires' in obj:
      return datetime.datetime.strptime(obj['expires'], "%a, %d-%b-%Y %H:%M:%S GMT")
  return None

def is_cookie_invalidated(set_cookie_header, cookie_name):
  expiry = get_cookie_expiration_datetime(set_cookie_header, cookie_name)
  if expiry is None:
    return False
  if expiry < datetime.datetime.now():
    return True
  return False

def check_clears_cards_auth_token_cookie(path):
  r = testClient.adminGET(path, allow_redirects=False)
  assert 'Set-Cookie' in r.headers, "FAIL: Set-Cookie not response headers"
  assert is_cookie_invalidated(r.headers['Set-Cookie'], "cards_auth_token"), "FAIL: Visiting {} on the admin port did not invalidate the cards_auth_token cookie".format(path)
  print("PASS: Request to {} on the admin port invalidated the cards_auth_token cookie".format(path))

  r = testClient.userGET(path, allow_redirects=False)
  assert 'Set-Cookie' in r.headers, "FAIL: Set-Cookie not response headers"
  assert is_cookie_invalidated(r.headers['Set-Cookie'], "cards_auth_token"), "FAIL: Visiting {} on the user port did not invalidate the cards_auth_token cookie".format(path)
  print("PASS: Request to {} on the user port invalidated the cards_auth_token cookie".format(path))

withSaml = args.saml
withHttps = args.https

# Run the tests
check_standard_http_headers()

if withHttps:
  check_https_hsts()
  check_secure_cookie()

check_basic_http_auth()
check_session_cookie_auth()
check_ncr_routing()
check_root_redirect(withSaml=withSaml)
check_clears_cards_auth_token_cookie("/Survey")
check_clears_cards_auth_token_cookie("/Survey.html")

if withSaml:
  # Run the SAML-specific tests
  check_saml_consumer_accessibility()
  check_goto_saml_login()
  check_fetch_requires_saml_login()
  check_system_sling_logout()
  check_system_console_logout()
