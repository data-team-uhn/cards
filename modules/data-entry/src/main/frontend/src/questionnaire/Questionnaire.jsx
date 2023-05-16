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
  List,
  ListItem,
  Popover,
  Tooltip,
  Typography,
} from "@mui/material";

import withStyles from '@mui/styles/withStyles';

import { DateTime } from "luxon";

import EditIcon from '@mui/icons-material/Edit';
import MoreIcon from '@mui/icons-material/MoreVert';
import PreviewIcon from '@mui/icons-material/FindInPage';
import DeleteButton from "../dataHomepage/DeleteButton";
import ExportButton from "../dataHomepage/ExportButton";
import QuestionnaireStyle from "./QuestionnaireStyle";
import { blue, blueGrey, cyan, deepPurple, indigo, orange, purple } from '@mui/material/colors';
import { ENTRY_TYPES } from "./FormEntry";
import Fields from "../questionnaireEditor/Fields";
import LabeledField from "../questionnaireEditor/LabeledField";
import CreationMenu from "../questionnaireEditor/CreationMenu";
import { usePageNameWriterContext } from "../themePage/Page.jsx";
import QuestionnaireItemCard from "../questionnaireEditor/QuestionnaireItemCard";
import ResourceHeader from "./ResourceHeader";
import QuestionnairePreview from "./QuestionnairePreview";
import { QuestionnaireProvider, useQuestionnaireWriterContext } from "./QuestionnaireContext";
import { findQuestionnaireEntries, stripCardsNamespace } from "./QuestionnaireUtilities";

export const QUESTIONNAIRE_ITEM_NAMES = ENTRY_TYPES.map(type => stripCardsNamespace(type));

// GUI for displaying details about a questionnaire.
let Questionnaire = (props) => {
  let { id, classes } = props;
  let [ data, setData ] = useState();
  let [ questionnaireTitle, setQuestionnaireTitle ] = useState();
  let [ actionsMenu, setActionsMenu ] = useState(null);
  let [ error, setError ] = useState();
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

  // First, fetch the questionnaire data
  useEffect(() => {
    fetchData();
  }, []);

  useEffect(() => {
    setQuestionnaireTitle(data?.title || decodeURI(id));
  }, [data?.title]);

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

  let dropdownList = (
      <List>
        <ListItem className={classes.actionsMenuItem}>
          <ExportButton
              entityData={data}
              entryPath={data ? data["@path"] : `/Questionnaires/${id}`}
              entryName={questionnaireTitle || id}
              entryType="Questionnaire"
              size="medium"
              variant="text"
              onClose={() => { setActionsMenu(null); }}
          />
        </ListItem>
        <ListItem className={classes.actionsMenuItem}>
          <DeleteButton
              entryPath={data ? data["@path"] : `/Questionnaires/${id}`}
              entryName={questionnaireTitle}
              entryType="Questionnaire"
              onComplete={() => history.replace(baseUrl)}
              size="medium"
              variant="text"
              onClose={() => { setActionsMenu(null); }}
          />
        </ListItem>
      </List>
  )

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
        <Tooltip title="More actions" onClick={(event) => {setActionsMenu(event.currentTarget)}}>
          <IconButton size="large">
            <MoreIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        <Popover
            open={Boolean(actionsMenu)}
            anchorEl={actionsMenu}
            onClose={() => {setActionsMenu(null)}}
            anchorOrigin={{
                vertical: 'bottom',
                horizontal: 'right',
            }}
            transformOrigin={{
                vertical: 'top',
                horizontal: 'right',
            }}
        >
          { dropdownList }
        </Popover>
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
    error ?
      <Typography variant="h2" color="error">
        Error obtaining questionnaire info: {error.status} {error.statusText}
      </Typography>
    :
      ( data?.["jcr:primaryType"] == "cards:Questionnaire" &&
        <Grid container direction="column" spacing={4} wrap="nowrap">
          { questionnaireHeader }
          <Grid item>
            { !isEdit ?
              <QuestionnairePreview
                data={data}
                title={questionnaireTitle}
                contentOffset={props.contentOffset}
              />
            :
              <QuestionnaireProvider>
                <QuestionnaireContents
                  disableDelete
                  data={data}
                  classes={classes}
                  onFieldsChanged={(newData) => newData?.title && setQuestionnaireTitle(newData.title)}
                  onActionDone={()=>{}}
                  menuProps={{isMainAction: true}}
                />
              </QuestionnaireProvider>
            }
          </Grid>
        </Grid>
      )
  );
};

