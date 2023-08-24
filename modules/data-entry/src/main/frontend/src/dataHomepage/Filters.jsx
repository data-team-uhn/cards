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
import React, { useCallback, useRef, useState, useContext, useEffect } from "react";
import { Chip, Typography, Button, CircularProgress, IconButton, Tooltip } from "@mui/material";
import { DialogActions, DialogContent, Grid, Select, MenuItem, TextField } from "@mui/material";
import withStyles from '@mui/styles/withStyles';
import Add from "@mui/icons-material/Add";
import CloseIcon from '@mui/icons-material/Close';

import ResponsiveDialog from "../components/ResponsiveDialog";
import VariableAutocomplete from "./VariableAutocomplete";
import LiveTableStyle from "./tableStyle.jsx";
import FilterComponentManager from "./FilterComponents/FilterComponentManager.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import DateQuestionUtilities from "../questionnaire/DateQuestionUtilities.jsx";

// We have to import each filter dependency here to load them properly into the FilterComponentManager
import DateFilter from "./FilterComponents/DateFilter.jsx";
import ListFilter from "./FilterComponents/ListFilter.jsx";
import BooleanFilter from "./FilterComponents/BooleanFilter.jsx";
import NumericFilter from "./FilterComponents/NumericFilter.jsx";
import VocabularyFilter from "./FilterComponents/VocabularyFilter.jsx";
import TextFilter from "./FilterComponents/TextFilter.jsx";
import SubjectFilter from "./FilterComponents/SubjectFilter.jsx";
import QuestionnaireFilter from "./FilterComponents/QuestionnaireFilter.jsx";
import ResourceFilter from "./FilterComponents/ResourceFilter.jsx";
import UserFilter from "./FilterComponents/UserFilter.jsx";
import { UNARY_COMPARATORS, TEXT_COMPARATORS } from "./FilterComponents/FilterComparators.jsx";

const FILTER_URL = "/Questionnaires.filters";

