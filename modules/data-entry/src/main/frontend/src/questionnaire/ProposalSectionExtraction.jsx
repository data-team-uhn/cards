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

import { useContext, useEffect, useRef, useState } from "react";

import { Alert, Box, Button, CircularProgress, Typography } from "@mui/material";
import PropTypes from "prop-types";

import { fetchWithReLogin, GlobalLoginContext } from "../login/ReLoginDialog.js";
import { checkPropTypes } from "../propTypes";
import { useFormReaderContext } from "./FormContext";

// Whether the section's answer node already holds at least one non-empty answer value. Used to decide
// whether auto-extraction is still needed: once answers exist, the auto-trigger must never fire again, which
// keeps a reload (that re-renders or remounts this component) from re-triggering extraction in a loop.
const sectionHasAnswerValues = (answerSection) => {
  if (!answerSection || typeof answerSection !== "object") {
    return false;
  }
  return Object.values(answerSection).some((value) =>
    value && typeof value === "object"
      && value["sling:resourceSuperType"] === "cards/Answer"
      && value["value"] != null && value["value"] !== ""
  );
};

// Drives server-side extraction for a section flagged with `extractFromProposal`. When the section's page
// becomes active in edit mode, it POSTs to `/Forms/<id>.extract`; the backend parses the proposal document
// (via the active LLM) and writes the answers, after which the form is reloaded so the answers appear. A
// "Re-extract" button re-runs the extraction on demand, overwriting the current answers.
function ProposalSectionExtraction(props) {
  checkPropTypes(ProposalSectionExtraction, props);
  const { pageActive, existingSectionAnswer } = props;

  const globalLoginDisplay = useContext(GlobalLoginContext);
  const reader = useFormReaderContext();
  const formURL = reader?.["/URL"];
  const reload = reader?.["/Reload"];

  // Extraction has run for this section if the server stamped the AnswerSection (it does so even when zero
  // answers were produced) or if any answer already has a value. Either way the auto-trigger must not fire.
  const alreadyExtracted = existingSectionAnswer?.["extractionSourceTimestamp"] != null
    || sectionHasAnswerValues(existingSectionAnswer);

  // idle | loading | done | skipped | empty | error
  const [state, setState] = useState("idle");
  const [errorMessage, setErrorMessage] = useState(null);
  const wasActiveRef = useRef(false);
  const inFlightRef = useRef(false);

  const runExtraction = (force) => {
    if (!formURL || inFlightRef.current) {
      return;
    }
    inFlightRef.current = true;
    setState("loading");
    setErrorMessage(null);
    const url = `${formURL}.extract${force ? "?force=true" : ""}`;
    fetchWithReLogin(globalLoginDisplay, url, { method: "POST", headers: { Accept: "application/json" } })
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        if (json?.error) {
          setErrorMessage(json.error);
          setState("error");
        } else if (json?.status === "extracted") {
          // Refresh the form in place so the newly saved answers render; this does not remount the form.
          reload?.();
          setState("done");
        } else if (json?.status === "no_document") {
          setState("empty");
        } else {
          setState("skipped");
        }
      })
      .catch(() => {
        setErrorMessage("The extraction request could not be completed.");
        setState("error");
      })
      .finally(() => {
        inFlightRef.current = false;
      });
  };

  // Auto-run once when the page becomes active AND the section has no answers yet. Gating on the actual
  // answers (not just a ref) means a reload or remount can never re-trigger extraction once answers exist,
  // which prevents the extract -> reload -> re-extract loop. Use the "Re-extract" button to force a re-run.
  useEffect(() => {
    if (pageActive && !alreadyExtracted && !wasActiveRef.current) {
      wasActiveRef.current = true;
      runExtraction(false);
    } else if (!pageActive) {
      wasActiveRef.current = false;
    }
  }, [pageActive, alreadyExtracted]);

  if (!pageActive) {
    return null;
  }

  return (
    <Box sx={{ width: "100%", mb: 2 }}>
      {state === "loading" &&
        <Box sx={{ display: "flex", alignItems: "center", gap: 2, py: 2 }}>
          <CircularProgress size={28} />
          <Typography>Extracting information from the proposal...</Typography>
        </Box>
      }
      {state === "error" &&
        <Alert severity="error" sx={{ mb: 1 }}>{errorMessage}</Alert>
      }
      {state === "empty" &&
        <Alert severity="info" sx={{ mb: 1 }}>
          No parsed proposal document is available to extract from yet. Upload a proposal on the previous page.
        </Alert>
      }
      {state !== "loading" &&
        <Button size="small" variant="outlined" onClick={() => runExtraction(true)}>
          Re-extract from proposal
        </Button>
      }
    </Box>
  );
}

ProposalSectionExtraction.propTypes = {
  pageActive: PropTypes.bool,
  existingSectionAnswer: PropTypes.object,
};

export default ProposalSectionExtraction;
