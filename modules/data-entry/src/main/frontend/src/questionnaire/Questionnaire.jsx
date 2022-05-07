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

import React, { useEffect, useState } from "react";
import { Link, useHistory } from 'react-router-dom';
import PropTypes from "prop-types";

import {
  CircularProgress,
  Grid,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Tooltip,
  Typography,
} from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import { DateTime } from "luxon";

import EditIcon from '@mui/icons-material/Edit';
import PreviewIcon from '@mui/icons-material/FindInPage';
import DeleteButton from "../dataHomepage/DeleteButton";
import QuestionnaireStyle from "./QuestionnaireStyle";
import { blue } from '@mui/material/colors';
import { ENTRY_TYPES } from "./FormEntry";
import Fields from "../questionnaireEditor/Fields";
import CreationMenu from "../questionnaireEditor/CreationMenu";
import { usePageNameWriterContext } from "../themePage/Page.jsx";
import QuestionnaireItemCard from "../questionnaireEditor/QuestionnaireItemCard";
import FormattedText from "../components/FormattedText.jsx";
import ResourceHeader from "./ResourceHeader";
import QuestionnairePreview from "./QuestionnairePreview";

let _stripCardsNamespace = str => str.replaceAll(/^cards:/g, "");

export const QUESTIONNAIRE_ITEM_NAMES = ENTRY_TYPES.map(type => _stripCardsNamespace(type));

