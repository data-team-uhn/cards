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
import PropTypes from "prop-types";

import {
  CircularProgress,
  Grid,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Typography,
  withStyles
} from "@material-ui/core";

import moment from "moment";

import QuestionnaireStyle from "./QuestionnaireStyle";
import Fields from "../questionnaireEditor/Fields";
import CreationMenu from "../questionnaireEditor/CreationMenu";
import { usePageNameWriterContext } from "../themePage/Page.jsx";
import QuestionnaireItemCard from "../questionnaireEditor/QuestionnaireItemCard";
import FormattedText from "../components/FormattedText.jsx";

// GUI for displaying details about a questionnaire.
let Questionnaire = (props) => {
  let { id, classes } = props;
  let [ data, setData ] = useState();
  let [ error, setError ] = useState();
  let [ doHighlight, setDoHighlight ] = useState(false);

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

  return (
    <>
      { error &&
        <Typography variant="h2" color="error">
          Error obtaining questionnaire info: {error.status} {error.statusText}
        </Typography>
      }
      <QuestionnaireItemSet
        data={data}
        classes={classes}
        onActionDone={() => reloadData()}
      >
      <Grid container direction="column" spacing={4} wrap="nowrap">
      <Grid item>
        <Typography variant="h2">{questionnaireTitle} </Typography>
        {
          data?.['jcr:createdBy'] && data?.['jcr:created'] &&
            <Typography variant="overline">
              Created by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}
            </Typography>
        }
      </Grid>
      { data?.["jcr:primaryType"] == "cards:Questionnaire" &&
        <Grid item>
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
        </Grid>
      }
      { data &&
          <CreationMenu
            isMainAction={true}
            data={data}
            onClose={onCreate}
          />
      }
      </Grid>
      </QuestionnaireItemSet>
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
      <Grid item>{children}</Grid>
      {
        data ?
        Object.entries(data).filter(([key, value]) => (value['jcr:primaryType'] == 'cards:Section' || value['jcr:primaryType'] == 'cards:Question'))
            .map(([key, value]) =>
                value['jcr:primaryType'] == 'cards:Question' ?
                <Grid item key={key}><Question data={value} onActionDone={onActionDone} classes={classes}/></Grid> :
                value['jcr:primaryType'] == 'cards:Section' ?
                <Grid item key={key}><Section data={value} onActionDone={onActionDone} classes={classes}/></Grid>
                : null
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


// Details about a particular question in a questionnaire.
// Not to be confused with the public Question component responsible for rendering questions inside a Form.
let Question = (props) => {
  let { onActionDone, data, classes } = props;
  let [ questionData, setQuestionData ] = useState(data);
  let [ doHighlight, setDoHighlight ] = useState(data.doHighlight);
  let answers = Object.values(questionData).filter(value => value['jcr:primaryType'] == 'cards:AnswerOption')
                      .sort((option1, option2) => (option1.defaultOrder - option2.defaultOrder));

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
    >
      <Fields data={questionData} JSON={require('../questionnaireEditor/Question.json')[0]} edit={false} />
      { answers.length > 0 && displayAnswers() }
    </QuestionnaireItemCard>
  );
};

Question.propTypes = {
  closeData: PropTypes.func,
  data: PropTypes.object.isRequired
};

let Section = (props) => {
  let { onActionDone, data, classes } = props;
  let [ sectionData, setSectionData ] = useState(data);
  let [ doHighlight, setDoHighlight ] = useState(data.doHighlight);

  let extractProperties = () => {
    let p = Array();
    let spec = require('../questionnaireEditor/Section.json');
    Object.keys(spec[0]).filter(key => {return (key != 'label') && !!sectionData[key]}).map(key => {
      p.push({name: key, label: key.charAt(0).toUpperCase() + key.slice(1).replace( /([A-Z])/g, " $1" ).toLowerCase(), value: sectionData[key] + "", type: spec[0][key]});
    });
    // Find conditionals
    Object.entries(sectionData).filter(([key, value]) => (value['jcr:primaryType'] == 'cards:Conditional')).map(([key, value]) => {
      p.push({
        name: key + sectionData["@name"],
        label: "Condition",
        value : value?.operandA?.value.join(', ') + " " + value?.comparator + " " + value?.operandB?.value.join(', ')
      });
    })
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
        type="Section"
        data={sectionData}
        classes={classes}
        doHighlight={doHighlight}
        action={
            <CreationMenu
              data={sectionData}
              onClose={onCreate}
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
