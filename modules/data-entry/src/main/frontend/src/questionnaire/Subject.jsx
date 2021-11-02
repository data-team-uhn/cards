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
import { Link, useLocation, withRouter } from 'react-router-dom';
import PropTypes from "prop-types";
import moment from "moment";

import {
  CircularProgress,
  Card,
  CardContent,
  Chip,
  Grid,
  Tooltip,
  Tab,
  Tabs,
  Typography,
  withStyles,
} from "@material-ui/core";

import DeleteButton from "../dataHomepage/DeleteButton.jsx";
import ResourceHeader from "./ResourceHeader.jsx"
import SubjectContainer from "./SubjectContainer.jsx";
import SubjectTimeline from "./SubjectTimeline.jsx";
import SubjectFormsContainer from "./SubjectFormsContainer.jsx";
import FormattedText from "../components/FormattedText";
import QuestionnaireStyle from "./QuestionnaireStyle.jsx";
import NewFormDialog from "../dataHomepage/NewFormDialog";

import { usePageNameWriterContext } from "../themePage/Page.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "../login/loginDialogue.js";

let defaultCreator = (node) => {
  return {to: "/content.html" + node["@path"]}
}

// Extract the subject id from the subject path
// returns null if the parameter is not a valid subject path (expected format: Subjects/<id>)
export function getSubjectIdFromPath (path) {
  return /Subjects\/([^.]+)/.exec(path || '')?.[1];
}

// Recursive function to get a flat list of parents
export function getHierarchy (node, RenderComponent, propsCreator) {
  let HComponent = RenderComponent || Link;
  let hpropsCreator = propsCreator || defaultCreator;
  let props = hpropsCreator(node);
  let output = <React.Fragment>{node.type.label} <HComponent {...props}>{node.identifier}</HComponent></React.Fragment>;
  if (node["parents"]) {
    let ancestors = getHierarchy(node["parents"], HComponent, propsCreator);
    return <React.Fragment>{ancestors} / {output}</React.Fragment>
  } else {
    return output;
  }
}

// Recursive function to get a flat list of parents with no links and subject labels
export function getTextHierarchy (node, withType = false) {
  let type = withType ? (node?.["type"]?.["@name"] + " "): "";
  let output = node.identifier;
  if (node["parents"]) {
    let ancestors = getTextHierarchy(node["parents"], withType);
    return `${ancestors} / ${type}${output}`;
  } else {
    return type + output;
  }
}

// Recursive function to get the list of ancestors as an array
export function getHierarchyAsList (node, includeHomepage) {
  let props = defaultCreator(node);
  let parent = <>{node.type.label} <Link {...props}>{node.identifier}</Link></>;
  if (node["parents"]) {
    let ancestors = getHierarchyAsList(node["parents"]);
    ancestors.push(parent);
    return ancestors;
  } else {
    let result = [parent];
    includeHomepage && result.unshift(getHomepageLink(node));
    return result;
  }
}

export function getHomepageLink (subjectNode) {
  let props = defaultCreator({"@path": `/Subjects#subjects:activeTab=${subjectNode?.type?.["@name"]}`});
  return (<Link {...props}>{subjectNode?.type?.subjectListLabel || "Subjects"}</Link>);
}

/**
 * Component that displays a Subject.
 *
 * @example
 * <Subject id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {string} id the identifier of a subject; this is the JCR node name
 * @param {int} maxDisplayed the maximum number of form question/answers to be displayed. defaults to 4
 */

