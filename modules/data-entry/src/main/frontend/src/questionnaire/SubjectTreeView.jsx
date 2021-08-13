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

import TreeView from '@material-ui/lab/TreeView';
import TreeItem from '@material-ui/lab/TreeItem';

import ExpandMoreIcon from '@material-ui/icons/ExpandMore';
import ChevronRightIcon from '@material-ui/icons/ChevronRight';
import MoreIcon from '@material-ui/icons/MoreHoriz';

import FormattedText from "../components/FormattedText";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";
import { QUESTION_TYPES, SECTION_TYPES, ENTRY_TYPES } from "./FormEntry.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";
import { displayQuestion, handleDisplay } from "./Subject.jsx";

import {
  CircularProgress,
  Chip,
  Grid,
  IconButton,
  Tooltip,
  Typography,
  makeStyles,
  withStyles,
} from "@material-ui/core";
import FileIcon from "@material-ui/icons/InsertDriveFile";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";
import EditButton from "../dataHomepage/EditButton.jsx";
import ResourceHeader from "./ResourceHeader.jsx"
import SubjectTimeline from "./SubjectTimeline.jsx";


/**
 * Component displays the forms associated with this subject, and all its child subjects with their forms,
 * in a browsable tree structure
 */
function SubjectTreeView (props) {
  let { subject } = props;

  return ( subject ?
    <TreeView
      defaultCollapseIcon={<ExpandMoreIcon />}
      defaultExpandIcon={<ChevronRightIcon />}
      disableSelection
    >
      <SubjectItem {...props} />
    </TreeView>
    : null
  );
}

SubjectTreeView.propTypes = {
  subject: PropTypes.object,
}

/**
 * Component that recursively gets and displays the selected subject and its related SubjectTypes
 */
function SubjectItem(props) {
  let { subject, withTitle, ...rest } = props;
  let { classes, history } = rest;
  // Error status set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // Error message set when fetching the data from the server fails
  let [ errorMessage, setErrorMessage ] = useState(null);
  // hold related subjects
  let [relatedSubjects, setRelatedSubjects] = useState();
  // whether the subject has been deleted
  let [ deleted, setDeleted ] = useState();

  let globalLoginDisplay = useContext(GlobalLoginContext);

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response);
  };
  
  // Create a URL that checks for the existence of a subject
  let createQueryURL = (query, type) => {
    let url = new URL("/query", window.location.origin);
    url.searchParams.set("query", `SELECT * FROM [${type}] as n` + query);
    return url;
  } 

  let check_url = createQueryURL(` WHERE n.'parents'='${subject?.['jcr:uuid']}' order by n.'jcr:created'`, "cards:Subject");

  let fetchRelated = () => {
    fetchWithReLogin(globalLoginDisplay, check_url)
    .then((response) => response.ok ? response.json() : Promise.reject(response))
    .then((json) => { setRelatedSubjects(json.rows); })
    .catch(handleError);
  }

  // Fetch this Subject's data
  useEffect(() => {
    if (subject) {
      fetchRelated();
    } else {
      setRelatedSubjects(null);
    }
  }, [subject]);

  useEffect(() => {
    setErrorMessage(error ?
    ( <Tree item id={subject.identifier + "-error"}
        label= {
          <Typography color="error">
            Error obtaining subject data: {error.status} {error.statusText ? error.statusText : error.toString()}
          </Typography>
        }
      />)
    : null);
  }, [error]);

  if (!subject || deleted) {
    return null;
  }

  let hasParent = withTitle;
  let hasChildren = !!(relatedSubjects?.length);
  let noDataMessage = hasParent || hasChildren ? null : <Typography key="error" color="textSecondary" variant="caption">{`No data associated with this ${subject?.type?.label?.toLowerCase()} was found.`}</Typography>;

  let forms = <SubjectForms key="forms" {...props} placeholder={noDataMessage}/>;
  let childSubjects = relatedSubjects?.map( (s, i) => <SubjectItem key={i} subject={s} withTitle {...rest} /> ) || [];
  let childItems = [forms];
  if (errorMessage) {
     childItems.push(errorMessage);
  } else {
     childItems.push(...childSubjects);
  }

  let identifier = subject?.identifier;
  let label = subject?.type?.label || "Subject";
  let title = `${label} ${identifier}`;
  let path = subject?.["@path"];

  return ( withTitle ?
    <TreeItem
      nodeId={identifier}
      label={
        <TreeItemLabel
           variant="h6"
           label={<>{label} <Link to={"/content.html" + path}>{identifier}</Link></>}
           action={
             <DeleteButton
               entryPath={path}
               entryName={title}
               entryType={label}
               onComplete={() => {setDeleted(true)}}
            />
          }
        />
      }
    >
     {childItems}
    </TreeItem>
    : <>
      {childItems}
    </>
  );
};

/**
 * Component that displays all forms related to a Subject
 */
