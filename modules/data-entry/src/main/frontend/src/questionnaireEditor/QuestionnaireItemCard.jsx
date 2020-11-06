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

import React, { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Avatar,
  Card,
  CardContent,
  Grid,
  Icon,
  IconButton,
  Typography,
  withStyles
} from "@material-ui/core";

import DeleteIcon from "@material-ui/icons/Delete";
import EditIcon from '@material-ui/icons/Edit';

import EditDialog from "./EditDialog";
import DeleteDialog from "./DeleteDialog";
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
    onClose,
    classes
  } = props;
  let [ editDialogOpen, setEditDialogOpen ] = useState(false);
  let [ deleteDialogOpen, setDeleteDialogOpen ] = useState(false);
  return (
    <Card variant="outlined">
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
            <IconButton onClick={() => { setDeleteDialogOpen(true); }}>
              <DeleteIcon />
            </IconButton>
            }
          </div>
        }
      />
      <CardContent className={classes.questionnaireItemContent + (!!!plain ? " avatarCardContent" : '')}>
        {children}
      </CardContent>
      { editDialogOpen && <EditDialog
                              targetExists={true}
                              data={data}
                              type={type}
                              isOpen={editDialogOpen}
                              onClose={() => { onClose();}}
                              onCancel={() => { setEditDialogOpen(false); }}
                            />
      }
      { deleteDialogOpen && <DeleteDialog
                              isOpen={deleteDialogOpen}
                              data={data}
                              type={type}
                              onClose={() => { onClose(); }}
                              onCancel={() => { setDeleteDialogOpen(false); }}
                            />
      }
    </Card>
  );
};

QuestionnaireItemCard.propTypes = {
  data: PropTypes.object.isRequired,
  type: PropTypes.string.isRequired,
  onClose: PropTypes.func.isRequired,
};

export default QuestionnaireItemCard;
