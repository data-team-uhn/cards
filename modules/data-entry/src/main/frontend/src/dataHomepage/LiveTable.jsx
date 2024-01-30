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

import React, { useState, useEffect, useContext } from "react";
import { Paper, Table, TableHead, TableBody, TableRow, TableCell, TablePagination } from "@mui/material";
import { Card, CardHeader, CardContent, CardActions, Typography, Button, LinearProgress } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import { Link } from 'react-router-dom';
import { DateTime } from "luxon";

import Filters from "./Filters.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

import LiveTableStyle from "./tableStyle.jsx";

// Convert a date into the given format string
// If the date is invalid (usually because it is missing), return ""
let _formatDate = (date, formatString) => {
  let dateObj = DateTime.fromISO(date);
  if (dateObj.isValid) {
    return dateObj.toFormat(formatString);
  }
  return "";
};

function LiveTable(props) {
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Define the component's state

  const { customUrl, resourceSelectors, columns, defaultLimit, updateData, classes,
    filters, entryType, actions, admin, disableTopPagination, disableBottomPagination,
    onDataReceived, onFiltersChange, filtersJsonString, ...rest } = props;
  const [tableData, setTableData] = useState();
  const [cachedFilters, setCachedFilters] = useState(null);
  const [paginationData, setPaginationData] = useState(
    {
      "offset": 0,
      "limit": defaultLimit,
      "displayed": 0,
      "total": -1,
      "page": 0,
      "totalIsApproximate": false,
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
      new URL(
        ((s) => s.substring(s.lastIndexOf("/")))(window.location.pathname.replace(/\/$/, "")).concat(".paginate"),
        window.location.origin
      )
  );

  const globalLoginDisplay = useContext(GlobalLoginContext);

  // When data is changed, trigger a new fetch in the table
  useEffect(() => {
    // subscribe event
    window.addEventListener("LivetableRefresh",  refresh);
    return () => {
      // unsubscribe event
      document.removeEventListener("LivetableRefresh",  refresh);
    };
  }, [entryType]);

  // When new data is added, trigger a new fetch
  useEffect(() => {
    if (updateData){
      refresh();
    }
  }, [updateData]);

  // When the data path is changed, trigger a new fetch
  useEffect(() => {
    if (customUrl){
      refresh();
    }
  }, [customUrl]);

  // Initialize the component: if there's no data loaded yet, fetch the first page
  useEffect(() => {
    if (fetchStatus.currentRequestNumber == -1) fetchData(paginationData, true);
  }, [fetchStatus.currentRequestNumber]);

  let refresh = () => {
    setFetchStatus(Object.assign({}, fetchStatus, {
      "currentRequestNumber": -1,
    }));
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Define the component's behavior

  let fetchData = (newPage, goToStart) => {
    if (fetchStatus.currentFetch) {
      // TODO: abort previous request
    }

    let url = new URL(urlBase);
    url.searchParams.set("offset", goToStart ? 0 : newPage.offset ?? paginationData.offset);
    url.searchParams.set("limit", newPage.limit || paginationData.limit);
    url.searchParams.set("req", ++fetchStatus.currentRequestNumber);
    resourceSelectors && url.searchParams.set("resourceSelectors", resourceSelectors);

    // filters should be nullable, but if left undefined we use the cached filters
    let filters = (newPage.filters === null ? null : (newPage.filters || cachedFilters));

    // Add the filters (if they exist)
    if (filters != null) {
      filters["fields"].forEach((field) => {url.searchParams.append("filternames", field)});
      filters["comparators"].forEach((comparator) => {url.searchParams.append("filtercomparators", comparator)});
      filters["values"].forEach((value) => {url.searchParams.append("filtervalues", value)});
      filters["types"].forEach((type) => {url.searchParams.append("filtertypes", type)});
      filters["empties"].forEach((value) => {url.searchParams.append("filterempty", value)});
      filters["notempties"].forEach((value) => {url.searchParams.append("filternotempty", value)});
    }
    let currentFetch = fetchWithReLogin(globalLoginDisplay, url);
    setFetchStatus(Object.assign({}, fetchStatus, {
      "currentFetch": currentFetch,
      "fetchError": false,
    }));
    // Clear tableData (set it to undefined) so that Please wait... is displayed
    setTableData();
    currentFetch.then((response) => response.ok ? response.json() : Promise.reject(response)).then(handleResponse).catch(handleError);
    // TODO: update the displayed URL with pagination details, so that we can share/reload at the same page
  };

  let handleResponse = (json) => {
    if (+json.req !== fetchStatus.currentRequestNumber) {
      // This is the response for an older request. Discard it, wait for the right one.
      return;
    }
    setTableData(json.rows);
    onDataReceived && onDataReceived(json.rows);
    setPaginationData(
      {
        "offset": json.offset,
        "limit": json.limit,
        "displayed": json.returnedrows,
        "total": json.totalrows,
        "page": Math.floor(json.offset / json.limit),
        "totalIsApproximate": json.totalIsApproximate,
      }
    );
  };

  let handleError = (response) => {
    let err = response.statusText ? response.statusText : response.toString();
    if (response.status == 404) {
      err = "Access to data is pending the approval of your account";
    }
    setFetchStatus(Object.assign({}, fetchStatus, {
      "currentFetch": false,
      "fetchError": err,
    }));
    setTableData([]);
  };

  let makeRow = (entry, i) => {
    return (
      <TableRow key={entry["@path"] + i}>
        { columns ?
          (
            columns.map((column, index) => makeCell(entry, column, index))
          )
        :
          (
            <TableCell><a href={entry["@path"]}>{entry.title}</a></TableCell>
          )
        }
        { actions ? makeActions(entry, actions, columns ? columns.count : 0) : null}
      </TableRow>
    );
  };

  let makeCell = (entry, column, index) => {
    let content = getNestedValue(entry, column.key);

    // Handle display formatting
    if (column.format && typeof column.format === "function") {
      content = column.format(entry);
    } else if (column.format && column.format.startsWith('date')) {
      // The format can be either just "date", in which case a default date format is used, or "date:FORMAT".
      // Cutting after the fifth char means that either we skip "date:" and read the format,
      // or we just get the empty string and use the default format.
      let format = column.format.substring(5) || 'yyyy-MM-dd';
      content = _formatDate(content, format);
    }

    // allow livetable to link to components in the admin dashboard
    // if livetable item must link to a component within the admin dashboard, set "admin": true
    let pathPrefix = (admin ? "/content.html/admin" : "/content.html");

    if (column.link) {
      if (column.link === 'path') {
        content = (<a href={entry["@path"]}>{content}</a>);
      } else if (column.link === 'dashboard+path') {
        content = (<Link to={pathPrefix + entry["@path"]} underline="hover">{content}</Link>);
      } else if (column.link === 'value') {
        content = (<a href={content}>{content}</a>);
      } else if (column.link === 'dashboard+value') {
        content = (<Link to={pathPrefix + content} underline="hover">{content}</Link>);
      } else if (column.link.startsWith('field:')) {
        content = (<a href={getNestedValue(entry, column.link.substring('field:'.length))}>{content}</a>);
      } else if (column.link.startsWith('dashboard+field:')) {
        content = (<Link to={pathPrefix + getNestedValue(entry, column.link.substring('dashboard+field:'.length))} underline="hover">{content}</Link>);
      }
    }

    // Render the cell
    return <TableCell key={index} className={ column.type === 'actions' ? classes.tableActions : ""} {...column.props}>{content}</TableCell>
  };

  let makeActions = (entry, actions, index) => {
    let content = actions.map((Action, index) => {
      return <Action
        key={index}
        entryPath={entry["@path"]}
        entryName={getEntityIdentifier(entry)}
        onComplete={refresh}
        entryType={entryType}
        entryLabel={entry["jcr:primaryType"] == "cards:Subject" ? entry.type?.label : undefined}
        admin={admin} />
    });
    return <TableCell key={index} className={classes.tableActions}>{content}</TableCell>;
  }

  let getNestedValue = (entry, path) => {
    if (!path) return entry;
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
    let types = [];
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
        types.push(filter.type || "text");
      }
    });

    if (filtersNotBlank) {
      let filter_obj = {
        fields: fields,
        comparators: comparators,
        values: values,
        types: types,
        empties: empties,
        notempties: notempties
      };
      setCachedFilters(filter_obj);
      // Store entire new filters JSON object as a Base64-encoded ASCII string to pass on in case we need to expand a table
      // via a callback "onFiltersChange()" from upper component that contains a table and expand element
      onFiltersChange && onFiltersChange(window.btoa(encodeURIComponent(JSON.stringify(newFilters))));
      fetchData({
        "filters": filter_obj
      });
    } else {
      setCachedFilters(null);
      fetchData({
        "filters": null
      }, true);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // The rendering code

  /*
  // The pagination is outside the table itself to support internal scrolling of the table.
  // The element used by TablePagination by default is TableCell, but since it is not in a TableRow, we have to override this to be a <div>.
  */
  const paginationControls = tableData && (
    <TablePagination
      component="div"
      rowsPerPageOptions={[10, 20, 50, 100, 1000]}
      count={paginationData.totalIsApproximate ? -1 : paginationData.total}
      rowsPerPage={paginationData.limit}
      page={paginationData.page}
      onPageChange={handleChangePage}
      onRowsPerPageChange={handleChangeRowsPerPage}
      labelDisplayedRows={({from, to, count}) =>
          `${from}-${to} of ${paginationData.totalIsApproximate ? `more than ${paginationData.total}` : count}`
      }
    />
  )

  return (
    // We wrap everything in a Paper for a nice separation, as a Table has no background or border of its own.
    <Paper elevation={0}>
      {filters && <Filters onChangeFilters={handleChangeFilters} disabled={!Boolean(tableData)} filtersJsonString={filtersJsonString} {...rest} />}
      {!disableTopPagination &&
      <div>
        {paginationControls}
      </div>
      }
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
              columns.map((column, index) =>
                <TableCell
                  key={index}
                  className={[classes.tableHeader, column.type == 'actions' ? classes.tableActionsHeader : ''].join(' ')}
                  {...column.props}
                >
                  {column.label}
                </TableCell>
              )
            )
          :
            (
              <TableCell>Name</TableCell>
            )
          }
          {actions ? <TableCell key={columns ? columns.count : 1} className={[classes.tableHeader, classes.tableActionsHeader].join(' ')}>Actions</TableCell> : null}
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
              ( tableData.map(makeRow) )
              :
              ( <TableRow><TableCell colSpan={columns ? columns.length : 1}>Please wait...</TableCell></TableRow> )
          }
        </TableBody>
      </Table>
      {!disableBottomPagination && paginationControls}
      {!tableData && (<LinearProgress className={classes.progressIndicator}/>)}
    </Paper>
  );
}

LiveTable.defaultProps = {
  defaultLimit: 20
}

export default withStyles(LiveTableStyle)(LiveTable);
