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

const CLIENT_PORT = 5000;
const CLIENT_HOSTNAME = 'neuralcr';
const AUTH_URL = 'http://cardsinitial:8080/system/sling/info.sessionInfo.json';

const http = require('http');
const fetch = require('node-fetch');

function checkAuthentication(opts, method_allow, method_deny) {
	//Check if the cookies are valid
	if ('cookie' in opts) {
		fetch(AUTH_URL, {
				method: 'GET',
				headers: {
					Cookie: opts['cookie']
				}
			})
			.then(res => res.json())
			.then((json) => {
				if ('userID' in json) {
					if (json['userID'].toUpperCase() === 'ADMIN') {
						method_allow();
						return;
					}
				}
				method_deny();
			});
	}
	else {
		method_deny();
	}
}

function removeLeadingText(input_text, removal_text) {
	if (input_text.startsWith(removal_text)) {
		return input_text.slice(removal_text.length);
	}
	return input_text;
}

module.exports.handleConnection = (s_req, s_res) => {
	let { cookie, ...filtered_headers } = s_req.headers;
	let ncr_path = "/" + removeLeadingText(s_req.url, "/ncr/");
	console.log("NCR handler running for connection to http://neuralcr:5000" + ncr_path);
	let client_opts = {
		hostname: CLIENT_HOSTNAME,
		port: CLIENT_PORT,
		path: ncr_path,
		method: s_req.method,
		headers: filtered_headers
	};

	//Check to see if we should allow the request or not
	checkAuthentication(s_req.headers, function() {
		var client_req = http.request(client_opts, function(c_res) {
			s_res.writeHead(c_res.statusCode, c_res.headers);
			c_res.pipe(s_res, {end: true});
		});
		s_req.pipe(client_req, {end: true});
	},
	function() {
		s_res.writeHead(403, {'Content-Type': 'text/html'});
		s_res.write("ACCESS DENIED");
		s_res.end();
	});
};
