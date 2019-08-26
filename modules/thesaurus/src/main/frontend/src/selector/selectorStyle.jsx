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

import { dangerColor, grayColor } from "MaterialDashboardReact/ThemeStyle";

const selectorStyle = theme => ({
    selectionChild: {
        paddingLeft: theme.spacing(0),
    },
    childFormControl: {
        marginLeft: theme.spacing(0),
    },
    deleteButton: {
        padding: theme.spacing(1,0),
        margin: theme.spacing(-1,0),
        fontSize: "10px",
        minWidth: "42px",
        "& svg": {
            color: grayColor[8],
            "&:hover": {
                color: dangerColor[0],
            }
        }
    },
    inputLabel: {
        paddingLeft: theme.spacing(1),
        display: "inline-block",
    },
    checkbox: {
        margin: theme.spacing(-2,0),
    },
    selectionList: {
        paddingBottom: "0px",
    }
});

export default selectorStyle;
