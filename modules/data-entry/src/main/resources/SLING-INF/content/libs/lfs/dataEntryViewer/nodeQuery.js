//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//
// Locate all visible lfs:dataEntry nodes
use(function(){
  var query = resolver.findResources("select * from [lfs:dataEntry] as n", "JCR-SQL2");

  // Dump our iterator into a list of strings
  var nodeNames = [];
  while (query.hasNext()) {
    var resource = query.next();
    var stringOut = resource.getPath() + "|";

    // Create a comma-delimited list of entries for each property
    var propIterator = resource.properties.keySet().iterator();
    while (propIterator.hasNext()) {
      var key = propIterator.next();
      stringOut += key + ' ' + resource.properties[key]+"\n";
    }
    nodeNames.push(stringOut);
  }

  return {
    results: nodeNames
  };
});