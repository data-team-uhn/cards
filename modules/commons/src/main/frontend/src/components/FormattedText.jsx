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

import { Typography } from "@mui/material";
import MDEditor from '@uiw/react-md-editor';
import PropTypes from 'prop-types';
import { makeStyles } from 'tss-react/mui';

import { checkPropTypes } from "../propTypes";

const useStyles = makeStyles()(theme => ({
  markdown: {
    "&.wmde-markdown" : {
      background: "transparent",
      color: "inherit",
      fontSize: "inherit",
      fontFamily: "inherit",
      "& .anchor" : {
        display: "none",
      },
      "& img": {
        background: "transparent"
      }
    },
  }
}));

// MUI v9 removed the textPrimary/textSecondary color aliases; map them to sx equivalents
const LEGACY_COLORS = {
  textSecondary: 'text.secondary',
  textPrimary: 'text.primary',
};

let FormattedText = (props) => {
  checkPropTypes(FormattedText, props);
  let { children, color, sx, ...typographyProps } = props;
  const { classes } = useStyles();

  const mappedColor = LEGACY_COLORS[color];

  return (
    <Typography
      component="div"
      {...typographyProps}
      color={mappedColor ? undefined : color}
      sx={mappedColor ? { color: mappedColor, ...sx } : sx}
    >
      <MDEditor.Markdown classes={classes} className={classes.markdown} source={children} />
    </Typography>
  );
}

FormattedText.propTypes = {
  children: PropTypes.string
};

export default FormattedText;
