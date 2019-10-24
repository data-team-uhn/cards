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

import Actions from "./actions";

const Config = require("./config.json");
const Phase = require("./phaseCodes.json");

const useStyles = makeStyles(theme => ({
  root: {
    width: "100%",
    marginTop: theme.spacing(3),
    overflowX: "auto"
  },
  table: {
    tableLayout: "fixed"
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

const HeaderTableCell = withStyles(theme => ({
  head: {
    backgroundColor: theme.palette.common.black,
    color: theme.palette.common.white
  }
}))(TableCell);

const StyledTableCell = withStyles(theme => ({
  body: {
    whiteSpace: "pre",
    textAlign: "right"
  },
}))(TableCell);

export default function VocabularyTable(props) {
  const classes = useStyles();
  const vocabList = props.remoteVocabList;
  const headingTypography = Config["tableHeadingTypography"];
  const bodyTypography = Config["tableBodyTypography"];
  const columnWidths = Config["tableColumnWidths"]

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
        <Table className={classes.table}>
          <TableHead>
            <TableRow>

              <HeaderTableCell width={columnWidths["id"]}>
                <Typography variant={headingTypography}>ID</Typography>
              </HeaderTableCell>

              <HeaderTableCell width={columnWidths["name"]}>
                <Typography variant={headingTypography}>Name</Typography>
              </HeaderTableCell>

              <HeaderTableCell width={columnWidths["version"]}>
                <Typography variant={headingTypography}>Version</Typography>
              </HeaderTableCell>

              <HeaderTableCell width={columnWidths["releaseDate"]}>
                <Typography variant={headingTypography}>Release Date</Typography>
              </HeaderTableCell>

              <HeaderTableCell width={columnWidths["actions"]}/>

            </TableRow>
          </TableHead>

          <TableBody>
            {vocabList.map((vocab) => {
              if (vocab.status === "production") {
                const date = new Date(vocab.released);
                return(
                  <StyledTableRow key={"Row_"+vocab.ontology.acronym}>

                    <TableCell component="th" scope="row" >
                      <Typography variant={bodyTypography}>
                        {vocab.ontology.acronym}
                      </Typography>
                    </TableCell>

                    <TableCell>
                      <Typography variant={bodyTypography}>
                        {vocab.ontology.name}
                      </Typography>
                    </TableCell>

                    <TableCell>
                      <Typography variant={bodyTypography} noWrap>
                        {vocab.version}
                      </Typography>
                    </TableCell>

                    <StyledTableCell>
                      <Typography variant={bodyTypography}>
                        {date.toString().substring(4,15)}
                      </Typography>
                    </StyledTableCell>

                    <StyledTableCell>
                      <Actions
                        acronym={vocab.ontology.acronym}
                        description={vocab.description}
                        initPhase={initPhase(vocab.ontology.acronym, vocab.released)}
                        name={vocab.ontology.name}
                        released={vocab.released}
                        version={vocab.version}
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