Questionnaire.propTypes = {
  id: PropTypes.string.isRequired
};

export default withStyles(QuestionnaireStyle)(Questionnaire);


let QuestionnaireItemSet = (props) => {
  let { children, models, onActionDone, data, classes } = props;

  let prioritaryModels = {};
  let prioritaryEntryTypes = null;
  let generalModels = {};
  let generalEntryTypes = ENTRY_TYPES;

  let getEntryTypes = entryModels => Object.keys(entryModels || {}).map(e => `cards:${e}`);

  if (models) {
    // Is defaultOrder specified for some entry types? Pull those into a flat "priority" list
    // to display them first, in the order specified by their `defaultOrder`
    // Example:
    // {
    //   a: "a.json",
    //   group1 : {entries: {b: "b,json", c: "c.json"}},
    //   group2 : {entries: {d: "d,json", e: "e.json"}, defaultOrder: 5},
    //   f: "f.json",
    //   group3 : {entries: {g: "g,json", h: "h.json"}, defaultOrder: 1}
    // }
    // => {g: "g.json", h: "h,json", d: "d.json", e: "e.json"}
    Object.values(models || {})
      // Filter in groups with entries and with defaultOrder specified
      .filter(v => typeof(v) == "object" && typeof(v?.entries) != "undefined" && typeof(v?.defaultOrder) != "undefined")
      // Sort by the specified order
      .sort((a, b) => a.defaultOrder - b.defaultOrder)
      // Record the sorted entries into the priority list
      .forEach(v => prioritaryModels = {...prioritaryModels, ...v.entries});

    // If there are any entries with defaultOrder, we update the priorityEntryTypes
    if (Object.keys(prioritaryModels).length > 0) {
      prioritaryEntryTypes = getEntryTypes(prioritaryModels);
    }

    // Get entry types without a defaultOrder specified, in the order they appear in the configuration
    // Dive inside groups to get the `entries`
    // Example:
    // {
    //   a: "a.json",
    //   group1 : {entries: {b: "b,json", c: "c.json"}},
    //   group2 : {entries: {d: "d,json", e: "e.json"}, defaultOrder: 5},
    //   f: "f.json",
    //   group3 : {entries: {g: "g,json", h: "h.json"}, defaultOrder: 1}
    // }
    // => {a: "a.json", b: "b,json", c: "c.json", f: "f.json"}
    Object.entries(models).forEach(([k,v]) => {
      if ( typeof(v) == "object") {
        if (typeof(v?.entries) != "undefined" && typeof(v?.defaultOrder) == "undefined") {
          // Flatten groups with `entries` but without `defaultOrder` (the ones with defaultOrder are already in the "priority" list)
          generalModels = {...generalModels, ...v.entries}
        }
      } else {
        // also record groups without metadata
        generalModels[k] = v;
      }
    });
    // If there are any entries without defaultOrder, we update the generalEntryTypes
    // Otherwise the original list is kept, i.e. ENTRY_TYPES
    if (Object.keys(generalModels).length > 0) {
       generalEntryTypes = getEntryTypes(generalModels);
    }
  }

  // Display questionnaire entries of the (primary) types specified by the `types` argument.
  // For these entries, display only the properties specified in the model mapping (the `typeModels` argument).
  //
  // NB: We implemented React components for displaying each supported entry type.
  //     The names of the React components match the primary types, i.e. we have a `Question` component for
  //     displaying entries with `jcr:primaryType` = "cards:Question", a `Section` component for displaying
  //     entries with `jcr:primaryType` = "cards:Section", etc.
  //     To call the right component for each entry that has passed the "types" filter,  we strip its primaryType
  //     of the "cards:" prefix and then `eval` the result to the functional component's name, which is passed as
  //     a parameter to an anonymous function called for each such entry, that renders the component inside a
  //     Grid item.
  //
  // @param types - an array of (primary) types of entries to display.
  //   Each element in the array is a string representing a primaryType, e.g. "cards:Question"
  // @param typeModels - an object mapping an entry type (where the type is tripped of the "cards:"
  //   prefix) to a json file specifying the "model", i.e. which properties to display
  // @return a React fragment rendering the entries from the `data` prop according to the `types` filter and
  //   the `typeModels` property restriction
  let listEntries = (typeModels, types) => (
    <>
    { Object.entries(data)
      .filter(([key, value]) => types?.includes(value['jcr:primaryType']))
      .map(([key, value]) => (
        EntryType => <Grid item key={key}>
                       <EntryType
                         data={value}
                         model={typeModels?.[stripCardsNamespace(value['jcr:primaryType'])]}
                         onActionDone={onActionDone}
                         classes={classes}
                       />
                     </Grid>
        )(eval(stripCardsNamespace(value['jcr:primaryType'])))
      )
    }
    </>
  )

  // There is no data to display, do not render an empty container
  if ( !!!children &&
       !Object.values(data).some(v => [...(generalEntryTypes ||[]), ...(prioritaryEntryTypes || [])].includes(v['jcr:primaryType'])) ) {
    return null;
  }

  return (
    <Grid container direction="column" spacing={4} wrap="nowrap">
      {children}
      {
        data ?
        <>
        { prioritaryEntryTypes && listEntries(prioritaryModels, prioritaryEntryTypes) }
        { listEntries(generalModels, generalEntryTypes) }
        </>
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

// Questionnaire contents: properties + entries
let QuestionnaireContents = (props) => {
  let { data } = props;

  let changeQuestionnaireContext = useQuestionnaireWriterContext();

  useEffect(() => {
    // Load initial data
    changeQuestionnaireContext(findQuestionnaireEntries(data, ["cards:Question"]));
    // Clear context when unmounting component
    return (() => changeQuestionnaireContext([]));
  }, []);

  return <QuestionnaireEntry {...props} />;
};

QuestionnaireContents.propTypes = {
  onActionDone: PropTypes.func,
  onFieldsChanged: PropTypes.func,
  disableCollapse: PropTypes.bool,
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  avatar: PropTypes.string,
  avatarColor: PropTypes.string,
  titleField: PropTypes.string,
  model: PropTypes.string.isRequired
};

QuestionnaireContents.defaultProps = {
  disableCollapse: false,
  type: "Questionnaire",
  avatar: "assignment",
  avatarColor: blueGrey[700],
  titleField: "title",
  model: "Questionnaire.json"
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

// Details about an id mapping block displayed in a questionnaire
let ExternalLink = (props) => <QuestionnaireEntry {...props} />;

ExternalLink.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  avatar: PropTypes.string,
  avatarColor: PropTypes.string,
  model: PropTypes.string.isRequired
};

ExternalLink.defaultProps = {
  type: "ExternalLink",
  avatar: "link",
  avatarColor: purple[300],
  model: "ExternalLink.json"
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
  avatarColor: deepPurple[700],
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
  avatarColor: orange[800],
  titleField: "label",
  model: "Section.json"
};


// Details about a simple condition for desplaying a section
let Conditional = (props) => <QuestionnaireEntry {...props} />;

Conditional.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  avatar: PropTypes.string,
  avatarColor: PropTypes.string,
  model: PropTypes.string.isRequired
};

Conditional.defaultProps = {
  type: "Conditional",
  avatarColor: cyan[800],
  model: "Conditional.json"
};

// Details about a group pf conditions for desplaying a section
let ConditionalGroup = (props) => <QuestionnaireEntry {...props} />;

ConditionalGroup.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  avatar: PropTypes.string,
  avatarColor: PropTypes.string,
  model: PropTypes.string.isRequired
};

ConditionalGroup.defaultProps = {
  type: "ConditionalGroup",
  avatarColor: indigo[800],
  model: "ConditionalGroup.json"
};

// Generic QuestionnaireEntry component that can be adapted to any entry type via props

let QuestionnaireEntry = (props) => {
  let { onActionDone, onFieldsChanged, data, type, titleField, model, classes, menuProps, ...rest } = props;
  let [ entryData, setEntryData ] = useState(data);
  let [ menuItems, setMenuItems ] = useState([]);
  let [ doHighlight, setDoHighlight ] = useState(data.doHighlight);

  // --------------------------------------------------------------
  // Questionnaire context manipulation

  let changeQuestionnaireContext = useQuestionnaireWriterContext();

  let updateContext = (data) => {
    let vars = findQuestions({data: data});
    changeQuestionnaireContext((oldContext) => {
       let newContext = oldContext || [];
       vars.forEach(v => {
         const index = newContext.findIndex(x => x.id == v.id);
         if (index >= 0) {
           newContext.splice(index, 1, v);
         } else {
           newContext.push(v);
         }
       });
       return newContext;
    });
  }

  let removeFromContext = (id) => {
    changeQuestionnaireContext((oldContext) => {
      let newContext = oldContext || [];
      const index = newContext.findIndex(x => x.id == id);
      if (index >= 0) {
        newContext.splice(index, 1);
      }
      return newContext;
    });
  }

  // -------------------------------------------------------------
  // Find child item specifications

  let spec = require(`../questionnaireEditor/${model}`)[0];

  // If this entry type has any children by default, they should be specified in the `//CHILDREN` field
  let childModels = spec["//CHILDREN"];

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
  Object.entries(spec || {})
    // ignore the default `//CHILDREN` specification
    .filter(([key, value]) => key != "//CHILDREN")
    // look for overrides deeper
    .find(([key, value]) => findChildrenSpec(key, value));

  // -------------------------------------------------------------
  // Determine the menu items for creating children, based on the
  // `//CHILDREN` spec and whether the maximum allowed for each
  // child type was reached

  useEffect(() => {
    // Add the child types to the menu
    setMenuItems(Object.keys(childModels || {}).filter(k => typeof(childModels[k]) != "object"));

    // Some child entries may be configured to have a maximum number of entries
    // (for example, only one conditional or conditional group per section)
    // Exclude from the creation menu any entries corresponding to child types
    // for which maximum of that type has been reached
    if (childModels) {
      Object.values(childModels)
        .filter(v => {
          if (typeof(v) != "object" || typeof(v?.entries) != "object") return false;
          if (!v.hasOwnProperty("max")) return true;
          let entryTypes = Object.keys(v.entries).map(e => `cards:${e}`);
          return (Object.values(entryData).filter(e => entryTypes?.includes(e['jcr:primaryType'])).length < v.max);
        })
        .forEach(v => setMenuItems(items => [...(items || []), ...Object.keys(v.entries)]));
    }
  }, [entryData, childModels]);

  // -------------------------------------------------------------
  // Handle data updates (field changes, child item creation or
  // deletion)

  let handleDataChange = (newData) => {
    // There's new data to load, display and highlight it:
    if (newData) {
      setEntryData(newData);
      setDoHighlight(true);
      onFieldsChanged ? onFieldsChanged(newData) : updateContext(newData);
    } else {
      // Try to reload the data from the server
      fetch(`${data["@path"]}.deep.json`)
        .then(response => response.ok ? response.json() : Promise.reject(response))
        .then(json => handleDataChange(json))
        .catch(() => {
           // If it fails, it's because we deleted an item
           // Update the context to remove the deleted item
           removeFromContext(`${data['jcr:uuid']}`);
           // Then pass it up to the parent
           onActionDone();
        });
    }
  }

  let onCreated = (newData) => {
    setEntryData(newData);
    updateContext(newData);
  }

  // -------------------------------------------------------------
  // Rendering

  let renderFields = (options) => (<>
    <LabeledField name={`${type}Id`} {...options}>{entryData["@name"]}</LabeledField>
    <Fields data={entryData} JSON={spec} edit={false} {...options} />
  </>);
  let FIELDS_CLASS_NAME = "cards-questionnaire-entry-props";

  return (
    <QuestionnaireItemCard
        titleField={titleField}
        moreInfo={renderFields({condensed: true})}
        data={entryData}
        type={type}
        classes={classes}
        doHighlight={doHighlight}
        action={
            menuItems?.length > 0 ?
            <CreationMenu
              data={entryData}
              onCreated={onCreated}
              menuItems={menuItems}
              models={childModels}
              {...menuProps}
            />
            : undefined
        }
        onActionDone={handleDataChange}
        model={model}
        {...rest}
    >
      { childModels ?
        <QuestionnaireItemSet
          data={entryData}
          classes={classes}
          onActionDone={handleDataChange}
          models={childModels}
        >
          <Grid item className={FIELDS_CLASS_NAME}>{renderFields()}</Grid>
        </QuestionnaireItemSet>
        : <div className={FIELDS_CLASS_NAME}>{renderFields()}</div>
      }
    </QuestionnaireItemCard>
  );
};

QuestionnaireEntry.propTypes = {
  onActionDone: PropTypes.func,
  onFieldsChanged: PropTypes.func,
  disableCollapse: PropTypes.bool,
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  plain: PropTypes.bool,
  avatar: PropTypes.string,
  avatarColor: PropTypes.string,
  title: PropTypes.string,
  titleField: PropTypes.string,
  model: PropTypes.string.isRequired
};

QuestionnaireEntry.defaultProps = {
  disableCollapse: true,
};
