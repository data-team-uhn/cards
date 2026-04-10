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
import { useState, useMemo } from "react";

import { Chip, Tooltip } from "@mui/material";
import PropTypes from "prop-types";

export class ChipState {
  constructor( chipProps, value, tooltip) {
    this.chipProps = chipProps;
    this.value = value;
    this.tooltip = tooltip;
  }
}

export class ChipProps {
  constructor (label, variant, color, icon) {
    this.label = label;
    this.variant = variant;
    this.color = color;
    this.icon = icon;
  }
}

function MultiStateChip(props) {
  const { size, states, onChange } = props;

  const [ currentStateIndex, setCurrentState ] = useState(0);

  useMemo(() => {
    if (currentStateIndex >= states?.length) {
      setCurrentState(0);
    }
  }, [states])

  return states?.length > 0 &&
    <Tooltip title={states[currentStateIndex].tooltip}>
      <Chip
        size={size}
        {...states[currentStateIndex].chipProps ?? {}}
        onClick={() => {
          let newIndex = (currentStateIndex + 1) % states.length;
          setCurrentState(newIndex);
          onChange(states[newIndex].value);
        }}
      />
    </Tooltip>
}

MultiStateChip.propTypes = {
  key: PropTypes.string,
  size: PropTypes.oneOf(["small", "medium"]),
  states: PropTypes.arrayOf(PropTypes.instanceOf(ChipState)).isRequired
}

export default MultiStateChip;
