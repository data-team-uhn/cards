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
import React, { useState, useEffect } from 'react';
import PropTypes from "prop-types";
import { makeStyles } from '@mui/styles';
import { deepPurple, orange } from '@mui/material/colors';
import { Avatar, Checkbox, Dialog, DialogTitle, DialogActions, DialogContent, FormControl, Icon, Grid, Radio, RadioGroup,
  FormControlLabel, Typography, Button, IconButton, Tooltip, InputLabel, Select, ListItemText, MenuItem } from "@mui/material";
import { FileDownload } from "@mui/icons-material";
import CloseIcon from '@mui/icons-material/Close';


const useStyles = makeStyles(theme => ({
  container: {
    paddingTop: theme.spacing(2),
    "&:last-child > .MuiGrid-item:first-child" : {
      paddingTop: theme.spacing(2),
    },
  },
  entryActionIcon: {
    float: "right",
    marginRight: theme.spacing(1),
  },
  variableDropdown: {
    "& > .MuiInputLabel-root" : {
      maxWidth: `calc(100% - ${theme.spacing(3)})`,
    },
  },
  variableOption: {
    whiteSpace: "normal",
  },
  valueEntry: {
    border: "1px solid " + theme.palette.divider,
    borderRadius: theme.spacing(.5, 3, 3, .5),
    margin: theme.spacing(1, 0),
    "& > .MuiGrid-item" : {
      display: "flex",
      paddingLeft: theme.spacing(1.5),
      alignItems: "center",
    },
  },
  valueActions: {
    justifyContent: "flex-end",
    paddingTop: "0!important"
  },
  avatar: {
    marginRight: theme.spacing(1),
    marginTop:  theme.spacing(1),
    alignSelf: "start",
    zoom: "75%"
  }
}));

let findQuestionsOrSections = (json, result = []) =>  {
  Object.entries(json || {}).forEach(([k,e]) => {
    if (e?.['jcr:primaryType'] == "cards:Question" || e?.['jcr:primaryType'] == "cards:Section") {
      result.push({name: e['@name'], text: e['text'] || e['label'], path: e['@path'], type: e['jcr:primaryType'].replace("cards:", '')});
      e?.['jcr:primaryType'] == "cards:Section" && findQuestionsOrSections(e, result);
    } else if (typeof(e) == 'object') {
      findQuestionsOrSections(e, result);
    }
  })
  return result;
}

let entitySpecs = {
  Question: {
    avatarColor: deepPurple[700]
  },
  Section: {
    avatar: "view_stream",
    avatarColor: orange[800]
  }
}

/**
 * A component that renders an icon or button to open the export dialog that generates an export URL for an entry.
 */
