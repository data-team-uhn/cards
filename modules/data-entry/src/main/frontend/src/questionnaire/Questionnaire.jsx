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

import { useEffect, useState, useMemo } from "react";

import EditIcon from '@mui/icons-material/Edit';
import PreviewIcon from '@mui/icons-material/FindInPage';
import MoreIcon from '@mui/icons-material/MoreVert';
import {
  Button,
  CircularProgress,
  Divider,
  Grid,
  IconButton,
  Link as MuiLink,
  List,
  ListItem,
  Popover,
  Tabs,
  Tab,
  Tooltip,
  Typography,
  useScrollTrigger,
} from "@mui/material";
import {
  blue,
  deepPurple,
  green,
  orange,
  purple
} from '@mui/material/colors';
import _ from "lodash";
import { DateTime } from "luxon";
import PropTypes from "prop-types";
import { Link, useNavigate, useLocation } from 'react-router';
import { withStyles } from 'tss-react/mui';

import { ENTRY_TYPES, QUESTION_TYPES, SECTION_TYPES } from "./FormEntry";
import formStyles from "./formStyles.jsx";
import { FORM_ENTRY_CONTAINER_PROPS } from "./questionnaireConstants.jsx";
import { QuestionnaireProvider, useQuestionnaireInViewContext, getAncestorPath } from "./QuestionnaireContext";
import QuestionnairePreview from "./QuestionnairePreview";
import { stripCardsNamespace } from "./QuestionnaireUtilities";
import ResourceHeader from "./ResourceHeader";
import DeleteButton from "../dataHomepage/DeleteButton";
import ExportButton from "../dataHomepage/ExportButton";
import { checkPropTypes } from "../propTypes";
import CreationMenu from "../questionnaireEditor/CreationMenu";
import EditorHeader from "../questionnaireEditor/EditorHeader.jsx";
import Fields from "../questionnaireEditor/Fields";
import LabeledField from "../questionnaireEditor/LabeledField";
import QuestionnaireItemCard from "../questionnaireEditor/QuestionnaireItemCard";
import { useQuestionnaireTreeContext, QuestionnaireTreeProvider } from "../questionnaireEditor/QuestionnaireTreeContext.jsx";
import ReorderDraft from "../questionnaireEditor/ReorderDraft.jsx";
import { ReorderModal } from "../questionnaireEditor/ReorderModal.jsx";
import { usePageNameWriterContext } from "../themePage/Page.jsx";

export const QUESTIONNAIRE_ITEM_NAMES = ENTRY_TYPES.map(type => stripCardsNamespace(type));

let Questionnaire = (props) => {
  let location = useLocation();
  let id = /Questionnaires\/([^.]+)/.exec(location.pathname)[1];

  return (
    <QuestionnaireTreeProvider questionnaireId={id}>
      <QuestionnaireComponent {...props} />
    </QuestionnaireTreeProvider>
  )
}

// Renders the sticky resource header with a dynamic "location" breadcrumb showing the
// path of the item currently scrolled to the top of the Edit tab. Isolated as its own
// component so that scroll-driven activeItem changes re-render only the header, not the
// questionnaire content.
let QuestionnaireResourceHeader = (props) => {
  let { title, action, baseUrl, contentOffset, data, showLocation } = props;
  const { state: { nodes } } = useQuestionnaireTreeContext();
  const inView = useQuestionnaireInViewContext();
  // Only show the location path once the header has collapsed (the big title has scrolled
  // out of view), so the title isn't shown twice. Same threshold ResourceHeader uses.
  const collapsed = useScrollTrigger({ target: window, disableHysteresis: true, threshold: 120 });

  // The breadcrumb path is the questionnaire root -> ... -> the current item. The root is
  // always shown (clicking it scrolls to the top); the section and question are appended
  // as you scroll. Before anything has scrolled into view, only the root is shown.
  const renderCrumb = (node) => (
    <MuiLink
      key={node.id}
      component="button"
      underline="hover"
      onClick={() => inView.scrollToItem(node.id)}
    >
      {node.title || node.name}
    </MuiLink>
  );
  let pathNodes = [];
  if (showLocation && collapsed) {
    if (inView.activeItem) {
      // getAncestorPath already returns the root as its first element.
      pathNodes = getAncestorPath(nodes, inView.activeItem);
    } else {
      // Before anything is scrolled into view, show just the questionnaire root.
      const root = Object.values(nodes || {}).find(node => node.parent === null);
      pathNodes = root ? [root] : [];
    }
  }
  const locationCrumbs = pathNodes.map(renderCrumb);

  return (
    <ResourceHeader
      title={title || ""}
      breadcrumbs={[
        <Link key="questionnaires" to={".." + baseUrl} underline="hover">Questionnaires</Link>,
        ...locationCrumbs
      ]}
      action={action}
      contentOffset={contentOffset}
      hideBreadcrumbTitle={showLocation}
    >
      { data?.['jcr:createdBy'] && data?.['jcr:created'] &&
        <Typography variant="overline">
          Created by {data['jcr:createdBy']} on {DateTime.fromISO(data['jcr:created']).toLocaleString(DateTime.DATE_MED_WITH_WEEKDAY)}
        </Typography>
      }
      <EditorHeader />
    </ResourceHeader>
  );
};

