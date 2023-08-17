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

const CLIENT_PORT = 8080;
const CLIENT_HOSTNAME = 'cardsinitial';

const http = require('http');
const fetch = require('node-fetch');

module.exports.handleConnection = (s_req, s_res) => {
	console.log("Forms handler running for connection to http://cardsinitial:8080" + s_req.url);
	let client_opts = {
		hostname: CLIENT_HOSTNAME,
		port: CLIENT_PORT,
		path: s_req.url,
		method: s_req.method,
		headers: s_req.headers
	};

	// Simply allow the connection through
	let client_req = http.request(client_opts, function(c_res) {
		s_res.writeHead(c_res.statusCode, c_res.headers);
		c_res.pipe(s_res, {end: true});
	});
	s_req.pipe(client_req, {end: true});

};
