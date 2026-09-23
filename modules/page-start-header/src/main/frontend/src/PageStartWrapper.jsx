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

import { useMemo, useState } from "react";

import PropTypes from 'prop-types';

import LayoutContext from './components/LayoutContext.jsx';
import PageStart from './PageStart';

// Renders the PageStart banners, pushes its children down by their total height, and publishes
// that height (plus the width of the navigation drawer, if the layout has one) through
// LayoutContext so that sticky and fixed elements further down the tree can stay clear of them.
//
// Props:
// extensionsName: the PageStart extension point to load the banners from (default "PageStart")
// drawerWidth: width in px of the permanent navigation drawer of the enclosing layout, 0 if none

const PageStartWrapper = (props) => {
  const { children, extensionsName, drawerWidth = 0 } = props;
  const [contentOffset, setContentOffset] = useState(0);

  const layout = useMemo(() => ({ drawerWidth, contentOffset }), [drawerWidth, contentOffset]);

  return (
    <LayoutContext.Provider value={layout}>
      <PageStart
        extensionsName={extensionsName}
        setTotalHeight={(th) => {
          if (contentOffset !== th) {
            setContentOffset(th);
          }
        }}
      />
      <div style={{ position: 'relative', top: contentOffset + 'px' }}>
        {children}
      </div>
    </LayoutContext.Provider>
  );
};

PageStartWrapper.propTypes = {
  extensionsName: PropTypes.string,
  drawerWidth: PropTypes.number,
  children: PropTypes.node,
};

export default PageStartWrapper;
