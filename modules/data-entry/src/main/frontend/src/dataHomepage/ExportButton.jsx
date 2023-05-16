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
import React, { useState, useEffect, useContext } from 'react';
import PropTypes from "prop-types";
import { makeStyles } from '@mui/styles';
import { deepPurple, orange } from '@mui/material/colors';

import Autocomplete, { createFilterOptions } from "@mui/material/Autocomplete";
import { Checkbox, DialogActions, DialogContent, Divider, Stack, FormControl, Grid, Radio, RadioGroup,
  FormControlLabel, TextField, Typography, Button, IconButton, Tooltip } from "@mui/material";
import DownloadIcon from '@mui/icons-material/FileDownload';

import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import { AdapterLuxon } from "@mui/x-date-pickers/AdapterLuxon";
import { LocalizationProvider } from '@mui/x-date-pickers';

import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import ResponsiveDialog from "../components/ResponsiveDialog";
import QuestionnaireAutocomplete from "../questionnaire/QuestionnaireAutocomplete";
import { findQuestionnaireEntries } from "../questionnaire/QuestionnaireUtilities";

const useStyles = makeStyles(theme => ({
  container: {
    marginBottom: theme.spacing(1.5),
    "& + .MuiDivider-root" : {
      margin: theme.spacing(3, -3),
    },
  },
  withMultiSelect: {
    "& > .MuiGrid-item:first-child" : {
      marginTop: theme.spacing(1),
    },
  },
  withSelect: {
    "& > .MuiGrid-item:first-child" : {
      marginTop: theme.spacing(.5),
    },
  },
  dateRange: {
    alignItems: "baseline",
    marginBottom: theme.spacing(-1.5),
    "& .MuiFormHelperText-root.Mui-focused:not(.Mui-error)" : {
      color: theme.palette.primary.main,
    },
    "& .MuiFormHelperText-root:not(.Mui-focused, .Mui-error)" : {
      visibility: "hidden",
    },
    "& + .MuiTypography-root": {
      marginTop: theme.spacing(-2.5),
    },
  },
}));

const filterUserOptions =  createFilterOptions({
  stringify: (option) => `${option.name} ${option.principalName}`
});

/**
 * A component that renders an icon or button to open the export dialog that generates an export URL for an entry.
 */
