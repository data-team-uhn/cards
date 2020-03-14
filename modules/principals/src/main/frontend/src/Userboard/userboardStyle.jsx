/*
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
*/

// Taken from https://www.creative-tim.com/product/material-dashboard-react

const userboardStyle = theme => ({
    cardTitle: {
      color: theme.palette.grey['800'],
      marginTop: "0px",
      minHeight: "auto",
      fontWeight: "300",
      fontFamily: "'Roboto', 'Helvetica', 'Arial', sans-serif",
      marginBottom: "3px",
      textDecoration: "none",
      paddingBottom: "0",
      verticalAlign: "bottom",
      "& small": {
        color: theme.palette.grey['700'],
        fontWeight: "400",
        lineHeight: "1"
      }
    },
    cardCategory: {
      color: theme.palette.grey['500'],
      margin: "0",
      fontSize: "14px",
      marginTop: "0",
      paddingTop: "10px",
      marginBottom: "0",
      width: "20%",
      paddingBottom: "0",
      verticalAlign: "bottom"
    },
    containerButton: {
      marginRight: "8px",
    },
    cardActions: {
      justifyContent: "flex-end",
      marginTop: "16px"
    },
    cardRoot: {
      paddingLeft: "120px"
    },
    info: {
      backgroundColor: theme.palette.info.main
    },
    addIcon: {
      backgroundColor: theme.palette.primary.main
    },
    closeButton: {
      float: 'right'
    }
});

export default userboardStyle;
