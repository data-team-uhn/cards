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
  IconButton
} from "@material-ui/core";

import DeleteIcon from "@material-ui/icons/Delete";
import EditIcon from '@material-ui/icons/Edit';

import EditDialog from "./EditDialog";
import DeleteButton from "../dataHomepage/DeleteButton.jsx";
import QuestionnaireCardHeader from "./QuestionnaireCardHeader";

// General class or Sections and Questions

let QuestionnaireItemCard = (props) => {
  let {
    children,
    avatar,
    avatarColor,
    type,
    title,
    action,
    disableEdit,
    disableDelete,
    plain,
    data,
    onActionDone,
    doHighlight,
    model,
    classes
  } = props;
  let [ editDialogOpen, setEditDialogOpen ] = useState(false);
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

  let fetchData = () => {
    fetch(`${data["@path"]}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        onActionDone(json);
      })
      .catch(() => onActionDone());
  };

  return (
    <Card variant="outlined" ref={highlight ? itemRef : undefined} className={highlight ? classes.focusedQuestionnaireItem : ''}>
      <QuestionnaireCardHeader
        avatar={avatar}
        avatarColor={avatarColor}
        type={type}
        label={title || data.label || data.text}
        plain={plain}
        action={
          <div>
            {action}
            {!disableEdit &&
            <IconButton onClick={() => { setEditDialogOpen(true); }}>
              <EditIcon />
            </IconButton>
            }
            {!disableDelete &&
            <DeleteButton
               entryPath={data["@path"]}
               entryName={title || data.label || data.text || data["@name"]}
               entryType={type}
               onComplete={onActionDone}
            />
            }
          </div>
        }
      />
      <CardContent className={classes.questionnaireItemContent + (!!!plain ? " avatarCardContent" : '')}>
        {children}
        { editDialogOpen && <EditDialog
                              targetExists={true}
                              data={data}
                              type={type}
                              model={model}
                              isOpen={editDialogOpen}
                              onClose={() => { setEditDialogOpen(false); fetchData(); }}
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
