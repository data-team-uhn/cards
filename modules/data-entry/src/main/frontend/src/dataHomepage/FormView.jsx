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
import { useEffect, useMemo, useState } from "react";

import DescriptionIcon from '@mui/icons-material/Description';
import LaunchIcon from '@mui/icons-material/Launch';
import {
  Avatar,
  Card,
  CardContent,
  CardHeader,
  Divider,
  IconButton,
  LinearProgress,
  Tooltip,
  Typography,
} from "@mui/material";
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import DeleteButton from "./DeleteButton.jsx";
import EditButton from "./EditButton.jsx";
import LiveTable from "./LiveTable.jsx";
import NewFormDialog from "./NewFormDialog.jsx";
import TriStateChip from "../components/TriStateChip.jsx";
import { getEntityIdentifier } from "../themePage/EntityIdentifier.jsx";
import { loadExtensions } from "../uiextension/extensionManager";

const useStyles = makeStyles()(theme => ({
  formView: {
    "& .MuiTabs-indicator": {
      background: theme.palette.primary.main,
    },
  },
  formViewAvatar: {
    background: theme.palette.primary.main,
  },
  formViewChipsContainer: {
    padding: theme.spacing(0, 2),
    marginTop: theme.spacing(-0.5),
    marginBottom: theme.spacing(0.5),
    "& .MuiChip-root": {
      marginRight: theme.spacing(0.5),
    }
  },
}));

