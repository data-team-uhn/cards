// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

/*
 * This program provides the necessary HTTP header modifications for
 * running the keycloak_demo CARDS test without requiring a Docker
 * container running the Apache HTTP reverse proxy.
 */

const SERVER_PORT = 9090;
const CLIENT_PORT = 8080;
const CLIENT_HOSTNAME = 'localhost';
const KEYCLOAK_ENDPOINT = 'http://localhost:8484/auth/realms/myrealm/protocol/saml';

const http = require('http');

var server = new http.Server();
server.on('request', (s_req, s_res) => {
	let client_opts = {
		hostname: CLIENT_HOSTNAME,
		port: CLIENT_PORT,
		path: s_req.url,
		method: s_req.method,
		headers: s_req.headers
	};
  var client_req = http.request(client_opts, (c_res) => {
		let newHeaders = c_res.headers;
		if (c_res.statusCode == 302 &&
			newHeaders.location &&
			newHeaders.location.startsWith(KEYCLOAK_ENDPOINT + "?SAMLRequest=")
			&& s_req.url !== "/fetch_requires_saml_login.html") {
			newHeaders.location = newHeaders.location.replace(KEYCLOAK_ENDPOINT, "http://localhost:" + SERVER_PORT + "/login");
		}      
		s_res.writeHead(c_res.statusCode, newHeaders);
		c_res.pipe(s_res, {end: true});
	});
	s_req.pipe(client_req, {end: true});
});

server.listen(SERVER_PORT, '127.0.0.1', function(error) {
	if (error) {
		console.log("Proxy failed to start");
	}
	else {
		console.log("Proxy listening on port " + SERVER_PORT);
		console.log(" - with CARDS at " + CLIENT_HOSTNAME + ":" + CLIENT_PORT);
		console.log(" - Keycloak login endpoint of: " + KEYCLOAK_ENDPOINT);
	}
});
