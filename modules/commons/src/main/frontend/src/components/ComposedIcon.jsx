//
//MIT License

//Copyright (c) 2020 rand0mC0d3r

//Permission is hereby granted, free of charge, to any person obtaining a copy
//of this software and associated documentation files (the "Software"), to deal
//in the Software without restriction, including without limitation the rights
//to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//copies of the Software, and to permit persons to whom the Software is
//furnished to do so, subject to the following conditions:
//
//The above copyright notice and this permission notice shall be included in all
//copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
//SOFTWARE.
//
// Inspired by https://github.com/rand0mC0d3r/material-ui-mix-icon/blob/master/src/components/ComposedIcon/ComposedIcon.js

import React from 'react';
import PropTypes from 'prop-types';
import { useTheme } from '@mui/material/styles';

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
  theme = useTheme(),
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
        textShadow: `0.75px 0px 0.5px ${theme.palette.background.default}, 0px 0.75px 0.5px ${theme.palette.background.default}, -0.75px 0px 0.5px ${theme.palette.background.default}, 0px -0.75px 0.5px ${theme.palette.background.default}`,
        bottom: position.includes('bottom') ? '-4px' : null,
        top: position.includes('top') ? '-4px' : null,
        left: position.includes('start') ? '-4px' : null,
        right: position.includes('end') ? '-4px' : null,
      }}>
      { ExtraIcon && <ExtraIcon
        color={color}
        style={{
          fontSize: sizesMap[size].extraSize,
          color: disabled ? theme.palette.text.disabled : null,
        }}
      /> }
    </div>
  </div>
)

ComposedIcon.propTypes = {
  MainIcon: PropTypes.elementType.isRequired,
  ExtraIcon: PropTypes.elementType,
  size: PropTypes.oneOf(['small', 'medium', 'large']),
  color: PropTypes.string,
  position: PropTypes.oneOf(['top-start', 'top-end', 'bottom-start', 'bottom-end']),
  disabled: PropTypes.bool,
};

export default ComposedIcon;