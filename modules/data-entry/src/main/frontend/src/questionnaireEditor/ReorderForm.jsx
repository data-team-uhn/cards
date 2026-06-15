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

import { useEffect, useMemo, useCallback, useReducer, useState } from 'react';

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
import { ENTRY_TYPES } from '../questionnaire/FormEntry';
import QuestionnaireAutocomplete from '../questionnaire/QuestionnaireAutocomplete';
import { stripCardsNamespace } from '../questionnaire/QuestionnaireUtilities';

// State for reorder submission
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
      return { ...state, inputs: { ...state.inputs, reorderSource: action.payload } };
    case 'SET_NEWPARENT':
      return { ...state, inputs: { ...state.inputs, newParent: action.payload } };
    case 'SET_POSITIONRADIO': {
      const positionRadio = action.payload;
      const newPosition = positionRadio === 'first' ? 0 : positionRadio === 'last' ? -1 : null;
      return {
        ...state,
        inputs: {
          ...state.inputs,
          positionRadio,
          newPosition
        }
      };
    }
    case 'SET_NEWPOSITION':
      return { ...state, inputs: { ...state.inputs, newPosition: action.payload } };
    default:
      return state;
  }
};


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
    return Object.values(nodes)
      .filter(node => ENTRY_TYPES.includes(node.jcrPrimaryType));
  }, [nodes]);


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
    // Only the "After..." (other) mode takes its position from this autocomplete; First/Last
    // set newPosition directly via the radio. Without this guard, clearing the selection (e.g.
    // on a parent change) would blank newPosition even for First/Last and keep Move disabled.
    if (reorderState.inputs.positionRadio !== 'other') return;
    const newPosition = newPositionSelection.length ? newPositionSelection[0] : '';
    reorderDispatch({ type: 'SET_NEWPOSITION', payload: newPosition });
  }, [newPositionSelection, reorderState.inputs.positionRadio]);

  const newPosition = reorderState.inputs.newPosition;

  // Parent options for a given source node is any section or the root questionnaire
  const parentOptions = useMemo(() => {
    if (!reorderSource) return [];
    return Object.values(nodes)
      .filter(node => ['cards:Section', 'cards:Questionnaire'].includes(node.jcrPrimaryType));
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
    if (!newParent) return [];
    const parent = nodes[newParent];
    return parent.children
      .map(id => nodes[id])
      .filter(node => ENTRY_TYPES.includes(node.jcrPrimaryType))
      .map((node, index) => ({
        value: index,
        name: node.name,
        text: node.title,
        path: node.path,
        relativePath: node.relativePath,
        type: stripCardsNamespace(node.jcrPrimaryType)
      }));
  }, [nodes, newParent]);


  useEffect(() => {
    if (Object.keys(nodes).length === 0) {
      // Nothing to do until nodes load into context
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
    // If newParent has empty children is empty of entry types (conditionals not included)
    // then set positionRadio to first
    const newParentChildrenPrimaryTypes =
      nodes[newParent]?.children.map(child => nodes[child].jcrPrimaryType);
    const newParentHasNoEntryChildren =
      newParentChildrenPrimaryTypes?.filter(primaryType => ENTRY_TYPES.includes(primaryType))?.length === 0;

    if (newParentHasNoEntryChildren) {
      reorderDispatch({ type: 'SET_POSITIONRADIO', payload: 'first' });
    }
  }, [newParent]);

  const selectReorderSourceContent = (
    <>
      {!disableReorderSourceSelect &&
        <Grid size={3}>
          <Typography variant="subtitle2">Item to move</Typography>
        </Grid>
      }
      <Grid size={disableReorderSourceSelect ? 12 : 9}>
        {!disableReorderSourceSelect &&
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
              };
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
  );

  const reorderFormContent = (
    <Grid container alignItems='baseline' direction="row" rowSpacing={3} columnSpacing={2}>
      {selectReorderSourceContent}
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
              disabled
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
                nodes[nodes[reorderSource].parent].children
                  .filter(nodeId => ENTRY_TYPES.includes(nodes[nodeId]?.jcrPrimaryType))
                  .indexOf(reorderSource)
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
            const newParentHasNoEntryChildren =
              !nodes[newParent]?.children.some(child => ENTRY_TYPES.includes(nodes[child].jcrPrimaryType))
            // First/Last are no-ops only when staying in the same parent; moving into a
            // different parent, First/Last are always valid (different) destinations.
            const isSameParent = nodes[reorderSource]?.parent === newParent;
            const filteredChildren = nodes[nodes[reorderSource]?.parent]?.children?.filter(
              nodeId => ENTRY_TYPES.includes(nodes[nodeId]?.jcrPrimaryType));
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
                    // An empty target parent has a single slot, so keep First (which the
                    // effect auto-selects) enabled and disable After.../Last.
                    (newParentHasNoEntryChildren && value !== 'first'),
                    (value === 'first' && isSameParent && originalPositionIsFirst),
                    (value === 'last' && isSameParent && originalPositionIsLast)
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
  );

  const emptyNodes = !Object.keys(nodes).length;
  const nodeNotInTree = disableReorderSourceSelect && !Object.prototype.hasOwnProperty.call(nodes, reorderSource);
  // Wait until nodes is loaded into context
  if (emptyNodes) {
    return null;
  }
  if (nodeNotInTree) {
    return null;
  }

  const isLoadingOrError = ['loading', 'error'].includes(reorderState.status);
  const noReorderSource = !reorderSource;
  const sameNewParent = !noReorderSource && nodes[reorderSource]?.parent === newParent;
  const invalidPosition = newPosition === null || newPosition === "";

  // Compute the actual Sling :order value to send.
  // For 'other' (After...), newPosition is a 0-based filtered-children index of the reference node.
  // We need position = refAllIndex + 1 (place after reference), adjusted when the source sits
  // before that slot in the same parent (removing source shifts subsequent indices down by 1).
  const computeSlingPosition = () => {
    if (reorderState.inputs.positionRadio !== 'other') return newPosition;
    const allChildren = nodes[newParent].children;
    const filteredChildren = allChildren.filter(id => ENTRY_TYPES.includes(nodes[id]?.jcrPrimaryType));
    const referenceNodeId = filteredChildren[newPosition];
    if (!referenceNodeId) return null;
    const refAllIndex = allChildren.indexOf(referenceNodeId);
    let slingPos = refAllIndex + 1;
    if (nodes[reorderSource]?.parent === newParent) {
      const sourceAllIndex = allChildren.indexOf(reorderSource);
      if (sourceAllIndex < slingPos) {
        slingPos -= 1;
      }
    }
    return slingPos;
  };
  const slingPosition = (!invalidPosition && newParent) ? computeSlingPosition() : null;

  const noNewTarget = sameNewParent && slingPosition !== null
    && slingPosition === nodes[newParent]?.children.indexOf(reorderSource);
  const disableSubmit = isLoadingOrError || noReorderSource || noNewTarget || invalidPosition;

  const handleError = (e) => {
    console.warn('Reorder error', e);
    reorderDispatch({ type: 'SET_ERROR', payload: e });
  };
  const handleSuccess = (data) => {
    // Reload in place (fetchRootData, not refreshTree) so the questionnaire doesn't flash
    // blank. Pass the moved entry's new path (target parent's path + its own name) so its
    // card highlights and scrolls into view after the reload, the way create/edit do.
    const movedPath = `${nodes[newParent].path}/${nodes[reorderSource].name}`;
    treeContext.actions.fetchRootData(movedPath);
    reorderDispatch({ type: 'SET_SUCCESS', payload: data });
    onClose();
  };
  const handleSubmit = (e) => {
    e.preventDefault();
    if (!reorderSource || !newParent || invalidPosition) {
      return;
    }
    reorderDispatch({ type: 'SET_LOADING' });
    treeContext.actions.reorderNode(reorderSource, newParent, slingPosition)
      .then(handleSuccess)
      .catch(handleError);
  };

  return (
    <>
      <DialogContent>
        {reorderFormContent}
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
  );
}
