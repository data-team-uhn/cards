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
import React, { useState, useEffect } from "react";
import { loadRemoteComponents, loadRemoteIcons, loadContentNodes } from "../themePage/routes"

// import { Button, Card, CardContent, CardHeader } from "@material-ui/core";

function AdminDashboard() {
  let [ adminRoutes, setAdminRoutes ] = useState([]);

  let handleRoutes = (uixData) => {
    console.log(uixData);
    // var routes = sidebarRoutes.slice();
    // uixData.sort((firstEl, secondEl) => {return firstEl.order - secondEl.order;});
    // for (var id in uixData) {
    //   var uixDatum = uixData[id];
    //   routes.push({
    //     path: uixDatum.path,
    //     name: uixDatum.name,
    //     icon: uixDatum.icon,
    //     component: uixDatum.reactComponent,
    //     isAdmin: this._isAdministrativeButton(uixDatum.order),
    //     rtlName: "rtl:test",
    //     layout: "/content.html"
    //   });
    // }
    // this.setState({routes: routes, loading: false});
  }

  useEffect(() => {
    loadContentNodes("AdminDashboard")
    .then(loadRemoteComponents)
    .then(loadRemoteIcons)
    .then(handleRoutes)
    .catch(function(err) {
      console.log("Something went wrong: " + err);
    });
  })

  return (
    <div>This is the Admin Dashboard</div>
  );
}

export default AdminDashboard;
