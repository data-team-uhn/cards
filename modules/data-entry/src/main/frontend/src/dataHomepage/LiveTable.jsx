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

import React, { useState } from "react";
import { Paper, Table, TableHead, TableBody, TableFooter, TableRow, TableCell, TablePagination } from "@material-ui/core";
import { Card, CardHeader, CardContent, CardActions, Typography, Button } from "@material-ui/core";
import moment from "moment";

// Convert a date into the given format string
// If the date is invalid (usually because it is missing), return ""
let _formatDate = (date, formatString) => {
  let dateObj = moment(date);
  if (dateObj.isValid()) {
    return dateObj.format(formatString)
  }
  return "";
};

export default function LiveTable(props) {
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Define the component's state

  const { customUrl, columns, classes } = props;
  const [tableData, setTableData] = useState();
  const [paginationData, setPaginationData] = useState(
    {
      "offset": 0,
      "limit": 50,
      "displayed": 0,
      "total": -1,
      "page": 0,
    }
  );
  const [fetchStatus, setFetchStatus] = useState(
    {
      "currentRequestNumber": -1,
      "currentFetch": false,
      "fetchError": false,
    }
  );
  // The base URL to fetch from.
  // This can either be a custom URL provided in props,
  // or an URL obtained from the current location by extracting the last path segment and appending .paginate
  // Later, the query string of this URL base will be updated with the pagination details.
  const urlBase = (
    customUrl ?
      new URL(customUrl, window.location.origin)
    :
      new URL(window.location.pathname.substring(window.location.pathname.lastIndexOf("/")) + ".paginate", window.location.origin)
  );

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Define the component's behavior

  let fetchData = (newPage) => {
    if (fetchStatus.currentFetch) {
      // TODO: abort previous request
    }

    let url = new URL(urlBase);
    url.searchParams.set("offset", newPage.offset);
    url.searchParams.set("limit", newPage.limit || paginationData.limit);
    url.searchParams.set("req", ++fetchStatus.currentRequestNumber);
    let currentFetch = fetch(url);
    setFetchStatus(Object.assign({}, fetchStatus, {
      "currentFetch": currentFetch,
      "fetchError": false,
    }));
    currentFetch.then((response) => response.ok ? response.json() : Promise.reject(response)).then(handleResponse).catch(handleError);
    // TODO: update the displayed URL with pagination details, so that we can share/reload at the same page
  };

  let handleResponse = (json) => {
    if (+json.req !== fetchStatus.currentRequestNumber) {
      // This is the response for an older request. Discard it, wait for the right one.
      return;
    }
    setTableData(json.rows);
    setPaginationData(
      {
        "offset": json.offset,
        "limit": json.limit,
        "displayed": json.returnedrows,
        "total": json.totalrows,
        "page": Math.floor(json.offset / json.limit),
      }
    );
  };

  let handleError = (response) => {
    setFetchStatus(Object.assign({}, fetchStatus, {
      "currentFetch": false,
      "fetchError": (response.statusText ? response.statusText : response.toString()),
    }));
    setTableData();
  };

  let makeRow = (entry) => {
    return (
      <TableRow key={entry[0]}>
        { columns ?
          (
            columns.map((column, index) => makeCell(entry, column, index))
          )
        :
          (
            <TableCell><a href={entry[0]}>{entry[1].title}</a></TableCell>
          )
        }
      </TableRow>
    );
  };

  let makeCell = (entry, column, index) => {
    let content = entry[1];
    for (let subpath of column.key.split('/')) {
      content = content && content[subpath];
    }
    let target = false;

    // Handle links
    if (column.link === 'entry') {
      target = entry[0];
    } else if (column.link === 'value') {
      target = content;
    }

    // Handle display formatting
    if (column.format && column.format.startsWith('date')) {
      // The format can be either just "date", in which case a default date format is used, or "date:FORMAT".
      // Cutting after the fifth char means that either we skip "date:" and read the format,
      // or we just get the empty string and use the default format.
      let format = column.format.substring(5) || 'YYYY-MM-dd';
      content = _formatDate(content, format);
    }

    // Render the cell
    return <TableCell key={index}>{ target ? (<a href={target}>{content}</a>) : ( content )}</TableCell>
  };

  let handleChangePage = (event, page) => {
    // TODO: Clicking twice on prev page makes two requests for the same "previous" page.
    // Expected behavior is one of:
    // - disable the buttons while waiting for the new page, so only one click is possible
    // - or abort the first request and request the two-behind previous page
    // The second behavior seems more intuitive, but it needs to store two states:
    // - the currently displayed page
    // - the expected page
    // TODO: Change the code to behave better
    fetchData({
      "offset" : page * paginationData.limit,
    });
  };

  let handleChangeRowsPerPage = (event) => {
    const newPageSize = +event.target.value;
    fetchData({
      "offset": Math.floor(paginationData.offset / newPageSize) * newPageSize,
      "limit": newPageSize,
    });
  };

  // Initialize the component: if there's no data loaded yet, fetch the first page

  if (fetchStatus.currentRequestNumber == -1) {
    fetchData(paginationData);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // The rendering code

  return (
    // We wrap everything in a Paper for a nice separation, as a Table has no background or border of its own.
    <Paper>
      {/*
      // stickyHeader doesn't really work right now, since the Paper just extends all the way down to fit the table.
      // The whole UI needs to be redesigned so that we can set a maximum height to the Paper,
      // which would make the table scroll internally.
      // <Table stickyHeader>
      */}
      <Table>
        {/* TODO: Also add pagination controls at the top? Or maybe the UI redesign mentioned above will fix the problem. */}
        <TableHead>
          {/* TODO: Move the whole header in a separate, smarter component that can do filtering and sorting. */}
          <TableRow>
          { columns ?
            (
              columns.map((column, index) => <TableCell key={index}>{column.label}</TableCell>)
            )
          :
            (
              <TableCell>Name</TableCell>
            )
          }
          </TableRow>
        </TableHead>
        <TableBody>
          { fetchStatus.fetchError ?
            (
              <TableRow>
                <TableCell colSpan={columns ? columns.length : 1}>
                  <Card>
                    <CardHeader title="Error"/>
                    <CardContent>
                      <Typography>{fetchStatus.fetchError}</Typography>
                    </CardContent>
                    <CardActions>
                      <Button onClick={() => fetchData(paginationData)}>Retry</Button>
                    </CardActions>
                  </Card>
                </TableCell>
              </TableRow>
            )
            :
            tableData ?
              ( Object.entries(tableData).map(makeRow) )
              :
              ( <TableRow><TableCell colSpan={columns ? columns.length : 1}>Please wait...</TableCell></TableRow> )
            /* TODO: Better progress bar, add some Suspense */
          }
        </TableBody>
      </Table>
      {/*
      // The pagination is outside the table itself to support internal scrolling of the table.
      // The element used by TablePagination by default is TableCell, but since it is not in a TableRow, we have to override this to be a <div>.
      */}
      { tableData && (
          <TablePagination
            component="div"
            rowsPerPageOptions={[10, 50, 100, 1000]}
            count={paginationData.total}
            rowsPerPage={paginationData.limit}
            page={paginationData.page}
            onChangePage={handleChangePage}
            onChangeRowsPerPage={handleChangeRowsPerPage}
          />
        )
      }
    </Paper>
  );
}
