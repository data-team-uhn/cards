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
  withStyles
} from "@material-ui/core";

import moment from "moment";

import EditIcon from '@material-ui/icons/Edit';
import PreviewIcon from '@material-ui/icons/FindInPage';
import DeleteButton from "../dataHomepage/DeleteButton";
import QuestionnaireStyle from "./QuestionnaireStyle";
import { blue } from '@material-ui/core/colors';
import { ENTRY_TYPES, QUESTION_TYPES, SECTION_TYPES, INFO_TYPES } from "./FormEntry";
import Fields from "../questionnaireEditor/Fields";
import CreationMenu from "../questionnaireEditor/CreationMenu";
import { usePageNameWriterContext } from "../themePage/Page.jsx";
import QuestionnaireItemCard from "../questionnaireEditor/QuestionnaireItemCard";
import FormattedText from "../components/FormattedText.jsx";
import ResourceHeader from "./ResourceHeader";
import QuestionnairePreview from "./QuestionnairePreview";

let _stripCardsNamespace = str => str.replaceAll(/^cards:/g, "");

const QUESTIONNAIRE_ITEM_NAMES = QUESTION_TYPES.concat(SECTION_TYPES).concat(INFO_TYPES).map(type => _stripCardsNamespace(type));

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
            <IconButton>
              <PreviewIcon />
            </IconButton>
          </Tooltip>
          :
          <Tooltip title="Edit" onClick={() => history.push(questionnaireUrl + ".edit")}>
            <IconButton color="primary">
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
          breadcrumbs={[<Link to={baseUrl}>Questionnaires</Link>]}
          action={questionnaireMenu}
          contentOffset={props.contentOffset}
          >
          { data?.['jcr:createdBy'] && data?.['jcr:created'] &&
            <Typography variant="overline">
              Created by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}
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
  let { children, onActionDone, data, classes } = props;

  return (
    <Grid container direction="column" spacing={4} wrap="nowrap">
      {children}
      {
        data ?
        Object.entries(data).filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
            .map(([key, value]) => (
                    EntryType => <Grid item key={key}>
                                   <EntryType
                                     data={value}
                                     type={_stripCardsNamespace(value['jcr:primaryType'])}
                                     parentDisplayMode={data.displayMode}
                                     onActionDone={onActionDone}
                                     classes={classes}
                                   />
                                 </Grid>
                  )(eval(_stripCardsNamespace(value['jcr:primaryType'])))
                )
        : <Grid item><Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid></Grid>
      }
    </Grid>
  );
}

QuestionnaireItemSet.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object
};


// Details about an information block displayed in a questionnaire
let Information = (props) => {
  let { onActionDone, data, classes } = props;
  let [ infoData, setInfoData ] = useState(data);
  let [ doHighlight, setDoHighlight ] = useState(data.doHighlight);

  let reloadData = (newData) => {
    if (newData) {
      setInfoData(newData);
      setDoHighlight(true);
    } else {
      onActionDone();
    }
  }
  return (
    <QuestionnaireItemCard
        avatar="info"
        title=" "
        avatarColor={blue[600]}
        type="Information"
        data={infoData}
        classes={classes}
        onActionDone={reloadData}
        doHighlight={doHighlight}
    >
      <Fields data={infoData} JSON={require('../questionnaireEditor/Information.json')[0]} edit={false} />
    </QuestionnaireItemCard>
  );
};

Information.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired
};


// Details about a particular question in a questionnaire.
// Not to be confused with the public Question component responsible for rendering questions inside a Form.
let Question = (props) => {
  let { onActionDone, data, parentDisplayMode, classes } = props;
  let [ questionData, setQuestionData ] = useState(data);
  let [ doHighlight, setDoHighlight ] = useState(data.doHighlight);
  let answers = Object.values(questionData).filter(value => value['jcr:primaryType'] == 'cards:AnswerOption')
                      .sort((option1, option2) => (option1.defaultOrder - option2.defaultOrder));
  let json = parentDisplayMode === "matrix"
             ? require('../questionnaireEditor/QuestionMatrix.json')[0]
             : require('../questionnaireEditor/Question.json')[0];

  let reloadData = (newData) => {
    if (newData) {
      setQuestionData(newData);
      setDoHighlight(true);
    } else {
      onActionDone();
    }
  }

  let displayAnswers = () => {
    return (
        <Grid container key={questionData['jcr:uuid']} alignItems='flex-start' spacing={2}>
          <Grid item key="label" xs={4}>
            <Typography variant="subtitle2">Answer options:</Typography>
          </Grid>
          <Grid item key="values" xs={8}>
            { answers.map(item => <Typography key={item['jcr:uuid']}>{(item.label || item.value) + (item.label ? (" (" + item.value + ")") : "")}</Typography>) }
          </Grid>
        </Grid>
    );
  };

  return (
    <QuestionnaireItemCard
        avatar=""
        avatarColor="purple"
        type="Question"
        data={questionData}
        classes={classes}
        onActionDone={reloadData}
        doHighlight={doHighlight}
        parentDisplayMode={parentDisplayMode}
    >
      <Fields data={questionData} JSON={json} edit={false} />
      { parentDisplayMode != "matrix" && answers.length > 0 && displayAnswers() }
    </QuestionnaireItemCard>
  );
};

