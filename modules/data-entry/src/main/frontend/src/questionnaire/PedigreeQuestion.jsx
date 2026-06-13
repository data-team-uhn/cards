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

import { useMemo, useState, useRef } from "react";

import { Button, Dialog, DialogContent, Grid, Link, Tooltip } from "@mui/material";
import PropTypes from "prop-types";
import { makeStyles } from 'tss-react/mui';

import { checkPropTypes } from "../propTypes";
import Answer from "./Answer";
import Question from "./Question";
import DeleteButton from "../dataHomepage/DeleteButton";
import PedigreeEditor from "../pedigree/pedigree";

// Component that renders a pedigree, although answering these questions is not currently possible.
//
// Optional props:
//  existingAnswer: array of length 1, where the first entry corresponds to a pedigree object. The
//    pedigree object is assumed to contain an image property with an SVG as its value.
//  questionDescription: props forwarded to the Question element.
//
// Sample usage:
// <PedigreeQuestion
//    questionDefinition={{
//      text="Patient pedigree"
//      description="De-identified information only."
//      }}
//    />

const useStyles = makeStyles()(theme => ({
  thumbnail: {
    border: "1px solid " + theme.palette.divider,
  },
  thumbnailLink: {
    cursor: "pointer",
    "& div:hover" : {
      borderColor: "inherit !important",
    },
  },
}));

function PedigreeQuestion(props) {
  checkPropTypes(PedigreeQuestion, props);
  const { existingAnswer, pageActive, ...rest } = props;

  const { classes } = useStyles();

  const [ expanded, setExpanded ] = useState(false);
  // default pedigreeData state variable to the pedigree saved in CARDS:
  const [ pedigreeData, setPedigree ] = useState(existingAnswer && existingAnswer.length > 1 && existingAnswer[1].value
    ? { "image": existingAnswer[1].image, "pedigreeJSON": existingAnswer[1].value }
    : {});
  const pedigreeEditorRef = useRef(null);

  // FIXME: hardcoded value
  const PEDIGREE_THUMBNAIL_WIDTH = 300;

  let resizeSVG = function(svgText, newWidthInPixels) {
    const newWidth = "$1width=\"" + newWidthInPixels + "px\"";
    let resizedSVG = svgText?.replace(/(<svg[^>]+)height="\d+"/, "$1");
    resizedSVG = resizedSVG.replace(/(<svg[^>]+)width="\d+"/, newWidth);
    return resizedSVG;
  };

  let pedigreeJSON = null;
  let pedigreeSVG  = null;
  let displayedImage = '';

  if (pedigreeData?.image && pedigreeData.pedigreeJSON) {
    // use pedigree stored in React component state:
    // default value for that state is the pedigree loaded from CARDS, but it gets overwritten each time pedigree is saved
    // from the pedigree editor, even if that data is not yet saved to CARDS
    pedigreeSVG  = pedigreeData.image;
    pedigreeJSON = pedigreeData.pedigreeJSON;
    displayedImage = resizeSVG(pedigreeSVG, PEDIGREE_THUMBNAIL_WIDTH);
  }

  let answerMetadata = { image:  pedigreeSVG };

  const outputAnswers = useMemo(() =>
    pedigreeJSON ? [["value", pedigreeJSON]] : []
  , [pedigreeJSON]);

  let image_div = <div className={classes.thumbnail} dangerouslySetInnerHTML={{ __html: displayedImage }}/>;

  let closeDialog = function () {
    setExpanded(false);
  };

  let openPedigree = function () {
    pedigreeEditorRef.current = new PedigreeEditor({
      "pedigreeJSON": pedigreeJSON,
      "pedigreeDiv": "pedigreeEditor",  // the DIV to render entire pedigree in
      "onCloseCallback": closeDialog,
      "onPedigreeSaved": onUpdatedPedigree,
      "readOnlyMode": false });
  };

  let closePedigree = function () {
    if (pedigreeEditorRef.current) {
      pedigreeEditorRef.current.unload();
      pedigreeEditorRef.current = null;
    }
    typeof(props.onChange) == 'function' && props.onChange();
  };

  let onUpdatedPedigree = function (pedigreeJSON, pedigreeSVG) {
    // state change will trigger re-render
    setPedigree({ "image": pedigreeSVG, "pedigreeJSON": pedigreeJSON });
  };

  let defaultDisplayFormatter = function(label, idx) {
    return image_div || "";
  }

  return (
    <Question
      defaultDisplayFormatter={defaultDisplayFormatter}
      currentAnswers={outputAnswers.length}
      {...props}
    >
      {
        pageActive && <>
          { pedigreeData.image ?
            <Grid container justifyContent="flex-start" alignItems="flex-start" spacing={0}>
              <Grid>
                <Tooltip title="Edit Pedigree">
                  <Link className={classes.thumbnailLink} onClick={() => setExpanded(true)} underline="hover">
                    {image_div}
                  </Link>
                </Tooltip>
              </Grid>
              <Grid>
                <DeleteButton
                  entryName="pedigree"
                  entryType="Pedigree"
                  onComplete={() => setPedigree({})}
                />
              </Grid>
            </Grid>
            :
            <Button variant="outlined" onClick={() => setExpanded(true)}>Draw</Button>
          }
          <Dialog fullScreen open={expanded}
            onClose={() => setExpanded(false)}
            slotProps={{
              transition: {
                onEntering: () => openPedigree(),
                onExit: () => closePedigree(),
              },
            }}>
            <DialogContent>
              <div id="pedigreeEditor"></div>
            </DialogContent>
          </Dialog>
        </>
      }
      <Answer
        answers={outputAnswers}
        answerMetadata={answerMetadata}
        questionDefinition={props.questionDefinition}
        existingAnswer={existingAnswer}
        answerNodeType="cards:PedigreeAnswer"
        valueType="String"
        pageActive={pageActive}
        {...rest}
      />
    </Question>);
}

PedigreeQuestion.propTypes = {
  questionDefinition: PropTypes.shape({
    text: PropTypes.string,
    description: PropTypes.string
  }).isRequired,
  existingAnswer: PropTypes.array,
}

PedigreeQuestion.canProcess = (questionDefinition) => {
  if (questionDefinition.dataType === "pedigree") {
    return [PedigreeQuestion, 50];
  }
};

export default PedigreeQuestion;
