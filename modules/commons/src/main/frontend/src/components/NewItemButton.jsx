/*
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
*/

import React from "react";
import PropTypes from "prop-types";
import AddIcon from "@mui/icons-material/Add";
import MainActionButton from './MainActionButton.jsx';

// Component that renders a (+) button at the bottom right of the screen,
// enabling the "creation" of a new item.
//
// Example use cases: create a new Form on the Forms page, create a new user
// on the Users page, etc.
//
// The creation functionality is not provided within this component, it is
// ensured by the mandatory onClick property of type function.
//
// Optional props:
// title: the title (tooltip) displayed when hovering the button; defaults to "New"
// inProgress: whether creating a new item is currently in progress, in which case
//   the button is disabled and a CircularProgress is displayed on top of it.
//
// Sample usage:
//<NewItemButton
//  title="Create new chart"
//  onClick={() => openNewChartDialog()}
//  inProgress={dialogIsLoading}
//  />
//
function NewItemButton(props) {
  return (
    <MainActionButton
      ariaLabel="new"
      icon={<AddIcon />}
      {...props}
    />
  );
}

NewItemButton.propTypes = {
  title: PropTypes.string,
  onClick: PropTypes.func.isRequired,
  inProgress: PropTypes.bool,
}

NewItemButton.defaultProps = {
  title: "New",
  inProgress: false,
};

export default NewItemButton;