function SubjectForms (props) {
  let { subject, classes, placeholder, history } = props;
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  let [ subjectGroups, setSubjectGroups ] = useState();

  let globalLoginDisplay = useContext(GlobalLoginContext);

  const customUrl=`/Forms.paginate?fieldname=subject&fieldvalue=${encodeURIComponent(subject['jcr:uuid'])}&includeallstatus=true&limit=1000`;

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
  }, [subject['jcr:uuid']]);


  // If an error was returned, report it
  if (error) {
    return (
      <TreeItem
        nodeId={subject.identifier + "data-error"}
        label={
          <Typography color="error">
            Error obtaining subject data: {error.status} {error.statusText ? error.statusText : error.toString()}
          </Typography>
        }
      />
    );
  }

  // If we have no data to display for this subject, return the specified placeholder
  if (!subject || !(Object.keys(subjectGroups || {}).length)) {
    return placeholder;
  }

  return (
    <>
        { Object.keys(subjectGroups).map( (questionnaireTitle, j) => {
          return (
            <TreeItem
              key={questionnaireTitle}
              nodeId={`${subject.identifier}-${questionnaireTitle}`}
              label={<Typography variant="h6">{questionnaireTitle}</Typography>}
            >
              { subjectGroups[questionnaireTitle].map((rowData, i) => (
                <TreeItem
                  key={i}
                  nodeId={`${subject.identifier}-${questionnaireTitle}-${i}`}
                  label={
                    <TreeItemLabel
                      variant="subtitle1"
                      label={<>
                        <Link to={"/content.html" + rowData["@path"]}>
                           {moment(rowData['jcr:created']).format("YYYY-MM-DD")}
                        </Link>
                        { rowData["statusFlags"].map((status) => (
                          <Chip
                             key={status}
                             label="Draft"
                             variant="outlined"
                             className={`${classes.subjectChip} ${classes[status + "Chip"] || classes.DefaultChip}`}
                             size="small"
                           />
                        ))}
                      </>}
                      action={<>
                        <EditButton
                          entryPath={rowData["@path"]}
                          entryType="Form"
                        />
                        <DeleteButton
                          entryPath={rowData["@path"]}
                          entryName={`${subject.identifier}: ${rowData.questionnaire["@name"]}`}
                          entryType="Form"
                          warning={rowData ? rowData["@referenced"] : false}
                          onComplete={fetchTableData}
                        />
                      </>}
                    />
                  }
                 >
                   <TreeItem
                     nodeId={`${subject.identifier}-${questionnaireTitle}-${i}-data`}
                     label={
                       <TreeItemLabel
                         label={<FormExcerpt className={classes.formData} formId={rowData["@name"]} classes={classes}/>}
                         action={
                           <Tooltip title="View form">
                             <IconButton onClick={() => {history.push("/content.html" + rowData["@path"])}}>
                               <MoreIcon />
                             </IconButton>
                           </Tooltip>
                         }
                       />
                     }
                   />
                 </TreeItem>
              ))}
            </TreeItem>)
          })
        }
    </>
  );
};

const useTreeItemStyles = makeStyles((theme) => ({
  treeItemLabel : {
    display: "flex",
    alignItems: 'center',
    justifyContent: "space-between",
    "& .MuiChip-root" : {
      marginLeft: theme.spacing(1.5),
      marginTop: theme.spacing(0.5),
    }
  },
  treeItemAction: {
  }
}));
// Component that displays a structured tree item label
function TreeItemLabel(props) {
  let {label, variant, action} = props;
  
  let classes = useTreeItemStyles();
  
  return (
    <div className={classes.treeItemLabel}>
      <Typography variant={variant} component="div">{label}</Typography>
      {action && <div className={classes.treeItemAction}>{action}</div>}
    </div>
  )
}

// Component that displays a preview of the saved form answers
function FormExcerpt(props) {
  let { formId, size, classes } = props;
  // This holds the full form JSON, once it is received from the server
  let [ data, setData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // number of form question/answers listed, will increase
  let displayed = 0;

  let globalLoginDisplay = useContext(GlobalLoginContext);

  // Fetch the form's data from the server
  // It will be stored in the `data` state variable
  let getFormData = (formId) => {
    fetchWithReLogin(globalLoginDisplay, `/Forms/${formId}.deep.json`)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => setData(json))
        .catch(handleFormError)
  }

  let handleFormError = (response) => {
    setError(response);
    setData([]);  // Prevent an infinite loop if data was not set
  };

  // Fetch this Form's data
  useEffect(() => {
    getFormData(formId);
  }, [formId]);

  // If the data has not yet been fetched, return an in-progress symbol
  if (!data) {
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  if (error) {
    return (
      <Typography color="error">
        Error obtaining form data: {error.status} {error.statusText}
      </Typography>
    );
  }
  // Handle questions and sections differently
  let handleDisplayQuestion = (entryDefinition, data, key) => {
    let result = displayQuestion(entryDefinition, data, key, classes);
    if (result && displayed < size) {
      displayed++;
    } else {
      result = null;
    }
    return result;
  }
  
  let excerpt = (
      Object.entries(data.questionnaire)
          .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
          .map(([key, entryDefinition]) => handleDisplay(entryDefinition, data, key, handleDisplayQuestion))
  );

  if (data && data.questionnaire) {
    return (
      <React.Fragment>
        { excerpt?.length ? excerpt : <Typography variant="caption">This form is empty.</Typography> }
      </React.Fragment>
    );
  }
  else return;
}

FormExcerpt.propTypes = {
  formId: PropTypes.string.isRequired,
  size: PropTypes.number,
}

FormExcerpt.defaultProps = {
  size: 4,
}


export default withStyles(QuestionnaireStyle)(withRouter(SubjectTreeView));
