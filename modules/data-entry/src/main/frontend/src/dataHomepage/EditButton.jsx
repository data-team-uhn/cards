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
import EditIcon from "@mui/icons-material/Edit";
import { IconButton, Tooltip } from "@mui/material";
import PropTypes from "prop-types";
import { Link } from 'react-router';

import { checkPropTypes } from "../propTypes";

/**
 * A component that renders an icon to open the edit URL for an entry or to use local edit dialog.
 */
function EditButton(props) {
  checkPropTypes(EditButton, props);
  const {
    entryPath,
    entryType = "",
    size = "large",
    className,
    extensionURL,
    onClick
  } = props;

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
        <Link to={(extensionURL ? "../content.html/" + extensionURL : "../content.html") + entryPath + ".edit"} underline="hover">
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
  extensionURL: PropTypes.string,
  onClick: PropTypes.func
}


export default EditButton;
