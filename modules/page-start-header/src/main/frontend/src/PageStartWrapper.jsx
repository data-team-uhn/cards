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

import React, { useState } from "react";
import PropTypes from 'prop-types';
import PageStart from './PageStart';

import { useMediaQuery } from "@mui/material";
import { useTheme } from '@mui/material/styles';

let PageStartWrapper = (props) => {
  let { children, extensionsName } = {...props};
  const [ contentOffset, setContentOffset ] = useState(0);

  const theme = useTheme();
  const appbarExpanded = useMediaQuery(theme.breakpoints.up('md'));

  return (<>
      <PageStart
        extensionsName={extensionsName}
        setTotalHeight={(th) => {
              if (contentOffset != th) {
                setContentOffset(th);
              }
            }
        }
      />
      <div id="page-start-wrapper-content" style={ { position: appbarExpanded ? 'relative' : 'absolute', top: contentOffset + 'px' } }>
       { children }
     </div>
   </>
  );
}

PageStartWrapper.propTypes = {
  extensionsName: PropTypes.string,
};

export default PageStartWrapper;
