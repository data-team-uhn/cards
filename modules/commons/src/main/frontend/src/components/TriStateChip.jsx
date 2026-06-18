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
import { useEffect, useState } from 'react';

import CancelIcon from '@mui/icons-material/Cancel';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import PropTypes from "prop-types";

import MultiStateChip, { ChipState, ChipProps } from './MultiStateChip';
import { checkPropTypes } from "../propTypes";


/**
 * A 3 state chip supporting the following 3 values and states, each with a configurable tooltip.
 * Expects a single label and size that will apply to all states.
 * 0: A default, outlined, empty circle icon
 * 1: A success color with a checkmark icon
 * -1: An error color with an x icon
 */
function TriStateChip(props) {
  checkPropTypes(TriStateChip, props);
  const {
    size,
    label,
    defaultTooltip = "Clear",
    positiveTooltip = "Include",
    negativeTooltip = "Exclude",
    onSetPositive,
    onSetNegative,
    onClear,
    initialState
  } = props;

  const [ states, setStates ] = useState([]);

  useEffect(() => {
    setStates ([
      new ChipState(new ChipProps(label, "outlined", "", <RadioButtonUncheckedIcon color="disabled"/>), 0, defaultTooltip),
      new ChipState(new ChipProps(label, "filled", "success", <CheckCircleIcon/>), 1, positiveTooltip),
      new ChipState(new ChipProps(label, "filled", "error",  <CancelIcon/>), -1, negativeTooltip)
    ])
  }, [label, defaultTooltip, positiveTooltip, negativeTooltip]);

  let onChange = (newState) => {
    if (newState == 0) {
      onClear?.();
    }
    else if (newState == 1) {
      onSetPositive?.();
    }
    else {
      onSetNegative?.();
    }
  }

  return MultiStateChip({
    "size": size,
    "states": states,
    "onChange": onChange,
    "initialState": initialState == -1 ? 2 : initialState,
  });
}

TriStateChip.propTypes = {
  size: PropTypes.oneOf(["small", "medium"]),
  label: PropTypes.string.isRequired,
  defaultTooltip: PropTypes.string,
  positiveTooltip: PropTypes.string,
  negativeTooltip: PropTypes.string,
  onSetPositive: PropTypes.func,
  onSetNegative: PropTypes.func,
  onClear: PropTypes.func,
  initialState: PropTypes.number,
}

export default TriStateChip;
