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

import { useState, useEffect, useContext, useRef } from "react";

import {
  Paper,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TablePagination
} from "@mui/material";
import {
  Card,
  CardHeader,
  CardContent,
  CardActions,
  Typography,
  Button,
  LinearProgress,
  Stack
} from "@mui/material";
import { Link } from 'react-router';
import { withStyles } from 'tss-react/mui';

import Filters from "./Filters.jsx";
import liveTableStyles from "./tableStyles.jsx";
import DateTimeUtilities from "../components/DateTimeUtilities.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/ReLoginDialog.js";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";

function LiveTable(props) {
  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Define the component's state

  const {
    customUrl,
    resourceSelectors,
    columns,
    showTotalRows = false,
    defaultLimit = 20,
    updateData,
    classes,
    filters,
    entryType,
    actions,
    extensionURL,
    disableTopPagination,
    disableBottomPagination,
    onDataReceived,
    onFiltersChange,
    filtersJsonString,
    ...rest
  } = props;

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
      "fetchError": false,
    }
  );
  // The number of the most recently issued request, so async callbacks (handleResponse) see the
  // correct value. It must keep increasing for the lifetime of the table: handleResponse identifies
  // superseded responses by their number, so reusing one would let a stale response through.
  const requestNumberRef = useRef(-1);
  // The controller of the in-flight request, so that a new request can abort the one it supersedes.
  // A ref for the same reason as the request number above: fetchData must see the request that is
  // actually in flight, not whichever one its closure happened to capture.
  const abortControllerRef = useRef(null);
  // The page the pagination controls point at, which is the page being *requested*, not the one
  // still displayed. Keeping the two apart is what lets a second click while a page is still
  // loading move another page along, instead of asking for the same one again.
  const [requestedPage, setRequestedPage] = useState(0);
  // Whether a response has ever arrived. The pagination controls need it to stay mounted while a
  // page loads: they used to be tied to the row data, so every page change made them vanish and
  // reappear, which both shifted the layout and made a second click impossible.
  const [everLoaded, setEverLoaded] = useState(false);
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

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Define the component's behavior

  let handleError = (response) => {
    if (response?.name === "AbortError") {
      // We aborted this request ourselves because a newer one replaced it.
      return;
    }
    let err = response.statusText ? response.statusText : response.toString();
    if (response.status == 404) {
      err = "Access to data is pending the approval of your account";
    }
    setFetchStatus(oldStatus => ({
      ...oldStatus,
      "fetchError": err,
    }));
    setTableData([]);
  };

  let fetchData = (newPage, goToStart) => {
    // Abandon the request this one supersedes. The response guard in handleResponse is what keeps
    // stale data off the screen; aborting is about not paying for an answer nobody will read, which
    // matters because every keystroke in a filter, and every click on a pagination button, starts
    // another request.
    abortControllerRef.current?.abort();
    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    const nextRequestNumber = requestNumberRef.current + 1;
    requestNumberRef.current = nextRequestNumber;

    const offset = goToStart ? 0 : newPage.offset ?? paginationData.offset;
    const limit = newPage.limit || paginationData.limit;
    // Remember the page we are heading to, so that a further click computes its own target from
    // here rather than from the page still on screen
    setRequestedPage(Math.floor(offset / limit));

    let url = new URL(urlBase);
    url.searchParams.set("offset", offset);
    url.searchParams.set("limit", limit);
    url.searchParams.set("req", nextRequestNumber);
    url.searchParams.set("showTotalRows", showTotalRows);
    resourceSelectors && url.searchParams.set("resourceSelectors", resourceSelectors);

    // filters should be nullable, but if left undefined we use the cached filters
    let filters = (newPage.filters === null ? null : (newPage.filters || cachedFilters));

    // Add the filters (if they exist)
    if (filters != null) {
      filters["fields"].forEach((field) => url.searchParams.append("filternames", field));
      filters["comparators"].forEach((comparator) => url.searchParams.append("filtercomparators", comparator));
      filters["values"].forEach((value) => url.searchParams.append("filtervalues", value));
      filters["types"].forEach((type) => url.searchParams.append("filtertypes", type));
      filters["empties"].forEach((value) => url.searchParams.append("filterempty", value));
      filters["notempties"].forEach((value) => url.searchParams.append("filternotempty", value));
    }
    let currentFetch = fetchWithReLogin(globalLoginDisplay, url, { "signal": abortController.signal });
    setFetchStatus(oldStatus => ({
      ...oldStatus,
      "currentRequestNumber": nextRequestNumber,
      "fetchError": false,
    }));
    // Clear tableData (set it to undefined) so that Please wait... is displayed
    setTableData();
    currentFetch.then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(handleResponse)
      .catch(handleError);
    // TODO: update the displayed URL with pagination details, so that we can share/reload at the same page
  };

  let handleResponse = (json) => {
    if (+json.req !== requestNumberRef.current) {
      // This is the response for an older request. Discard it, wait for the right one.
      return;
    }
    setTableData(json.rows);
    onDataReceived?.(json.rows);
    setEverLoaded(true);
    const page = Math.floor(json.offset / json.limit);
    // The server decides where we actually landed, which is not always where we asked to go — a
    // filter change or a smaller page size can move us
    setRequestedPage(page);
    setPaginationData(
      {
        "offset": json.offset,
        "limit": json.limit,
        "displayed": json.returnedrows,
        "total": json.totalrows,
        "page": page,
        "totalIsApproximate": json.totalIsApproximate,
      }
    );
  };

  // Rewinding the trigger to -1 makes the initialization effect fetch the data again. The request
  // number itself keeps increasing, so responses to requests issued before the refresh are dropped.
  let refresh = () => {
    setFetchStatus(oldStatus => ({
      ...oldStatus,
      "currentRequestNumber": -1,
    }));
  }

  // Abandon whatever is still in flight when the table goes away: nobody is left to display it
  useEffect(() => {
    return () => abortControllerRef.current?.abort();
  }, []);

  // When data is changed, trigger a new fetch in the table
  useEffect(() => {
    // subscribe event
    window.addEventListener("LivetableRefresh",  refresh);
    return () => {
      // unsubscribe event
      window.removeEventListener("LivetableRefresh",  refresh);
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

  let makeRow = (entry, i) => {
    return (
      <TableRow key={entry["@path"] + i} className={classes.dataRow}>
        { columns ?
          (
            columns.map((column, index) => makeCell(entry, column, index))
          )
          :
          (
            <TableCell><a href={entry["@path"]}>{entry.title}</a></TableCell>
          )
        }
        { actions && actions.length > 0 ? makeActions(entry, actions, columns ? columns.count : 0) : null}
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
      content = DateTimeUtilities.formatDateAnswer(format, content);
    }

    let pathPrefix = (extensionURL ? "../content.html/" + extensionURL : "../content.html");

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
        extensionURL={extensionURL}
      />
    });
    return (
      <TableCell key={index}>
        <Stack direction="row" sx={{ justifyContent: "flex-end", my: .5 }}>
          { content }
        </Stack>
      </TableCell>
    );
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

  // The page argument is computed by the pagination control from the page it is pointing at, which
  // is requestedPage: clicking "previous" twice in a row therefore asks for two pages back, rather
  // than for the same page twice. The request the first click started is abandoned by fetchData.
  let handleChangePage = (event, page) => {
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
      onFiltersChange?.(window.btoa(encodeURIComponent(JSON.stringify(newFilters))));
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
  const paginationControls = everLoaded && (
    <TablePagination
      component="div"
      rowsPerPageOptions={[10, 20, 50, 100, 1000]}
      count={paginationData.totalIsApproximate ? -1 : paginationData.total}
      rowsPerPage={paginationData.limit}
      page={requestedPage}
      onPageChange={handleChangePage}
      onRowsPerPageChange={handleChangeRowsPerPage}
      labelDisplayedRows={({ from, to, count }) =>
        `${from}-${to} of ${paginationData.totalIsApproximate ? `more than ${paginationData.total}` : count}`
      }
    />
  )

  return (
    // We wrap everything in a Paper for a nice separation, as a Table has no background or border of its own.
    <Paper elevation={0}>
      {filters &&
        <Filters
          onChangeFilters={handleChangeFilters}
          disabled={!Boolean(tableData)}
          filtersJsonString={filtersJsonString}
          {...rest}
        />
      }
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
              ( <TableRow><TableCell colSpan={columns ? columns.length : 1}>
                <Typography>Please wait...</Typography>
              </TableCell></TableRow> )
          }
        </TableBody>
      </Table>
      {!disableBottomPagination && paginationControls}
      {!tableData && (<LinearProgress/>)}
    </Paper>
  );
}

export default withStyles(LiveTable, liveTableStyles);
