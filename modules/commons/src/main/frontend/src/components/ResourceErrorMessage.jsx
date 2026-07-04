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
import { Stack, Typography } from '@mui/material';
import PropTypes from 'prop-types';

import { checkPropTypes } from "../propTypes";

// A compact, embeddable error message for a failed resource fetch. Echoes the
// visual hierarchy of ErrorPage (small bold heading + secondary detail) without
// the full-page logo/card chrome, so it can be dropped into an existing view.
//
// @param {string} title - the heading shown when the fetch failed; required
// @param {string} notFoundTitle - a friendlier heading shown instead of `title`
//   when the resource does not exist (the error is an HTTP 404)
// @param {object} error - the failed response or thrown error; its `status` and
//   `statusText` (falling back to `toString()`) are shown as the detail line
function ResourceErrorMessage(props) {
  checkPropTypes(ResourceErrorMessage, props);
  const { title, notFoundTitle, error } = props;
  // Show a friendlier heading when the resource simply doesn't exist (404).
  const heading = (notFoundTitle && error?.status === 404) ? notFoundTitle : title;
  const detail = [error?.status, error?.statusText || error?.toString?.()]
    .filter(Boolean).join(" ");

  return (
    <Stack spacing={1} sx={{ py: 4, mx: 'auto', textAlign: 'center' }}>
      <Typography variant="h4" component="h1" color="primary"  sx={{ fontWeight: 'bold' }}>
        {heading}
      </Typography>
      {detail &&
        <Typography variant="subtitle1" color="textSecondary">{detail}</Typography>
      }
    </Stack>
  );
}

ResourceErrorMessage.propTypes = {
  title: PropTypes.string.isRequired,
  notFoundTitle: PropTypes.string,
  error: PropTypes.object.isRequired,
}

export default ResourceErrorMessage;
