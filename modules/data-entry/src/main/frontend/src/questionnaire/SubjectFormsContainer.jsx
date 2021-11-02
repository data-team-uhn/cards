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
import { Link, withRouter } from 'react-router-dom';
import PropTypes from "prop-types";
import moment from "moment";

import {
  Avatar,
  Checkbox,
  CircularProgress,
  Chip,
  FormControl,
  Grid,
  Input,
  MenuItem,
  Select,
  Typography,
  withStyles,
} from "@material-ui/core";

import PrintIcon from "@material-ui/icons/Print";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";
import Form from "./Form.jsx";
import MainActionButton from "../components/MainActionButton.jsx";

import { ENTRY_TYPES } from "./FormEntry.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import { displayQuestion, handleDisplay } from "./SubjectUtilities.jsx";

/***
 * Create a URL that checks for the existence of a subject
 */
let createQueryURL = (query, type) => {
  let url = new URL("/query", window.location.origin);
  url.searchParams.set("query", `SELECT * FROM [${type}] as n` + query);
  return url;
}


/**
 * Component that recursively gets and displays the selected subject's forms 
 * and forms of it's selected descendant subject types
 */
function SubjectFormsContainer(props) {
  let { id, classes, subject, subjectTitle, level, updateDescendantSubjects, selectedSubjects } = props;
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // hold all related descendant subjects
  let [relatedSubjects, setRelatedSubjects] = useState();
  // hold filtered related descendant subjects to show
  let [relatedFilteredSubjects, setRelatedFilteredSubjects] = useState();
  // And array of strings holding descendant subject types in the right descending order
  let [descendantSubjectTypes, setDescendantSubjectTypes] = useState([]);
  // Holds sorted related subjects by descendant type to display the dropdowns by subject type
  let [descendantSubjectsByType, setDescendantSubjectsByType] = useState({});
  // Holds subjects set in filters by user organized as an object with keys as subject types for corresponding arrays of selected subjects 
  let [filteredSubjects, setFilteredSubjects] = useState({});
  // Holds array of subject ids
  let [filteredSubjectIds, setFilteredSubjectIds] = useState(selectedSubjects || []);
  // Show root subject forms by default
  let [subjectFormsEnabled, setSubjectFormsEnabled] = useState(true);

  let updateDescSubjects = (subjects) => {
    subjects.map(subj => {
      setDescendantSubjectsByType(old => {
        var _new = {...old};
        var contains = _new[subj.type.label] ? _new[subj.type.label].find(el => el["jcr:uid"] == subj["jcr:uid"]) : null;
        !contains && (_new[subj.type.label]?.push(subj) || (_new[subj.type.label] = [subj]));
        return _new;
      });
    });
  }

  let globalLoginDisplay = useContext(GlobalLoginContext);

  // 'level' of subject component
  const currentLevel = level || 0;

  // Update the subjects to be fetched upon filters change
  useEffect(() => {
    if (filteredSubjectIds && relatedSubjects && relatedSubjects.length > 0) {
      if (filteredSubjectIds.length == 0) {
        // No filters set, show all subjects
        setRelatedFilteredSubjects(relatedSubjects);
      } else {
        let filteredRelatedSubjects = relatedSubjects.filter(subject => filteredSubjectIds.includes(subject["jcr:uuid"]));
        setRelatedFilteredSubjects(filteredRelatedSubjects);
      }
    }
  }, [filteredSubjectIds]);

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response);
  };

  let check_url = createQueryURL(` WHERE n.'parents'='${subject?.['jcr:uuid']}' order by n.'jcr:created'`, "cards:Subject");
  let fetchRelated = () => {
    fetchWithReLogin(globalLoginDisplay, check_url)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then((json) => {
      setRelatedSubjects(json.rows);
      // Initially show forms for all subjects
      setRelatedFilteredSubjects(json.rows);
      // Callback to update the component level collector for all available subjects for filter display
      updateDescendantSubjects && updateDescendantSubjects(json.rows);
      currentLevel == 0 && updateDescSubjects(json.rows);
    })
    .catch(handleError);
  }

  let getDescendantSubjectTypes = (object, types) => {
    for (const property in object) {
      if (object[property]["jcr:primaryType"] === "cards:SubjectType") {
        types.push(object[property].label);
        getDescendantSubjectTypes(object[property], types);
      }
    }
    return types;
  }

  // Fetch this Subject's data
  useEffect(() => {
    if (subject) {
      fetchRelated();
      // Get the descendant subject types if we are at the top level
      currentLevel == 0 && setDescendantSubjectTypes(getDescendantSubjectTypes(subject, []));
    } else {
      setRelatedSubjects(null);
      setRelatedFilteredSubjects(null);
    }
  }, [subject]);

  // If the data has not yet been fetched, return an in-progress symbol
  if (!subject) {
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  if (error) {
    return (
      <Grid container justify="center">
        <Grid item>
          <Typography variant="h2" color="error">
            Error obtaining subject data: {error.status} {error.statusText ? error.statusText : error.toString()}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  let handleFilterChange = (subjectLabel, event) => {
    let value = event?.target?.value;
    if (value) {
      var _new = {...filteredSubjects};
      _new[subjectLabel] = value;
      
      let ids = [];
      Object.keys(_new).map(type => {
        _new[type].map( subject => ids.push(subject["jcr:uuid"]) );
      });

      setFilteredSubjectIds(ids);
      setFilteredSubjects(_new);
    }
  }

  return (
    subject && <React.Fragment>
      { currentLevel == 0 && <Grid item className={classes.subjectFormsContainer}>
        <Grid container spacing={1}>
          <Grid item>
            <Typography variant="subtitle2" className={classes.subjectFiltersHeader} >{subjectTitle}</Typography>
          </Grid>
          <Grid item>
            <Checkbox
              checked={subjectFormsEnabled}
              onChange={(event) => {setSubjectFormsEnabled(event?.target?.checked)}}
              className={classes.subjectCheckbox}
              color="primary"
            />
          </Grid>
          { Object.keys(descendantSubjectsByType).map( (subjectLabel) => (<>
             <Grid item>
              <Typography variant="subtitle2" className={classes.subjectFiltersHeader} >{subjectLabel}</Typography>
            </Grid>
            <Grid item>
              <Select
                multiple
                className={classes.subjectSelect}
                value={filteredSubjects[subjectLabel] || []}
                onChange={(event) => {handleFilterChange(subjectLabel, event);}}
                input={<Input id={subjectLabel} />}
                renderValue={(value) => (
                  <div>
                    {value.map( (val, index) => (
                      <Chip key={val+index} label={val.identifier}/>
                    ))}
                  </div>
                )}
              >
                { descendantSubjectsByType[subjectLabel].map((subject) => (
                  <MenuItem
                    key={subject.identifier}
                    value={subject}
                  >
                    {subject.identifier}
                  </MenuItem>
                ))}
              </Select>
            </Grid>
          </>))}
        </Grid>
      </Grid> }
      { subjectFormsEnabled && <SubjectFormMember classes={classes} id={id} level={currentLevel} data={subject} /> }
      {relatedFilteredSubjects && relatedFilteredSubjects.length > 0 ?
        (<Grid item xs={12}>
          {relatedFilteredSubjects.map( (subject, i) => (
            // Render component again for each related subject
              <SubjectFormsContainer
                key={i}
                classes={classes}
                level={currentLevel+1}
                path={subject["@path"]}
                subject={subject}
                selectedSubjects={filteredSubjectIds}
                updateDescendantSubjects={ currentLevel > 0 ? updateDescendantSubjects : updateDescSubjects}
              />
            )
          )}
        </Grid>
        ) : ""
      }
      <MainActionButton
        icon={<PrintIcon />}
        onClick={() => window.print()}
        label="Print"
      />
    </React.Fragment>
  );
}

/**
 * Component that displays all forms related to a Subject. Do not use directly, use SubjectMember instead.
 */
function SubjectFormMemberInternal (props) {
  let { classes, data, id, level } = props;
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  let [ subjectGroups, setSubjectGroups ] = useState();

  let globalLoginDisplay = useContext(GlobalLoginContext);

  const customUrl=`/Forms.paginate?fieldname=subject&fieldvalue=${encodeURIComponent(data['jcr:uuid'])}&includeallstatus=true&limit=1000`;

  // Fetch the forms associated with the subject as JSON from the server
  // It will be stored in the `tableData` state variable
  let fetchTableData = () => {
    fetchWithReLogin(globalLoginDisplay, customUrl)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then(handleTableResponse)
    .catch(handleTableError);
  };

  let handleTableResponse = (json) => {
    let groups = {};
    json.rows.map( (entry, i) => {
      let title = entry.questionnaire.title || entry.questionnaire["@name"];
      groups[title]?.push(entry) || (groups[title] = [entry]);
    })
    setSubjectGroups(groups);
  };

  let handleTableError = (response) => {
    setError(response);
    setSubjectGroups({});
  };

  let wordToTitleCase = (word) => {
    return word[0].toUpperCase() + word.slice(1).toLowerCase();
  }

  // Fetch table data for all forms related to a Subject
  useEffect(() => {
    fetchTableData();
  }, [data['jcr:uuid']]);

  // If an error was returned, do not display a subject at all, but report the error
  if (error) {
    return (
      <Grid container justify="center">
        <Grid item>
          <Typography variant="h2" color="error">
            Error obtaining subject data: {error.status} {error.statusText ? error.statusText : error.toString()}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  let identifier = data && data.identifier ? data.identifier : id;
  let label = data?.type?.label || "Subject";
  let title = `${label} ${identifier}`;
  let path = data ? data["@path"] : "/Subjects/" + id;

  return ( data &&
    <>
      { subjectGroups && Object.keys(subjectGroups).length > 0 &&
        Object.keys(subjectGroups).map( (questionnaireTitle, j) => (
            <Grid item key={questionnaireTitle}>
              { subjectGroups[questionnaireTitle].map(form => (
                  <Form id={form["@name"]} key={form["@name"]} disableButton mode="print"/>
              ))}
            </Grid>
      ))}
    </>
  );
};

let SubjectFormMember = withRouter(SubjectFormMemberInternal);


export default withStyles(QuestionnaireStyle)(SubjectFormsContainer);
