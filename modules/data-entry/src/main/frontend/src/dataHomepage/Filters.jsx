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
import React, { useCallback, useRef, useState } from "react";
import { Chip, Typography, Button, Dialog, CircularProgress, IconButton } from "@material-ui/core";
import { DialogActions, DialogContent, DialogTitle, Grid, Select, MenuItem, TextField, withStyles } from "@material-ui/core";
import Add from "@material-ui/icons/Add";
import CloseIcon from '@material-ui/icons/Close';

import LiveTableStyle from "./tableStyle.jsx";
import FilterComponentManager from "./FilterComponents/FilterComponentManager.jsx";

// We have to import each filter dependency here to load them properly into the FilterComponentManager
import DateFilter from "./FilterComponents/DateFilter.jsx";
import ListFilter from "./FilterComponents/ListFilter.jsx";
import BooleanFilter from "./FilterComponents/BooleanFilter.jsx";
import NumericFilter from "./FilterComponents/NumericFilter.jsx";
import VocabularyFilter from "./FilterComponents/VocabularyFilter.jsx";
import TextFilter from "./FilterComponents/TextFilter.jsx";
import SubjectFilter from "./FilterComponents/SubjectFilter.jsx";
import { UNARY_COMPARATORS } from "./FilterComponents/FilterComparators.jsx";

const FILTER_URL = "/Questionnaires.filters";