function Filters(props) {
  const { classes, disabled, onChangeFilters, questionnaire, filtersJsonString } = props;
  // Filters, as displayed in the dialog, and filters as actually saved
  const [editingFilters, setEditingFilters] = useState([]);
  const [activeFilters, setActiveFilters] = useState([]);
  // Information on the questionnaires
  const [filterableAnswers, setFilterableAnswers] = useState({});
  // maps from path to question definition
  const [questionDefinitions, setQuestionDefinitions] = useState({});
  const [autoselectOptions, setAutoselectOptions] = useState([]);

  const [filterComparators, setFilterComparators] = useState({});
  const [textFilterComponent, setTextFilterComponent] = useState({});

  // Other state variables
  const [error, setError] = useState();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [filterRequestSent, setFilterRequestSent] = useState(false);
  const [toFocus, setFocusRow] = useState(null);
  const notesComparator = "notes contain";

  const globalLoginDisplay = useContext(GlobalLoginContext);

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

  // Parse filters that were passed from one of dashboard table expansions
  // When new data is added, trigger a new fetch
  useEffect(() => {
    if (!filterRequestSent) {
      grabFilters();
    }

    if (filtersJsonString && Object.keys(questionDefinitions).length > 0) {
      // Parse out the filters
      let newFilters = [];
      try {
        newFilters = JSON.parse(decodeURIComponent(window.atob(filtersJsonString)));
      } catch (err) {
        // Ignore silently malformed filters sent in the URL
        return;
      }
      if (!Array.isArray(newFilters)) return;
      newFilters.forEach( (newFilter) => {
        getOutputChoices(newFilter.name);
      });
      setEditingFilters(newFilters);
      setActiveFilters(newFilters);
      onChangeFilters && onChangeFilters(newFilters);
    }
  }, [filtersJsonString, questionDefinitions]);

  // Obtain information about the filters that can be applied
  let grabFilters = () => {
    setFilterRequestSent(true);
    let url = new URL(questionnaire ? questionnaire + ".filters" : FILTER_URL, window.location.origin);

    fetchWithReLogin(globalLoginDisplay, url)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(parseFilterData)
      .catch(setError);
  }

  // Parse the json response from FilterSerlet
  let parseFilterData = (data) => {

    let newQuestionDefinitions = {};
    let newAutoselectOptions = [];

    let parseFilterList = (entries, title) => {
      for (let entry of entries) {
        let path = entry["@path"];
        newQuestionDefinitions[path] = entry;

        let option = {
          path : path,
          label: entry.text,
          breadcrumbs: entry.sectionBreadcrumbs?.join(" / ")
        };
        if (title) {
          option.category = title;
        }
        newAutoselectOptions.push(option);
      }
    }

    if (data.metadataFilters) {
      parseFilterList(data.metadataFilters);
    }

    if (questionnaire) {
      parseFilterList(data.questions);
    } else {
      // Parse the response from examining every questionnaire
      for (let [title, thisQuestionnaire] of Object.entries(data)) {
        (thisQuestionnaire["jcr:primaryType"] == "cards:Questionnaire") && parseFilterList(thisQuestionnaire.questions, title);
      }
    }

    setQuestionDefinitions(newQuestionDefinitions);
    setAutoselectOptions(newAutoselectOptions);
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
      grabFilters();
    }
  }

  // Add a new filter
  let addFilter = () => {
    setEditingFilters(oldfilters => {var newfilters = oldfilters.slice(); newfilters.push({}); return(newfilters);})
  }

  // Handle the user changing one of the active filter categories
  let handleChangeFilter = (index, path) => {

    // Load up the comparators for this index, if not already loaded
    let [loadedComparators, component] = getOutputChoices(path);

    // Automatically add a new filter if they've edited the final filter
    if (index == editingFilters.length-1) {
      addFilter();
    }

    getOutputChoices(path);
    setEditingFilters(oldfilters => {
      var newfilters = oldfilters.slice();
      var newfilter = {
        name: path,
        uuid: questionDefinitions[path]["jcr:uuid"],
        comparator: loadedComparators[0],
        title: questionDefinitions[path]?.text,
        type: questionDefinitions[path]?.dataType || "text"
      }

      // Keep any old data, if possible
      if (index in oldfilters && newfilter.type == oldfilters[index].type) {
        newfilter.value = oldfilters[index].value;
        newfilter.comparator = oldfilters[index].comparator;
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

  let handleChangeOutput = (index, newValue, newLabel, dataType) => {
    setEditingFilters( oldfilters => {
      let newFilters = oldfilters.slice();
      let newFilter =  {...newFilters[index], value: newValue, type: dataType};
      if (newLabel != null) {
        newFilter.label = newLabel;
      }
      newFilters.splice(index, 1, newFilter);
      return(newFilters);
    })
  }

  let getOutputChoices = (field) => {
    let [comparators, component] = FilterComponentManager.getFilterComparatorsAndComponent(questionDefinitions[field]);
    if (questionDefinitions[field].enableNotes && !comparators.includes(notesComparator)) {
      comparators = comparators.slice();
      comparators.push(notesComparator);
      setTextFilterComponent(old => ({
        ...old,
        [field]: FilterComponentManager.getTextFilterComponent(questionDefinitions[field])
      }));
    }
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
    // Remove special mandatory filters with no value
      .filter( (toCheck) => (!toCheck.uuid.startsWith('cards:') || toCheck.value))
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
    let dataType = questionDefinitions[filterDatum.name]?.dataType || "text";

    let CachedComponent = filterDatum.comparator === notesComparator ?
      textFilterComponent[filterDatum.name]
      :
      filterableAnswers[filterDatum.name];
    return (
      <CachedComponent
        ref={focusRef}
        questionDefinition={questionDefinitions[filterDatum.name]}
        defaultValue={editingFilters[index].value}
        defaultLabel={editingFilters[index].label}
        onChangeInput={(newValue, label) => {handleChangeOutput(index, newValue, label, dataType);}}
        />);
  }

  return(
    <div className={classes.filterContainer}>
      {/* Place the stuff in one row on the top */}
      <Typography display="inline" className={classes.filterLabel}>
        Filters:
      </Typography>
      { activeFilters.map( (activeFilter, index) => {
        let filterValue = activeFilter.label || activeFilter.value;

        return(
            <Chip
              key={`${activeFilter.title}-${index}`}
              size="small"
              label={<>
                       <Tooltip title={activeFilter.title}><span>{activeFilter.title}</span></Tooltip>
                       <span>{activeFilter.comparator}</span>
                       { !UNARY_COMPARATORS.includes(activeFilter.comparator) &&
                         <Tooltip title={filterValue}><span>{filterValue}</span></Tooltip>
                       }
                     </>}
              disabled={disabled}
              variant="outlined"
              color="primary"
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
          );
        })
      }
      <Button
        size="small"
        color="primary"
        className={classes.addFilterButton}
        disabled={disabled}
        onClick={() => {
          openDialogAndAdd();
          setFocusRow(activeFilters.length);
        }}
        >
        <Add fontSize="small" />
      </Button>
      {/* Dialog for setting up filters */}
      <ResponsiveDialog
        open={dialogOpen}
        onClose={closeDialog}
        BackdropProps={{invisible: true}}
        width="md"
        disableEnforceFocus
        title="Modify filters"
        >
        <DialogContent dividers>
          {error &&
            <Typography color="error" className={classes.filterLabel}>
              Error obtaining filter data: {error.status} {error.statusText}
            </Typography>}
          { /* If there is no error but also no data, show a progress circle */
          !error && autoselectOptions.length == 0 &&
            <CircularProgress />}
          <Grid container alignItems="flex-start" spacing={2} className={classes.filterTable}>
            {editingFilters.map( (filterDatum, index) => {
              // We grab focus on the field if we were asked to
              let isUnary = filterDatum.comparator && UNARY_COMPARATORS.includes(filterDatum.comparator);
              let isNotesContain = filterDatum.comparator && (filterDatum.comparator === notesComparator);
              let isContain = filterDatum.comparator && (filterDatum.comparator.includes(TEXT_COMPARATORS));
              return(
                <React.Fragment key={index}>
                  {/* Select the field to filter */}
                  <Grid item xs={12} sm={6}>
                    <VariableAutocomplete
                      disableClearable
                      selectedValue={filterDatum.name}
                      onValueChanged={(value) => {
                        handleChangeFilter(index, value);
                      }}
                      onClose={forceRegrabFocus}
                      autoFocus={(index === editingFilters.length-1 && toFocus === index)}
                      options={autoselectOptions}
                      groupBy={option => option?.category}
                      getOptionValue={option => option?.path}
                      getOptionSecondaryLabel={option => option?.breadcrumbs}
                      getHelperText={option => [option?.category, option?.breadcrumbs].filter(t=>t).join(": ")}
                      textFieldProps={{placeholder: "Add new filter..."}}
                    />
                  </Grid>
                  {/* Depending on whether or not the comparator chosen is unary, the size can change */}
                  <Grid item xs={isUnary ? 11 : isNotesContain || isContain ? 3 : 1} sm={isUnary ? 5 : (isNotesContain ? 3 : (isContain ? 2 : 1))} className={index == editingFilters.length-1 ? classes.hidden : ""}>
                    <Select
                      variant="standard"
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
                    <Grid item xs={isNotesContain || isContain ? 8 : 10} sm={isNotesContain ? 2 : (isContain ? 3 : 4)} className={index == editingFilters.length-1 ? classes.hidden : ""}>
                      {filterDatum.comparator ?
                          getCachedInput(filterDatum, index, (index !== editingFilters.length-1 && toFocus === index ? focusCallback : undefined))
                        : <TextField variant="standard" disabled className={classes.answerField}></TextField>
                      }
                    </Grid>}
                  {/* Deletion button */}
                  <Grid item xs={1} className={index == editingFilters.length-1 ? classes.hidden : classes.tableActions}>
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
            variant="outlined"
            onClick={closeDialog}
            >
            {'Cancel'}
          </Button>
        </DialogActions>
      </ResponsiveDialog>
    </div>
  );
}

export default withStyles(LiveTableStyle)(Filters);
