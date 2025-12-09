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

import { Link } from 'react-router';

function defaultCreator (node) {
  return { to: "../content.html" + node["@path"] }
}

function extensionCreator(node, extensionURL) {
  return extensionURL ? { to: "../content.html/" + extensionURL + node["@path"] } : defaultCreator(node)
}

// Extract the subject id from the subject path
// returns null if the parameter is not a valid subject path (expected format: Subjects/<id>)
export function getHomepageLink (subjectNode, extensionURL="") {
  let props = extensionCreator({ "@path": `/Subjects#subjects:activeTab=${subjectNode?.type?.["@name"]}` }, extensionURL);
  return (<Link {...props} underline="hover">{subjectNode?.type?.subjectListLabel || "Subjects"}</Link>);
}


// Extract the subject id from the subject path
// returns null if the parameter is not a valid subject path (expected format: Subjects/<id>)
export function getSubjectIdFromPath (path) {
  return /Subjects\/([^.]+)/.exec(path || '')?.[1];
}

// Recursive function to get a flat list of parents
export function getHierarchy (node, RenderComponent, propsCreator, extensionURL="") {
  let HComponent = RenderComponent || Link;
  let props = propsCreator || extensionCreator(node, extensionURL);
  let output = <React.Fragment>{node.type.label} <HComponent {...props}>{node.identifier}</HComponent></React.Fragment>;
  if (node["parents"]?.type) {
    let ancestors = getHierarchy(node["parents"], HComponent, propsCreator, extensionURL);
    return <React.Fragment>{ancestors} / {output}</React.Fragment>
  } else {
    return output;
  }
}

// Recursive function to get a flat list of parents with no subject labels
export function getShortHierarchy (node, RenderComponent, propsCreator, extensionURL="") {
  let HComponent = RenderComponent || Link;
  let props = propsCreator || extensionCreator(node, extensionURL);
  let output = <HComponent {...props}>{node.identifier}</HComponent>;
  if (node["parents"] && node["parents"].type) {
    let ancestors = getShortHierarchy(node["parents"], HComponent, propsCreator, extensionURL);
    return <React.Fragment>{ancestors} / {output}</React.Fragment>
  } else {
    return output;
  }
}

// Recursive function to get a flat list of parents with no links and subject labels
export function getTextHierarchy (node, withType = false) {
  let type = withType ? (node?.["type"]?.["@name"] + " "): "";
  let output = node.identifier;
  if (node["parents"]) {
    let ancestors = getTextHierarchy(node["parents"], withType);
    return `${ancestors} / ${type}${output}`;
  } else {
    return type + output;
  }
}

// Recursive function to get the list of ancestors as an array
export function getHierarchyAsList (node, includeHomepage, extensionURL="") {
  if (!node?.type) {
    return includeHomepage ? [getHomepageLink(undefined, extensionURL)] : [];
  }
  let props = extensionCreator(node, extensionURL);
  let parent = <>{node.type.label} <Link {...props} underline="hover">{node.identifier}</Link></>;
  if (node["parents"]) {
    let ancestors = getHierarchyAsList(node["parents"], undefined, extensionURL);
    ancestors.push(parent);
    return ancestors;
  } else {
    let result = [parent];
    includeHomepage && result.unshift(getHomepageLink(node, extensionURL));
    return result;
  }
}
