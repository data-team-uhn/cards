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

import {
  DialogContent
} from "@material-ui/core";

import FormattedText from "./components/FormattedText.jsx";
import ResponsiveDialog from "./components/ResponsiveDialog";

// Component that renders the Dialog width Terms of Use
//
// Optional props:
// withCloseButton: Boolean specifying whether the dialog should have a Close button (x)
//   at the top-right corner
// children: the dialog contents
// onClose: Callback for closing the dialog
//
// Sample usage:
//<ToUDialog
//  withCloseButton
//  open={open}
//  onClose={closeDialog}
//  >
//  <DialogActions>
//    {...}
//  </DialogActions>
//</ToUDialog>
//

function ToUDialog(props) {
  const { open, children, withCloseButton, onClose, ...rest } = props;

  const tou = require('./ToU.json');

  return (
    <ResponsiveDialog
      title={tou.title}
      open={open}
      withCloseButton={withCloseButton}
      width="lg"
      onClose={onClose}
    >
      <DialogContent dividers>
       <FormattedText>{tou.text}</FormattedText>
      </DialogContent>
      { children }
    </ResponsiveDialog>
  );
}

ToUDialog.propTypes = {
  open: PropTypes.bool,
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
  withCloseButton: PropTypes.bool,
  onClose: PropTypes.func,
}

export default ToUDialog;
