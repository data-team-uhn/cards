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
import { AccountBox, Assignment, Dashboard, Pets, Settings, Subtitles } from '@material-ui/icons';

import DashboardPage from "./Dashboard/dashboard.jsx";

var sidebarRoutes = [
    {
      path: "/content",
      name: "Dashboard",
      rtlName: "rtl:Dashboard",
      icon: Dashboard,
      component: DashboardPage,
      layout: ""
    },
    {
      path: "/view.html",
      name: "Patients",
      rtlName: "rtl:Patients",
      icon: Assignment,
      component: "",
      layout: ""
    },
    {
      path: "/modelorganisms.html",
      name: "Model Organisms",
      rtlName: "rtl:ModelOrganisms",
      icon: Pets,
      component: "",
      layout: ""
    },
    {
      path: "/variants.html",
      name: "Variants",
      rtlName: "rtl:Variants",
      icon: Subtitles,
      component: "",
      layout: ""
    },
    {
      path: "/userpage.html",
      name: "User Profile",
      rtlName: "rtl:userprofile",
      icon: AccountBox,
      component: "",
      layout: ""
    },
    {
      path: "/admin.html",
      name: "Administration",
      rtlName: "rtl:Admin",
      icon: Settings,
      component: "",
      layout: ""
    },
];

// TODO:Find all findable JCR nodes and add them to the available routes


export default sidebarRoutes