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

import React, { useState, useRef } from "react";
import PropTypes from "prop-types";

import { CircularProgress, Link, Tooltip, withStyles, Typography } from "@material-ui/core";

import QuestionnaireStyle from "./QuestionnaireStyle";
import Note from "./Note.jsx";

const NCRURL = window.location.origin + "/ncr/annotate/";

// Attempt to split up the given text & tooltips to highlight
function ParsedNoteSection (props) {
  let { classes, onAddSuggestion, tooltips, text, offset } = props;
  let hasMatch = tooltips.length > 0;
  let frontMatter = text;
  if (hasMatch) {
    var matches = tooltips.sort( (e1, e2) => (e1.start-e2.start) );
    var firstMatch = matches[0];
    // The front matter is the text before the first match begins
    frontMatter = text.substring(0, firstMatch.start-offset);
    var matchID = firstMatch["hp_id"]; // TODO: Handle more than just HPO
    var matchName = firstMatch["names"][0];

    // The contained matter is the text inside the first match
    var containedMatches = matches.filter( (el) => (
        el.start >= firstMatch.start && el.end <= firstMatch.end && !(el.start == firstMatch.start && el.end == firstMatch.end)
      ));
    var containedMatter = text.substring(firstMatch.start-offset, firstMatch.end-offset);

    // The uncontained matter is the text after the first match, up until the end of the last match
    // (It still needs to be parsed for more notes)
    var uncontainedMatches = matches.filter( (el) => (el.end > firstMatch.end));
    var lastMatch = Math.max(...(tooltips.map((el) => (el.end))));
    var middleMatter = text.substring(firstMatch.end-offset, lastMatch-offset);

    // The end matter is the text after the last match
    var endMatter = text.substring(lastMatch-offset);
  }

  // Handle the user clicking on a link which corresponds to a suggestion
  let addSuggestion = (event) => {
    event.preventDefault();
    onAddSuggestion(firstMatch["hp_id"], firstMatch["names"][0])
  }

  return (<React.Fragment>
    <Typography display="inline">{frontMatter}</Typography>
    {hasMatch &&
      <React.Fragment>
        <Tooltip interactive title={`Add ${matchName} (${matchID}) to selection`}>
          <Link
            onClick={addSuggestion}
            component="button"
            >
            {/* Create a div purely to hold a ref for the above Tooltip */}
            <span className={classes.NCRTooltip}>
              <ParsedNoteSection
                tooltips={containedMatches}
                text={containedMatter}
                offset={firstMatch.start}
                classes={classes}
                />
            </span>
          </Link>
        </Tooltip>
        <ParsedNoteSection
          tooltips={uncontainedMatches}
          text={middleMatter}
          offset={firstMatch.end}
          classes={classes}
          />
        <Typography display="inline">{endMatter}</Typography>
      </React.Fragment>}
  </React.Fragment>)
}

function NCRNote (props) {
  const { classes, existingAnswer, vocabulary, onAddSuggestion, onChangeNote, onBlur, ...rest } = props;
  const [ cachedText, setCachedText ] = useState(existingAnswer?.[1]?.note || "");
  const [ parsedText, setParsedText ] = useState();
  const [ isLoading, setIsLoading ] = useState(false);
  const [ error, setError ] = useState();

  let setNCRCallback = (event) => {
    // Call super if necessary
    onBlur && onBlur(event);
    
    // Clear our current search
    setIsLoading(true);
    setParsedText("");
    setError(null);

    // Access the NCR URL
    let url = new URL(NCRURL);
    url.searchParams.set("text", cachedText);
    // NCR calls HP HPO, so we do a quick conversion here
    if (vocabulary == "HP") {
      url.searchParams.set("model", "HPO");
    } else {
      url.searchParams.set("model", vocabulary);
    }

    fetch(url)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(parseNCRData)
      .then(saveParsedText)
      .catch(handleError);
  }

  let parseNCRData = (json) => {
    // Return nothing if we have no matches
    if (!("matches" in json)) {
      return <div></div>;
    }

    return (<div>
      <ParsedNoteSection
        tooltips={json["matches"]}
        text={cachedText}
        offset={0}
        classes={classes}
        onAddSuggestion={onAddSuggestion}
        />
    </div>)
  }

  let saveParsedText = (text) => {
    setParsedText(text);
    setIsLoading(false);
  }

  let handleError = (error) => {
    console.log(error);
    setError(error.toString());
  }

  let storeAndChangeNote = (text) => {
    onChangeNote && onChangeNote(text);
    setCachedText(text);
  }

  return (
    <Note
      onChangeNote = {storeAndChangeNote}
      onBlur = {setNCRCallback}
      existingAnswer = {existingAnswer}
      {...rest}
      >
      {isLoading && <CircularProgress className={classes.NCRLoadingIndicator} />}
      {parsedText}
      {error && <div>{error}</div>}
    </Note>
    );
}

export default withStyles(QuestionnaireStyle)(NCRNote);