// GUI for displaying details about a questionnaire.
let QuestionnaireComponent = (props) => {
  let { classes } = props;
  let [ actionsMenu, setActionsMenu ] = useState(null);
  let [ error, setError ] = useState();
  let location = useLocation();
  let baseUrl = /((.*)\/Questionnaires)\/([^.]+)/.exec(location.pathname)[1];
  let id = /Questionnaires\/([^.]+)/.exec(location.pathname)[1];
  let questionnaireUrl = `${baseUrl}/${id}`;

  const treeContext = useQuestionnaireTreeContext();

  const { data } = treeContext.state;
  const questionnaireTitle = data?.title || decodeURI(id);

  let navigate = useNavigate();
  let isEdit = location.pathname.endsWith(".edit");
  let isReorder = location.pathname.endsWith(".reorder");
  // Derive the active tab from the URL rather than holding it in separate state. This keeps
  // the Reorder tab mounted when its navigation guard blocks a tab switch: the guard blocks
  // the URL change, the derived tab stays on "reorder", and no out-of-sync state lingers.
  const editTab = isReorder ? 'reorder' : 'edit';
  let pageNameWriter = usePageNameWriterContext();

  // First, fetch the questionnaire data
  useEffect(() => {
    treeContext.actions.refreshTree().catch((error) => { setError(error) });
  }, []);

  useEffect(() => {
    pageNameWriter(questionnaireTitle);
  }, [questionnaireTitle]);

  useEffect(() => {
    if (!(isEdit || isReorder)) return;
    // Perform a JCR check-out of the Questionnaire and register a check-in
    treeContext.actions.checkOut(id);
    const performCheckIn = () => { treeContext.actions.checkIn(id) };
    window.addEventListener("beforeunload", performCheckIn);
    return (() => {
      window.removeEventListener("beforeunload", performCheckIn);
    });
  }, [isEdit, isReorder, id]);

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
          onClose={() => setActionsMenu(null)}
        />
      </ListItem>
      <ListItem className={classes.actionsMenuItem}>
        <Button
          size="medium"
          component="a"
          download={`${id}.json`}
          href={`/Questionnaires/${id}.deep.-identify.importable.json`}
          onClick={() => {
            setActionsMenu(null);
          }}
        >
          Export as JSON
        </Button>
      </ListItem>
      <ListItem className={classes.actionsMenuItem}>
        <DeleteButton
          entryPath={data ? data["@path"] : `/Questionnaires/${id}`}
          entryName={questionnaireTitle}
          entryType="Questionnaire"
          onComplete={() => navigate(baseUrl, { replace: true })}
          size="medium"
          variant="text"
          onClose={() => setActionsMenu(null)}
        />
      </ListItem>
    </List>
  )

  let questionnaireMenu = (
    <div className={classes.actionsMenu}>
      {(isEdit || isReorder) ?
        <Tooltip title="Preview" onClick={() => navigate(questionnaireUrl)}>
          <IconButton size="large">
            <PreviewIcon />
          </IconButton>
        </Tooltip>
        :
        <Tooltip title="Edit" onClick={() => navigate(questionnaireUrl + ".edit")}>
          <IconButton color="primary" size="large">
            <EditIcon />
          </IconButton>
        </Tooltip>
      }
      <Tooltip title="More actions" onClick={(event) => setActionsMenu(event.currentTarget)}>
        <IconButton size="large">
          <MoreIcon fontSize="small" />
        </IconButton>
      </Tooltip>
      <Popover
        open={Boolean(actionsMenu)}
        anchorEl={actionsMenu}
        onClose={() => setActionsMenu(null)}
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

  return (
    <QuestionnaireProvider>
      { error ?
        <Typography variant="h2" color="error">
          Error obtaining questionnaire info: {error.status} {error.statusText}
        </Typography>
        :
        data?.["jcr:primaryType"] === "cards:Questionnaire" &&
          <Grid container {...FORM_ENTRY_CONTAINER_PROPS}>
            <QuestionnaireResourceHeader
              title={questionnaireTitle}
              action={questionnaireMenu}
              baseUrl={baseUrl}
              contentOffset={props.contentOffset}
              data={data}
              showLocation={isEdit}
            />
            <Grid>
              { !(isEdit || isReorder)
                ?
                <QuestionnairePreview
                  data={data}
                  title={questionnaireTitle}
                  contentOffset={props.contentOffset}
                />
                :
                <>
                  <Tabs
                    value={editTab}
                    onChange={(event, newValue) => {
                      navigate(questionnaireUrl + `.${newValue}`);
                    }}
                  >
                    <Tab label="Edit" value="edit" />
                    <Tab label="Reorder" value="reorder" />
                  </Tabs>
                  <Divider />
                  { editTab == "edit" &&
                    <QuestionnaireContents
                      key={treeContext.state.revision}
                      disableDelete
                      data={data}
                      classes={classes}
                      menuProps={{ isMainAction: true }}
                    />
                  }
                  { editTab == "reorder" &&
                    <ReorderDraft key={treeContext.state.timestamp} />
                  }
                </>
              }
            </Grid>
          </Grid>
      }
    </QuestionnaireProvider>
  );
};

