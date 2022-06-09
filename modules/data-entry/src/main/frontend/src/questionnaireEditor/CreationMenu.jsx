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
  Button,
  Menu,
  MenuItem
} from "@mui/material";

import EditDialog from "./EditDialog";
import NewItemButton from "../components/NewItemButton";

// Menu for creating questions or sections

let CreationMenu = (props) => {
  const { isMainAction, data, menuItems, models, onCreated } = props;
  let [ anchorEl, setAnchorEl ] = useState(null);
  let [ entityType, setEntityType ] = useState('Question');
  let [ dialogOpen, setDialogOpen ] = useState(false);

  let handleOpenMenu = (event) => {
    setAnchorEl(event.currentTarget);
  }

  let handleCloseMenu = () => {
    setAnchorEl(null);
  }

  let openDialog = (type) => {
    setEntityType(type);
    setDialogOpen(true);
  }

  return (
    <>
      { isMainAction ?
      <NewItemButton title="Add..." onClick={handleOpenMenu} />
      :
      <Button aria-controls={"simple-menu" + data['@name']} aria-haspopup="true" onClick={handleOpenMenu}>
        Add...
      </Button>
      }
      <Menu
          id={"simple-menu" + data['@name']}
          anchorEl={anchorEl}
          keepMounted
          open={Boolean(anchorEl)}
          onClose={handleCloseMenu}
      >
        { menuItems.map(type =>
            <MenuItem key={type} onClick={() => { openDialog(type); handleCloseMenu(); }}>{type}</MenuItem>
        )}
      </Menu>
      { dialogOpen && <EditDialog
                        targetExists={false}
                        data={data}
                        type={entityType}
                        model={models?.[entityType]}
                        isOpen={dialogOpen}
                        onSaved={(newData) => { setDialogOpen(false); onCreated && onCreated(newData); }}
                        onCancel={() => { setDialogOpen(false); }}
                      />
      }
    </>
  );
}

CreationMenu.propTypes = {
  isMainAction: PropTypes.bool,
  data: PropTypes.object.isRequired,
  menuItems: PropTypes.array.isRequired,
  onCreated: PropTypes.func
};

CreationMenu.defaultProps = {
  isMainAction: false,
}

export default CreationMenu;
