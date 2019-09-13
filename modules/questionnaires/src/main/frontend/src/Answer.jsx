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

import { withStyles } from "@material-ui/core";

import {Card, CardHeader, CardBody} from "MaterialDashboardReact";

import QuestionnaireStyle from "./QuestionnaireStyle";

function Answer (props) {
  let { answers, classes, children, title, subtitle } = props;
  return (
    <Card>
      <CardHeader color="warning">
        <h4 className={classes.cardTitleWhite}>{title}</h4>
        <p className={classes.cardCategoryWhite}>{subtitle}</p>
      </CardHeader>
      <CardBody>
        {children}
        {/*Create hidden inputs with the answers here, for later form submission*/
          answers.map( (name, id) => {
          return (
            <input type="hidden" id={id} key={id} value={name}></input>
            );
        })}
      </CardBody>
    </Card>
    );
}

Answer.propTypes = {
    classes: PropTypes.object.isRequired,
    title: PropTypes.string,
    subtitle: PropTypes.string,
    onInputFocus: PropTypes.func,
};

Answer.defaultProps = {
  Vocabulary: 'hpo',
  title: 'LFS Patients',
  searchDefault: 'Search',
  clearOnClick: true
};

export default withStyles(QuestionnaireStyle)(Answer);