function Filters(props) {
  const { classes, onChangeFilters, questionnaire } = props;
  // Filters, as displayed in the dialog, and filters as actually saved
  const [editingFilters, setEditingFilters] = useState([]);
  const [activeFilters, setActiveFilters] = useState([]);
  // Information on the questionnaires
  const [filterableAnswers, setFilterableAnswers] = useState({});
  const [filterableFields, setFilterableFields] = useState([]);
  const [filterableTitles, setFilterableTitles] = useState({});
  const [filterableUUIDs, setFilterableUUIDs] = useState({});
  const [filterComparators, setFilterComparators] = useState({});
  const [questionDefinitions, setQuestionDefinitions] = useState({});
  // Other state variables
  const [error, setError] = useState();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [filterRequestSent, setFilterRequestSent] = useState(false);
  const [toFocus, setFocusRow] = useState(null);

  // Focus on inputs as they are flagged for focus
  const focusRef = useRef();
  const focusCallback = useCallback(node => {
    if (node !== null) {
      node.focus();
      setFocusRow(null);
      focusRef.current = node;
    }
  }, []);

  // Refocus on the current input
  // Note: When selecting an option of a Select component, the select will forcibly re-grab
  // focus after our focusRef callback. This allows us to re-grab it after the Select.
  let forceRegrabFocus = () => {
    if (focusRef.current != null) {
      focusRef.current.focus();
    }
  }

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
    // Parse through, but keep a custom field for the subject
    let fields = ["Subject"];
    let uuids = {Subject: "lfs:Subject"};
    let titles = {Subject: "Subject"};
    for (let [questionName, question] of Object.entries(filterJson)) {
      // For each question, save the name, data type, and answers (if necessary)
      fields.push(questionName);
      uuids[questionName] = question["jcr:uuid"];
      titles[questionName] = question["text"];
    }
    filterJson["Subject"] = {
      dataType: "subject"
    };
    setFilterableFields(fields);
    setQuestionDefinitions(filterJson);
    setFilterableTitles(titles);
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
      getOutputChoices(newFilter.name);
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
    let [loadedComparators, component] = getOutputChoices(event.target.value);

    // Automatically add a new filter if they've edited the final filter
    if (index == editingFilters.length-1) {
      addFilter();
    }

    getOutputChoices(event.target.value);
    setEditingFilters(oldfilters => {
      var newfilters = oldfilters.slice();
      var newfilter = {
        name: event.target.value,
        uuid: filterableUUIDs[event.target.value],
        comparator: loadedComparators[0],
        title: filterableTitles[event.target.value]
      }
      newfilters[index] = newfilter;
      return(newfilters);
    });
    setFocusRow(index);
  }

  // Allow the comparator for any filter to change
  let handleChangeComparator = (index, newValue) => {
    // Load up the output value for this index, if not already loaded
    setEditingFilters(oldfilters => {
      let newFilters = oldfilters.slice();
      let newFilter = {...newFilters[index], comparator: newValue};
      newFilters.splice(index, 1, newFilter);
      return(newFilters);
    });
  }

  let handleChangeOutput = (index, newValue, newLabel) => {
    setEditingFilters( oldfilters => {
      let newFilters = oldfilters.slice();
      let newFilter =  {...newFilters[index], value: newValue };
      if (newLabel != null) {
        newFilter.label = newLabel;
      }
      newFilters.splice(index, 1, newFilter);
      return(newFilters);
    })
  }

  let getOutputChoices = (field, overridefilters) => {
    let [comparators, component] = FilterComponentManager.getFilterComparatorsAndComponent(questionDefinitions[field]);
    setFilterComparators( old => ({
      ...old,
      [field]: comparators
    }));
    setFilterableAnswers( old => ({
      ...old,
      [field]: component
    }));
    return [comparators, component]
  }

  // Parse out all of our stuff into chips
  let saveFilters = () => {
    // Create a deep copy of the active filters
    let newFilters = deepCopyFilters(editingFilters)
    // Remove filters that are not complete
      .filter( (toCheck) => (toCheck.uuid && toCheck.comparator))
    // Replace filters with empty output to use either the "is empty" or "is not empty" comparator
      .map( (toCheck) => ((toCheck.value || UNARY_COMPARATORS.includes(toCheck.comparator)) ?
        toCheck
        :
        {...toCheck, comparator: (toCheck.comparator == "=" ? "is empty" : "is not empty")}));
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

  // Return the pre-computed input element, and focus it if we were asked to
  let getCachedInput = (filterDatum, index, focusRef) => {
    let CachedComponent = filterableAnswers[filterDatum.name];
    return (
      <CachedComponent
        ref={focusRef}
        questionDefinition={questionDefinitions[filterDatum.name]}
        defaultValue={editingFilters[index].value}
        onChangeInput={(newValue, label) => {handleChangeOutput(index, newValue, label);}}
        />);
  }

  return(
    <div className={classes.filterContainer}>
      {/* Place the stuff in one row on the top */}
      <Typography display="inline" className={classes.filterLabel}>
        Filters:
      </Typography>
      {activeFilters.map( (activeFilter, index) => {
        let label = activeFilter.title + " " + activeFilter.comparator +
          // Include the label (if available) or value for this filter iff the comparator is not unary
          (UNARY_COMPARATORS.includes(activeFilter.comparator) ? ""
            : (" " + (activeFilter.label != undefined ? activeFilter.label : activeFilter.value)));
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
              onClick={() => {
                openDialogAndAdd();
                setFocusRow(index);
              }}
              className={classes.filterChips}
              />
          </React.Fragment>
          );
        })
      }
      <Button
        size="small"
        variant="contained"
        color="default"
        className={classes.addFilterButton}
        onClick={() => {
          openDialogAndAdd();
          setFocusRow(activeFilters.length);
        }}
        >
        <Add fontSize="small" />
      </Button>
      {/* Dialog for setting up filters */}
      <Dialog
        open={dialogOpen}
        onClose={closeDialog}
        className={classes.dialog}
        BackdropProps={{invisible: true}}
        fullWidth
        disableEnforceFocus
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
          <Grid container alignItems="flex-end" spacing={2} className={classes.filterTable}>
            {editingFilters.map( (filterDatum, index) => {
              // We grab focus on the field if we were asked to
              let isUnary = filterDatum.comparator && UNARY_COMPARATORS.includes(filterDatum.comparator);
              return(
                <React.Fragment key={index}>
                  {/* Select the field to filter */}
                  <Grid item xs={5}>
                    <Select
                      value={filterDatum.name || ""}
                      onChange={(event) => {handleChangeFilter(index, event);}}
                      MenuProps={{
                        onExited: forceRegrabFocus
                      }}
                      className={classes.answerField}
                      autoFocus={(index === editingFilters.length-1 && toFocus === index)}
                      displayEmpty
                      >
                        <MenuItem value="" disabled>
                          <span className={classes.selectPlaceholder}>Add new filter...</span>
                        </MenuItem>
                        {(filterableFields.map( (name) =>
                            <MenuItem value={name} key={name} className={classes.categoryOption}>{filterableTitles[name]}</MenuItem>
                        ))}
                    </Select>
                  </Grid>
                  {/* Depending on whether or not the comparator chosen is unary, the size can change */}
                  <Grid item xs={isUnary ? 6 : 1} className={index == editingFilters.length-1 ? classes.hidden : ""}>
                    <Select
                      value={filterDatum.comparator || ""}
                      onChange={(event) => {handleChangeComparator(index, event.target.value);}}
                      >
                      {(filterComparators[filterDatum.name]?.map( (name) => {
                        return(
                            <MenuItem value={name} key={name}>{name}</MenuItem>
                        );
                      }))}
                    </Select>
                  </Grid>
                  {/* Look up whether or not the component can be loaded */}
                  {!isUnary &&
                    <Grid item xs={5} className={index == editingFilters.length-1 ? classes.hidden : ""}>
                      {filterDatum.comparator ?
                          getCachedInput(filterDatum, index, (index !== editingFilters.length-1 && toFocus === index ? focusCallback : undefined))
                        : <TextField disabled className={classes.answerField}></TextField>
                      }
                    </Grid>}
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
                      className={classes.deleteButton}
                      >
                      <CloseIcon />
                    </IconButton>
                  </Grid>
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
            {'Apply'}
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

export default withStyles(LiveTableStyle)(Filters);
