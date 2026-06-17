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

import { useEffect, useRef, useState, useMemo } from 'react';

import EditIcon from '@mui/icons-material/Edit';
import MoreIcon from '@mui/icons-material/MoreHoriz';
import CollapseIcon from '@mui/icons-material/UnfoldLess';
import ExpandIcon from '@mui/icons-material/UnfoldMore';
import {
  Card,
  CardContent,
  CardHeader,
  IconButton,
  Popover,
  Tooltip,
} from "@mui/material";
import PropTypes from 'prop-types';
import { makeStyles } from 'tss-react/mui';

import { checkPropTypes } from "../propTypes";
import EditDialog from "./EditDialog";
import { camelCaseToWords } from "./LabeledField";
import { useQuestionnaireTreeContext } from './QuestionnaireTreeContext.jsx';
import FormattedText from "../components/FormattedText.jsx";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";
import { ENTRY_TYPES } from '../questionnaire/FormEntry.jsx';
import { getOrdinalString } from '../questionnaire/QuestionnaireUtilities.jsx';

const useStyles = makeStyles()((theme, { entryTypeColor } = {}) => {
  const accentColor = entryTypeColor || theme.palette.divider;
  return ({
    root : {
      border: "0 none",
      background: theme.palette.action.hover,
      "& .MuiCardHeader-avatar": {
        alignSelf: "start",
        zoom: .75,
        marginTop: theme.spacing(.75),
        fontWeight: "bold",
      },
      "& .MuiCardHeader-content .MuiIconButton-root": {
        display: "none",
      },
      marginBottom: theme.spacing(.75),
      // When highlighted, use the entry-type colour for the outline instead of the default
      // primary colour (the 2px solid outline itself comes from .cards-focused in formStyles).
      "&.cards-focused": {
        outlineColor: `${accentColor} !important`,
        // Square the left corners so the outline lines up with the straight left accent
        // border of the wrapper (the outline follows the card's border-radius).
        borderTopLeftRadius: 0,
        borderBottomLeftRadius: 0,
      },
    },
    title: {
      display: "inline",
    },
    titlePlaceholder: {
      opacity: "0.6",
      fontWeight: "300 !important",
    },
    collapsed: {
      "& .MuiCardContent-root": {
        paddingTop: 0,
        paddingBottom: 0,
      },
      "& .cards-questionnaire-entry-props": {
        display: "none",
      },
      "& .MuiCardContent-root > .MuiGrid-container > .MuiGrid-root:last-child": {
        marginBottom: theme.spacing(2),
      },
      "& .MuiCardHeader-content .MuiIconButton-root": {
        display: "inline-flex",
      }
    },
    moreInfo: {
      "& h6": {
        whiteSpace: "nowrap",
      }
    },
    withAvatar: {
      "&.MuiCardContent-root > .cards-questionnaire-entry-props": {
        paddingLeft: theme.spacing(5.5),
      },
      "&.MuiCardContent-root > .MuiGrid-container > .MuiGrid-root": {
        paddingLeft: theme.spacing(5.5),
      },
      "&.MuiCardContent-root > .MuiGrid-container > .MuiGrid-root.cards-questionnaire-entry-props": {
        paddingLeft: theme.spacing(7.5),
      },
    },
    entryWrapper: {
      position: "relative",
      borderLeft: `3px solid ${accentColor}`,
    },
    ordinalBadge: {
      position: "absolute",
      top: 0,
      left: 0,
      zIndex: 1,
      backgroundColor: accentColor,
      color: theme.palette.getContrastText(accentColor),
      fontSize: "x-small",
      paddingRight: "3px",
    },
  });
});

// General class or Sections and Questions

