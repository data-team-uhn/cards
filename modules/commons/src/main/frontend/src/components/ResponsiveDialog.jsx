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
  Dialog,
  DialogTitle,
  useMediaQuery
} from "@material-ui/core";

import { useTheme } from '@material-ui/core/styles';

// Component that renders the Dialog containers that expand to full screen once
// the screen becomes more narrow than the specified width
//
// Optional props:
// title: the title of the dialog
// width: the default size of the dialog, and the screen size that makes it go
//   fullScreen. One of xs, sm, md, lg, xl. Defaults to sm.
// children: the dialog contents
//
// Sample usage:
//<ResponsiveDialog
//  title="Select a subject"
//  open={open}
//  onClose={selectSubject}
//  >
//  <DialogContent dividers>
//    {...}
//  </DialogContent>
//  <DialogActions>
//    {...}
//  </DialogActions>
//</ResponsiveDialog>
//
export default function ResponsiveDialog(props) {
  const { title, width, children, ...rest } = props;

  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down(width));

  return (
    <Dialog
      maxWidth={width}
      fullWidth
      fullScreen={fullScreen}
      disableBackdropClick
      {...rest}
    >
      { title && <DialogTitle>{title}</DialogTitle>}
      { children }
    </Dialog>
  );
}

ResponsiveDialog.propTypes = {
  title: PropTypes.string,
  width: PropTypes.oneOf(["xs", "sm", "md", "lg", "xl"]),
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node
  ]),
}

ResponsiveDialog.defaultProps = {
  width: "sm",
};
