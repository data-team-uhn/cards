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

const SERVER_PORT = 8600;

const fs = require('fs');
const http = require('http');

// Register the pause request handlers
const handler_names = fs.readdirSync('request_pause_rules');
const REGISTERED_HANDLERS = {};
for (let i = 0; i < handler_names.length; i++) {
	let handler_path = fs.readFileSync('request_pause_rules' + '/' + handler_names[i] + '/' + 'path.txt', 'utf-8').trim();
	let handler_method = require('./request_pause_rules' + '/' + handler_names[i] + '/' + 'handler').handleConnection;
	REGISTERED_HANDLERS[handler_path] = handler_method;
}

const server = new http.Server();

console.log("Handlers...");
console.log(REGISTERED_HANDLERS);

function removeLeftBlank(list) {
	if (list.length == 0) {
		return list;
	}
	if (list[0] === '') {
		return list.slice(1);
	}
	return list;
}

function removeRightBlank(list) {
	if (list.length == 0) {
		return list;
	}
	if (list[list.length - 1] === '') {
		return list.slice(1, list.length - 1);
	}
	return list;
}

function removeEdgeBlanks(list) {
	return removeLeftBlank(removeRightBlank(list));
}

function getMatchLength(rule_string, test_string) {
	let rule_parts = removeEdgeBlanks(rule_string.split('/'));
	let test_parts = removeEdgeBlanks(test_string.split('/'));
	if (test_parts.length < rule_parts.length) {
		return 0;
	}

	for (let i = 0; i < rule_parts.length; i++) {
		if (test_parts[i] !== rule_parts[i]) {
			return 0;
		}
	}

	return rule_parts.length;
}

function getMatchingRule(request_path) {
	let best_match = undefined;
	let best_match_score = 0;
	for (let handler_path in REGISTERED_HANDLERS) {
		let this_match_score = getMatchLength(handler_path, request_path);
		if (this_match_score > best_match_score) {
			best_match_score = this_match_score;
			best_match = handler_path;
		}
	}
	return best_match;
}

server.on('request', (request, response) => {
	let selected_rule = getMatchingRule(request.url);
	console.log("Received a request for: " + request.url);
	console.log("Handling using the rule: " + selected_rule);

	if (selected_rule !== undefined) {
		REGISTERED_HANDLERS[selected_rule](request, response);
	}
});

server.listen(SERVER_PORT, function(error) {
	if (error) {
		console.log("Proxy failed to start");
	}
	else {
		console.log("Proxy listening on port " + SERVER_PORT);
	}
});