Question.propTypes = {
  closeData: PropTypes.func,
  data: PropTypes.object.isRequired
};

let QuestionMatrix = (props) => {
  let { onActionDone, data, type, classes } = props;
  let [ sectionData, setSectionData ] = useState(data);
  let [ doHighlight, setDoHighlight ] = useState(data.doHighlight);
  let answers = Object.values(data).filter(value => value['jcr:primaryType'] == 'cards:AnswerOption')
                      .sort((option1, option2) => (option1.defaultOrder - option2.defaultOrder));

  let extractPropertiesFromJson = (json, p) => {
    Object.keys(json).filter( key => {return (key != 'label') && !!sectionData[key]} )
                        .map( key => { p.push({
                                         name: key,
                                         label: key.charAt(0).toUpperCase() + key.slice(1).replace( /([A-Z])/g, " $1" ).toLowerCase(),
                                         value: sectionData[key] + "",
                                         type: json[key]
                                       });
                                     });
  }

  let extractProperties = () => {
    let p = Array();
    let spec = require('../questionnaireEditor/Section.json');
    extractPropertiesFromJson(spec[0], p);
    extractPropertiesFromJson(spec[0].displayMode.matrix, p);
    return p;
  }

  let reloadData = (newData) => {
    if (newData) {
      setSectionData(newData);
      setDoHighlight(true);
    } else {
      onActionDone();
    }
  }

  let onCreate = (newData) => {
    setSectionData({});
    setSectionData(newData);
  }

  let displayAnswers = () => {
    return (
        <Grid container key={sectionData['jcr:uuid']} alignItems='flex-start' spacing={2}>
          <Grid item key="label" xs={4}>
            <Typography variant="subtitle2">Answer options:</Typography>
          </Grid>
          <Grid item key="values" xs={8}>
            { answers.map(item => <Typography key={item['jcr:uuid']}>{(item.label || item.value) + (item.label ? (" (" + item.value + ")") : "")}</Typography>) }
          </Grid>
        </Grid>
    );
  };

  return (
    <QuestionnaireItemCard
        avatar="view_stream"
        avatarColor="orange"
        type={type}
        data={sectionData}
        classes={classes}
        doHighlight={doHighlight}
        action={
            <CreationMenu
              data={sectionData}
              onClose={onCreate}
              menuItems={["Question"]}
            />
        }
        onActionDone={reloadData}
    >
      <QuestionnaireItemSet
        data={sectionData}
        classes={classes}
        onActionDone={onActionDone}
      >
         <FieldsGrid fields={extractProperties()} classes={classes}/>
         { answers.length > 0 && displayAnswers() }
      </QuestionnaireItemSet>
    </QuestionnaireItemCard>
  );
};

let Section = (props) => {
  let { onActionDone, data, type, classes } = props;
  let [ sectionData, setSectionData ] = useState(data);
  let [ doHighlight, setDoHighlight ] = useState(data.doHighlight);

  if (data.displayMode == "matrix") {
    return <QuestionMatrix {...props} />
  }

  let extractProperties = () => {
    let p = Array();
    let spec = require('../questionnaireEditor/Section.json');
    Object.keys(spec[0]).filter( key => {return (key != 'label') && !!sectionData[key]} )
                        .map( key => { p.push({
                                         name: key,
                                         label: key.charAt(0).toUpperCase() + key.slice(1).replace( /([A-Z])/g, " $1" ).toLowerCase(),
                                         value: sectionData[key] + "",
                                         type: spec[0][key]
                                       });
                                     });
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
    if (newData) {
      setSectionData(newData);
      setDoHighlight(true);
    } else {
      onActionDone();
    }
  }

  let onCreate = (newData) => {
    setSectionData({});
    setSectionData(newData);
  }
  
  return (
    <QuestionnaireItemCard
        avatar="view_stream"
        avatarColor="orange"
        type={type}
        data={sectionData}
        classes={classes}
        doHighlight={doHighlight}
        action={
            <CreationMenu
              data={sectionData}
              onClose={onCreate}
              menuItems={ QUESTIONNAIRE_ITEM_NAMES }
            />
        }
        onActionDone={reloadData}
    >
      <QuestionnaireItemSet
        data={sectionData}
        classes={classes}
        onActionDone={onActionDone}
      >
         <FieldsGrid fields={extractProperties()} classes={classes}/>
      </QuestionnaireItemSet>
    </QuestionnaireItemCard>
  );
};

Section.propTypes = {
  data: PropTypes.object.isRequired
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
