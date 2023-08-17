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

const fs = require('fs');
const process = require('process');

const OUTPUT_FILE = process.argv[2];
if (OUTPUT_FILE === undefined) {
	console.log("Usage: nodejs generate_non_cards_routes.js");
	process.exit(1)
}

const proxyErrorConfigSegment =
`<Location "/proxyerror">
	ProxyPass !
</Location>`;

function generateProxyPassConfigSegment(path) {
const configSegment =
`<Location "${path}">
	ProxyPass http://127.0.0.1:8600${path}
	ProxyPassReverse http://127.0.0.1:8600${path}
</Location>`;

	return configSegment
}

// Open the Apache httpd config file for writing
const output_fd = fs.openSync(OUTPUT_FILE, 'w');

// All configurations should serve a custom HTTP 503 error page
fs.writeSync(output_fd, proxyErrorConfigSegment);
fs.writeSync(output_fd, "\n\n");

// Generate ProxyPass configurations for all the rules registered in /request_pause_rules
const handler_names = fs.readdirSync('request_pause_rules');
for (let i = 0; i < handler_names.length; i++) {
	let handler_path = fs.readFileSync('request_pause_rules' + '/' + handler_names[i] + '/' + 'path.txt', 'utf-8').trim();
	fs.writeSync(output_fd, generateProxyPassConfigSegment(handler_path));
	fs.writeSync(output_fd, "\n\n");
}

// Finished creating the Apache httpd config file
fs.closeSync(output_fd);
