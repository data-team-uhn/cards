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

import React, { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Avatar,
  Icon,
  CardHeader,
} from "@mui/material";

let QuestionnaireCardHeader = (props) => {
  return (
      <CardHeader
        disableTypography
        avatar={
          !!!props.plain && (props.avatar || props.type) ?
            <Avatar aria-label="recipe" style={{backgroundColor: props.avatarColor || "black"}}>
              { props.avatar ? <Icon>{props.avatar}</Icon> : props.type.charAt(0) }
            </Avatar>
            : null
        }
        title={props.type && props.id && <>{props.type} : {props.id}</>}
        action={props.action}
      >
      </CardHeader>
  );
};

export default QuestionnaireCardHeader;
