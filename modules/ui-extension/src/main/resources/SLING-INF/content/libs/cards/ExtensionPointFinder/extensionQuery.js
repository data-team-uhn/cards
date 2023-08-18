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
// Locate all visible cards:dataEntry nodes
use(function(){
    var uixp = request.getRequestParameter("uixp");
    if (!uixp) {
        return {'uixp': ''};
    }
    uixp = uixp.getString();
    var queryManager = currentSession.getWorkspace().getQueryManager();
    var q = "select * from [cards:ExtensionPoint] as n WHERE n.'cards:extensionPointId' = $path and ISDESCENDANTNODE(n, '/apps/cards/ExtensionPoints/') OPTION (index tag property)";
    var query = queryManager.createQuery(q, "JCR-SQL2");
    query.bindValue("path", currentSession.getValueFactory().createValue(uixp));
    var queryResults = query.execute().getNodes();

    // If no results were found, throw an exception
    if (!queryResults.hasNext()) {
      throw("No UIXP found at " + uixp);
    }

    // Dump the first result into our return statement
    return {
      uixp: queryResults.hasNext() ? queryResults.next().getPath() : ""
    };
  });
