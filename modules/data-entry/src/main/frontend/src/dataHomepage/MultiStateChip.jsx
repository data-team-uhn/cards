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
import React, { useState, useMemo } from "react";

import CancelIcon from '@mui/icons-material/Cancel';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import { Chip, Tooltip } from "@mui/material";
import PropTypes from "prop-types";

export class ChipState {
  constructor(label, variant, color, icon, value, tooltip) {
    this.label = label;
    this.variant = variant;
    this.color = color;
    this.icon = icon;
    this.value = value;
    this.tooltip = tooltip;
  }
}

/**
 * A 3 state chip supporting the following 3 values and states, each with a configurable tooltip.
 * Expects a single label and size that will apply to all states.
 * 0: A default, outlined, empty circle icon
 * 1: A success color with a checkmark icon
 * -1: An error color with an x icon
 */
export function TriStateChip(props) {
  const { size, label, defaultTooltip, positiveTooltip, negativeTooltip, onChange } = props;
  let states = [
    new ChipState(label, "outlined", "primary", <RadioButtonUncheckedIcon/>, 0, defaultTooltip),
    new ChipState(label, "outlined", "success", <CheckCircleIcon/>, 1, positiveTooltip),
    new ChipState(label, "outlined", "error",  <CancelIcon/>, -1, negativeTooltip),
  ]

  return MultiStateChip({
    "size": size,
    "states": states,
    "onChange": onChange,
  });
}

function MultiStateChip(props) {
  const { size, states, onChange } = props;

  const [ currentStateIndex, setCurrentState ] = useState(0);

  useMemo(() => {
    if (currentStateIndex > states?.length && currentStateIndex > 0) {
      setCurrentState(0);
    }
  }, [states])

  return <Tooltip title={states[currentStateIndex]?.tooltip}>
      <Chip
        size={size}
        label={states[currentStateIndex]?.label}
        variant={states[currentStateIndex]?.variant}
        color={states[currentStateIndex]?.color}
        icon={states[currentStateIndex]?.icon}
        onClick={() => {
          let newIndex = (currentStateIndex + 1 >= states?.length) ? 0 : (currentStateIndex + 1);
          setCurrentState(newIndex);
          onChange(states[newIndex].value);
        }}
      />
    </Tooltip>
}

MultiStateChip.propTypes = {
  key: PropTypes.string,
  size: PropTypes.oneOf(["small", "medium", "large"]),
  states: PropTypes.arrayOf(PropTypes.shape({
    label: PropTypes.string,
    variant: PropTypes.oneOf(["filled", "outlined"]),
    color: PropTypes.string,
    icon: PropTypes.node,
    value: PropTypes.string,
  }))
}

export default MultiStateChip;
