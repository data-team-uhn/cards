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
import { useState } from 'react';

import MoveDownIcon from '@mui/icons-material/MoveDown';
import {
  Dialog,
  DialogTitle,
  IconButton,
  Tooltip,
} from '@mui/material';

import { useQuestionnaireTreeContext } from './QuestionnaireTreeContext';
import ReorderForm from './ReorderForm';

// If no entry data is provided then reorderSource can be selected
export function ReorderModal(props) {
  const { entryData } = props;
  const noEntryData = [undefined, null].includes(entryData);
  const [ open, setOpen ] = useState(false);
  const treeContext = useQuestionnaireTreeContext();
  const title = treeContext?.state?.nodes[entryData?.['jcr:uuid']]?.title;

  return (
    <>
      <Tooltip title={"Reorder entries"}>
        <IconButton onClick={() => setOpen(true)} size="large">
          <MoveDownIcon />
        </IconButton>
      </Tooltip>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        maxWidth="md"
        fullWidth
      >
        <DialogTitle>
          { noEntryData ? "Select position of an entry" : `Select position of '${title ?? entryData?.["@name"] ?? "entry"}'` }
        </DialogTitle>
        <ReorderForm
          onClose={() => setOpen(false)}
          data={noEntryData ? null : entryData}
        />
      </Dialog>
    </>
  );
}
