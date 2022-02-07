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
import React, { useState, useRef, useEffect } from "react";
import { withRouter } from "react-router-dom";
import PropTypes from "prop-types";

import { Button, IconButton, Tooltip, withStyles } from "@material-ui/core";
import PrintIcon from "@material-ui/icons/Print";
import PrintPreview from "../questionnaire/PrintPreview.jsx";
import moment from "moment";
import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

/**
 * A component that renders a button to open the print preview for an entry.
 *
 * Required props:
 * resourcePath: String specifying the path of the resource entry (ex. form, subject, etc ) to print
 *
 * Optional props:
 * title: String specifying the title to associate with the rendered content
 * breadcrumb: String displayed in small fonts above the title, providing some context for the printed resource.
 *   Example usage: the formatted identifier of the subject for this resource.
 * date: String displayed with breadcrumb in the preview header above the title, providing the time context for the printed resource  
 * variant: String defining the render component and view of the print action button, default "icon"
 * size: String regulating the size of an icon button, default "medium"
 * buttonClass: String of class name that applies to the button element if the IconButton when variant == "icon"
 * buttonText: String specifying the text to be displayed on the button or the tooltip, default "Print preview"
 * fullScreen: Boolean specifying if the preview is full screen or displayed as a modal, default true
 * disableShortcut: Boolean specifying if Ctrl+P should activate this button or not. Default is false, meaning the shortcut is active.
 * onOpen: Callback for opening the preview dialog
 * onClose: Callback for closing the preview dialog
 *
 * Sample usage:
 *<PrintButton
 *  resourcePath="/Forms/UUID-1235"
 *  title="..."
 *  variant="icon"
 *  onClose={() => {...}}
 *  />
 *
 */
function PrintButton(props) {
  const { resourcePath, title, date, breadcrumb, onOpen, onClose, size, variant, label, buttonClass, disablePreview, fullScreen, disableShortcut } = props;

  const [ open, setOpen ] = useState(false);

  // Prevent browser to open print dialog on user ctrl+P keydown and force to go through the custom print preview
  useEffect(() => {
    if (disableShortcut) return;
    // subscribe event
    window.addEventListener("keydown", handleOnPrintKeydown);
    return () => {
      // unsubscribe event
      document.removeEventListener("keydown", handleOnPrintKeydown);
    };
  }, []);

  let handleOnPrintKeydown = (event) => {
    if ((event.ctrlKey || event.metaKey) && (event.key == "p" || event.keyCode == 80)) {
      event.stopPropagation();
      event.preventDefault ? event.preventDefault() : (event.returnValue = false);
      onOpenView();
    }
  }

  let onOpenView = () => {
    onOpen && onOpen();
    setOpen(true);
  }

  let onCloseView = () => {
    onClose && onClose();
    setOpen(false);
  }

  let buttonText = label || (disablePreview ? "Print" : "Print preview");

  return( <>
    <PrintPreview
      open={open}
      disablePreview={disablePreview}
      fullScreen={fullScreen}
      title={title}
      resourcePath={resourcePath}
      date={date}
      breadcrumb={breadcrumb}
      onClose={onCloseView}
    />
    { variant == "icon" ?
        <Tooltip title={buttonText}>
          <IconButton component="span" onClick={onOpenView} className={buttonClass} size={size}>
            <PrintIcon fontSize={size == "small" ? size : undefined}/>
          </IconButton>
        </Tooltip>
        :
        <Button
          onClick={onOpenView}
          size={size ? size : "medium"}
          startIcon={variant == "extended" ? <PrintIcon /> : undefined}
        >
          {buttonText}
        </Button>
    }
  </>)
}

PrintButton.propTypes = {
  variant: PropTypes.oneOf(["icon", "text", "extended"]), // "extended" means both icon and text
  title: PropTypes.string,
  resourcePath: PropTypes.string.isRequired,
  buttonText: PropTypes.string,
  buttonClass: PropTypes.string,
  breadcrumb: PropTypes.string,
  fullScreen: PropTypes.bool,
  disableShortcut: PropTypes.bool,
  date: PropTypes.string,
  onOpen: PropTypes.func,
  onClose: PropTypes.func,
  size: PropTypes.oneOf(["small", "medium"]),
}

PrintButton.defaultProps = {
  variant: "icon",
  size: "medium",
  fullScreen: true,
}

export default withStyles(QuestionnaireStyle)(withRouter(PrintButton));
