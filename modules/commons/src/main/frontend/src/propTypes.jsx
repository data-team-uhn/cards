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

import PropTypes from 'prop-types';

/**
 * A wrapper around PropTypes.checkPropTypes that provides default values for the common parameters.
 * This simplifies prop type checking by only requiring the component and props to be passed.
 *
 * @param {React.Component} Component - The React component to check props for
 * @param {Object} props - The props object to validate
 */
export function checkPropTypes(Component, props) {
  PropTypes.checkPropTypes(Component.propTypes, props, 'prop', Component.name);
}
