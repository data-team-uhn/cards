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

import {
  Grid,
  makeStyles,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  withStyles
} from "@material-ui/core";

import Actions from "./Actions";

const Config = require("./config.json");
const Phase = require("./phaseCodes.json");

const useStyles = makeStyles(theme => ({
  root: {
    width: "100%",
    marginTop: theme.spacing(3),
    overflowX: "auto"
  }
}));

// Creating customized Table components for a nicer look

const StyledTableRow = withStyles(theme => ({
  root: {
    "&:nth-of-type(odd)": {
      backgroundColor: theme.palette.background.default
    }
  },
}))(TableRow);

const StyledTableCell = withStyles(theme => ({
  head: {
    backgroundColor: theme.palette.common.black,
    color: theme.palette.common.white,
    whiteSpace: "pre"
  },
  body: {
    whiteSpace: "pre",
    textAlign: "right"
  },
}))(TableCell);

export default function RenderVocabListTable(props) {
  const classes = useStyles();
  const vocabList = props.remoteVocabList;
  const tableHeadingSize = Config["tableHeadingSize"];
  const tableBodySize = Config["tableBodySize"];

  function initPhase(acronym, released) {
    if (!props.optimisedDateList.hasOwnProperty(acronym)) {
      return Phase["Not Installed"];
    }
    const remoteReleaseDate = new Date(released);
    const localInstallDate = new Date(props.optimisedDateList[acronym]);
    return (remoteReleaseDate > localInstallDate ? Phase["Update Available"] : Phase["Latest"]);
  }

  return(
    <Grid item>
      <Paper className={classes.root}>
        <Table>
          <TableHead>
            <TableRow>

              <StyledTableCell>
                <Typography variant={tableHeadingSize}>ID</Typography>
              </StyledTableCell>

              <StyledTableCell>
                <Typography variant={tableHeadingSize}>Name</Typography>
              </StyledTableCell>

              <StyledTableCell>
                <Typography variant={tableHeadingSize}>Version</Typography>
              </StyledTableCell>

              <StyledTableCell>
                <Typography variant={tableHeadingSize}>Release Date</Typography>
              </StyledTableCell>

              <StyledTableCell/>

            </TableRow>
          </TableHead>

          <TableBody>
            {vocabList.map((vocab) => {
              if (vocab.status === "production") {
                const date = new Date(vocab.released);
                return(
                  <StyledTableRow key={"Row_"+vocab.ontology.acronym}>

                    <TableCell component="th" scope="row" >
                      <Typography variant={tableBodySize}>
                        {vocab.ontology.acronym}
                      </Typography>
                    </TableCell>

                    <TableCell>
                      <Typography variant={tableBodySize}>
                        {vocab.ontology.name}
                      </Typography>
                    </TableCell>

                    <TableCell>
                      <Typography variant={tableBodySize}>
                        {vocab.version ? vocab.version.substring(0, Math.min(20, vocab.version.length)) : ""}
                      </Typography>  
                    </TableCell>

                    <TableCell>
                      <Typography variant={tableBodySize}>
                        {date.toString().substring(4,15)}
                      </Typography>
                    </TableCell>

                    <StyledTableCell>
                      <Actions
                        acronym={vocab.ontology.acronym}
                        description={vocab.description}
                        initPhase={initPhase(vocab.ontology.acronym, vocab.released)}
                        name={vocab.ontology.name}
                        released={vocab.released}
                        version={vocab.version ? vocab.version.substring(0, Math.min(20, vocab.version.length)) : ""}
                      />
                    </StyledTableCell>

                  </StyledTableRow>
                );
              }
            })}
          </TableBody>

        </Table>
      </Paper>
    </Grid>
  );
}
