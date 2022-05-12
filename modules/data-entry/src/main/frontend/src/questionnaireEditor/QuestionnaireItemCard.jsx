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
import { withRouter } from "react-router-dom";
import PropTypes from 'prop-types';
import {
  Card,
  CardContent,
  IconButton,
  Tooltip,
  Typography,
} from "@mui/material";

import makeStyles from '@mui/styles/makeStyles';

import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from '@mui/icons-material/Edit';
import ExpandIcon from '@mui/icons-material/UnfoldMore';
import CollapseIcon from '@mui/icons-material/UnfoldLess';

import EditDialog from "./EditDialog";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";
import QuestionnaireCardHeader from "./QuestionnaireCardHeader";

import { camelCaseToWords }  from "./Fields";

const useStyles = makeStyles(theme => ({
  root : {
    border: "0 none",
    background: theme.palette.action.hover,
   "& > .MuiCardHeader-root": {
     paddingBottom: 0,
   },
   "& > .MuiCardContent-root": {
     paddingTop: 0,
   },
  },
  title: {
    "& + *" : {
      paddingTop: theme.spacing(4),
    },
  },
  collapsed: {
    "& .cards-questionnaire-entry-props": {
      display: "none",
    }
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
    action,
    disableEdit,
    disableDelete,
    disableCollapse,
    plain,
    data,
    onActionDone,
    doHighlight,
    model,
    classes
  } = props;
  let [ editDialogOpen, setEditDialogOpen ] = useState(false);
  let [ isCollapsed, setCollapsed ] = useState(false);
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

  const styles = useStyles();

  let cardClasses = [styles.root];
  if (isCollapsed) {
    cardClasses.push(styles.collapsed);
  }
  if (highlight) {
    cardClasses.push(classes.focusedQuestionnaireItem);
  }

  let formattedType = camelCaseToWords(type);

  return (
    <Card variant="outlined" ref={highlight ? itemRef : undefined} className={cardClasses.join(" ")}>
      <QuestionnaireCardHeader
        avatar={avatar}
        avatarColor={avatarColor}
        type={formattedType}
        id={data["@name"]}
        plain={plain}
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
      <CardContent className={classes.questionnaireItemContent + (!!!plain ? " avatarCardContent" : '')}>
        <Typography className={styles.title} variant="h6">{title || data[titleField] || ''}</Typography>
        {children}
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

export default (withRouter(QuestionnaireItemCard));
