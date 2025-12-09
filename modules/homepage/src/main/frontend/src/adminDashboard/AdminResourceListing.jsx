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
import React, { useState, useContext, useEffect } from "react";

import { MaterialReactTable } from "material-react-table";

import AdminScreen from "./AdminScreen.jsx";
import NewItemButton from "../components/NewItemButton.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/ReLoginDialog.js";


function AdminResourceListing(props) {
  const { title, columns, tableActions, buttonProps, dataUrl, entryType, resourceSelectors, updateData,
    onDataReceived, customFilter, ...rest } = props;

  const [ data, setData ] = useState([]);
  const [ isLoading, setIsLoading ] = useState(false);
  const [ isRefetching, setIsRefetching ] = useState(false);

  const globalLoginDisplay = useContext(GlobalLoginContext);

  const fetchData = async () => {
    if (!data.length) {
      setIsLoading(true);
    } else {
      setIsRefetching(true);
    }

    const urlBase = dataUrl || '/query?query=' + encodeURIComponent('select * from [cards:'+ entryType+']');
    let url = new URL(urlBase, window.location.origin);
    resourceSelectors && url.searchParams.set("resourceSelectors", resourceSelectors);
    url.searchParams.set("limit", 1000);
    const response = await fetchWithReLogin(globalLoginDisplay, url);
    const json = await response.json();
    setData(json["rows"]);
    onDataReceived?.(json.rows);

    setIsLoading(false);
    setIsRefetching(false);
  };

  let addData = (event) => {
    if (event.detail) {
      const newEntry = event.detail;
      setData([...data, newEntry]);;
    }
  }

  // Fetch data from the server
  useEffect(() => {
    fetchData();
  }, [ ]);

  // When new data is added, add a new row to the table.
  useEffect(() => {
    if (updateData) {
      fetchData();
    }
  }, [updateData]);

  // When data is created, add it to the table
  useEffect(() => {
    // subscribe event
    window.addEventListener("MaterialTableAppend", addData);
    return () => {
      // unsubscribe event
      document.removeEventListener("MaterialTableAppend", addData);
    };
  });

  return (
    <AdminScreen
      title={title}
      action={buttonProps ? <NewItemButton {...buttonProps}/> : action}
    >
      <MaterialReactTable
        enableColumnFilters={false}
        positionToolbarAlertBanner="none"
        muiSearchTextFieldProps={{ autoFocus: true }}
        initialState={{ showGlobalFilter: true }}
        state={{ isLoading: isLoading, showProgressBars: isRefetching }}
        columns={columns}
        data={data}
        muiTablePaperProps={{ elevation: 0 }}
        muiTableHeadCellProps={{
          sx: (theme) => ({
            background: theme.palette.grey['200'],
          }),
        }}
        displayColumnDefOptions={{
          'mrt-row-actions': {
            muiTableHeadCellProps: { align: 'right' },
            muiTableBodyCellProps: {
              sx: {
                padding: '0',
              },
            },
          },
          'mrt-row-expand': {
            size: 8,
          },
        }}
        enableRowActions={tableActions}
        positionActionsColumn="last"
        renderRowActions={tableActions}
        filterFns={{
          myCustomFilterFn: customFilter,
        }}
        globalFilterFn={customFilter ? "myCustomFilterFn" : "contains"}
      />
    </AdminScreen>
  );
}

export default AdminResourceListing;