export default withStyles(Questionnaire, formStyles);


let QuestionnaireItemSet = (props) => {
  checkPropTypes(QuestionnaireItemSet, props);
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
      .forEach(v => prioritaryModels = { ...prioritaryModels, ...v.entries });

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
          generalModels = { ...generalModels, ...v.entries }
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
          EntryType => <Grid key={key}>
            <EntryType
              data={value}
              model={typeModels?.[stripCardsNamespace(value['jcr:primaryType'])]}
              onActionDone={onActionDone}
              classes={classes}
            />
          </Grid>
        // eslint-disable-next-line react-hooks/unsupported-syntax
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
          : <Grid><Grid container justifyContent="center"><Grid><CircularProgress/></Grid></Grid></Grid>
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
  checkPropTypes(QuestionnaireContents, props);

  return <QuestionnaireEntry
    disableCollapse={false}
    type="Questionnaire"
    titleField="title"
    model="Questionnaire.json"
    { ...props } />;
};

QuestionnaireContents.propTypes = {
  onActionDone: PropTypes.func,
  disableCollapse: PropTypes.bool,
  data: PropTypes.object.isRequired,
  type: PropTypes.string,
  entryTypeColor: PropTypes.string,
  titleField: PropTypes.string,
  model: PropTypes.string
};

// Details about an information block displayed in a questionnaire
let Information = (props) => <QuestionnaireEntry
  type="Information"
  entryTypeColor={blue[600]}
  model="Information.json"
  {...props} />;

Information.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired,
  type: PropTypes.string,
  entryTypeColor: PropTypes.string,
  model: PropTypes.string
};

// Details about an id mapping block displayed in a questionnaire
let ExternalLink = (props) => <QuestionnaireEntry
  type="ExternalLink"
  entryTypeColor={purple[300]}
  model="ExternalLink.json"
  {...props} />;

ExternalLink.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired,
  type: PropTypes.string,
  entryTypeColor: PropTypes.string,
  model: PropTypes.string
};

// Details about a particular question in a questionnaire.
// Not to be confused with the public Question component responsible for rendering questions inside a Form.
let Question = (props) => <QuestionnaireEntry
  type="Question"
  entryTypeColor={deepPurple[700]}
  titleField="text"
  model="Question.json"
  {...props} />;

Question.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired,
  type: PropTypes.string,
  entryTypeColor: PropTypes.string,
  titleField: PropTypes.string,
  model: PropTypes.string
};

// Details about a particular section in a questionnaire.
// Not to be confused with the public Section component responsible for rendering sections inside a Form.
let Section = (props) => <QuestionnaireEntry
  type="Section"
  entryTypeColor={orange[800]}
  titleField="label"
  model="Section.json"
  {...props} />

Section.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired,
  type: PropTypes.string,
  entryTypeColor: PropTypes.string,
  titleField: PropTypes.string,
  model: PropTypes.string
};


// Details about a simple condition for displaying a section
let Conditional = (props) => <QuestionnaireEntry
  type="Conditional"
  entryTypeColor={green[800]}
  model="Conditional.json"
  {...props} />;

Conditional.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired,
  type: PropTypes.string,
  entryTypeColor: PropTypes.string,
  model: PropTypes.string
};

