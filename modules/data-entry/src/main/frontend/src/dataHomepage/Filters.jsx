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
import { UNARY_COMPARATORS, TEXT_COMPARATORS } from "./FilterComponents/FilterComparators.jsx";

const ALL_QUESTIONNAIRES_URL = "/Questionnaires.deep.json";
const FILTER_URL = "/Questionnaires.filters";

function Filters(props) {
  const { classes, disabled, onChangeFilters, questionnaire, filtersJsonString } = props;
  // Filters, as displayed in the dialog, and filters as actually saved
  const [editingFilters, setEditingFilters] = useState([]);
  const [activeFilters, setActiveFilters] = useState([]);
  // Information on the questionnaires
  const [filterableAnswers, setFilterableAnswers] = useState({});
  const [filterableFields, setFilterableFields] = useState([]);
  const [filterableTitles, setFilterableTitles] = useState({});
  const [filterableUUIDs, setFilterableUUIDs] = useState({});
  const [filterComparators, setFilterComparators] = useState({});
  const [textFilterComponent, setTextFilterComponent] = useState({});
  const [questionDefinitions, setQuestionDefinitions] = useState({});
  // Other state variables
  const [error, setError] = useState();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [filterRequestSent, setFilterRequestSent] = useState(false);
  const [toFocus, setFocusRow] = useState(null);
  const notesComparator = "notes contain";
  const [autoselectOptions, setAutoselectOptions] = useState([]);

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

  useEffect(() => {
    if (filterableFields.length > 0 && Object.keys(filterableTitles).length > 0 && autoselectOptions.length == 0) {
      setAutoselectOptions(getFieldsLabelsList(filterableFields, ""));
    }
  }, [filterableFields, filterableTitles]);

  // Obtain information about the filters that can be applied
  let grabFilters = () => {
    setFilterRequestSent(true);
    let url;

    if (questionnaire) {
      // Setting the questionnaire prop will go through the fitler servlet (FilterServlet.java)
      url = new URL(FILTER_URL, window.location.origin);
      url.searchParams.set("questionnaire", questionnaire);
      fetchWithReLogin(globalLoginDisplay, url)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then(parseFilterData)
        .catch(setError);
    } else {
      // Otherwise, we need a structured output -- go through all Questionnaires.deep.json instead
      url = new URL(ALL_QUESTIONNAIRES_URL, window.location.origin);
      fetchWithReLogin(globalLoginDisplay, url)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(parseQuestionnaireData)
      .catch(setError);
    }
  }

  // Parse the response from examining every questionnaire
  let parseQuestionnaireData = (questionnaireJson) => {
    // newFilterableFields is a list of fields that can be filtered on
    // It is a list of either strings (for options) or recursive lists
    // Each recursive list must have a string for its 0th option, which
    // is taken to be its title
    let newFilterableFields = ["Questionnaire", "Subject", "CreatedDate"];
    // newFilterableUUIDs is a mapping from a string in newFilterableFields to a jcr:uuid
    let newFilterableUUIDs = {Questionnaire: "cards:Questionnaire", Subject: "cards:Subject", CreatedDate: "cards:CreatedDate"};
    // newFilterableTitles is a mapping from a string in newFilterableFields to a human-readable title
    let newFilterableTitles = {Questionnaire: "Questionnaire", Subject: "Subject", CreatedDate: "Created Date"};
    // newQuestionDefinitions is normally the straight input from FilterServlet.java
    // Instead, we need to reconstruct this client-side
    let newQuestionDefinitions = {Questionnaire: {dataType: "questionnaire"}, Subject: {dataType: "subject"}}

    // We'll need a helper recursive function to copy over data from sections/questions
    let parseSectionOrQuestionnaire = (sectionJson, path="") => {
      let retFields = [];
      Object.entries(sectionJson).map(([title, object]) => {
        // We only care about children that are cards:Questions or cards:Sections
        if (object["jcr:primaryType"] == "cards:Question") {
          // If this is an cards:Question, copy the entire thing over to our Json value
          retFields.push(path+title);
          // Also save the human-readable name, UUID, and data type
          newQuestionDefinitions[path+title] = object;
          newFilterableUUIDs[path+title] = object["jcr:uuid"];
          newFilterableTitles[path+title] = object["text"];
        } else if (object["jcr:primaryType"] == "cards:Section") {
          // If this is an cards:Section, recurse deeper
          retFields.push(...parseSectionOrQuestionnaire(object, path+title+"/"));
        }
        // Otherwise, we don't care about this value
      });
      return retFields;
    }

    // From the questionnaire homepage, we're looking for children that are objects of type cards:Questionnaire
    for (let [title, thisQuestionnaire] of Object.entries(questionnaireJson)) {
      if (thisQuestionnaire["jcr:primaryType"] != "cards:Questionnaire") {
        continue;
      }

      newFilterableFields.push([thisQuestionnaire.title || title, ...parseSectionOrQuestionnaire(thisQuestionnaire, title+"/")]);
    }

    newQuestionDefinitions["Subject"] = {
      dataType: "subject"
    };
    newQuestionDefinitions["CreatedDate"] = {
      dataType: "createddate"
    };

    // We also need a filter over the subject
    setFilterableFields(newFilterableFields);
    setQuestionDefinitions(newQuestionDefinitions);
    setFilterableTitles(newFilterableTitles);
    setFilterableUUIDs(newFilterableUUIDs);
  }

  // Parse the response from our FilterServlet
  let parseFilterData = (filterJson) => {
    // Parse through, but keep a custom field for the subject
    let fields = ["Subject", "CreatedDate"];
    let uuids = {Subject: "cards:Subject", CreatedDate: "cards:CreatedDate"};
    let titles = {Subject: "Subject", CreatedDate: "Created Date"};
    for (let [questionName, question] of Object.entries(filterJson)) {
      // For each question, save the name, data type, and answers (if necessary)
      fields.push(questionName);
      uuids[questionName] = question["jcr:uuid"];
      titles[questionName] = question["text"];
    }
    filterJson["Subject"] = {
      dataType: "subject"
    };
    filterJson["CreatedDate"] = {
      dataType: "createddate"
    };
    setFilterableFields(fields);
    setQuestionDefinitions(filterJson);
    setFilterableTitles(titles);
    setFilterableUUIDs(uuids);
  }

  let removeCreatedDateTimezone = (filters) => {
    let newFilters = [];
    filters.forEach( (filter) => {
      if (filter.type === "createddate") {
        newFilters.push({ ...filter, value: filter.value.split('T')[0]});
      } else {
        newFilters.push({ ...filter });
      }
    });
    return newFilters;
  };

  let addCreatedDateTimezone = (filters) => {
    const getClientTimezoneOffset = () => {
      const padTwo = (s) => {
        if (s.length < 2) {
          return '0' + s;
        }
        return s;
      };
      let totalOffsetMinutes = new Date().getTimezoneOffset();
      let offsetSign = (totalOffsetMinutes < 0) ? '+' : '-';
      let offsetMinute = Math.abs(totalOffsetMinutes) % 60;
      let offsetHour = Math.floor(Math.abs(totalOffsetMinutes) / 60);
      return offsetSign + padTwo(offsetHour.toString()) + ":" + padTwo(offsetMinute.toString());
    };
    let newFilters = [];
    filters.forEach( (filter) => {
      if (filter.type === "createddate") {
        newFilters.push({ ...filter, value: filter.value + "T00:00:00" + getClientTimezoneOffset() });
      } else {
        newFilters.push({ ...filter });
      }
    });
    return newFilters;
  };

  // Open the filter selection dialog
  let openDialogAndAdd = () => {
    setDialogOpen(true);
    // Replace our defaults with a deep copy of what's actually active, plus an empty one
    let newFilters = deepCopyFilters(activeFilters);
    newFilters = removeCreatedDateTimezone(newFilters);
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
        uuid: filterableUUIDs[path],
        comparator: loadedComparators[0],
        title: filterableTitles[path],
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
    newFilters = addCreatedDateTimezone(newFilters);
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

  let getFieldsLabelsList = (fields, category) => {
    return fields.map((path) => {
      if (typeof path == "string") {
        // Straight strings are MenuItems
        return {path: path, label: filterableTitles[path], category: category}
      } else if (Array.isArray(path)) {
        // Arrays represent Questionnaires of Sections
        // which we'll need to turn into opt groups
        return [getFieldsLabelsList(path.slice(1), path[0])].flat();
      }
    }).flat();
  }

  return(
    <div className={classes.filterContainer}>
      {/* Place the stuff in one row on the top */}
      <Typography display="inline" className={classes.filterLabel}>
        Filters:
      </Typography>
      { activeFilters.map( (activeFilter, index) => {
        let filterValue = activeFilter.label || activeFilter.value;
        filterValue = (activeFilter.type === "createddate") ? filterValue.split('T')[0] : filterValue;
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
          !error && !filterableFields &&
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
                      groupBy={(option) => option?.category}
                      getOptionValue={option => option?.path}
                      getHelperText={option => option?.category}
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
