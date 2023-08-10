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
import { withRouter } from "react-router-dom";
import PropTypes from "prop-types";

import { IconButton, Tooltip } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import EditIcon from "@mui/icons-material/Edit";
import { Link } from 'react-router-dom';

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

/**
 * A component that renders an icon to open the edit URL for an entry.
 */
function EditButton(props) {
  const { entryPath, entryType, size, className, admin } = props;
  return(
    <Link to={(admin ? "/content.html/admin" : "/content.html") + entryPath + ".edit"} underline="hover">
      <Tooltip title={entryType ? "Edit " + entryType.toLowerCase() : "Edit"}>
        <IconButton className={className} size={size}>
          <EditIcon />
        </IconButton>
      </Tooltip>
    </Link>
  )
}

EditButton.propTypes = {
  entryPath: PropTypes.string,
  entryType: PropTypes.string,
  size: PropTypes.oneOf(["small", "medium", "large"]),
  className: PropTypes.string,
  admin: PropTypes.bool,
}

EditButton.defaultProps = {
  entryType: "",
  size: "large",
}

export default withStyles(QuestionnaireStyle)(withRouter(EditButton));
