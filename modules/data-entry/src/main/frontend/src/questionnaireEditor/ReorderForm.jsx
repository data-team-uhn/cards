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

import { useQuestionnaireTreeContext } from './QuestionnaireTreeContext';
import {
  getMoveValidity,
  isNoOpMove,
  resolveTargetIndex,
  MOVE_INVALID,
} from './reorderModel';
import { getEntryChildIds } from './treeQueries';
import { ENTRY_TYPES } from '../questionnaire/FormEntry';
import QuestionnaireAutocomplete from '../questionnaire/QuestionnaireAutocomplete';
import { getOrdinalString, stripCardsNamespace } from '../questionnaire/QuestionnaireUtilities';

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
      // 'last' is sent verbatim as Sling's :order keyword
      const newPosition = positionRadio === 'first' ? 0 : positionRadio === 'last' ? 'last' : null;
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
    // Exclude the source itself and any of its descendants — neither can be its parent.
    // Name collisions are intentionally not pre-checked here; they surface on submit, as before.
    const { code } = getMoveValidity(nodes, reorderSource, option.value);
    return code === MOVE_INVALID.SELF || code === MOVE_INVALID.DESCENDANT;
  }, [nodes, reorderSource]);

  const positionOptions = useMemo(() => {
    if (!newParent) return [];
    return getEntryChildIds(nodes, newParent).map((id, index) => {
      const node = nodes[id];
      return ({
        value: index,
        id: id,
        name: node.name,
        text: node.title,
        path: node.path,
        relativePath: node.relativePath,
        type: stripCardsNamespace(node.jcrPrimaryType)
      });
    });
  }, [nodes, newParent]);

  // Disable any "After..." reference whose slot wouldn't move the item: the item itself, and
  // (within the same parent) its current predecessor — placing it after either leaves it put.
  const getPositionOptionDisabled = useCallback((option) =>
    isNoOpMove(nodes, reorderSource, newParent,
      resolveTargetIndex(nodes, reorderSource, newParent, { type: 'after', refId: option.id })),
  [nodes, reorderSource, newParent]);

  // A different target parent must not already contain a child with the same node name (JCR
  // siblings need unique names). Detected up front so the radios and Move stay disabled and we can
  // warn inline, rather than letting reorderNode throw on submit.
  const nameCollision = !!nodes[reorderSource] && !!nodes[newParent]
    && getMoveValidity(nodes, reorderSource, newParent).code === MOVE_INVALID.NAME_COLLISION;

  // Which position radios are selectable, computed once and shared by the radio render and the
  // effect that clears a selection once it stops being valid. A radio is disabled when there is
  // no new parent, a name collision blocks the whole move, or picking it wouldn't move the source:
  //  - First/Last: the source already sits in that slot (no-op).
  //  - After...: every offered reference position is a no-op (e.g. moving the last of two
  //    children — "after the first" is its current spot, "after itself" is excluded).
  // An empty target parent has only a single slot, so only First stays enabled there.
  const positionRadioDisabled = useMemo(() => {
    const canEvaluate = !!nodes[reorderSource] && !!newParent && !!nodes[newParent];
    const noNewParent = !newParent;
    const newParentHasNoEntryChildren = canEvaluate && getEntryChildIds(nodes, newParent).length === 0;
    const isNoOp = (slot) =>
      isNoOpMove(nodes, reorderSource, newParent, resolveTargetIndex(nodes, reorderSource, newParent, slot));
    const firstIsNoOp = canEvaluate && isNoOp({ type: 'first' });
    const lastIsNoOp = canEvaluate && isNoOp({ type: 'last' });
    const afterHasViableTarget = canEvaluate
      && getEntryChildIds(nodes, newParent).some(refId => !isNoOp({ type: 'after', refId }));
    return {
      first: noNewParent || nameCollision || firstIsNoOp,
      other: noNewParent || nameCollision || newParentHasNoEntryChildren || !afterHasViableTarget,
      last: noNewParent || nameCollision || newParentHasNoEntryChildren || lastIsNoOp,
    };
  }, [nodes, reorderSource, newParent, nameCollision]);


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
        const originalPosition = getEntryChildIds(nodes, originalParent).indexOf(reorderSource);
        setNewParentSelection([originalParent]);
        setNewPositionSelection([originalPosition]);
      }
    } else {
      const originalParent = nodes[reorderSource].parent;
      const originalPosition = getEntryChildIds(nodes, originalParent).indexOf(reorderSource);
      setNewParentSelection([originalParent]);
      setNewPositionSelection([originalPosition]);
    }
  }, [nodes, reorderSource]);

  useEffect(() => {
    setNewPositionSelection([]);
    if (!newParent) return;
    // If the target parent has no entry-type children (conditionals excluded), First is the only
    // slot, so default to it.
    const newParentHasNoEntryChildren = getEntryChildIds(nodes, newParent).length === 0;
    if (newParentHasNoEntryChildren) {
      reorderDispatch({ type: 'SET_POSITIONRADIO', payload: 'first' });
      return;
    }
    // Otherwise, if the current choice is no longer selectable (e.g. "After..." lost all viable
    // targets after a parent change), drop it so a stale, unusable selection and its dropdown
    // can't linger — falling back to First when that one is still valid.
    const current = reorderState.inputs.positionRadio;
    if (current && positionRadioDisabled[current]) {
      reorderDispatch({ type: 'SET_POSITIONRADIO', payload: positionRadioDisabled.first ? '' : 'first' });
    }
  }, [newParent, nodes, positionRadioDisabled, reorderState.inputs.positionRadio]);

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
                getEntryChildIds(nodes, nodes[reorderSource].parent).indexOf(reorderSource)
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
        {nameCollision &&
          <Typography variant="caption" color="error" sx={{ display: 'block', mt: 0.5 }}>
            This destination already contains an item with the same name. Choose a different destination.
          </Typography>
        }
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
          {[
            { value: 'first', label: 'First' },
            { value: 'other', label: 'After...' },
            { value: 'last', label: 'Last' },
          ].map(({ value, label }) =>
            <FormControlLabel
              key={value}
              value={value}
              label={label}
              disabled={positionRadioDisabled[value]}
              control={<Radio />}
            />
          )}
        </RadioGroup>
        { reorderState.inputs.positionRadio === 'other' &&
          <QuestionnaireAutocomplete
            showSelection={false}
            multiple={false}
            entities={positionOptions}
            getOptionDisabled={getPositionOptionDisabled}
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
  const invalidPosition = newPosition === null || newPosition === "";

  // Resolve the Sling :order value to send, from the selected radio:
  //  - First/Last become the 0 / 'last' slot; resolveTargetIndex returns 'last' verbatim.
  //  - 'other' (After...) places the source after the entry child at the selected index.
  // resolveTargetIndex handles the same-parent shift (removing the source moves later siblings up).
  const computeSlingPosition = () => {
    if (!nodes[reorderSource] || !newParent) return null;
    const radio = reorderState.inputs.positionRadio;
    if (radio === 'first') return resolveTargetIndex(nodes, reorderSource, newParent, { type: 'first' });
    if (radio === 'last') return resolveTargetIndex(nodes, reorderSource, newParent, { type: 'last' });
    const referenceNodeId = getEntryChildIds(nodes, newParent)[newPosition];
    if (!referenceNodeId) return null;
    return resolveTargetIndex(nodes, reorderSource, newParent, { type: 'after', refId: referenceNodeId });
  };
  const slingPosition = (!invalidPosition && newParent) ? computeSlingPosition() : null;

  const noNewTarget = slingPosition !== null && isNoOpMove(nodes, reorderSource, newParent, slingPosition);
  const disableSubmit = isLoadingOrError || noReorderSource || noNewTarget || invalidPosition || nameCollision;

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
    if (disableSubmit) {
      return;
    }
    reorderDispatch({ type: 'SET_LOADING' });
    // reorderNode validates synchronously and can throw before it returns a promise, so a
    // try/catch is needed alongside .catch() for the async (server) failure path.
    try {
      treeContext.actions.reorderNode(reorderSource, newParent, slingPosition)
        .then(handleSuccess)
        .catch(handleError);
    } catch (err) {
      handleError(err);
    }
  };

  return (
    <>
      <DialogContent>
        {reorderFormContent}
        {reorderState.status === 'error' &&
          <Alert severity='error'>
            {reorderState.error?.message || String(reorderState.error)}
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
