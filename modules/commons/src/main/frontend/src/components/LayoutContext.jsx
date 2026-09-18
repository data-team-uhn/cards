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

// Describes the page chrome around the current React tree, so that fixed-position elements
// (e.g. LoadingOverlay) can avoid covering it.
//
// drawerWidth: width in px of the permanent left navigation drawer shown on md+ screens,
//   or 0 when the layout has no such drawer (patient portal, login and error pages).
//
// The default value is "no chrome"; the layout that owns the drawer (the main CARDS layout
// in homepage/themePage/index.jsx, used by all staff-facing pages) provides the actual value.
const LayoutContext = createContext({ drawerWidth: 0 });

export default LayoutContext;