// Details about a group pf conditions for displaying a section
let ConditionalGroup = (props) => <QuestionnaireEntry
  type="ConditionalGroup"
  entryTypeColor={green[800]}
  model="ConditionalGroup.json"
  {...props} />;

ConditionalGroup.propTypes = {
  onActionDone: PropTypes.func,
  data: PropTypes.object.isRequired,
  type: PropTypes.string,
  entryTypeColor: PropTypes.string,
  model: PropTypes.string
};

// Generic QuestionnaireEntry component that can be adapted to any entry type via props

let QuestionnaireEntry = (props) => {
  checkPropTypes(QuestionnaireEntry, props);
  let { onActionDone, data, type, titleField, model, classes, menuProps, ...rest } = props;
  let [ entryData, setEntryData ] = useState(data);
  let [ doHighlight, setDoHighlight ] = useState(data.doHighlight);

  // --------------------------------------------------------------
  // Questionnaire context manipulation

  const treeContext = useQuestionnaireTreeContext();

  useEffect(() => {
    if (!_.isEqual(entryData, data)) {
      treeContext.actions.updateNodeData(entryData);
    }
  }, [entryData]);

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
      typeof entryData[key] !== "undefined" &&
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

  const menuItems = useMemo(() => {
    if (!childModels) return [];
    // Add the child types to the menu
    const base = Object.keys(childModels || {}).filter(k => typeof(childModels[k]) != "object");

    // Some child entries may be configured to have a maximum number of entries
    // (for example, only one conditional or conditional group per section)
    // Exclude from the creation menu any entries corresponding to child types
    // for which maximum of that type has been reached
    const extras =
      Object.values(childModels)
        .filter((v) => typeof v === "object" && typeof v?.entries === "object")
        .filter((v) => {
          if (!Object.hasOwn(v, "max")) return true;
          let entryTypes = Object.keys(v.entries).map(e => `cards:${e}`);
          const currentCount = Object.values(entryData).filter(e => entryTypes?.includes(e['jcr:primaryType'])).length;
          return currentCount < v.max;
        })
        .flatMap((v) => Object.keys(v.entries));
    return [...base, ...extras];
  }, [entryData, childModels]);

  // -------------------------------------------------------------
  // Handle data updates (field changes, child item creation or
  // deletion)

  let handleDataChange = (newData) => {
    // There's new data to load, display and highlight it:
    if (newData) {
      setEntryData(newData);
      setDoHighlight(true);
    } else {
      // Reload this entry from the server after an edit (or detect deletion via a 404).
      // Loading the entry's data dispatches UPDATE_ONDATA, which keeps `state.data` and
      // the derived nodes in sync, so no full-tree reload is needed.
      fetch(`${data["@path"]}.deep.json`)
        .then(response => response.ok ? response.json() : Promise.reject(response))
        .then(json => handleDataChange(json))
        .catch(() => {
          // The reload failed because the item was deleted; remove it from `data` by
          // path (works for nodes with or without a jcr:uuid, e.g. conditionals).
          treeContext.actions.removeNode(data['@path']);
          // Then pass it up to the parent
          onActionDone?.();
        });
    }
  }

  let onCreated = (newData) => {
    setEntryData(newData);
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
      moreInfo={renderFields({ condensed: true })}
      data={entryData}
      type={type}
      doHighlight={doHighlight}
      action={<>
        { menuItems?.length > 0 &&
          <CreationMenu
            data={entryData}
            onCreated={onCreated}
            menuItems={menuItems}
            models={childModels}
            {...menuProps}
          />
        }
        { !!menuProps?.isMainAction ?
          // If this is the main action, render MoveEntryModal without data to select reorder source
          // Otherwise render MoveEntryModal with data set
          <ReorderModal />
          :
          [...QUESTION_TYPES, ...SECTION_TYPES].includes(entryData['jcr:primaryType']) &&
            <ReorderModal entryData={entryData} />
        }
      </>}
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
          <Grid className={FIELDS_CLASS_NAME}>{renderFields()}</Grid>
        </QuestionnaireItemSet>
        : <div className={FIELDS_CLASS_NAME}>{renderFields()}</div>
      }
    </QuestionnaireItemCard>
  );
};

QuestionnaireEntry.propTypes = {
  onActionDone: PropTypes.func,
  disableCollapse: PropTypes.bool,
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  plain: PropTypes.bool,
  avatar: PropTypes.string,
  entryTypeColor: PropTypes.string,
  title: PropTypes.string,
  titleField: PropTypes.string,
  model: PropTypes.string.isRequired
};
