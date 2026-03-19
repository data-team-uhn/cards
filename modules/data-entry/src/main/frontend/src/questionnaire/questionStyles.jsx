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

const questionStyles = theme => ({
  questionCard : {
    overflow: "unset",
    "& .MuiCardHeader-root" : {
      padding: theme.spacing(1, 3, 0, 3),
      "& h6 ol" : {
        paddingLeft: 0,
      },
      "& h6 li" : {
        listStylePosition: "inside",
      },
    },
    "& .MuiCardContent-root" : {
      paddingLeft: theme.spacing(3),
      paddingRight: theme.spacing(3),
    },
    "& .MuiList-root": {
      marginLeft: theme.spacing(-2),
    },
    "& .cards-answerInstructions" : {
      margin: theme.spacing(-3,0,1),
      padding: theme.spacing(1, 0),
    },
  },
  editModeAnswers: {
    "& .MuiListItem-root:hover" : {
      background: theme.palette.action.hover,
      borderRadius: theme.spacing(0.5),
    }
  },
  viewModeAnswers :{
    "& .MuiList-root": {
      padding: 0,
    },
    "& .MuiListItem-root" : {
      paddingTop: 0,
      paddingBottom: 0,
    }
  },
});

export default questionStyles;
