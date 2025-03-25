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

import React, { useEffect, useRef, useState } from "react";
import { createPortal } from 'react-dom';
import PropTypes from "prop-types";
import {
  Checkbox,
  Grid,
  IconButton,
  TextField,
  Tooltip,
} from "@mui/material";
import CloseIcon from '@mui/icons-material/Close';
import DragIndicatorIcon from '@mui/icons-material/DragIndicator';

import { draggable, dropTargetForElements, } from '@atlaskit/pragmatic-drag-and-drop/element/adapter';
import { setCustomNativeDragPreview } from '@atlaskit/pragmatic-drag-and-drop/element/set-custom-native-drag-preview';
import { pointerOutsideOfPreview } from '@atlaskit/pragmatic-drag-and-drop/element/pointer-outside-of-preview';
import { combine } from '@atlaskit/pragmatic-drag-and-drop/combine';
import { attachClosestEdge, extractClosestEdge, } from '@atlaskit/pragmatic-drag-and-drop-hitbox/closest-edge';
import { DropIndicator } from "@atlaskit/pragmatic-drag-and-drop-react-drop-indicator/box";
import invariant from 'tiny-invariant';


const valueKey = Symbol("value"); // creates a unique property keys

function getOptionData(value) {
  return { [valueKey]: true, valueId: value.value };
}

export function isOptionData(data) {
  return data[valueKey] === true;
}

function DroppableAnswerOption(props) {
  const { classes, value, index, deleteOption, generateDescriptionIcon } = props;

  const ref = useRef(null);

  /* Object to maintain the draggable state
    type DraggableState =
      | { type: "idle" }
      | { type: "preview"; container: HTMLElement }
      | { type: "dragging" }
      | { type: 'dragging-over'; closestEdge: Edge | null }
  */
  const [draggableState, setDraggableState] = useState({ type: 'idle' });

  useEffect(() => {
    const element = ref.current;
    invariant(element);

    const data = getOptionData(value);

    return combine(
      draggable({
        element,
        getInitialData() {
            return data;
        },
        onGenerateDragPreview({ nativeSetDragImage }) {
            setCustomNativeDragPreview({
                nativeSetDragImage,
                getOffset: pointerOutsideOfPreview({
                    x: '4px',
                    y: '4px',
                }),
                render({ container }) {
                    setDraggableState({ type: 'preview', container });
                },
            });
        },
        onDragStart() {
            setDraggableState({ type: "dragging" });
        },
        onDrop() {
            setDraggableState({ type: 'idle' });
        },
      }),
      dropTargetForElements({
        element,
        canDrop({ source }) {
            // not allowing dropping on yourself
            if (source.element === element) {
                return false;
            }
            // only allowing options to be dropped on me
            return isOptionData(source.data);
        },
        getData({ input }) {
            return attachClosestEdge(data, {
                element,
                input,
                allowedEdges: ['top', 'bottom'],
            });
        },
        getIsSticky() {
            return true;
        },
        onDragEnter({ self }) {
            const closestEdge = extractClosestEdge(self.data);
            setDraggableState({ type: 'dragging-over', closestEdge });
        },
        onDrag({ self }) {
            const closestEdge = extractClosestEdge(self.data);
            // Only need to update react state if nothing has changed.
            // Prevents re-rendering.
            setDraggableState((current) => {
               if (current.type === 'dragging-over' && current.closestEdge === closestEdge) {
                  return current;
               }
               return { type: 'dragging-over', closestEdge };
            });
        },
        onDragLeave() {
            setDraggableState({ type: 'idle' });
        },
        onDrop() {
            setDraggableState({ type: 'idle' });
        },
    }));
  }, [value]);

  let generateOption = (isPerview) => {
    return (
      <Grid container
          data-option-id={value.value}
          direction="row"
          justifyContent="space-between"
          alignItems="stretch"
          className={classes.answerOption + ' ' + (!isPerview && draggableState.type === "dragging" ? classes.optionDisabled : "")}
          ref={ref}
        >
          <Grid item xs={1}>
            <Tooltip title={!isPerview ? "Drag to reorder" : ""}>
              <IconButton className={classes.optionsDragIndicator}>
                <DragIndicatorIcon />
              </IconButton>
            </Tooltip>
          </Grid>
          <Grid item xs={8}>
            {!isPerview && <span>
            <input type='hidden' name={`${value['@path']}/jcr:primaryType`} value='cards:AnswerOption' />
            <input type='hidden' name={`${value['@path']}/label`} value={value.label} />
            <input type='hidden' name={`${value['@path']}/value`} value={value.value} />
            <input type='hidden' name={`${value['@path']}/defaultOrder`} value={index+1} />
            <input type="hidden" name={`${value['@path']}/description`} value={value.description || ''} />
            <input type="hidden" name={`${value['@path']}/isDefault`} value={value.isDefault || false} />
            <input type="hidden" name={`${value['@path']}/isDefault@TypeHint`} value="Boolean" />
            </span>}
            <Tooltip title="Selected by default">
              <Checkbox
                color="secondary"
                checked={value.isDefault}
                onChange={(event) => {
                  setOptions(old => {
                    var _new = old.slice();
                    _new[index].isDefault = !!(event?.target?.checked);
                    return _new;
                  });
                }}/>
            </Tooltip>
            <TextField
              variant="standard"
              InputProps={{
                readOnly: true,
              }}
              className={classes.answerOptionReadonly}
              defaultValue={value.label? value.value + " = " + value.label : value.value}
              multiline
            />
          </Grid>
          <Grid item xs={3} className={classes.answerOptionActions}>
            {generateDescriptionIcon(value, index, false)}
            <Tooltip title="Delete option">
              <IconButton onClick={() => { deleteOption(index); }} className={classes.answerOptionButton}>
                <CloseIcon/>
              </IconButton>
            </Tooltip>
          </Grid>
        </Grid>
	)
  }

  return (
    <React.Fragment>
      <div className={classes.optionsList}>
        {generateOption(false)}
        {draggableState?.type === 'dragging-over' && draggableState?.closestEdge &&
          (<DropIndicator edge={draggableState.closestEdge} gap='8px'/>)}
      </div>
      { draggableState.type === "preview" &&
        createPortal(
           generateOption(true),
          draggableState.container
        )}
    </React.Fragment>
  )
}

DroppableAnswerOption.propTypes = {
  value: PropTypes.object.isRequired,
  index: PropTypes.number.isRequired,
  deleteOption: PropTypes.func.isRequired,
  generateDescriptionIcon: PropTypes.func.isRequired,
};

export default DroppableAnswerOption;
