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
import { Grid, Typography, withStyles } from "@material-ui/core";
import uuidv4 from "uuid/v4";

import FormEntry, { ENTRY_TYPES } from "./FormEntry";
import QuestionnaireStyle from "./QuestionnaireStyle";

/// Component that consists of a few sections, and optionally has some criteria to its display
function Section(props) {
  const { classes, depth, existingAnswer, path, sectionDefinition } = props;
  const [sectionID] = useState((existingAnswer && existingAnswer[0]) || uuidv4());
  const sectionPath = path + "/" + sectionID;
  const headerVariant = (depth > 3 ? "body1" : ("h" + (depth+3)));

  const titleEl = sectionDefinition["label"] && <Typography variant={headerVariant}>{sectionDefinition["label"]} </Typography>;
  const descEl = sectionDefinition["description"] && <Typography variant="overline">{sectionDefinition["description"]} </Typography>
  const hasPadding = titleEl || descEl;

  return (
    <Grid item className={hasPadding && classes.paddedSection}>
      <input type="hidden" name={`${sectionPath}/jcr:primaryType`} value={"lfs:AnswerSection"}></input>
      <input type="hidden" name={`${sectionPath}/question`} value={sectionDefinition['jcr:uuid']}></input>
      <input type="hidden" name={`${sectionPath}/question@TypeHint`} value="Reference"></input>

      <Grid container direction="column" spacing={4} alignItems="stretch" justify="space-between" wrap="nowrap">
        {hasPadding &&
          <Grid item className={classes.sectionHeader}>
            {titleEl}
            {descEl}
          </Grid>
        }
        {Object.entries(sectionDefinition)
          .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
          .map(([key, definition]) => FormEntry(definition, sectionPath, depth+1, existingAnswer && existingAnswer[1], key))
        }
      </Grid>
    </Grid>
  );
}

export default withStyles(QuestionnaireStyle)(Section);
