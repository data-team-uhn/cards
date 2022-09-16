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
const styles = theme => ({
  main: {
    width: 'auto',
    display: 'block', // Fix IE 11 issue.
    [theme.breakpoints.up(400 + theme.spacing(6))]: {
      width: 400,
      marginLeft: 'auto',
      marginRight: 'auto',
    },
  },
  paper: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    padding: theme.spacing(2, 3, 3),
  },
  selfContained: {
    marginTop: theme.spacing(14),
    marginBottom: theme.spacing(2),
  },
  form: {
    width: '100%', // Fix IE 11 issue.
    marginTop: theme.spacing(1),
    textAlign: "center",
  },
  formAction: {
    float: 'right',
    marginTop: theme.spacing(3),
    marginLeft: theme.spacing(2)
  },
  actions: {
    marginTop: theme.spacing(4),
  },
  submit: {
    whiteSpace: "pre",
    width: "auto !important",
  },
  appInfo: {
    marginTop: theme.spacing(6),
    textAlign: "center",
    "& ol, li": {
      display: "inline-block",
    },
  },
  closeButton: {
    float: 'right',
    marginLeft: theme.spacing(2),
    marginBottom: theme.spacing(2)
  },
});

export default styles;
