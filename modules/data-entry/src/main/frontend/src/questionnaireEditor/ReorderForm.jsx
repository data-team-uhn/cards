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

import React, { useEffect, useMemo, useCallback, useReducer, useState } from 'react';
import {
  Alert,
  Button,
  DialogActions,
  DialogContent,
  FormControlLabel,
  Grid,
  Typography,
  RadioGroup,
  Radio,
} from '@mui/material';
import { useQuestionnaireTreeContext, isDescendant, getOrdinalString } from './QuestionnaireTreeContext';
import QuestionnaireAutocomplete from '../questionnaire/QuestionnaireAutocomplete';
import { stripCardsNamespace } from '../questionnaire/QuestionnaireUtilities';
import { ENTRY_TYPES } from '../questionnaire/FormEntry';

// State for reorder submission
// {status: 'idle' | 'loading' | 'success' | 'error', data: null, error: null, inputs: {reorderSource: string, newParent: string, positionRadio: string, newPosition: number}}
const initialReorderState = {
  status: 'idle',
  data: null,
  error: null,
  inputs: {
    reorderSource: '',
    newParent: '',
    positionRadio: '',
    newPosition: null
  }
};

const reorderReducer = (state, action) => {
  let newInputs;
  switch (action.type) {
    case 'SET_IDLE':
      return { ...state, status: 'idle' };
    case 'SET_LOADING':
      return { ...state, status: 'loading' };
    case 'SET_SUCCESS':
      return { ...state, status: 'success', data: action.payload };
    case 'SET_ERROR':
      return { ...state, status: 'error', error: action.payload };
    case 'SET_REORDERSOURCE':
      newInputs = { ...state.inputs, reorderSource: action.payload };
      return { ...state, inputs: newInputs };
    case 'SET_NEWPARENT':
      newInputs = {...state.inputs, newParent: action.payload };
      return { ...state, inputs: newInputs };
    case 'SET_POSITIONRADIO':
      if (!['first', 'last', 'other'].includes(action.payload)) {
        console.error('Invalid position radio value', action.payload);
        return state;
      }
      newInputs = { ...state.inputs, positionRadio: action.payload };
      // If first or last, set newPosition as well
      if (['first', 'last'].includes(action.payload)) {
        newInputs = {...newInputs, newPosition: action.payload === 'first' ? 0 : -1};
      } else {
        // If other then reset newPosition
        newInputs = {...newInputs, newPosition: null};
      }
      return { ...state, inputs: newInputs };
    case 'SET_NEWPOSITION':
      newInputs = { ...state.inputs, newPosition: action.payload };
      return { ...state, inputs: newInputs };
    default:
      console.warning('Unhandled action in reorderReducer', action);
      return state;
  }
}


