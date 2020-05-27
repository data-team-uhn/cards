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

import React, { useState, useEffect } from "react";
import { Paper, Table, TableHead, TableBody, TableRow, TableCell, TablePagination } from "@material-ui/core";
import { Card, CardHeader, CardContent, CardActions, Chip, Typography, Button, withStyles } from "@material-ui/core";
import { Link } from 'react-router-dom';
import moment from "moment";

import Filters from "./Filters.jsx";

import LiveTableStyle from "./tableStyle.jsx";

// Convert a date into the given format string
// If the date is invalid (usually because it is missing), return ""
let _formatDate = (date, formatString) => {
  let dateObj = moment(date);
  if (dateObj.isValid()) {
    return dateObj.format(formatString)
  }
  return "";
};

function LiveTable(props) {
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Define the component's state

  const { customUrl, columns, defaultLimit, joinChildren, formPreview, onSendFormIds, classes, filters, ...rest } = props;
  const [tableData, setTableData] = useState();
  const [cachedFilters, setCachedFilters] = useState(null);
  const [paginationData, setPaginationData] = useState(
    {
      "offset": 0,
      "limit": defaultLimit,
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
  // This holds the full form JSONs, once it is received from the server
  let [ formData, setFormData ] = useState();
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

    // filters should be nullable, but if left undefined we use the cached filters
    let filters = (newPage.filters === null ? null : (newPage.filters || cachedFilters));

    // Add the filters (if they exist)
    if (filters != null) {
      url.searchParams.set("joinchildren", joinChildren);
      filters["fields"].forEach((field) => {url.searchParams.append("filternames", field)});
      filters["comparators"].forEach((comparator) => {url.searchParams.append("filtercomparators", comparator)});
      filters["values"].forEach((value) => {url.searchParams.append("filtervalues", value)});
      filters["empties"].forEach((value) => {url.searchParams.append("filterempty", value)});
      filters["notempties"].forEach((value) => {url.searchParams.append("filternotempty", value)});
    }
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

  // FORM

  const requests = [];

  // Callback method for the `fetchFormData` method, invoked when the request failed.
  let handleFormError = (response) => {
    setError(response);
    setFormData([]);  // Prevent an infinite loop if data was not set
  };

  // Fetch data for each related form (e.g. in Subject table)
  useEffect(() => {
    if (tableData && formPreview) {
      tableData.map((entry) => {
        console.log(entry["@name"]);
        requests.push(
          fetch(`/Forms/${entry["@name"]}.deep.json`)
          .then((response) => response.ok ? response.json() : Promise.reject(response))
          .then((json) => requests.push(json))
          .catch(handleFormError)
        )
      });
      
      // update formData state once data for all forms have been fetched
      Promise.all(requests).then(() => {
        console.log(requests)
        setFormData(requests.filter((data) => data["jcr:primaryType"] == "lfs:Form"));
        console.log(formData);
      });
    }
  }, [tableData]);


  let makeRow = (entry, index) => {
    return (
      <React.Fragment>
        <TableRow key={entry["@path"]}>
          { columns ?
            (
              columns.map((column, index) => makeCell(entry, column, index))
            )
          :
            (
              <TableCell><a href={entry["@path"]}>{entry.title}</a></TableCell>
            )
          }
        </TableRow>
        
        {/* if a form preview is needed (e.g. in Subject table, render the rest) */}
        {/* todo: place below in a separate function/component */}
        {formData ?
          (
            <div>
              {
                formData[index] && formData[index].questionnaire && formData[index].questionnaire.title ?
                  <Typography variant="overline">{formData[index].questionnaire.title}</Typography>
                : ""
              }
            </div>
          )
          : ""
        }
        
        {/* render the form preview and fetch here OR send entry[@name] to subject (contains the form id) and perform fetch there */}
      </React.Fragment>
    );
  };

  let makeCell = (entry, column, index) => {
    let content = getNestedValue(entry, column.key);

    // Handle display formatting
    if (column.format && column.format.startsWith('date')) {
      // The format can be either just "date", in which case a default date format is used, or "date:FORMAT".
      // Cutting after the fifth char means that either we skip "date:" and read the format,
      // or we just get the empty string and use the default format.
      let format = column.format.substring(5) || 'YYYY-MM-dd';
      content = _formatDate(content, format);
    }

    // Handle links
    if (column.link) {
      if (column.link === 'path') {
        content = (<a href={entry["@path"]}>{content}</a>);
      } else if (column.link === 'dashboard+path') {
        content = (<Link to={"/content.html" + entry["@path"]}>{content}</Link>);
      } else if (column.link === 'value') {
        content = (<a href={content}>{content}</a>);;
      } else if (column.link === 'dashboard+value') {
        content = (<Link to={"/content.html" + content}>{content}</Link>);
      } else if (column.link.startsWith('field:')) {
        content = (<a href={getNestedValue(entry, column.link.substring('field:'.length))}>{content}</a>);
      } else if (column.link.startsWith('dashboard+field:')) {
        content = (<Link to={"/content.html" + getNestedValue(entry, column.link.substring('dashboard+field:'.length))}>{content}</Link>);
      }
    }

    // Render the cell
    return <TableCell key={index}>{content}</TableCell>
  };

  let getNestedValue = (entry, path) => {
    // Display the JCR node id
    if (path == 'jcr:uuid') {
      let el = /Forms\/(.+)/.exec(entry["@path"]);
      if (el && el[1]) {
        return el[1];
      }
    }

    let result = entry;
    for (let subpath of path.split('/')) {
      result = result && result[subpath];
    }
    return result;
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

  // Callback to the filters component to handle a change in filters
  let handleChangeFilters = (newFilters) => {
    // Parse out the new filters
    let fields = [];
    let comparators = [];
    let values = [];
    let empties = [];
    let notempties = [];

    let filtersNotBlank = false;
    newFilters.forEach((filter) => {
      filtersNotBlank = true;
      if (filter.comparator === "is empty") {
        empties.push(filter.uuid);
      } else if (filter.comparator === "is not empty") {
        notempties.push(filter.uuid);
      } else {
        fields.push(filter.uuid);
        comparators.push(filter.comparator);
        values.push(filter.value);
      }
    });

    if (filtersNotBlank) {
      let filter_obj = {
        fields: fields,
        comparators: comparators,
        values: values,
        empties: empties,
        notempties: notempties
      };
      setCachedFilters(filter_obj);
      fetchData({
        "filters": filter_obj
      });
    } else {
      setCachedFilters(null);
      fetchData({
        "filters": null
      });
    }
  }

  // Initialize the component: if there's no data loaded yet, fetch the first page

  if (fetchStatus.currentRequestNumber == -1) {
    fetchData(paginationData);
  }

  
  // if (tableData && formPreview) {
  //   console.log("this gets called")
  //   onSendFormIds(tableData);
  // }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // The rendering code

  /*
  // The pagination is outside the table itself to support internal scrolling of the table.
  // The element used by TablePagination by default is TableCell, but since it is not in a TableRow, we have to override this to be a <div>.
  */
  const paginationControls = tableData && (
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

  return (
    // We wrap everything in a Paper for a nice separation, as a Table has no background or border of its own.
    <Paper elevation={0}>
      {filters && <Filters onChangeFilters={handleChangeFilters} {...rest} />}
      <div>
        {paginationControls}
      </div>
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
              columns.map((column, index) => <TableCell key={index} className={classes.tableHeader}>{column.label}</TableCell>)
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
              ( tableData.map(makeRow))
              :
              ( <TableRow><TableCell colSpan={columns ? columns.length : 1}>Please wait...</TableCell></TableRow> )
            /* TODO: Better progress bar, add some Suspense */
          }
        </TableBody>
      </Table>
      {paginationControls}
    </Paper>
  );
}

LiveTable.defaultProps = {
  defaultLimit: 50
}

export default withStyles(LiveTableStyle)(LiveTable);
