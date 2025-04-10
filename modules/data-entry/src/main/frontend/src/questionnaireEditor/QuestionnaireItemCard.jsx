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

import React, { useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Avatar,
  Card,
  CardContent,
  CardHeader,
  Icon,
  IconButton,
  Popover,
  Tooltip,
} from "@mui/material";

import { makeStyles } from 'tss-react/mui';

import EditIcon from '@mui/icons-material/Edit';
import ExpandIcon from '@mui/icons-material/UnfoldMore';
import CollapseIcon from '@mui/icons-material/UnfoldLess';
import MoreIcon from '@mui/icons-material/MoreHoriz';

import EditDialog from "./EditDialog";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";

import FormattedText from "../components/FormattedText.jsx";

import { camelCaseToWords }  from "./LabeledField";

const useStyles = makeStyles()(theme => ({
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
  }
}));

// General class or Sections and Questions

let QuestionnaireItemCard = (props) => {
  let {
    children,
    avatar,
    avatarColor,
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
    upperClasses
  } = props;
  let [ editDialogOpen, setEditDialogOpen ] = useState(false);
  let [ isCollapsed, setCollapsed ] = useState(false);
  let [ moreInfoAnchor, setMoreInfoAnchor ] = useState(null);
  const highlight = doHighlight || window.location?.hash?.substr(1) == data["@path"];

  const itemRef = useRef();
  // if autofocus is needed and specified in the url
  // create a ref to store the question container DOM element
  useEffect(() => {
    if (highlight) {
      const timer = setTimeout(() => {
          itemRef?.current?.scrollIntoView({block: "center"});
        }, 500);
        return () => clearTimeout(timer);
    }
  }, [itemRef]);

  const { classes } = useStyles();

  let cardClasses = [classes.root];
  if (isCollapsed) {
    cardClasses.push(classes.collapsed);
  }
  if (highlight) {
    cardClasses.push(upperClasses.focusedQuestionnaireItem);
  }

  let formattedType = camelCaseToWords(type);

  let titleClasses = [classes.title];
  let titleText = title || data[titleField];
  if (!titleText) {
    titleText = `${formattedType} ${data["@name"]}`;
    titleClasses.push(classes.titlePlaceholder);
  }

  return (
    <Card variant="outlined" ref={highlight ? itemRef : undefined} className={cardClasses.join(" ")}>
      <CardHeader
        disableTypography
        avatar={!plain && (avatar || type) ?
          <Avatar style={{backgroundColor: avatarColor || "black"}}>
            { avatar ? <Icon>{avatar}</Icon> : type?.charAt(0) }
          </Avatar>
          : null
        }
        title={
          <>
            { <FormattedText className={titleClasses.join(" ")} variant="h6">{titleText}</FormattedText> }
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
              <IconButton onClick={() => { setEditDialogOpen(true); }} size="large">
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
              <IconButton onClick={() => setCollapsed(!isCollapsed)} disabled={!!!children} size="large">
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
                              targetExists={true}
                              data={data}
                              type={type}
                              model={model}
                              isOpen={editDialogOpen}
                              onSaved={() => { setEditDialogOpen(false); onActionDone(); }}
                              onCancel={() => { setEditDialogOpen(false); }}
                            />
        }
      </CardContent>
    </Card>
  );
};

QuestionnaireItemCard.propTypes = {
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  onActionDone: PropTypes.func.isRequired,
};

export default (QuestionnaireItemCard);
