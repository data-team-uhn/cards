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
import PropTypes from "prop-types";

import { IconButton, Tooltip } from "@mui/material";
import { withStyles } from 'tss-react/mui';
import EditIcon from "@mui/icons-material/Edit";
import { Link } from 'react-router-dom';

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

/**
 * A component that renders an icon to open the edit URL for an entry or to use local edit dialog.
 */
function EditButton(props) {
  const { entryPath, entryType, size, className, admin, onClick } = props;

  let innerButton =
        <IconButton className={className} size={size} onClick={onClick}>
          <EditIcon />
        </IconButton>

  return (
    <Tooltip title={entryType ? "Edit " + entryType.toLowerCase() : "Edit"}>
      { onClick
        ?
        innerButton
        :
        <Link to={(admin ? "/content.html/admin" : "/content.html") + entryPath + ".edit"} underline="hover">
          {innerButton}
        </Link>
      }
    </Tooltip>
  )
}

EditButton.propTypes = {
  entryPath: PropTypes.string,
  entryType: PropTypes.string,
  size: PropTypes.oneOf(["small", "medium", "large"]),
  className: PropTypes.string,
  admin: PropTypes.bool,
  onClick: PropTypes.func
}

EditButton.defaultProps = {
  entryType: "",
  size: "large",
}

export default withStyles(EditButton, QuestionnaireStyle);
