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

import { forwardRef, memo, useMemo } from "react";

import {
  Chip,
  Stack,
  Tooltip,
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

// Separate component for the chip to properly handle the props-based styles.
// forwardRef + spread so a wrapping Tooltip can attach its ref and hover/focus handlers.
const EntryChip = memo(forwardRef(function EntryChip({ label, entryColor, className, ...rest }, ref) {
  const { classes, cx } = useEntryChipStyles({ color: entryColor });
  return (
    <Chip
      ref={ref}
      label={label}
      variant='outlined'
      size='small'
      {...rest}
      className={cx(classes.entryChip, className)}
    />
  );
}));

/**
 * Header component for the questionnaire editor that displays counts and warnings for different entry types
 */
function EditorHeader() {
  const treeContext = useQuestionnaireTreeContext();

  const { state: { warnings, nodes } } = treeContext;

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

  if (!warnings || Object.keys(nodes).length === 0) {
    return null;
  }

  return (
    <>
      <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap' }}>
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
              const missingCount = missingTitlesByEntryType[entryType]?.length || 0;
              const tooltip = missingCount > 0 ? `Missing titles: ${missingCount}` : 'No missing titles';

              return (
                <Tooltip key={entryType} title={tooltip}>
                  <EntryChip label={label} entryColor={color} />
                </Tooltip>
              )
            })
        }
      </Stack>
    </>
  )
}

export default EditorHeader;
