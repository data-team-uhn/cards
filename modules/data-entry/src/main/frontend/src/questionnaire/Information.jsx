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

import { Alert, Card, CardContent } from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import QuestionnaireStyle from "./QuestionnaireStyle";
import FormattedText from "../components/FormattedText.jsx";

// GUI for displaying Information cards
function Information (props) {
  let { classes, infoDefinition, ...otherProps } = props;
  let { text, type } = { ...otherProps, ...infoDefinition }

  return (type == "plain" ?
    <Card
      className={classes.informationCard}
      variant="outlined"
    >
      <CardContent>
        <FormattedText>{text}</FormattedText>
      </CardContent>
    </Card>
    :
    <Alert severity={type} icon={false}>
      <FormattedText>{text}</FormattedText>
    </Alert>
  )
}

Information.propTypes = {
  classes: PropTypes.object.isRequired,
  infoDefinition: PropTypes.shape({
    text: PropTypes.string,
    type: PropTypes.oneOf(["plain", "info", "warning", "error", "success"]),
  }),
};

Information.defaultProps = {
  type: "plain",
};

export default withStyles(QuestionnaireStyle)(Information);
