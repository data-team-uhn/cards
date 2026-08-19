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

import { Autocomplete as MuiAutocomplete } from "@mui/material";
import PropTypes from "prop-types";

// A drop-in replacement for MUI's Autocomplete that doesn't put an empty aria-activedescendant on its
// input.
//
// While its popup is open, MUI renders that attribute as an empty string, so that React leaves alone the
// option identifier it sets imperatively when one is highlighted. An empty string isn't a valid element
// reference: Firefox resolves it as one anyway, fails, and logs "Empty string passed to getElementById()"
// on every transition into or out of that state, blaming whichever line last touched the attribute. Only
// once something has instantiated its accessibility engine, which an open developer console is enough to
// do, so it goes unnoticed until someone is looking at the console for another reason.
//
// Withholding the attribute until it names an option gives up neither half of what MUI wants: React still
// never overwrites an identifier that names one, and the null sent when the popup closes still passes
// through to clear whichever one it named last.
function Autocomplete({ renderInput, ...props }) {
  return (
    <MuiAutocomplete
      {...props}
      renderInput={({ inputProps: { "aria-activedescendant": activeOption, ...inputProps }, ...params }) =>
        renderInput({
          ...params,
          inputProps: activeOption === "" ? inputProps : { ...inputProps, "aria-activedescendant": activeOption },
        })
      }
    />
  );
}

Autocomplete.propTypes = {
  renderInput: PropTypes.func.isRequired,
};

export default Autocomplete;
