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

import React, { useEffect } from "react";

import { monitorForElements } from '@atlaskit/pragmatic-drag-and-drop/element/adapter';
import { triggerPostMoveFlash } from '@atlaskit/pragmatic-drag-and-drop-flourish/trigger-post-move-flash';
import { extractClosestEdge } from '@atlaskit/pragmatic-drag-and-drop-hitbox/closest-edge';
import { reorderWithEdge } from '@atlaskit/pragmatic-drag-and-drop-hitbox/util/reorder-with-edge';
import PropTypes from "prop-types";
import { flushSync } from 'react-dom';

import { checkPropTypes } from "../propTypes";
import DroppableAnswerOption, { isOptionData } from './DroppableAnswerOption.jsx';


function DroppableAnswerOptionList(props) {
  checkPropTypes(DroppableAnswerOptionList, props);
  const { classes, options, setOptions, deleteOption, generateDescriptionIcon } = props;

  useEffect(() => {
    return monitorForElements({
        canMonitor({ source }) {
            return isOptionData(source.data);
        },
        onDrop({ location, source }) {
            const target = location.current.dropTargets[0];
            if (!target) {
                return;
            }

            const sourceData = source.data;
            const targetData = target.data;
            if (!isOptionData(sourceData) || !isOptionData(targetData)) {
                return;
            }

            const indexOfSource = options.findIndex((option) => option.value === sourceData.valueId);
            const indexOfTarget = options.findIndex((option) => option.value === targetData.valueId);
            if (indexOfTarget < 0 || indexOfSource < 0) {
                return;
            }

            const closestEdgeOfTarget = extractClosestEdge(targetData);
            // Using `flushSync` so we can query the DOM straight after this line
            flushSync(() => {
                let reorderedList = reorderWithEdge({
                    list: options,
                    startIndex: indexOfSource,
                    indexOfTarget,
                    closestEdgeOfTarget,
                    axis: 'vertical',
                });
                setOptions(reorderedList);
            });
            // Being simple and just querying for the task after the drop.
            // We could use react context to register the element in a lookup,
            // and then we could retrieve that element after the drop and use
            // `triggerPostMoveFlash`. But this gets the job done.
            const element = document.querySelector(`[data-option-id="${sourceData.valueId}"]`);
            if (element instanceof HTMLElement) {
                triggerPostMoveFlash(element);
            }
        },
    });
  }, [options]);

  return (
    <React.Fragment>
      {options && options.map((value, index) =>
          <DroppableAnswerOption
            key={value.value}
            value={value}
            index={index}
            deleteOption={deleteOption}
            generateDescriptionIcon={generateDescriptionIcon}
            classes={classes}
          />
        )}
    </React.Fragment>
  );
}

DroppableAnswerOptionList.propTypes = {
  options: PropTypes.array.isRequired,
  deleteOption: PropTypes.func.isRequired,
  generateDescriptionIcon: PropTypes.func.isRequired,
};

export default DroppableAnswerOptionList;
