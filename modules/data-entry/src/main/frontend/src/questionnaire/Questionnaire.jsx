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

// GUI for displaying details about a questionnaire.
let Questionnaire = (props) => {
  let { id, classes } = props;
  let [ data, setData ] = useState();
  let [ error, setError ] = useState();

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

  let reloadData = () => {
    setData({});
    fetchData();
  }

  if (!data) {
    fetchData();
  }

  let questionnaireTitle = data ? data['title'] : id;
  useEffect(() => {
    pageNameWriter(questionnaireTitle);
  }, [questionnaireTitle]);

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
      <Grid container direction="column" spacing={4}>
      <Grid item>
        <Typography variant="h2">{questionnaireTitle} </Typography>
        {
          data?.['jcr:createdBy'] && data?.['jcr:created'] &&
            <Typography variant="overline">
              Created by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}
            </Typography>
        }
      </Grid>
      { data?.["jcr:primaryType"] == "lfs:Questionnaire" &&
        <Grid item>
          <QuestionnaireItemCard
            plain
            type="Questionnaire"
            title="Questionnaire properties"
            disableDelete
            data={data}
            classes={classes}
            onActionDone={() => {reloadData()}}
          >
              <FieldsGrid
                classes={classes}
                fields= {Array(
                          {name: "description", label: "Description", value : data.description},
                          {name: "maxPerType", label: "Maximum forms of this type per subject", value : data.maxPerSubject || 'Unlimited'},
                          {name: "subjectTypes", label: "Subject types", value: data.requiredSubjectTypes?.label || data.requiredSubjectTypes?.map(t => t.label).join(', ') || 'Any'},
                        )}
              />
          </QuestionnaireItemCard>
        </Grid>
      }
      { data &&
        <Grid item>
          <CreationMenu
            data={data}
            onClose={() => { reloadData(); }}
          />
        </Grid>
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
    <Grid container direction="column" spacing={4}>
      <Grid item>{children}</Grid>
      {
        data ?
        Object.entries(data).filter(([key, value]) => (value['jcr:primaryType'] == 'lfs:Section' || value['jcr:primaryType'] == 'lfs:Question'))
            .map(([key, value]) =>
                value['jcr:primaryType'] == 'lfs:Question' ?
                <Grid item key={key}><Question data={value} onActionDone={onActionDone} classes={classes}/></Grid> :
                value['jcr:primaryType'] == 'lfs:Section' ?
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
  let answers = Object.values(data).filter(value => value['jcr:primaryType'] == 'lfs:AnswerOption');

  let displayAnswers = () => {
    return (
        <Grid container key={data['jcr:uuid']} alignItems='flex-start' spacing={2}>
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
        data={data}
        classes={classes}
        onActionDone={onActionDone}
    >
      <Fields data={data} JSON={require('../questionnaireEditor/Question.json')[0]} edit={false} />
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

  let extractProperties = (data) => {
    let p = Array();
    let spec = require('../questionnaireEditor/Section.json');
    Object.keys(spec[0]).filter(key => {return (key != 'label') && !!data[key]}).map(key => {
      p.push({name: key, label: key.charAt(0).toUpperCase() + key.slice(1).replace( /([A-Z])/g, " $1" ).toLowerCase(), value: data[key] + ""});
    });
    // Find conditionals
    Object.entries(data).filter(([key, value]) => (value['jcr:primaryType'] == 'lfs:Conditional')).map(([key, value]) => {
      p.push({
        name: key + data["@name"],
        label: "Condition",
        value : value?.operandA?.value.join(', ') + " " + value?.comparator + " " + value?.operandB?.value.join(', ')
      });
    })
    return p;
  }
  
  return (
    <QuestionnaireItemCard
        avatar="view_stream"
        avatarColor="orange"
        type="Section"
        data={data}
        classes={classes}
        action={
            <CreationMenu
              data={data}
              onClose={() => { onActionDone(); }}
            />
        }
        onActionDone={onActionDone}
    >
      <QuestionnaireItemSet
        data={data}
        classes={classes}
        onActionDone={onActionDone}
      >
         <FieldsGrid fields={extractProperties(data)} classes={classes}/>
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
            <TableCell align="left">{row.value}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