// GUI for displaying details about a questionnaire.
let Questionnaire = (props) => {
  let { id, classes } = props;
  let [ data, setData ] = useState();
  let [ error, setError ] = useState();
  let [ doHighlight, setDoHighlight ] = useState(false);
  let baseUrl = /((.*)\/Questionnaires)\/([^.]+)/.exec(location.pathname)[1];
  let questionnaireUrl = `${baseUrl}/${id}`;
  let isEdit = window.location.pathname.endsWith(".edit");
  let history = useHistory();

  let pageNameWriter = usePageNameWriterContext();

  let handleError = (response) => {
    setError(response);
    setData({});
  }

  let fetchData = () => {
    fetch(`/Questionnaires/${id}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(setData)
      .catch(handleError);
  };

  let reloadData = (newData) => {
    if (newData) {
      setData(newData);
      setDoHighlight(true);
    } else {
      setData({});
      fetchData();
    }
  }

  if (!data) {
    fetchData();
  }

  let onCreate = (newData) => {
    setData({});
    setData(newData);
  }

  let questionnaireTitle = data ? data['title'] : decodeURI(id);
  useEffect(() => {
    pageNameWriter(questionnaireTitle);
  }, [questionnaireTitle]);

  useEffect(() => {
    if (!isEdit) return;
    //Perform a JCR check-out of the Questionnaire
    let checkoutForm = new FormData();
    checkoutForm.set(":operation", "checkout");
    fetch(`/Questionnaires/${id}`, {
      method: "POST",
      body: checkoutForm
    });

    function performCheckIn() {
      let checkinForm = new FormData();
      checkinForm.set(":operation", "checkin");
      fetch(`/Questionnaires/${id}`, {
        method: "POST",
        body: checkinForm
      });
    }

    window.addEventListener("beforeunload", performCheckIn);
    return (() => {
      window.removeEventListener("beforeunload", performCheckIn);
    });
  }, []);

  let questionnaireMenu = (
      <div className={classes.actionsMenu}>
        { isEdit ?
          <Tooltip title="Preview" onClick={() => history.push(questionnaireUrl)}>
            <IconButton size="large">
              <PreviewIcon />
            </IconButton>
          </Tooltip>
          :
          <Tooltip title="Edit" onClick={() => history.push(questionnaireUrl + ".edit")}>
            <IconButton color="primary" size="large">
              <EditIcon />
            </IconButton>
          </Tooltip>
        }
        <DeleteButton
          entryPath={data ? data["@path"] : `/Questionnaires/${id}`}
          entryName={questionnaireTitle}
          entryType="Questionnaire"
          variant="icon"
          onComplete={() => history.replace(baseUrl)}
        />
      </div>
  )

  let questionnaireHeader = (
        <ResourceHeader
          title={questionnaireTitle || ""}
          breadcrumbs={[<Link to={baseUrl} underline="hover">Questionnaires</Link>]}
          action={questionnaireMenu}
          contentOffset={props.contentOffset}
          >
          { data?.['jcr:createdBy'] && data?.['jcr:created'] &&
            <Typography variant="overline">
              Created by {data['jcr:createdBy']} on {DateTime.fromISO(data['jcr:created']).toLocaleString(DateTime.DATE_MED_WITH_WEEKDAY)}
            </Typography>
          }
        </ResourceHeader>
  );

  return (
    <>
      { error &&
        <Typography variant="h2" color="error">
          Error obtaining questionnaire info: {error.status} {error.statusText}
        </Typography>
      }
      { !isEdit
        ? <Grid container direction="column" spacing={4} wrap="nowrap">
            {questionnaireHeader}
            <Grid item>
              <QuestionnairePreview
                data={data}
                title={questionnaireTitle}
                contentOffset={props.contentOffset}
              />
            </Grid>
          </Grid>
      :
      <QuestionnaireItemSet
        data={data}
        classes={classes}
        onActionDone={() => reloadData()}
      >
        {questionnaireHeader}
        { data?.["jcr:primaryType"] == "cards:Questionnaire" && <Grid item>
          <QuestionnaireItemCard
            plain
            type="Questionnaire"
            title="Questionnaire properties"
            disableDelete
            data={data}
            classes={classes}
            onActionDone={reloadData}
            doHighlight={doHighlight}
          >
              <FieldsGrid
                classes={classes}
                fields= {Array(
                          {name: "description", label: "Description", value : data.description, type: "markdown"},
                          {name: "maxPerType", label: "Maximum forms of this type per subject", value : (data.maxPerSubject > 0 ? data.maxPerSubject : 'Unlimited')},
                          {name: "paginate", label: "Paginate", value : data.paginate, value : (data.paginate ? "Yes" : "No")},
                          {name: "subjectTypes", label: "Subject types", value: data.requiredSubjectTypes?.label || data.requiredSubjectTypes?.map(t => t.label).join(', ') || 'Any'},
                        )}
              />
          </QuestionnaireItemCard>
        </Grid>}
        { data &&
          <CreationMenu
            isMainAction={true}
            data={data}
            menuItems={QUESTIONNAIRE_ITEM_NAMES}
            onClose={onCreate}
          />
        }
      </QuestionnaireItemSet>
      }
    </>
  );
};

Questionnaire.propTypes = {
  id: PropTypes.string.isRequired
};

export default withStyles(QuestionnaireStyle)(Questionnaire);


let QuestionnaireItemSet = (props) => {
  let { children, entryTypes, models, onActionDone, data, classes } = props;

  return (
    <Grid container direction="column" spacing={4} wrap="nowrap">
      {children}
      {
        data ?
        Object.entries(data)
            .filter(([key, value]) => (entryTypes || ENTRY_TYPES).includes(value['jcr:primaryType']))
            .map(([key, value]) => (
                EntryType => <Grid item key={key}>
                               <EntryType
                                 data={value}
                                 model={models?.[_stripCardsNamespace(value['jcr:primaryType'])]}
                                 onActionDone={onActionDone}
                                 classes={classes}
                               />
                             </Grid>
                 )(eval(_stripCardsNamespace(value['jcr:primaryType'])))
            )
        : <Grid item><Grid container justifyContent="center"><Grid item><CircularProgress/></Grid></Grid></Grid>
      }
    </Grid>
  );
}

QuestionnaireItemSet.propTypes = {
  models: PropTypes.object,
  onActionDone: PropTypes.func,
  data: PropTypes.object
};


// Details about an information block displayed in a questionnaire
let Information = (props) => <QuestionnaireEntry {...props} />;

Information.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  avatar: PropTypes.string,
  avatarColor: PropTypes.string,
  model: PropTypes.string.isRequired
};

Information.defaultProps = {
  type: "Information",
  avatar: "info",
  avatarColor: blue[600],
  model: "Information.json"
};


// Details about a particular question in a questionnaire.
// Not to be confused with the public Question component responsible for rendering questions inside a Form.
let Question = (props) => <QuestionnaireEntry {...props} />;

Question.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  avatar: PropTypes.string,
  avatarColor: PropTypes.string,
  titleField: PropTypes.string,
  model: PropTypes.string.isRequired
};

Question.defaultProps = {
  type: "Question",
  avatarColor: "purple",
  titleField: "text",
  model: "Question.json"
};

// Details about a particular section in a questionnaire.
// Not to be confused with the public Section component responsible for rendering sections inside a Form.
let Section = (props) => <QuestionnaireEntry {...props} />;

Section.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  avatar: PropTypes.string,
  avatarColor: PropTypes.string,
  titleField: PropTypes.string,
  model: PropTypes.string.isRequired
};

Section.defaultProps = {
  type: "Section",
  avatar: "view_stream",
  avatarColor: "orange",
  titleField: "label",
  model: "Section.json"
};


// Generic QuestionnaireEntry component that can be adapted to any entry type via props

let QuestionnaireEntry = (props) => {
  let { onActionDone, data, type, titleField, avatar, avatarColor, model, classes } = props;
  let [ entryData, setEntryData ] = useState(data);
  let [ doHighlight, setDoHighlight ] = useState(data.doHighlight);

  let spec = require(`../questionnaireEditor/${model}`)[0];

  let childModels = null;

  // There may be `//CHILDREN` overrides for some definitions for this entry, find them and record them
  let findChildrenSpec = (key, value) => {
    if (key == '//CHILDREN') {
      childModels = value;
      return true;
    }
    return (
      typeof(entryData[key] != undefined) &&
      typeof(value) == "object" &&
      typeof(value[entryData[key]]) == "object" &&
      Object.entries(value[entryData[key]]).find(([k, v]) => findChildrenSpec(k, v))
    )
  };

  // Does this section have a different list of accepted child items?
  Object.entries(spec || {}).find(([key, value]) => findChildrenSpec(key, value));

  let menuItems = childModels && Object.keys(childModels);

  let extractConditions = () => {
    let p = Array();
    // Find conditionals
    Object.entries(sectionData).filter(([key, value]) => (value['jcr:primaryType'] == 'cards:Conditional'))
                               .map(([key, value]) => { p.push({
                                                          name: key + sectionData["@name"],
                                                          label: "Condition",
                                                          value : value?.operandA?.value.join(', ') + " " + value?.comparator + " " + value?.operandB?.value.join(', ')
                                                        });
                                                      });
    return p;
  }

  let reloadData = (newData) => {
    // There's new data to load, display and highlight it:
    if (newData) {
      setEntryData(newData);
      setDoHighlight(true);
    } else {
      // Try to reload the data from the server
      // If it fails, pass it up to the parent
      fetch(`${data["@path"]}.deep.json`)
        .then(response => response.ok ? response.json() : Promise.reject(response))
        .then(json => reloadData(json))
        .catch(() => onActionDone());
    }
  }

  let onCreated = (newData) => {
    setEntryData({});
    setEntryData(newData);
  }

  // If a `titleField` is provided, exclude that field when displaying the entry fields
  let viewSpec = Object.assign({}, spec);
  delete viewSpec[titleField];

  return (
    <QuestionnaireItemCard
        titleField={titleField}
        avatar={avatar}
        avatarColor={avatarColor}
        type={type}
        data={entryData}
        classes={classes}
        doHighlight={doHighlight}
        action={
            menuItems?.length > 0 ?
            <CreationMenu
              data={entryData}
              onCreated={onCreated}
              menuItems={menuItems}
              models={childModels}
            />
            : undefined
        }
        onActionDone={reloadData}
        model={model}
    >
      <Fields data={entryData} JSON={viewSpec} edit={false} />
      <AnswerOptionList data={entryData} modelDefinition={spec} />
      { menuItems &&
        <QuestionnaireItemSet
          data={entryData}
          classes={classes}
          onActionDone={reloadData}
          models={childModels}
          entryTypes={menuItems.map(t => `cards:${t}`)}
        />
      }
    </QuestionnaireItemCard>
  );
};

QuestionnaireEntry.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  avatar: PropTypes.string,
  avatarColor: PropTypes.string,
  titleField: PropTypes.string,
  model: PropTypes.string.isRequired
};

let FieldsGrid = (props) => {
  return (
    <Table aria-label="simple table">
      <TableBody>
        {props.fields?.map((row) => (
          <TableRow key={row.name}>
            <TableCell component="th" scope="row">{row.label}:</TableCell>
            <TableCell align="left">
              { row.type === "markdown"
                ?
                <FormattedText>{row.value}</FormattedText>
                :
                row.value
              }
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

let AnswerOptionList = (props) => {
  let { data, modelDefinition } = props;

  let hasAnswerOptions = (key, value) => {
    if (key == 'answerOptions') return true;
    return (
      typeof(data[key] != undefined) &&
      typeof(value) == "object" &&
      typeof(value[data[key]]) == "object" &&
      Object.entries(value[data[key]]).some(([k, v]) => hasAnswerOptions(k, v))
    )
  };

  let answerOptions = Object.values(data ||{}).filter(value => value['jcr:primaryType'] == 'cards:AnswerOption')
                      .sort((option1, option2) => (option1.defaultOrder - option2.defaultOrder));

  // Does this questionnaire entry have answerOptions enabled?
  let enabled = Object.entries(modelDefinition || {}).some(([key, value]) => hasAnswerOptions(key, value));

  if (!enabled || !(answerOptions?.length)) {
    return null;
  }

  return (
    <Grid container key={data['jcr:uuid']} alignItems='flex-start' spacing={2}>
      <Grid item key="label" xs={4}>
        <Typography variant="subtitle2">Answer options:</Typography>
      </Grid>
      <Grid item key="values" xs={8}>
        { answerOptions.map(item => <Typography key={item['jcr:uuid']}>{(item.label || item.value) + (item.label ? (" (" + item.value + ")") : "")}</Typography>) }
      </Grid>
    </Grid>
  );
}