export default function ReorderForm(props) {
  const { data, onClose } = props;
  const disableReorderSourceSelect = !!data;
  const treeContext = useQuestionnaireTreeContext();
  const { state: { nodes } } = treeContext;

  // If reorderSource is provided, pass into initialReorderState
  const [reorderState, reorderDispatch] =
    useReducer(reorderReducer,
              disableReorderSourceSelect
              ?
              { ...initialReorderState, inputs: { ...initialReorderState.inputs, reorderSource: data['jcr:uuid'] } }
              :
              initialReorderState);

  // Reorder source is the entry to be moved, array used for autocomplete
  const [reorderSourceSelection, setReorderSourceSelection] = useState([]);

  useEffect(() => {
    if (!disableReorderSourceSelect) {
      const newReorderSource = !!reorderSourceSelection.length ? reorderSourceSelection[0] : '';
      reorderDispatch({ type: 'SET_REORDERSOURCE', payload: newReorderSource });
    }
  }, [reorderSourceSelection]);

  const reorderSource = reorderState.inputs.reorderSource;

  const sourceOptions = useMemo(() => {
    const nodes = treeContext.state.nodes;
    if (!!Object.keys(nodes).length) {
      const newSourceOptions = [];
      for (const id in nodes) {
        if (nodes.hasOwnProperty(id)) {
          const node = nodes[id];
          if (ENTRY_TYPES.includes(node.jcrPrimaryType)) {
            newSourceOptions.push(node);
          }
        }
      }
      return newSourceOptions;
    } else {
      return [];
    }
  }, [treeContext.state.nodes]);

  // New parent is the entry to move the reorder source to
  const [newParentSelection, setNewParentSelection] = useState([]);

  useEffect(() => {
    const newParent = !!newParentSelection.length ? newParentSelection[0] : '';
    reorderDispatch({ type: 'SET_NEWPARENT', payload: newParent });
  }, [newParentSelection]);

  const newParent = reorderState.inputs.newParent;

  // New position is the index of new parent's children to move the reorder source to
  const [newPositionSelection, setNewPositionSelection] = useState([]);

  useEffect(() => {
    const newPosition = !!newPositionSelection.length ? newPositionSelection[0] : '';
    reorderDispatch({ type: 'SET_NEWPOSITION', payload: newPosition });
  }, [newPositionSelection]);

  const newPosition = reorderState.inputs.newPosition;

  // Parent options for a given source node is any section or the root questionnaire
  const parentOptions = useMemo(() => {
    if (!!reorderSource && !!Object.keys(nodes).length) {
      const newParentOptions = [];
      for (const id in nodes) {
        if (nodes.hasOwnProperty(id)) {
          const node = nodes[id];
          // Only sections and questionnaires can be new parents
          if (['cards:Section', 'cards:Questionnaire'].includes(node.jcrPrimaryType)) {
            newParentOptions.push(node);
          }
        }
      }
      return newParentOptions;
    } else {
      return [];
    }
  }, [nodes, reorderSource]);

  const getParentOptionDisabled = useCallback((option) => {
    // Exclude current node (can't reassign node as parent to itself)
    // Exclude children of current node (can't reassign parent to child)
    const isReorderSource = reorderSource === option.value;
    const isDescendantOfReorderSource = isDescendant(nodes, reorderSource, option.value);
    const disabled = isReorderSource || isDescendantOfReorderSource;
    return disabled;
  }, [nodes, reorderSource]);

  const positionOptions = useMemo(() => {
    if (!!newParent && !!Object.keys(nodes).length) {
      const parent = nodes[newParent];
      const newPositions = parent.children.map(id => (nodes[id])).map((node, index) => {
        const { name, title, path, relativePath, jcrPrimaryType } = node;
        if (!ENTRY_TYPES.includes(jcrPrimaryType)) {
          return null;
        }
        return {
          value: index,
          name: name,
          text: title,
          path: path,
          relativePath: relativePath,
          type: stripCardsNamespace(jcrPrimaryType)
        }
      }).filter(node => node !== null);
      return newPositions;
    } else {
      return [];
    }
  }, [nodes, newParent]);

  useEffect(() => {
    if (Object.keys(nodes).length === 0) {
      // If no data or not open reset
      console.warn('No nodes in tree');
    } else if (!disableReorderSourceSelect) {
      // If reorderSource select is enabled, check if anything is selected
      if (!reorderSource.length) {
        // If reorderSource node is not selected (i.e. "")
        setNewParentSelection([]);
        setNewPositionSelection([]);
      } else {
        // On a reorderSource select, set original parents
        const originalParent = nodes[reorderSource].parent;
        const originalPosition = nodes[originalParent].children.indexOf(reorderSource);
        setNewParentSelection([originalParent]);
        setNewPositionSelection([originalPosition]);
      }
    } else {
      const originalParent = nodes[reorderSource].parent;
      const originalPosition = nodes[originalParent].children.indexOf(reorderSource);
      setNewParentSelection([originalParent]);
      setNewPositionSelection([originalPosition]);
    }
  }, [nodes, reorderSource]);

  useEffect(() => {
    setNewPositionSelection([]);
    // If newParent has empty children is empty of entry types (conditionals not included) then set positionRadio to first 
    const newParentChildrenPrimaryTypes = nodes[newParent]?.children.map(child => nodes[child].jcrPrimaryType);
    const newParentHasNoEntryChildren = newParentChildrenPrimaryTypes?.filter(primaryType => ENTRY_TYPES.includes(primaryType))?.length === 0;

    if (newParentHasNoEntryChildren) {
      reorderDispatch({ type: 'SET_POSITIONRADIO', payload: 'first' });
    }
  }, [newParent]);

  const SelectReorderSourceAutocomplete = (props) => {
    return (
      <>
        {!disableReorderSourceSelect &&
          <Grid size={3}>
            <Typography variant="subtitle2">Item to move</Typography>
          </Grid>
        }
        <Grid size={disableReorderSourceSelect ? 12 : 9}>
          { // If reorderSource is preselected, don't show the select
            !disableReorderSourceSelect &&
              <QuestionnaireAutocomplete
                showSelection={false}
                multiple={false}
                entities={sourceOptions.map((node) => {
                  const { value, name, title, path, relativePath, jcrPrimaryType } = node;
                  return {
                    value: value,
                    name: name,
                    text: title,
                    path: path,
                    relativePath: relativePath,
                    type: stripCardsNamespace(jcrPrimaryType)
                  }
                })}
                selection={reorderSourceSelection}
                onSelectionChanged={setReorderSourceSelection}
                getOptionValue={(option) => option.value}
                id="reorderSource"
                placeholderText="Select a questionnaire entry"
                disabled={disableReorderSourceSelect}
              />
          }
        </Grid>
      </>
    )
  }

  const ReorderForm = (props) => {
    return (
      <Grid container alignItems='baseline' direction="row" rowSpacing={3} columnSpacing={2}>
        {/* Will render to null if preselected */}
        <SelectReorderSourceAutocomplete />
        {!!reorderSource.length &&
          <>
            <Grid size={3}>
              <Typography variant="subtitle2">
                Move from
              </Typography>
            </Grid>
            <Grid size={9}>
              <QuestionnaireAutocomplete
                showSelection={false}
                multiple={false}
                entities={[nodes[nodes[reorderSource].parent]].map((node) => {
                  const { value, name, title, path, relativePath, jcrPrimaryType } = node;
                  return {
                    value: value,
                    name: name,
                    text: title,
                    path: path,
                    relativePath: relativePath,
                    type: stripCardsNamespace(jcrPrimaryType),
                  }
                })}
                getOptionDisabled={() => true}
                selection={[nodes[reorderSource].parent]}
                getOptionValue={(option) => option.value}
                id="originalParent"
                disabled={true}
              />
            </Grid>
            <Grid size={3}>
              <Typography variant="subtitle2">
                With original position
              </Typography>
            </Grid>
            <Grid size={9}>
              <Typography>
                {getOrdinalString(
                  nodes[nodes[reorderSource].parent].children.filter(nodeId => ENTRY_TYPES.includes(nodes[nodeId]?.jcrPrimaryType)).indexOf(reorderSource)
                )}
              </Typography>
            </Grid>
          </>
        }

        <Grid size={3}>
          <Typography variant="subtitle2">Move into</Typography>
        </Grid>
        <Grid size={9}>
          <QuestionnaireAutocomplete
            showSelection={false}
            multiple={false}
            entities={parentOptions.map((node) => {
              const { value, name, title, path, relativePath, jcrPrimaryType } = node;
              return {
                value: value,
                name: name,
                text: title,
                path: path,
                relativePath: relativePath,
                type: stripCardsNamespace(jcrPrimaryType)
              }
            })}
            getOptionDisabled={getParentOptionDisabled}
            selection={newParentSelection}
            onSelectionChanged={setNewParentSelection}
            getOptionValue={(option) => option.value}
            placeholderText="Select a new parent entry"
            id="newParent"
            disabled={!reorderSource}
          />
        </Grid>

        <Grid size={3}>
          <Typography variant="subtitle2">With new position</Typography>
        </Grid>
        <Grid size={9}>
          <RadioGroup
            row
            value={reorderState.inputs.positionRadio}
            onChange={(e) => reorderDispatch({ type: 'SET_POSITIONRADIO', payload: e.target.value })}
          >
            {(() => {
              const noNewParent = !newParent
              const newParentHasNoEntryChildren = !nodes[newParent]?.children.some(child => ENTRY_TYPES.includes(nodes[child].jcrPrimaryType))
              const filteredChildren = nodes[nodes[reorderSource]?.parent]?.children?.filter(nodeId => ENTRY_TYPES.includes(nodes[nodeId]?.jcrPrimaryType));
              const originalPositionIndex = filteredChildren?.indexOf(reorderSource);
              const originalPositionIsFirst = originalPositionIndex === 0;
              const originalPositionIsLast = originalPositionIndex === filteredChildren?.length - 1;
              return (
                [ { value: 'first', label: 'First' },
                  { value: 'other', label: 'After...' },
                  { value: 'last', label: 'Last' },
                ].map(({ value, label }) =>
                  <FormControlLabel
				    key={value}
                    value={value}
                    label={label}
                    disabled={[
                      noNewParent,
                      newParentHasNoEntryChildren,
                      (value === 'first' && originalPositionIsFirst),
                      (value === 'last' && originalPositionIsLast)
                    ].includes(true)}
                    control={<Radio />}
                  />
                )
              )
            })()}
          </RadioGroup>
          { reorderState.inputs.positionRadio === 'other' &&
            <QuestionnaireAutocomplete
              showSelection={false}
              multiple={false}
              entities={positionOptions}
              getOptionDisabled={(option) => option.path === nodes[reorderSource]?.path}
              selection={newPositionSelection}
              onSelectionChanged={setNewPositionSelection}
              placeholderText="... other questionnaire entry"
              getOptionValue={(option) => option.value}
              id="newPosition"
              disabled={!newParent}
            />
          }
        </Grid>
      </Grid>
    )
  }

  const emptyNodes = !Object.keys(nodes).length;
  const nodeNotInTree = disableReorderSourceSelect && !nodes.hasOwnProperty(reorderSource);
  // Wait until nodes is loaded into context
  if (emptyNodes) {
    console.warn('No nodes, not rendering MoveEntryModal');
    return null;
  }
  if (nodeNotInTree) {
    console.error('Node not in tree, not rendering MoveEntryModal', data);
    return null;
  }

  const isLoadingOrError = ['loading', 'error'].includes(reorderState.status);
  const noReorderSource = !reorderSource;
  const sameNewParent = !noReorderSource && nodes[reorderSource]?.parent === newParent;
  const noNewTarget = sameNewParent && newPosition === nodes[newParent]?.children.indexOf(reorderSource);
  const invalidPosition = newPosition === null || newPosition === "";
  const disableSubmit = isLoadingOrError || noReorderSource || noNewTarget || invalidPosition;

  const handleError = (e) => {
    console.warn('Reorder error', e);
    reorderDispatch({ type: 'SET_ERROR', payload: e });
  };
  const handleSuccess = (data) => {
    treeContext.actions.refreshTree();
    reorderDispatch({ type: 'SET_SUCCESS', payload: data });
  };
  const handleClose = () => {
    reorderDispatch({ type: 'SET_IDLE' });
    onClose();
  };
  const handleSubmit = (e) => {
    reorderDispatch({ type: 'SET_LOADING' });
    if (!reorderSource || !newParent || invalidPosition) {
      throw new Error('Missing required fields for entry reorder');
    }
    e.preventDefault();
    treeContext.actions.reorderNode(reorderSource, newParent, newPosition)
      .then(handleSuccess)
      .catch(handleError)
      .finally(handleClose);
  };

  return (
    <>
      <DialogContent>
        <ReorderForm />
        {reorderState.status === 'error' &&
          <Alert severity='error'>
            {reorderState.error}
          </Alert>
        }
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant='contained'
          onClick={handleSubmit}
          disabled={disableSubmit}
        >
          Move
        </Button>
      </DialogActions>
    </>
  )
}
