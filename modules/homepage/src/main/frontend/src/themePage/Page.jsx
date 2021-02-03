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

import React, { createContext, useContext, useEffect, useState } from "react";

const DEFAULT_STATE = "";

const PageNameWriterContext = createContext(DEFAULT_STATE);

/**
 * Obtain a context that can be assigned to to set the page title to something custom
 * @returns {Object} a React context of values from the parent form
 * @throws an error if it is not within a FormProvider
 */
export function usePageNameWriterContext() {
  const context = useContext(PageNameWriterContext);

  if (context == undefined) {
    throw new Error("usePageNameWriterContext must be used within a Page")
  }

  return context;
}

// A pseudo-component that applies the title of the given page
function Page (props) {
  const { children, title, pageDefaultName } = props;
  const [ overrideName, setOverrideNameState ] = useState(DEFAULT_STATE);

  // When a page is loaded, change the title of the page
  useEffect(() => {
    document.title = (overrideName == "" ? pageDefaultName : overrideName) + title;
  }, [overrideName])

  return (
    <PageNameWriterContext.Provider value={setOverrideNameState}>
      {children}
    </PageNameWriterContext.Provider>
    );
}

export default Page;