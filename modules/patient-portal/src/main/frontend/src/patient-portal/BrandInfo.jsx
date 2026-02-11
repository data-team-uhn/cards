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

import {
  Link,
  Typography
} from "@mui/material";

function BrandInfo () {

  return (
    <Typography component="span" variant="body2">
      Built with <Link
        href="https://cards.uhndata.io"
        title="Clinical Archive for Data Science"
        target="_blank"
        rel="noreferrer"
        underline="hover"
      >
        CARDS
      </Link> by <Link
        href="https://uhndata.io"
        title="DATA Team @ UHN"
        target="_blank"
        rel="noreferrer"
      >
        <img
          src="/libs/cards/resources/media/default/data-logo_light_bg.png"
          alt="DATA"
          style={{ height: "0.8em" }}
        />
      </Link>
    </Typography>
  );
}

export default BrandInfo;
