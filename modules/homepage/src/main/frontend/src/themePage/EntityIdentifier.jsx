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
import React from "react";
import { getHierarchy } from "../questionnaire/Subject.jsx";

import { Link } from "react-router-dom";

// Location of the quick search result metadata in a node, outlining what needs to be highlighted
const LFS_QUERY_MATCH_KEY = "lfs:queryMatch";
const LFS_QUERY_MATCH_PATH_KEY = "@path";

// Display the identifier column of the result item wrt item primaryType
export function EntityIdentifier(row) {
    const anchorPath = row[LFS_QUERY_MATCH_KEY]?[LFS_QUERY_MATCH_PATH_KEY] : '';
    switch (row["jcr:primaryType"]) {
      // for forms (display full hierarchy for subjects with parents)
      case "lfs:Form":
        let questionnaire = (row.questionnaire?.title?.concat(' ') || '');
        let subjectHierarchy = row.subject ? getHierarchy(row.subject, Link, (node) => ({to: "/content.html" + node["@path"]})) : '';
        let formpath = `/content.html${row["@path"]}#${anchorPath}`;
        return (<React.Fragment>{subjectHierarchy} : <Link to={formpath}>{questionnaire}</Link></React.Fragment>)
      // for subjects (display full hierarchy for subjects with parents)
      case "lfs:Subject":
        return getHierarchy(row, Link, (node) => ({to: "/content.html" + node["@path"]}));
      case "lfs:Questionnaire":
        let fullpath = `/content.html/admin${row["@path"]}#${anchorPath}`;
        return (<Link to={fullpath}>{row.title}</Link>);
      // default covers other cases
      default:
        let id = row.subject?.identifier || row["@name"] || anchor;
        let path = `/content.html${row["@path"]}#${anchorPath}`;
        return (<Link to={path}>{id}</Link>);
    }
}
