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

import React from 'react';
import PropTypes from 'prop-types';

const sizesMap = {
  'small': { size: 17, extraSize: 10 },
  'medium': { size: 25, extraSize: 13 },
  'large': { size: 25, extraSize: 15 },
}

const ComposedIcon = ({
  MainIcon,
  ExtraIcon,
  size = 'small',
  color = 'inherit',
  position = 'bottom-end',
  disabled,
}) => (
  <div style={{
    position: 'relative',
    cursor: 'default'
  }}>
    <div style={{ lineHeight: '0px', cursor: 'pointer' }}>
      <MainIcon
        style={{
          fontSize: sizesMap[size].size,
        }} />
    </div>
    <div
      style={{
        lineHeight: '0px',
        cursor: 'pointer',
        position: 'absolute',
        textShadow: '0.75px 0px 0.5px #FFF, 0px 0.75px 0.5px #FFF, -0.75px 0px 0.5px #FFF, 0px -0.75px 0.5px #FFF',
        bottom: position.includes('bottom') ? '-4px' : null,
        top: position.includes('top') ? '-4px' : null,
        left: position.includes('start') ? '-4px' : null,
        right: position.includes('end') ? '-4px' : null,
      }}>
      <ExtraIcon
        color={color}
        style={{
          fontSize: sizesMap[size].extraSize,
          color: disabled ? 'rgba(0, 0, 0, 0.35)' : null,
        }}
      />
    </div>
  </div>
)

ComposedIcon.propTypes = {
  MainIcon: PropTypes.elementType.isRequired,
  ExtraIcon: PropTypes.elementType.isRequired,
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  color: PropTypes.string,
  position: PropTypes.oneOf(['top-start', 'top-end', 'bottom-start', 'bottom-end']),
  disabled: PropTypes.bool,
};

export default ComposedIcon;