function FormView(props) {
  const {
    extension,
    extensionURL,
    actionSwitches,
    questionnaire,
    expanded,
    disableHeader,
    disableAvatar,
    topPagination,
  } = props;

  const { classes } = useStyles();

  const [ title, setTitle ] = useState(props.title);
  const [ subtitle, setSubtitle ] = useState(props.subtitle);
  const [ qFilter, setQFilter ] = useState();
  const [ filtersJsonString, setFiltersJsonString ] = useState(new URLSearchParams(window.location.hash.substring(1)).get("forms:filters"));
  const defaultStatusFilter = "&includeallstatus=true";
  const [ statusFilter, setStatusFilter ] = useState(new URLSearchParams(window.location.hash.substring(1)).get("forms:statusFlags") || defaultStatusFilter);
  const [ statuses, setStatuses ] = useState([]);
  const [ statusValues, setStatusValues ] = useState([]);

  const activeExtensionURL = extension?.["cards:extensionURL"] || extensionURL || ""
  const baseURL = "../content.html" + (activeExtensionURL ? "/" + activeExtensionURL : "");

  // Column configuration for the LiveTables
  const columns = [
    {
      "key": "@name",
      "label": "Identifier",
      "format": getEntityIdentifier,
      "link": "dashboard+path",
    },
    {
      "key": "jcr:created",
      "label": "Created on",
      "format": "date:yyyy-MM-dd HH:mm",
    },
    {
      "key": "jcr:createdBy",
      "label": "Created by",
      "format": "string",
    },
  ]
  const actions = {
    "edit": EditButton,
    "delete": DeleteButton
  }

  let isActionEnabled = (action) => (!!!actionSwitches || !!(actionSwitches[action]()));
  const enabledActions = useMemo(() =>
    Object.entries(actions).filter(entry => isActionEnabled(entry[0])).map(entry => entry[1])
  , [actionSwitches]);

  useEffect(() => {
    loadExtensions("FormStatusFlags")
      .then(extensions => {
        // Load any status flag filter information from the URL
        let previousFilters = {};
        if (statusFilter != defaultStatusFilter) {
          let filters = statusFilter.split("&").slice(1);
          let i = 0;
          while (i+2 < filters.length) {
            previousFilters[filters[i + 1].split("=")[1]] = filters[i + 2].split("=")[1] == "%3C%3E" ? -1 : 1;
            i += 3;
          }
        }
        // Load the available status flags from the extension
        let values = Array(extensions.length).fill(0);
        const flags = extensions.map(e => ({
          key: e["cards:statusFlagKey"],
          label: e["cards:statusFlagLabel"],
        }));
        // Fill in the available status flags with the URL filter information
        for (let i = 0; i < flags.length; i++) {
          if (previousFilters[flags[i].key]) {
            values[i] = previousFilters[flags[i].key];
          }
        }
        setStatuses(flags);
        setStatusValues(values);
      })
      .catch(err => console.error("Failed to load status flags", err));
  }, []);

  useEffect(() => {
    let filter = "";
    statusValues.forEach((value, index) => {
      if (value != 0) {
        filter += `&fieldnames=statusFlags&fieldvalues=${statuses[index].key}&fieldcomparators=${value == -1 ? "%3C%3E" : "%3D"}`
      }
    });
    setStatusFilter(filter.length == 0 ? defaultStatusFilter : filter);
  }, [statusValues, statuses])

  useEffect (() => {
    // If a questionnaire parameter is specified:
    if (questionnaire) {
      // Fetch the questionnaire info and update the title, subtitle and query filter
      fetch(`${questionnaire}.json`)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then(qData => {
          setTitle(qData["title"]);
          setSubtitle(qData["description"]);
          setQFilter('&fieldnames=questionnaire&fieldcomparators=%3D&fieldvalues=' + encodeURIComponent(qData["jcr:uuid"]));
        })
        .catch(err => setQFilter(''));
    } else {
      // No filtering by questionnaire
      setQFilter('');
    }
  }, [questionnaire]);

  let setStatusFlagState = (index, value) => {
    // Set to a new array to trigger useEffects
    const newStatusValues = statusValues.slice();
    newStatusValues[index] = value;
    setStatusValues(newStatusValues);
  }

  return (
    <Card className={classes.formView}>
      {title &&
      <CardHeader
        title={
          <>
            {title && <Typography variant="h4">{title}</Typography>}
            {subtitle && <Typography variant="subtitle1">{subtitle}</Typography>}
          </>
        }
      />
      }
      {(!expanded || !disableHeader && !disableAvatar) &&
      <CardHeader
        avatar={!disableAvatar && <Avatar className={classes.formViewAvatar}><DescriptionIcon/></Avatar>}
        title={<Typography variant="h6">Questionnaires</Typography>}
        action={
          !expanded && isActionEnabled("expand") &&
          <Tooltip title="Expand">
            <Link underline="hover" to={baseURL + "/Forms#"
              + new URLSearchParams({ "forms:statusFlags": statusFilter }).toString() + "&"
              + new URLSearchParams({ "forms:filters" : filtersJsonString || "" }).toString()
            }>
              <IconButton size="large">
                <LaunchIcon/>
              </IconButton>
            </Link>
          </Tooltip>
        }
      />
      }
      <Divider />
      <CardContent>
        <div className={classes.formViewChipsContainer}>
          {
            statuses.map((status, index) => {
              return <TriStateChip
                key={`${status.key}-${index}`}
                size="small"
                label={status.label}
                defaultTooltip={`Show or hide ${status.label.toLowerCase()} forms?`}
                positiveTooltip={`Showing ${status.label.toLowerCase()} forms`}
                negativeTooltip={`Hiding ${status.label.toLowerCase()} forms`}
                onSetPositive={() => setStatusFlagState(index, 1)}
                onSetNegative={() => setStatusFlagState(index, -1)}
                onClear={() => setStatusFlagState(index, 0)}
                initialState={statusValues[index]}
              />
            })
          }
        </div>
        { typeof(qFilter) == "undefined" ? <LinearProgress /> :
          <LiveTable
            columns={props.columns || columns}
            customUrl={`/Forms.paginate?descending=true${qFilter}${statusFilter}`}
            defaultLimit={10}
            filters
            questionnaire={questionnaire}
            entryType="Form"
            actions={enabledActions.length > 0 ? enabledActions : undefined}
            disableTopPagination={!topPagination}
            onFiltersChange={(str) => setFiltersJsonString(str)}
            filtersJsonString={filtersJsonString}
            extensionURL={activeExtensionURL}
          />
        }
        { expanded && isActionEnabled("create") &&
        <NewFormDialog
          presetPath={questionnaire}
          withButton
          buttonTitle="New questionnaire"
          extensionURL={activeExtensionURL}
        />
        }
      </CardContent>
    </Card>
  );
}

export default FormView;