function ExportButton(props) {
  const { entityData, entryLabel, entryPath, entryName, variant, size, onClose } = props;

  const DEFAULTS = {
    fileFormat : ".csv",
    hasHeaderLabels: true,
    hasHeaderIdentifiers: false,
    hasAnswerLabels: false,
    columnSelectionMode: "exclude",
    statusSelectionMode: "status",
  }

  const DATE_FORMAT = "yyyy/MM/dd hh:mm a";

  const [ open, setOpen ] = useState(false);
  // List of questions and sections to display in dropdown select to exclude/include
  const [ entities, setEntities] = useState();

  // Decides if the generated export URL ends in .csv or in .tsv
  const [ fileFormat, setFileFormat ] = useState(DEFAULTS.fileFormat);
  // Decides if/how .csvHeader is specified:
  // by default we have csvHeader:labels
  // to disable labels, add .-csvHeader:labels
  // to enable identifiers, add .csvHeader:raw
  const [ hasHeaderLabels, setHeaderLabels ] = useState(DEFAULTS.hasHeaderLabels);
  const [ hasHeaderIdentifiers, setHeaderIdentifiers ] = useState(DEFAULTS.hasHeaderIdentifiers);
  // Specifies if the .labels processor is enabled (disabled by default for values)
  const [ hasAnswerLabels, setAnswerLabels ] = useState(DEFAULTS.hasAnswerLabels);

  // Column selection:
  // just one of the Include or Exclude options should be available at a time
  const [ columnSelectionMode, setColumnSelectionMode ] = useState(DEFAULTS.columnSelectionMode);
  // List of question or section ids to Include or Exclude
  const [ selectedEntityIds, setSelectedEntityIds ] = useState([]);

  const [ users, setUsers ] = useState();
  const [ createdBy, setCreatedBy ] = useState(null);
  const [ modifiedBy, setModifiedBy ] = useState(null);

  const [ createdAfter, setCreatedAfter ] = useState(null);
  const [ createdBefore, setCreatedBefore ] = useState(null);
  const [ modifiedAfter, setModifiedAfter ] = useState(null);
  const [ modifiedBefore, setModifiedBefore ] = useState(null);
  const [ createdRangeIsInvalid, setCreatedRangeIsInvalid ] = useState(false);
  const [ modifiedRangeIsInvalid, setModifiedRangeIsInvalid ] = useState(false);

  const statuses = [ "DRAFT", "INCOMPLETE", "INVALID", "SUBMITTED" ];
  const [ statusSelectionMode, setStatusSelectionMode ] = useState(DEFAULTS.statusSelectionMode);
  const [ status, setStatus ] = useState(null);

  const classes = useStyles();
  const globalLoginDisplay = useContext(GlobalLoginContext);

  useEffect(() => {
    if (entityData && !entities) {
      setEntities(findQuestionnaireEntries(entityData));
    }
    if (!entityData && entryPath && !entities && open) {
      fetchWithReLogin(globalLoginDisplay, `${entryPath}.deep.json`)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => {
          setEntities(findQuestionnaireEntries(json));
        });
    }
  }, [entityData, open]);

  useEffect(() => {
    if (!users && open) {
      fetchWithReLogin(globalLoginDisplay, "/home/users.json")
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => {
          setUsers(json.rows);
        });
    }
  }, [open]);

  // Determine if the before date is earlier than the after date
  useEffect(() => {
    open && setCreatedRangeIsInvalid(!!createdAfter && !!createdBefore && new Date(createdBefore).valueOf() <= new Date(createdAfter).valueOf());
  }, [createdAfter, createdBefore]);

  useEffect(() => {
    open && setModifiedRangeIsInvalid(!!modifiedAfter && !!modifiedBefore && new Date(modifiedBefore).valueOf() <= new Date(modifiedAfter).valueOf());
  }, [modifiedAfter, modifiedBefore]);

  let openDialog = () => {
    entryPath && !open && setOpen(true);
  }

  let closeDialog = () => {
    open && setOpen(false);
    onClose?.();
  }

  let handleExport = () => {
    // Construct the export URL
    let path = entryPath;
    if (!hasHeaderLabels) {
      path += ".-csvHeader:labels";
    }
    if (hasHeaderIdentifiers) {
      path += ".csvHeader:raw";
    }
    if (selectedEntityIds.length > 0) {
      path +=  ".questionnaireFilter";
      let pref = `.questionnaireFilter:${columnSelectionMode}=`;
      for (let id in selectedEntityIds) {
        path += pref + encodeURIComponent(encodeURIComponent(selectedEntityIds[id]));
      }
    }
    if (hasAnswerLabels) {
      path += ".labels";
    }
    if (createdBy) {
      path += ".dataFilter:createdBy=" + createdBy;
    }
    if (modifiedBy) {
      path += ".dataFilter:modifiedBy=" + modifiedBy;
    }
    if (createdAfter) {
      path += ".dataFilter:createdAfter=" + createdAfter.toISO();
    }
    if (createdBefore) {
      path += ".dataFilter:createdBefore=" + createdBefore.toISO();
    }
    if (modifiedAfter) {
      path += ".dataFilter:modifiedAfter=" + modifiedAfter.toISO();
    }
    if (modifiedBefore) {
      path += ".dataFilter:modifiedBefore=" + modifiedBefore.toISO();
    }
    if (status) {
      let pref = `.dataFilter:${statusSelectionMode}=`;
      path += pref + encodeURIComponent(encodeURIComponent(status));
    }
    path += fileFormat;
    window.open(path, '_blank');
  }

  let getDatePicker = (value, setter, rangeIsInvalid) => {
    return (<LocalizationProvider dateAdapter={AdapterLuxon}>
              <DateTimePicker
                inputFormat={DATE_FORMAT}
                value={value}
                onChange={(value) => {
                  setter(value);
                }}
                renderInput={(params) =>
                  <TextField
                    variant="standard"
                    {...params}
                    error={rangeIsInvalid || params.error}
                    helperText={rangeIsInvalid ? " " : DATE_FORMAT}
                    InputProps={{
                      ...params.InputProps
                    }}
                    inputProps={{
                      ...params.inputProps,
                      placeholder: "Any date"
                    }}
                  />
                }
              />
            </LocalizationProvider>
          );
  }

  let getDateRange = (valueA, setterA, valueB, setterB, rangeIsInvalid) => {
    return (<>
      <Stack direction="row" spacing={2} divider={<span>—</span>} className={classes.dateRange}>
        { getDatePicker(valueA, setterA, rangeIsInvalid) }
        { getDatePicker(valueB, setterB, rangeIsInvalid) }
      </Stack>
      { rangeIsInvalid &&
        <Typography component="div" variant="caption" color="error">
          The second date should be later than the first date
        </Typography>
      }
    </>);
  }

  let getUserSelector = (label, value, setter) => {
    return (
          <Grid container alignItems='center' direction="row" className={classes.container + ' ' + classes.withSelect}>
            <Grid item xs={4}><Typography variant="subtitle2">{label}</Typography></Grid>
            <Grid item xs={8}>
                <FormControl variant="standard" fullWidth>
                  <Autocomplete
                    value={value && users.find(item => item.name == value) || null}
                    filterOptions={filterUserOptions}
                    onChange={(event, value) => {
                      setter(value?.name);
                    }}
                    getOptionLabel={(option) => option?.name}
                    options={users || []}
                    renderInput={(params) =>
                      <TextField
                        variant="standard"
                        placeholder="Select user"
                        {...params}
                      />
                    }
                  />
                </FormControl>
            </Grid>
          </Grid>);
  }

  return(
    <React.Fragment>
      <ResponsiveDialog
        title={`Export "${entryName}" Data`}
        open={open}
        width="md"
        onClose={closeDialog}
      >
        <DialogContent dividers>
          <Grid container alignItems='center' direction="row" className={classes.container}>
            <Grid item xs={4}><Typography variant="subtitle2">File format:</Typography></Grid>
            <Grid item xs={8}>
              <RadioGroup
                row
                name="fileFormat"
                value={fileFormat}
                onChange={(event) => setFileFormat(event.target.value)}
              >
                <FormControlLabel value=".csv" control={<Radio />} label=".csv" />
                <FormControlLabel value=".tsv" control={<Radio />} label=".tsv" />
              </RadioGroup>
            </Grid>
          </Grid>

          <Grid container alignItems='center' direction="row" className={classes.container}>
            <Grid item xs={4}><Typography variant="subtitle2">Header format:</Typography></Grid>
            <Grid item xs={8}>
              <FormControlLabel
                control={
                  <Checkbox
                    defaultChecked={!!DEFAULTS.hasHeaderLabels}
                    onChange={(event) => { setHeaderLabels(!!event.target.checked);}}
                  />
                }
                label="Labels"
              />
              <FormControlLabel
                control={
                  <Checkbox
                    defaultChecked={!!DEFAULTS.hasHeaderIdentifiers}
                    onChange={(event) => { setHeaderIdentifiers(!!event.target.checked);}}
                  />
                }
                label="Identifiers"
              />
            </Grid>
          </Grid>

          <Grid container alignItems='center' direction="row" className={classes.container}>
            <Grid item xs={4}><Typography variant="subtitle2">Data format:</Typography></Grid>
            <Grid item xs={8}>
              <RadioGroup
                row
                name="data"
                value={hasAnswerLabels}
                onChange={(event) => setAnswerLabels(event.target.value === "true")}
              >
                <FormControlLabel value={true} control={<Radio />} label="Labels" />
                <FormControlLabel value={false} control={<Radio />} label="Values" />
              </RadioGroup>
            </Grid>
          </Grid>

          <Divider/>

          <Typography variant="h6">Columns</Typography>

          <Grid container alignItems='center' direction="row" className={classes.container}>
            <Grid item xs={4}><Typography variant="subtitle2">Column selection mode:</Typography></Grid>
            <Grid item xs={8}>
              <RadioGroup
                row
                name="columnSelectionMode"
                value={columnSelectionMode}
                onChange={(event) => setColumnSelectionMode(event.target.value)}
              >
                <FormControlLabel value="include" control={<Radio />} label="Include" />
                <FormControlLabel value="exclude" control={<Radio />} label="Exclude" />
              </RadioGroup>
            </Grid>
          </Grid>

          <Grid container alignItems='start' direction="row" className={classes.container + ' ' + classes.withMultiSelect}>
            <Grid item xs={4}>
              <Typography variant="subtitle2">Columns to {columnSelectionMode}:</Typography>
            </Grid>
            <Grid item xs={8}>
              <QuestionnaireAutocomplete
                entities={entities || []}
                selection={selectedEntityIds}
                onSelectionChanged={setSelectedEntityIds}
              />
            </Grid>
          </Grid>

          <Divider/>

          <Typography variant="h6">Filters</Typography>

          { getUserSelector("Created by:", createdBy, setCreatedBy) }

          <Grid container alignItems='baseline' direction="row" className={classes.container}>
            <Grid item xs={4}><Typography variant="subtitle2">Created between:</Typography></Grid>
            <Grid item xs={8}>
              { getDateRange(createdAfter, setCreatedAfter, createdBefore, setCreatedBefore, createdRangeIsInvalid) }
            </Grid>
          </Grid>

          { getUserSelector("Last modified by:", modifiedBy, setModifiedBy) }

          <Grid container alignItems='baseline' direction="row" className={classes.container}>
            <Grid item xs={4}><Typography variant="subtitle2">Last modified between:</Typography></Grid>
            <Grid item xs={8}>
              { getDateRange(modifiedAfter, setModifiedAfter, modifiedBefore, setModifiedBefore, modifiedRangeIsInvalid) }
            </Grid>
          </Grid>

          <Grid container alignItems='center' direction="row" className={classes.container}>
              <Grid item xs={4}><Typography variant="subtitle2">Status flag selection mode:</Typography></Grid>
              <Grid item xs={8}>
                <RadioGroup
                  row
                  name="statusSelectionMode"
                  value={statusSelectionMode}
                  onChange={(event) => setStatusSelectionMode(event.target.value)}
                >
                  <FormControlLabel value="status" control={<Radio />} label="Include" />
                  <FormControlLabel value="statusNot" control={<Radio />} label="Exclude" />
                </RadioGroup>
              </Grid>
          </Grid>

          <Grid container alignItems='center' direction="row" className={classes.container + ' ' + classes.withSelect}>
            <Grid item xs={4}>
              <Typography variant="subtitle2">{statusSelectionMode == "status" ? "Include only forms with the status flag:" : "Exclude all forms with the status flag:"}</Typography>
            </Grid>
            <Grid item xs={8}>
              <FormControl variant="standard" fullWidth>
                <Autocomplete
                    value={status}
                    onChange={(event, value) => { setStatus(value); }}
                    options={statuses || []}
                    renderInput={(params) =>
                      <TextField
                        variant="standard"
                        placeholder="Select a status flag"
                        {...params}
                      />
                    }
                />
              </FormControl>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
            <Button variant="outlined" size="small" onClick={closeDialog}>Cancel</Button>
            <Button
              variant="contained"
              size="small"
              disabled={createdRangeIsInvalid || modifiedRangeIsInvalid}
              onClick={() => handleExport()}
            >
              Export
            </Button>
        </DialogActions>
      </ResponsiveDialog>
      {variant == "icon" ?
        <Tooltip title={entryLabel}>
          <IconButton component="span" onClick={openDialog} size={size}>
            <DownloadIcon fontSize={size == "small" ? size : undefined}/>
          </IconButton>
        </Tooltip>
        :
        <Button
          onClick={openDialog}
          size={size ? size : "medium"}
          startIcon={variant == "extended" ? <DownloadIcon /> : undefined}
        >
          {entryLabel}
        </Button>
      }
    </React.Fragment>
  )
}

ExportButton.propTypes = {
  entityData: PropTypes.object,
  entryPath: PropTypes.string.isRequired,
  entryLabel: PropTypes.string,
  entryName: PropTypes.string.isRequired,
  size: PropTypes.oneOf(["small", "medium", "large"]),
  variant: PropTypes.oneOf(["icon", "text", "extended"]), // "extended" means both icon and text
}

ExportButton.defaultProps = {
  entryLabel: "Export forms",
  variant: "icon",
  size: "large",
}

export default ExportButton;