function ExportButton(props) {
  const { entityData, entryLabel, entryPath, entryName, entryType, variant, size, onClose } = props;

  const [ open, setOpen ] = useState(false);
  // List of questions and sections to display in dropdown select to exclude/include
  const [ entities, setEntities] = useState();

  // Decides if the generated export URL ends in .csv or in .tsv
  const [ fileFormat, setFileFormat ] = useState(".csv");
  // Decides if/how .csvHeader is specified:
  // by default we have csvHeader:labels
  // to disable labels, add .-csvHeader:labels
  // to enable identifiers, add .csvHeader:raw
  const [ labelFormat ,setLabelFormat ] = useState(true);
  const [ identifiersFormat, setIdentifiersFormat ] = useState(false);
  // Specifies if the .labels processor is enabled (disabled by default for values)
  const [ hasLabeles, setHasLabeles ] = useState(false);

  // Column selection:
  // just one of the Include or Exclude options should be available at a time
  const [ isInclude, setIsInclude ] = useState(false);
  // List of quesitons ids to Include OR Exclude
  const [ questionIds, setQuestionIds ] = useState([]);
  const [ tempValue, setTempValue ] = useState(''); // Holds new, non-selected values

  const buttonText = entryLabel || ("Export " + (entryType?.toLowerCase() || '')).trim();

  const classes = useStyles();

  useEffect(() => {
    if (entityData && !entities) {
      setEntities(findQuestionsOrSections(entityData));
    }
    if (!entityData && entryName && !entities && open) {
        fetch(`/Questionnaires/${entryName}.deep.json`)
          .then((response) => response.ok ? response.json() : Promise.reject(response))
          .then((json) => {
            setEntities(findQuestionsOrSections(json));
          });
    }
  }, [entityData, open]);

  let openDialog = () => {
    !open && setOpen(true);
  }

  let closeDialog = () => {
    open && setOpen(false);
    onClose?.();
  }

  let handleExport = () => {
    if (!entryPath) {
      closeDialog();
      return;
    }
    // Construct the export URL
    let path = entryPath;
    if (!labelFormat) {
      path += ".-csvHeader:labels";
    }
    if (identifiersFormat) {
      path += ".csvHeader:raw";
    }
    if (questionIds.length > 0) {
      path +=  ".questionnaireFilter";
      let pref = isInclude ? ".questionnaireFilter:include=" : ".questionnaireFilter:exclude=";
      for (let id in questionIds) {
        path += pref + encodeURIComponent(encodeURIComponent(questionIds[id]));
      }
    }
    if (hasLabeles) {
      path += ".labels";
    }
    path += fileFormat;
    //let url = new URL(path, window.location.origin);
    window.open(path, '_blank');
  }

  let handleSelected = (event) => {
    if (event.target.value) {
      let newValue = event.target.value.trim();
      setQuestionIds(oldValue => {
        var newValues = oldValue.slice();
        newValues.push(newValue);
        return newValues;
      });
    }
    tempValue && setTempValue('');

    // Have to manually invoke submit with timeout to let re-rendering of adding new answer option complete
    // Cause: Calling onBlur and mutating state can cause onClick for form submit to not fire
    // Issue details: https://github.com/facebook/react/issues/4210
    if (event?.relatedTarget?.type == "submit") {
      const timer = setTimeout(() => {
        saveButtonRef?.current?.click();
      }, 500);
    }
  }

  let deleteValue = (index) => {
    setQuestionIds(oldValues => {
      let newValues = oldValues.slice();
      newValues.splice(index, 1);
      return newValues;
    });
  }

  let getAvatar = (type) => {
	return (<Avatar
	            style={{backgroundColor: entitySpecs[type].avatarColor || "black"}}
	            className={classes.avatar}
	        >
	            { entitySpecs[type].avatar ? <Icon>{entitySpecs[type].avatar}</Icon> : type?.charAt(0) }
	        </Avatar>);
  }
  return(
    <React.Fragment>
      <Dialog fullWidth maxWidth='md' open={open} onClose={closeDialog}>
        <DialogTitle>
          Export {entryLabel ? entryLabel.concat(' ') : ''}{entryName}
        </DialogTitle>
        <DialogContent>
          <Grid container direction="column">
            <Grid container alignItems='start' direction="row" className={classes.container}>
              <Grid item xs={4}><Typography variant="subtitle2">File format:</Typography></Grid>
              <Grid item xs={8}>
                <RadioGroup
                  row
                  defaultValue=".csv"
                  aria-label="format"
                  name="format"
                  value={fileFormat}
                  onChange={(event) => setFileFormat(event.target.value)}
                >
                  <FormControlLabel value=".csv" control={<Radio />} label=".csv" />
                  <FormControlLabel value=".tsv" control={<Radio />} label=".tsv" />
                </RadioGroup>
              </Grid>
            </Grid>

            <Grid container alignItems='start' direction="row" className={classes.container}>
              <Grid item xs={4}><Typography variant="subtitle2">Header format:</Typography></Grid>
              <Grid item xs={8}>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={labelFormat}
                      onChange={(event) => { setLabelFormat(event.target.checked);}}
                      name="labels"
                    />
                  }
                  label="Labels"
                />
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={identifiersFormat}
                      onChange={(event) => { setIdentifiersFormat(event.target.checked);}}
                      name="identifiers"
                    />
                  }
                  label="Identifiers"
                />
              </Grid>
            </Grid>

            <Grid container alignItems='start' direction="row" className={classes.container}>
              <Grid item xs={4}><Typography variant="subtitle2">Data format:</Typography></Grid>
              <Grid item xs={8}>
                <RadioGroup
                  row
                  defaultValue="values"
                  aria-label="data"
                  name="data"
                  value={hasLabeles}
                  onChange={(event) => setHasLabeles(event.target.value === "true")}
                >
                  <FormControlLabel value={true} control={<Radio />} label="Labels" />
                  <FormControlLabel value={false} control={<Radio />} label="Values" />
                </RadioGroup>
              </Grid>
            </Grid>

            <Grid container alignItems='start' direction="row" className={classes.container}>
              <Grid item xs={4}><Typography variant="subtitle2">Column selection mode:</Typography></Grid>
              <Grid item xs={8}>
                <RadioGroup
                  row
                  defaultValue="isinclude"
                  aria-label="isinclude"
                  name="isinclude"
                  value={isInclude}
                  onChange={(event) => setIsInclude(event.target.value === "true")}
                >
                  <FormControlLabel value={true} control={<Radio />} label="Include" />
                  <FormControlLabel value={false} control={<Radio />} label="Exclude" />
                </RadioGroup>
              </Grid>
            </Grid>

            <Grid container alignItems='start' direction="row" className={classes.container}>
              <Grid item xs={4}>
                <Typography variant="subtitle2">{isInclude ? "Columns to include:" : "Columns to exclude:"}</Typography>
              </Grid>
              <Grid item xs={8}>

                {/* List the entered values */}
                { entities?.filter(v => questionIds.includes(v.path)).map((value, index) =>
                  <Grid container
                    key={`${value.name}-${index}`}
                    direction="row"
                    justifyContent="space-between"
                    alignItems="stretch"
                    className={classes.valueEntry}
                  >
                    <Grid item xs={9}>
                      { getAvatar(value.type) }
                      <ListItemText primary={value.name} secondary={value.text} />
                    </Grid>
                    <Grid item xs={3} className={classes.valueActions}>
                      <Tooltip title="Delete entry">
                        <IconButton onClick={() => deleteValue(questionIds.indexOf(value.path))}><CloseIcon/></IconButton>
                      </Tooltip>
                    </Grid>
                  </Grid>
                )}

                <FormControl variant="standard" fullWidth className={classes.variableDropdown}>
                  <InputLabel id="label">Select questions/sections from this questionnaire</InputLabel>
                  <Select
                    variant="standard"
                    labelId="label"
                    value={tempValue}
                    label="Select questions/sections from this questionnaire"
                    onChange={handleSelected}
                  >
                    { entities?.filter(v => !questionIds.includes(v.path))
                                .map(v =>
                                    <MenuItem value={v.path} key={`option-${v.name}`} className={classes.variableOption}>
                                      { getAvatar(v.type) }
                                      <ListItemText primary={v.name} secondary={v.text} />
                                    </MenuItem>)
                    }
                  </Select>
                </FormControl>
              </Grid>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions className={classes.dialogActions}>
            <Button variant="outlined" size="small" onClick={closeDialog}>Cancel</Button>
            <Button
              variant="contained"
              size="small"
              onClick={() => handleExport()}
            >
              Export
            </Button>
        </DialogActions>
      </Dialog>
      {variant == "icon" ?
        <Tooltip title={buttonText}>
          <IconButton component="span" onClick={openDialog} className={classes.entryActionIcon} size={size}>
            <FileDownload fontSize={size == "small" ? size : undefined}/>
          </IconButton>
        </Tooltip>
        :
        <Button
          onClick={openDialog}
          size={size ? size : "medium"}
          startIcon={variant == "extended" ? <FileDownload /> : undefined}
        >
          {buttonText}
        </Button>
      }
    </React.Fragment>
  )
}

ExportButton.propTypes = {
  entityData: PropTypes.object,
  entryPath: PropTypes.string,
  entryLabel: PropTypes.string,
  entryName: PropTypes.string,
  entryType: PropTypes.string,
  size: PropTypes.oneOf(["small", "medium", "large"]),
  variant: PropTypes.oneOf(["icon", "text", "extended"]), // "extended" means both icon and text
}

ExportButton.defaultProps = {
  entryName: "",
  entryType: "",
  entryLabel: "",
  variant: "icon",
  size: "large",
}

export default ExportButton;
