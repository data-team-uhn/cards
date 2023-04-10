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

import React, { useEffect, useState } from "react";

import {
  Grid,
  Typography,
  Tooltip
} from "@mui/material";

import MaterialReactTable from "material-react-table";
import VocabularyActions from "./vocabularyActions"
import Search from "./search";

const Phase = require("./phaseCodes.json");

export default function VocabularyTable(props) {
  const { vocabList, type  } = props;
  const [filterTable, setFilterTable] = useState(false);
  const [acronymFilterList, setAcronymFilterList] = useState([]);
  const [filteredVocabs, setFilteredVocabs] = useState([]);
  const [loading, setLoading] = React.useState(false);

  useEffect(() => {
    if (filterTable) {
      if (acronymFilterList.length == 0) {
        setFilteredVocabs([]);
      } else {
        setFilteredVocabs(vocabList.slice().filter(vocab => acronymFilterList.includes(vocab.acronym)));
      }
    }
  }, [filterTable, acronymFilterList])

  return(
    <React.Fragment>
      {(type === "remote") &&
      <Search
        setAcronymFilterList={setAcronymFilterList}
        setParentFilterTable={setFilterTable}
        setLoading={setLoading}
        vocabList={vocabList}
      />
      }

      {(vocabList.length > 0) &&
      <Grid item>
        <MaterialReactTable
            enableColumnActions={false}
            enableSorting={false}
            enableTopToolbar={false}
            enableToolbarInternalActions={false}
            state={{ isLoading: loading }}
            initialState={{ showColumnFilters: true }}
            columns={[
              { header: 'Identifier', accessorKey: 'acronym', size: 30, filterFn: 'contains' },
              { header: 'Name', accessorKey: 'name', filterFn: 'contains' },
              { header: 'Version', accessorKey: 'version', size: 10, enableColumnFilter: false,
                Cell: ({ renderedCellValue, row }) => row.original.version &&
                  <Tooltip title={row.original.version}>
                    <Typography style={{fontWeight: "inherit"}} noWrap>
                      {row.original.version}
                    </Typography>
                  </Tooltip>
              },
              { header: type === "local" ? "Installation Date" : "Release Date",
                accessorKey: 'released',
                size: 20,
                enableColumnFilter: false,
                Cell: ({ renderedCellValue, row }) => (new Date(type === "local" ? row.original.installed : row.original.released)).toString().substring(4,15)
              }
            ]}
            muiTableHeadCellProps={{
              sx: (theme) => ({
                background: theme.palette.grey['200'],
              }),
            }}
            displayColumnDefOptions={{
              'mrt-row-actions': {
                size: 50,
                muiTableHeadCellProps: {align: "right"},
                muiTableBodyCellProps: ({ cell }) => ({
                  sx: {
                    whiteSpace: "pre",
                    textAlign: "right",
                    paddingRight: "0.3rem"
                  },
                }),
                enableColumnFilter: false,
              },
            }}
            data={filterTable ? filteredVocabs : vocabList}
            enableRowActions
            positionActionsColumn="last"
            renderRowActions={({ row }) => (
              <VocabularyActions
                 type={type}
                 vocabulary={row.original}
                 updateLocalList={props.updateLocalList}
                 initPhase={props.acronymPhaseObject[row.original.acronym] || Phase["Not Installed"]}
                 setPhase={(phase) => props.setPhase(row.original.acronym, phase)}
                 addSetter={(setFunction) => props.addSetter(row.original.acronym, setFunction, type)}
               />
            )}
          />
      </Grid>
      }
    </React.Fragment>
  );
}
