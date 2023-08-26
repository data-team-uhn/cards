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

const statisticsStyle = theme => ({
  statsContainer: {
    minHeight: "50vh",
    marginTop: theme.spacing(4),
  },
  statsCard: {
    "& .MuiCardHeader-root" : {
      paddingBottom: 0,
    },
    "& .MuiCardContent-root" : {
      paddingTop: 0,
    },
    "& .recharts-legend-wrapper" : {
      marginRight: "-10px",
    },
  },
  subjectFilterInput: {
    width: "100%"
  },
  categoryOption: {
    whiteSpace: "normal",
  },
  customTooltip: {
    margin: 0,
    padding: "10px",
    backgroundColor: theme.palette.grey[50],
    border: `1px solid ${theme.palette.grey[50]}`,
    whiteSpace: "nowrap",
  },
  label : {
    margin: 0,
    padding: 0,
    listStyleType: "none"
  }
});

export default statisticsStyle;
