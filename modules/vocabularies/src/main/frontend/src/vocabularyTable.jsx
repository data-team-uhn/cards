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

import Search from "./search";
import VocabularyEntry from "./vocabularyEntry";

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
const HeaderTableCell = withStyles(theme => ({
  head: {
    backgroundColor: theme.palette.common.black,
    color: theme.palette.common.white
  }
}))(TableCell);

export default function VocabularyTable(props) {
  const [filterTable, setFilterTable] = React.useState(false);
  const [acronymList, setAcronymList] = React.useState([]);

  const vocabList = props.vocabList;
  const classes = useStyles();
  const headingTypography = Config["tableHeadingTypography"];
  const columnWidths = Config["tableColumnWidths"]

  return(
    <React.Fragment>
      {(props.type === "remote") &&
      <Search
        // Provide Search with a function that allows it to concatenate a list to acronymList
        concatParentAcronymList={list => {
          setAcronymList(oldAcronymList =>
            // Create a Set with the concatenated list which removes duplicates
            // Use this duplicate free Set to make a list object and return it
            [...new Set(
              // New List = oldAcronymList + list
              oldAcronymList.concat(list)
              )
            ]);
        }}
        setParentAcronymList={setAcronymList}
        setParentFilterTable={setFilterTable}
        vocabList={vocabList}
      />
      }

      {(vocabList.length > 0) &&
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
                if (props.type === "local" || vocab.status === "production" || vocab.status === "beta") {
                  return(
                    <VocabularyEntry
                      key={vocab.ontology.acronym}
                      acronym={vocab.ontology.acronym}
                      name={vocab.ontology.name}
                      source={vocab.source}
                      description={vocab.description}
                      released={vocab.released}
                      version={vocab.version}
                      updateLocalList={(action) => {props.updateLocalList(action, vocab)}}
                      // If filterTable is True, then check if the acronym is of a vocabulary to be displayed
                      // If filterTable is False, then don't hide anything
                      hidden={filterTable && !acronymList.includes(vocab.ontology.acronym)}
                      initPhase={(vocab.source == "fileupload") ? Phase["Latest"] : (
                        props.acronymPhaseObject.hasOwnProperty(vocab.ontology.acronym) ?
                          props.acronymPhaseObject[vocab.ontology.acronym]
                          :
                          Phase["Other Source"]
                      )} 
                      setPhase={(phase) => props.setPhase(vocab.ontology.acronym, phase)}
                      addSetter={(setFunction) => props.addSetter(vocab.ontology.acronym, setFunction, props.type)}
                    />
                  );
                }
              })}
            </TableBody>

          </Table>
        </Paper>
      </Grid>
      }
    </React.Fragment>
  );
}