function Subject(props) {
  let { id, classes, maxDisplayed, pageSize, history } = props;
  const [ currentSubject, setCurrentSubject ] = useState();
  const [ currentSubjectId, setCurrentSubjectId ] = useState(id);
  const [ activeTab, setActiveTab ] = useState(0);

  const tabs = ["Chart", "Timeline", "Questionnaires"]
  const location = useLocation();

  let identifier = currentSubject?.identifier || id;
  let label = currentSubject?.type?.label || "Subject";
  let subjectTitle = `${label} ${identifier}`;

  useEffect(() => {
    let newId = getSubjectIdFromPath(location.pathname);
    newId && setCurrentSubjectId(newId);
    if (location.hash.length > 0 && tabs.includes(location.hash.substring(1))) {
      setActiveTab(tabs.indexOf(location.hash.substring(1)));
    }
  }, [location]);

  let pageTitle = currentSubject && getTextHierarchy(currentSubject, true);

  // Change the title of the page whenever parentDetails changes
  let pageNameWriter = usePageNameWriterContext();
  useEffect(() => {
    pageTitle && pageNameWriter(pageTitle);
  }, [pageTitle]);

  // the subject data, fetched in the SubjectHeader component, will be stored in the `type` state
  function handleSubject(e) {
    setCurrentSubject(e);
  }

  function setTab(index) {
    history.replace(location.pathname+location.search+"#"+tabs[index], location.state)
    setActiveTab(index);
  }

  return (
    <React.Fragment>
      {activeTab != tabs.indexOf("Questionnaires") &&
        <NewFormDialog currentSubject={currentSubject}>
          { "New questionnaire for this " + (currentSubject?.type?.label || "Subject") }
        </NewFormDialog>
      }
      <Grid container spacing={4} direction="column" className={classes.subjectContainer}>
        <SubjectHeader id={currentSubjectId} key={"SubjectHeader"} classes={classes} getSubject={handleSubject} history={history} contentOffset={props.contentOffset}/>
        <Grid item>
          <Tabs className={classes.subjectTabs} value={activeTab} onChange={(event, value) => {
            setTab(value);
          }}>
            {tabs.map((tab) => {
              return <Tab label={tab} key={tab}/>;
            })}
          </Tabs>
          <Card variant="outlined"><CardContent>
          <Grid container spacing={4} direction="column" wrap="nowrap">
          { activeTab === tabs.indexOf("Chart")
          ? <SubjectContainer
              id={currentSubjectId}
              key={currentSubjectId}
              classes={classes}
              maxDisplayed={maxDisplayed}
              pageSize={pageSize}
              subject={currentSubject}
            />
          : activeTab === tabs.indexOf("Timeline")
          ? <Grid item>
              <SubjectTimeline
                id={currentSubjectId}
                classes={classes}
                pageSize={pageSize}
                subject={currentSubject}
              />
            </Grid>
          : <SubjectFormsContainer
              id={currentSubjectId}
              subjectTitle={subjectTitle}
              key={currentSubjectId}
              classes={classes}
              subject={currentSubject}
            />
          }
          </Grid>
          </CardContent></Card>
        </Grid>
      </Grid>
    </React.Fragment>
  );
}

/**
 * Component that displays the header for the selected subject and its SubjectType
 */
function SubjectHeader(props) {
  let { id, classes, getSubject, history } = props;
  // This holds the full form JSON, once it is received from the server
  let [ subject, setSubject ] = useState(null);
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();

  let globalLoginDisplay = useContext(GlobalLoginContext);

  // Fetch the subject's data as JSON from the server.
  // The data will contain the subject metadata,
  // such as authorship and versioning information.
  // Once the data arrives from the server, it will be stored in the `data` state variable.
  let fetchSubjectData = () => {
    fetchWithReLogin(globalLoginDisplay, `/Subjects/${id}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(handleSubjectResponse)
      .catch(handleError);
  };

  // Callback method for the `fetchData` method, invoked when the data successfully arrived from the server.
  let handleSubjectResponse = (json) => {
    getSubject(json);
    setSubject({data: json});
  };

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response);
    setSubject({});  // Prevent an infinite loop if data was not set
  };

  // When the top-level subject is deleted, redirect to its parent if it has one, otherwise to the Subjects page
  let handleDeletion = () => {
    let nodeName = id.substring(id.lastIndexOf("/") + 1);
    let parentNodePath = location.pathname.substring(0, location.pathname.lastIndexOf("/" + nodeName));
    let hasParentSubject = (id.indexOf("/") > 0);
    history.push(parentNodePath + (hasParentSubject ? (location.search + location.hash) : ""));
  }

  // Fetch this Subject's data
  useEffect(() => {
    fetchSubjectData();
  }, [id]);

  if (!subject?.data) {
    return (
      <Grid item><CircularProgress/></Grid>
    );
  }

  if (error) {
    return (
      <Grid item>
        <Typography variant="h2" color="error">
          Error obtaining subject data: {error.status} {error.statusText ? error.statusText : error.toString()}
        </Typography>
      </Grid>
    );
  }

  let identifier = subject?.data?.identifier || id;
  let label = subject?.data?.type?.label || "Subject";
  let title = `${label} ${identifier}`;
  let path = subject?.data?.["@path"] || "/Subjects/" + id;
  let subjectMenu = (
            <div className={classes.actionsMenu}>
               <DeleteButton
                 entryPath={path}
                 entryName={title}
                 entryType={label}
                 onComplete={handleDeletion}
                 size="medium"
               />
            </div>
  );
  let parentDetails = (subject?.data?.['parents'] && getHierarchyAsList(subject.data['parents'], true) || [getHomepageLink(subject?.data)]);

  return (
    subject?.data &&
      <ResourceHeader
        title={title}
        breadcrumbs={parentDetails}
        action={subjectMenu}
        contentOffset={props.contentOffset}
        >
      {
        subject?.data?.['jcr:created'] ?
        <Typography
          variant="overline"
          color="textSecondary" >
            Entered by {subject.data['jcr:createdBy']} on {moment(subject.data['jcr:created']).format("dddd, MMMM Do YYYY")}
        </Typography>
        : ""
      }
      </ResourceHeader>
  );
}

Subject.propTypes = {
  id: PropTypes.string
}

Subject.defaultProps = {
  maxDisplayed: 4,
  pageSize: 10,
}

export default withStyles(QuestionnaireStyle)(withRouter(Subject));
