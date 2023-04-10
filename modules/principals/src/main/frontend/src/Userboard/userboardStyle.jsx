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
    root: {
      "& .MuiPaper-root" : {
        backgroundColor: "transparent",
      },
    },
    containerButton: {
      marginRight: theme.spacing(1),
    },
    cardActions: {
      justifyContent: "flex-end",
      marginTop: theme.spacing(2)
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
    dialogTitle: {
      padding: theme.spacing(2,0,2,3)
    },
    dialogActions: {
      padding: theme.spacing(2, 3)
    }
});

export default userboardStyle;
