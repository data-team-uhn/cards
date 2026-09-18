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

import { createContext } from "react";

// Describes the page chrome around the current React tree, so that fixed and sticky elements
// (e.g. LoadingOverlay, sticky resource headers, the navigation drawer) can avoid covering it
// or being covered by it.
//
// drawerWidth: width in px of the permanent left navigation drawer shown on md+ screens,
//   or 0 when the layout has no such drawer (patient portal, login and error pages).
// contentOffset: height in px of the banners rendered by PageStart at the top of the page
//   (downtime warning, demo banner, ...), i.e. how far from the top sticky elements must stay.
//   Layouts that add their own sticky chrome (e.g. the patient portal header) can nest a
//   second provider adding their height to this value.
//
// The default value is "no chrome". PageStartWrapper provides the actual value: it measures
// the banners, and the layout using it (the main CARDS layout, the patient portal) tells it
// whether a drawer is present.
const LayoutContext = createContext({ drawerWidth: 0, contentOffset: 0 });

export default LayoutContext;