let QuestionnaireItemCard = (props) => {
  checkPropTypes(QuestionnaireItemCard, props);
  let {
    children,
    entryTypeColor,
    type,
    title,
    titleField,
    moreInfo,
    action,
    disableEdit,
    disableDelete,
    disableCollapse,
    plain,
    data,
    onActionDone,
    doHighlight,
    model,
  } = props;
  let [ editDialogOpen, setEditDialogOpen ] = useState(false);
  let [ isCollapsed, setCollapsed ] = useState(false);
  let [ moreInfoAnchor, setMoreInfoAnchor ] = useState(null);

  const highlight = doHighlight || window.location?.hash?.substr(1) == data["@path"];
  const treeContext = useQuestionnaireTreeContext();
  const itemRef = useRef();

  useEffect(() => {
    if (itemRef.current) {
      itemRef.current.setAttribute('in-view-data-id', data['jcr:uuid']);
    }
  }, [data]);

  // When this card is highlighted (created/edited/moved, or targeted via the url hash),
  // scroll it into view shortly after render.
  useEffect(() => {
    if (highlight) {
      const timer = setTimeout(() => {
        itemRef?.current?.scrollIntoView({ block: "center" });
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [itemRef]);

  const { classes } = useStyles({ entryTypeColor });

  let cardClasses = [classes.root];
  if (isCollapsed) {
    cardClasses.push(classes.collapsed);
  }
  if (highlight) {
    cardClasses.push("cards-focused");
  }

  let formattedType = camelCaseToWords(type);

  let titleClasses = [classes.title];
  let titleText = title || data[titleField];
  if (!titleText) {
    titleText = `${formattedType} ${data["@name"]}`;
    titleClasses.push(classes.titlePlaceholder);
  }

  const ordinalPosition = useMemo(() => {
    if (type === "Questionnaire" || !treeContext?.state?.nodes) {
      return null;
    }
    // Get id of current item
    const itemId = data['jcr:uuid'];
    // Get the index of the item in the parent from treeContext.state.nodes object
    const itemParent = Object.values(treeContext.state.nodes)
      .find(item => item['id'] === itemId)?.parent;
    const itemPosition = itemParent
      ? treeContext.state.nodes[itemParent].children
        .filter(childId => ENTRY_TYPES.includes(treeContext.state.nodes[childId]?.jcrPrimaryType))
        .indexOf(itemId)
      : null;
    return itemPosition !== null ? getOrdinalString(itemPosition) : null;
  }, [type, treeContext, data]);

  return (
    <div className={classes.entryWrapper}>
      { !!ordinalPosition &&
        <div className={classes.ordinalBadge}>
          {ordinalPosition}
        </div>
      }
      <Card
        variant="outlined"
        ref={itemRef}
        className={cardClasses.join(" ")}
      >
        <CardHeader
          disableTypography
          title={
            <>
              <FormattedText className={titleClasses.join(" ")} variant="h6">{titleText}</FormattedText>
              { moreInfo &&
                <Tooltip title="Properties">
                  <IconButton onClick={(event) => setMoreInfoAnchor(event.currentTarget)} size="large">
                    <MoreIcon />
                  </IconButton>
                </Tooltip>
              }
              { moreInfo && moreInfoAnchor &&
                <Popover
                  className={classes.moreInfo}
                  open={Boolean(moreInfoAnchor)}
                  anchorEl={moreInfoAnchor}
                  onClose={() => setMoreInfoAnchor(null)}
                  anchorOrigin={{
                    vertical: 'bottom',
                    horizontal: 'left',
                  }}
                  transformOrigin={{
                    vertical: 'top',
                    horizontal: 'left',
                  }}
                >
                  <Card><CardContent>{moreInfo}</CardContent></Card>
                </Popover>
              }
            </>
          }
          action={
            <div>
              {action}
              {!disableEdit &&
              <Tooltip title={`Edit ${formattedType.toLowerCase()} properties`}>
                <IconButton onClick={() => setEditDialogOpen(true)} size="large">
                  <EditIcon />
                </IconButton>
              </Tooltip>
              }
              {!disableDelete &&
              <DeleteButton
                entryPath={data["@path"]}
                entryName={title || data[titleField] || data["@name"]}
                entryType={formattedType.toLowerCase()}
                onComplete={onActionDone}
              />
              }
              {!disableCollapse &&
              <Tooltip title={isCollapsed? "Expanded view" : "Collapsed view"}>
                <IconButton onClick={() => setCollapsed(!isCollapsed)} disabled={!Boolean(children)} size="large">
                  { isCollapsed ? <ExpandIcon /> : <CollapseIcon /> }
                </IconButton>
              </Tooltip>
              }
            </div>
          }
        />
        <CardContent className={!plain ? classes.withAvatar : undefined}>
          { children }
          { editDialogOpen && <EditDialog
            targetExists
            data={data}
            type={type}
            model={model}
            isOpen={editDialogOpen}
            onSaved={() => { setEditDialogOpen(false); onActionDone(); }}
            onCancel={() => setEditDialogOpen(false)}
          />
          }
        </CardContent>
      </Card>
    </div>
  );
};

QuestionnaireItemCard.propTypes = {
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  onActionDone: PropTypes.func.isRequired,
};

export default (QuestionnaireItemCard);
