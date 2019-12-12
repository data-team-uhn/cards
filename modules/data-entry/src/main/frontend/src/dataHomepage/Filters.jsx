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
import { Chip, Typography, Button, Dialog, CircularProgress, IconButton } from "@material-ui/core";
import { DialogActions, DialogContent, DialogTitle, Grid, Select, MenuItem, TextField, withStyles } from "@material-ui/core";
import Add from "@material-ui/icons/Add";
import CloseIcon from '@material-ui/icons/Close';

import { DATE_FORMATS } from "../questionnaire/DateQuestion.jsx";
import { NumberFormatCustom } from "../questionnaire/NumberQuestion";
import VocabularySelector from "../vocabQuery/query.jsx";
import LiveTableStyle from "./tableStyle.jsx";

const FILTER_URL = "/Questionnaires.filters";

function Filters(props) {
  const { classes, onChangeFilters, questionnaire } = props;
  // Filters, as displayed in the dialog, and filters as actually saved
  const [editingFilters, setEditingFilters] = useState([]);
  const [activeFilters, setActiveFilters] = useState([]);
  // Information on the questionnaires
  const [filterableFields, setFilterableFields] = useState([]);
  const [filterableDataTypes, setFilterableDataTypes] = useState({});
  const [filterableDisplayModes, setFilterableDisplayModes] = useState({});
  const [filterableUUIDs, setFilterableUUIDs] = useState({});
  const [filterableAnswers, setFilterableAnswers] = useState({});
  const [filterComparators, setFilterComparators] = useState({});
  const [questionDefinitions, setQuestionDefinitions] = useState({});
  // Other state variables
  const [error, setError] = useState();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [filterRequestSent, setFilterRequestSent] = useState(false);

  // Obtain information about the filters that can be applied
  let grabFilters = (urlBase) => {
    setFilterRequestSent(true);
    let url = new URL(urlBase, window.location.origin);

    // Add information about the questionnaire, if we have any
    if (questionnaire) {
      url.searchParams.set("questionnaire", questionnaire);
    }

    fetch(url)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(parseFilterData)
      .catch(setError);
  }

  // Parse the response from our FilterServlet
  let parseFilterData = (filterJson) => {
    let dataTypes = {};
    let fields = [];
    let displayModes = {};
    let uuids = {}
    for (let [questionName, question] of Object.entries(filterJson)) {
      // For each question, save the name, data type, and answers (if necessary)
      fields.push(questionName);
      dataTypes[questionName] = question["dataType"];
      displayModes[questionName] = question["displayMode"];
      uuids[questionName] = question["jcr:uuid"];
    }
    setFilterableFields(fields);
    setQuestionDefinitions(filterJson);
    setFilterableDataTypes(dataTypes);
    setFilterableDisplayModes(displayModes);
    setFilterableUUIDs(uuids);
  }

  // Open the filter selection dialog
  let openDialogAndAdd = () => {
    setDialogOpen(true);
    // Replace our defaults with a deep copy of what's actually active, plus an empty one
    let newFilters = deepCopyFilters(activeFilters);
    setEditingFilters(newFilters);

    // Bugfix: also reload every active outputChoice, in order to refresh its copy of the state variables
    newFilters.forEach( (newFilter) => {
      getOutputChoices(newFilter.name, newFilter);
    });

    addFilter();

    // What filters are we looking at here?
    if (!filterRequestSent) {
      grabFilters(FILTER_URL);
    }
  }

  // Add a new filter
  let addFilter = () => {
    setEditingFilters(oldfilters => {var newfilters = oldfilters.slice(); newfilters.push({}); return(newfilters);})
  }

  // Handle the user changing one of the active filter categories
  let handleChangeFilter = (index, event) => {
    // Load up the comparators for this index, if not already loaded
    let loadedComparators = filterComparators[event.target.value];
    if (!loadedComparators) {
      loadedComparators = loadComparators(event.target.value);
    }

    // Automatically add a new filter if they've edited the final filter
    if (index == editingFilters.length-1) {
      addFilter();
    }

    getOutputChoices(event.target.value);
    setEditingFilters(oldfilters => {
      var newfilters = oldfilters.slice();
      var newfilter = {name: event.target.value, uuid: filterableUUIDs[event.target.value], comparator: loadedComparators[0]}
      newfilters[index] = newfilter;
      return(newfilters);
    });
  }

  // Determine what comparators are available for the input field
  let loadComparators = (field) => {
    // TODO: What happens if the field isn't inside of fieldData for some reason?
    let dataType = filterableDataTypes[field];
    let comparators = ["=", "<>"];

    // Allow comparison of greater than or less than if the dataType is comparable
    if (dataType == 'decimal' || dataType == 'long' || dataType == 'date') {
      comparators.concat(["<", "<=", ">", ">="]);
    }

    // As per React standards: copy, slice, and return our input object
    setFilterComparators(filterComparators => {
      return { [field]: comparators, ...filterComparators };
    })
    return comparators;
  }

  // Allow the comparator for any filter to change
  let handleChangeComparator = (index, event) => {
    // Load up the output value for this index, if not already loaded
    getOutputChoices(editingFilters[index].name);
    setEditingFilters(oldfilters => {
      let newFilters = oldfilters.slice();
      let newFilter = {...newFilters[index], comparator: event.target.value};
      newFilters.splice(index, 1, newFilter);
      return(newFilters);
    });
  }

  // Determine how to show the output choices field
  // Note that we this creates functions, that take the index of the filter to alter, and return the
  // component that handles its own onClick/onChange events
  let getOutputChoices = (field, overrideFilters) => {
    // TODO: What happens if the field isn't inside of fieldData for some reason?
    let dataType = filterableDataTypes[field];
    let displayMode = filterableDisplayModes[field];
    let newChoices;

    // If this question has multiple pre-defined responses, and a displayMode='list', store the responses
    // (we ignore list+input here because the user can select anything from those)
    if (displayMode === 'list') {
      // First, obtain the children nodes
      newChoices = (index) => (
        <SelfManagedSelect
          defaultValue={overrideFilters ? overrideFilters.value : editingFilters[index].value}
          onChange={(event) => {handleChangeOutput(index, event.target.value)}}
          className={classes.answerField}
          >
          {Object.entries(questionDefinitions[field])
          // answers are nodes with "jcr:primaryType" = "lfs:AnswerOption"
            .filter( (answer) => {
              return answer[1]['jcr:primaryType'] && answer[1]['jcr:primaryType'] === 'lfs:AnswerOption'
            })
          // turn these answers into menuItems
            .map( (answer) => {
              return(
                <MenuItem value={answer[1]['value']} key={answer[1]['value']}>{answer[1]['value']}</MenuItem>
              );
            })
          }
        </SelfManagedSelect>
      );
    } else if (dataType == 'decimal' || dataType == 'long') {
      newChoices = (index) => (
        <TextField
          className={classes.textField + " " + classes.answerField}
          inputProps={{
            decimalScale: dataType === "long" ? 0 : undefined
          }}
          InputProps={{
            inputComponent: NumberFormatCustom, // Used to override a TextField's type
            className: classes.textField
          }}
          defaultValue={overrideFilters ? overrideFilters.value : editingFilters[index].value}
          onChange={(event) => {handleChangeOutput(index, event.target.value)}}
          />
      );
    } else if (dataType == 'date') {
      // Dates should have a dateFormat, or default to "yyyy-MM-dd"
      let dateFormat = questionDefinitions[field]["dateFormat"] || "yyyy-MM-dd";
      let isMonth = dateFormat === DATE_FORMATS[1];
      let isDate = DATE_FORMATS.includes(dateFormat);
      let textFieldType = isMonth ? "month" :
        isDate ? "date" :
        "datetime-local";
      newChoices = (index) => (
        <TextField
          id="date"
          type={textFieldType}
          className={classes.textField + " " + classes.answerField}
          InputLabelProps={{
            shrink: true,
          }}
          InputProps={{
            className: classes.textField
          }}
          defaultValue={overrideFilters ? overrideFilters.value : editingFilters[index].value}
          onChange={(event) => {handleChangeOutput(index, event.target.value)}}
          />);
    } else if (dataType == 'vocabulary') {
      let vocabulary = questionDefinitions[field]["sourceVocabulary"];
      let suggestionCategories = questionDefinitions[field]["suggestionCategories"];
      newChoices = (index) => (
        <VocabularySelector
          onClick = {(id, name) => {handleChangeOutput(index, id)}}
          suggestionCategories = {suggestionCategories}
          vocabulary = {vocabulary}
        />
      );
    } else {
      // Assume a string input by default
      newChoices = (index) => (
        <TextField
          className={classes.textField + " " + classes.answerField}
          InputLabelProps={{
            shrink: true,
          }}
          InputProps={{
            className: classes.textField
          }}
          defaultValue={overrideFilters ? overrideFilters.value : editingFilters[index].value}
          onChange={(event) => {handleChangeOutput(index, event.target.value)}}
          />);
    }

    setFilterableAnswers( answers => {
      return { ...answers, [field]: newChoices };
    });
  }

  let handleChangeOutput = (index, newValue) => {
    setEditingFilters( oldfilters => {
      let newFilters = oldfilters.slice();
      let newFilter =  {...newFilters[index], value: newValue };
      newFilters.splice(index, 1, newFilter);
      return(newFilters);
    })
  }

  // Parse out all of our stuff into chips
  let saveFilters = () => {
    // Create a deep copy of the active filters
    let newFilters = deepCopyFilters(editingFilters)
    // Remove filters that are not complete
      .filter( (toCheck) => {return toCheck.uuid && toCheck.comparator});
    setActiveFilters(newFilters);
    onChangeFilters && onChangeFilters(newFilters);
    setDialogOpen(false);
  }

  // Create a deep copy of filters or activeFilters
  // This allows us to isolate the two from each other, preventing filter updates
  // while the dialog is active
  let deepCopyFilters = (toCopy) => {
    let newFilters = [];
    toCopy.forEach( (oldFilter) => {
      newFilters.push({ ...oldFilter });
    });
    return(newFilters);
  }

  // Helper function to close an open dialog without saving
  let closeDialog = () => {
    setDialogOpen(false);
  }

  return(
    <div className={classes.filterContainer}>
      {/* Place the stuff in one row on the top */}
      <Typography display="inline" className={classes.filterLabel}>
        Filters:
      </Typography>
      {activeFilters.map( (activeFilter, index) => {
        let label = activeFilter.name + " " + activeFilter.comparator + " " + activeFilter.value;
        return(
          <React.Fragment key={label}>
            <Chip
              key={label}
              size="small"
              label={label}
              onDelete={()=>{
                const newFilters = activeFilters.slice();
                newFilters.splice(index, 1);
                setActiveFilters(newFilters);
                onChangeFilters && onChangeFilters(newFilters);
                }
              }
              onClick={openDialogAndAdd}
              className={classes.filterChips}
              />
            {index+1 != activeFilters.length && (
              <Typography variant="body2" display="inline">and </Typography>
            )}
          </React.Fragment>
          );
        })
      }
      <Button
        size="small"
        variant="contained"
        color="default"
        className={classes.addFilterButton}
        onClick={openDialogAndAdd}
        >
        <Add fontSize="small" />
      </Button>
      {/* Dialog for setting up filters */}
      <Dialog
        open={dialogOpen}
        onClose={closeDialog}
        className={classes.dialog}
        fullWidth
        >
        <DialogTitle id="new-form-title">
          Modify filters
        </DialogTitle>
        <DialogContent dividers className={classes.dialogContent}>
          {error &&
            <Typography color="error" className={classes.filterLabel}>
              Error obtaining filter data: {error.status} {error.statusText}
            </Typography>}
          { /* If there is no error but also no data, show a progress circle */
          !error && !filterableFields &&
            <CircularProgress />}
          <Grid container spacing={3} className={classes.filterTable}>
            {editingFilters.map( (filterDatum, index) => {
              return(
                <React.Fragment key={index}>
                  {/* Select the field to filter */}
                  <Grid item xs={5}>
                    <Select
                      value={filterDatum.name || ""}
                      onChange={(event) => {handleChangeFilter(index, event);}}
                      className={classes.categoryField}
                      displayEmpty
                      >
                        <MenuItem value="" disabled>
                          <span className={classes.selectPlaceholder}>Add new filter...</span>
                        </MenuItem>
                        {(filterableFields.map( (name) => {
                          return(
                            <MenuItem value={name} key={name} className={classes.categoryOption}>{name}</MenuItem>
                          );
                        }))}
                    </Select>
                  </Grid>
                  {/* Select the comparison operator */}
                  <Grid item xs={1} className={index == editingFilters.length-1 ? classes.hidden : ""}>
                    <Select
                      disabled={!filterDatum.name}
                      value={filterDatum.comparator || ""}
                      onChange={(event) => {handleChangeComparator(index, event);}}
                      >
                        {(filterComparators[filterDatum.name] && filterComparators[filterDatum.name].map( (name) => {
                          return(
                            <MenuItem value={name} key={name}>{name}</MenuItem>
                          );
                        }))}
                    </Select>
                  </Grid>
                  {/* Options, generated from their function */}
                  <Grid item xs={5} className={index == editingFilters.length-1 ? classes.hidden : ""}>
                    {filterDatum.comparator ?
                        filterableAnswers[filterDatum.name](index)
                      : <TextField disabled className={classes.answerField}></TextField>
                    }
                  </Grid>
                  {/* Deletion button */}
                  <Grid item xs={1} className={index == editingFilters.length-1 ? classes.hidden : ""}>
                    <IconButton
                      size="small"
                      onClick={()=>{
                        setEditingFilters(
                          (oldData) => {
                            let newData = oldData.slice()
                            newData.splice(index, 1);
                            return(newData);
                          });
                        }}
                      >
                      <CloseIcon />
                    </IconButton>
                  </Grid>
                  {index < editingFilters.length-1 &&
                    <Grid item container xs={12} justify="center" className={classes.verticalFieldSpacer}>
                      <Typography variant="overline">and</Typography>
                    </Grid>
                  }
                </React.Fragment>
              );
            })}
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button
            variant="contained"
            color="primary"
            onClick={saveFilters}
            >
            {'Save'}
          </Button>
          <Button
            variant="contained"
            color="default"
            onClick={closeDialog}
            >
            {'Cancel'}
          </Button>
        </DialogActions>
      </Dialog>
    </div>
  );
}

// An implementation of Select that tracks its own state without using native=true
function SelfManagedSelect(props) {
  const { onChange, defaultValue, ...rest } = props;
  const [ selection, setSelection ] = useState(defaultValue || "");

  return(
    <Select
      value={selection}
      onChange={(event) => {
        setSelection(event.target.value);
        onChange(event);
      }}
      {...rest}
      />
  );
}

export default withStyles(LiveTableStyle)(Filters);
