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

const style = theme => ({
  mainActionButton: {
    margin: theme.spacing(1),
    position: "fixed",
    bottom: theme.spacing(0),
    right: theme.spacing(4),
    zIndex: 100,
    "& .MuiCircularProgress-root" : {
      position: 'absolute',
      top: 0,
      left: 0,
    },
    "& .MuiFab-extended + .MuiCircularProgress-root" : {
      margin: theme.spacing(1, 1.5),
    },
    "& .MuiFab-extended .MuiSvgIcon-root" : {
      marginRight: theme.spacing(1),
    },
    "& .MuiFab-extended .MuiFab-label" : {
      marginRight: theme.spacing(1),
    },
  },
  userInputAssistant: {
    "& .MuiCard-root" : {
      maxWidth: "375px",
      border: "2px solid " + theme.palette.primary.main,
      "&.Uia-placement-right": {
        marginLeft: theme.spacing(2),
        "&:before" : {
          content: "''",
          display: "block",
          borderTop: theme.spacing(2) + "px solid transparent",
          borderBottom: theme.spacing(2) + "px solid transparent",
          borderRight: theme.spacing(2) + "px solid " + theme.palette.primary.main,
          position: "absolute",
          left: 0,
          top: "50%",
          marginTop: theme.spacing(-2),
        },
      },
      "&.Uia-placement-bottom": {
        margin: theme.spacing(3,4,0),
      },
      "& .MuiAvatar-root" : {
        background: theme.palette.primary.main,
      },
      "&.Uia-hint-secondary" : {
        borderColor: theme.palette.secondary.main,
        "&.Uia-placement-right:before" : {
          borderRightColor: theme.palette.secondary.main,
        },
        "& .MuiAvatar-root" : {
          background: theme.palette.secondary.main,
        },
      },
      "&.Uia-success" : {
        borderColor: theme.palette.success.main,
        "&.Uia-placement-right:before" : {
          borderRightColor: theme.palette.success.main,
        },
        "& .MuiAvatar-root" : {
          background: theme.palette.success.main,
        },
      },
      "&.Uia-info" : {
        borderColor: theme.palette.info.main,
        "&.Uia-placement-right:before" : {
          borderRightColor: theme.palette.info.main,
        },
        "& .MuiAvatar-root" : {
          background: theme.palette.info.main,
        },
      },
      "&.Uia-warning" : {
        borderColor: theme.palette.warning.main,
        "&.Uia-placement-right:before" : {
          borderRightColor: theme.palette.warning.main,
        },
        "& .MuiAvatar-root" : {
          background: theme.palette.warning.main,
        },
      },
      "&.Uia-error" : {
        borderColor: theme.palette.error.main,
        "&.Uia-placement-right:before" : {
          borderRightColor: theme.palette.error.main,
        },
        "& .MuiAvatar-root" : {
          background: theme.palette.error.main,
        },
      },
    },
  },
});

export default style;
