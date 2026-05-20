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
//  Unless required by applicable law or agreed in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//

import { Fragment, memo, useMemo, useState } from "react";

import {
  Chip,
  Popover,
  Stack,
  Typography,
} from "@mui/material";
import { makeStyles } from "tss-react/mui";

import { useQuestionnaireTreeContext } from "./QuestionnaireTreeContext.jsx";
import { ENTRY_TITLE_FIELD_SPEC } from "./QuestionnaireTreeContext.jsx";
import { CONDITIONAL_TYPES } from "../questionnaire/FormEntry.jsx";
import { stripCardsNamespace } from "../questionnaire/QuestionnaireUtilities.jsx";

// EntryChip component with its own useStyles
const useEntryChipStyles = makeStyles()((theme, { color }) => ({
  entryChip: {
    background: theme.palette.background.paper,
    fontWeight: 'bold',
    border: `2px solid ${color || theme.palette.primary.main}`,
    color: color || theme.palette.primary.main,
  }
}));

// Separate component for the chip to properly handle the props-based styles
const EntryChip = memo(function EntryChip({ label, entryColor, onMouseEnter, onMouseLeave }) {
  const { classes } = useEntryChipStyles({ color: entryColor });
  return (
    <Chip
      label={label}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      variant='outlined'
      size='small'
      className={classes.entryChip}
    />
  );
});

/**
 * Header component for the questionnaire editor that displays counts and warnings for different entry types
 */
function EditorHeader() {
  const treeContext = useQuestionnaireTreeContext();

  const { state: { warnings, nodes } } = treeContext;

  const [anchorEl, setAnchorEl] = useState(null);

  const missingTitlesByEntryType = useMemo(() => {
    if (!warnings.missingTitles) return {};
    return Object.entries(warnings.missingTitles).reduce((acc, [id, jcrData]) => {
      const entryType = jcrData['jcr:primaryType'];
      if (!acc[entryType]) {
        acc[entryType] = [];
      }
      acc[entryType].push(jcrData);
      return acc;
    }, {});
  }, [warnings]);

  let handlePopoverOpen = (event, entryType) => {
    if (!event.currentTarget) return;
    setAnchorEl({ element: event.currentTarget, type: entryType });
  }

  let handlePopoverClose = () => {
    setAnchorEl(null);
  }

  if (!warnings || Object.keys(nodes).length === 0) {
    return null;
  }

  return (
    <>
      <Stack direction="row" spacing={2}>
        {
          Object.entries(ENTRY_TITLE_FIELD_SPEC)
            .filter(([entryType, entrySpec]) =>
              !CONDITIONAL_TYPES.includes(entryType)
            )
            .filter(([entryType, entrySpec]) =>
              Object.keys(warnings.countEntryTypes).includes(entryType)
            )
            .map(([entryType, entrySpec]) => {
              const { color } = entrySpec;
              const totalCount = warnings.countEntryTypes[entryType];
              const label = `${totalCount} ${stripCardsNamespace(entryType)}${totalCount > 1 ? 's' : ''}`;

              return (
                <Fragment key={entryType}>
                  <EntryChip
                    label={label}
                    entryColor={color}
                    onMouseEnter={(event) => handlePopoverOpen(event, entryType)}
                    onMouseLeave={handlePopoverClose}
                  />
                  <Popover
                    open={Boolean(anchorEl) && anchorEl.type === entryType}
                    anchorEl={anchorEl?.element}
                    onClose={handlePopoverClose}
                  >
                    <Typography>
                      {missingTitlesByEntryType[entryType] && missingTitlesByEntryType[entryType].length > 0
                        ? `${missingTitlesByEntryType[entryType].length} missing titles`
                        : 'No missing titles'}
                    </Typography>
                  </Popover>
                </Fragment>
              )
            })
        }
      </Stack>
    </>
  )
}

export default EditorHeader;
