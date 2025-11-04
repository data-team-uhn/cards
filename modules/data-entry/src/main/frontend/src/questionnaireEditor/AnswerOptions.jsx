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

import { useEffect, useState } from "react";

import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import NotesIcon from '@mui/icons-material/Notes';
import {
  Button,
  Card,
  CardActions,
  CardContent,
  CardHeader,
  Checkbox,
  FormControlLabel,
  Grid,
  IconButton,
  Popover,
  Switch,
  TextField,
  Tooltip,
} from "@mui/material";
import PropTypes from "prop-types";
import { makeStyles } from 'tss-react/mui';

import { checkPropTypes } from "../propTypes";
import DroppableAnswerOptionList from "./DroppableAnswerOptionList.jsx";
import EditorInput from "./EditorInput";
import MarkdownText from "./MarkdownText";
import QuestionComponentManager from "./QuestionComponentManager";
import ValueComponentManager from "./ValueComponentManager";
import ComposedIcon from "../components/ComposedIcon.jsx";
import { stringToHash } from "../escape.jsx";


let extractSortedOptions = (data) => {
  return Object.values(data).filter(value => value['jcr:primaryType'] == 'cards:AnswerOption'
                                             && !value.notApplicable
                                             && !value.noneOfTheAbove)
    .slice()
    .sort((option1, option2) => (option1.defaultOrder - option2.defaultOrder));
}

const useStyles = makeStyles()(theme => ({
  answerOption: {
    border: "1px solid " + theme.palette.divider,
    background: theme.palette.background.paper,
    borderRadius: theme.spacing(.5, 3, 3, .5),
    margin: theme.spacing(1, 0),
    "& > .MuiGrid-root" : {
      display: "flex",
    },
    "& .MuiFormControl-root" : {
      paddingTop: theme.spacing(1),
      width: "100%",
    },
    "& .MuiInputBase-input" : {
      paddingRight: theme.spacing(1),
      paddingLeft: theme.spacing(1),
    },
  },
  optionsList: {
    position: 'relative',
  },
  answerOptionReadonly: {
    "& .MuiInput-underline:before" : {
      borderBottom: "0 none !important",
    },
    "& .MuiInput-underline:after" : {
      borderBottom: "0 none !important",
    }
  },
  answerOptionActions : {
    justifyContent: "flex-end",
  },
  newOptionInput: {
    marginBottom: theme.spacing(2),
  },
  answerOptionSwitch: {
    margin: theme.spacing(0.5, 0.5, 0.5, -0.5),
  },
  optionsDragIndicator: {
    padding: theme.spacing(1.5, 0.5),
    borderRadius: theme.spacing(0.5),
  },
  descriptionPopover: {
    "& .MuiCardActions-root" : {
      justifyContent: "flex-end",
      padding: theme.spacing(1,2),
    },
  },
  optionDisabled: {
    opacity: 0.5,
  },
}));

let AnswerOptions = (props) => {
  checkPropTypes(AnswerOptions, props);
  const { objectKey, value, data, path, saveButtonRef, hint } = props;
  const { classes } = useStyles();
  let [ options, setOptions ] = useState(extractSortedOptions(data));
  let [ deletedOptions, setDeletedOptions ] = useState([]);
  let [ tempValue, setTempValue ] = useState(''); // Holds new, non-committed answer options
  let [ isDuplicate, setIsDuplicate ] = useState(false);
  let [ isNADuplicate, setIsNADuplicate ] = useState(false);
  let [ isNoneDuplicate, setIsNoneDuplicate ] = useState(false);
  let [ descriptionIndex, setDescriptionIndex ] = useState(null);
  let [ description, setDescription ] = useState('');
  let [ descriptionAnchorEl, setDescriptionAnchorEl ] = useState(null);
  let [ descriptionLabel, setDescriptionLabel ] = useState('');
  let [ isSpecialOption, setIsSpecialOption ] = useState(false);

  const notApplicable  = Object.values(data).find(option => option['jcr:primaryType'] == 'cards:AnswerOption' && option.notApplicable);
  const noneOfTheAbove = Object.values(data).find(option => option['jcr:primaryType'] == 'cards:AnswerOption' && option.noneOfTheAbove);

  const DEFAULT_NA_NODE_NAME = "None";
  const DEFAULT_NONEOFTHEABOVE_NODE_NAME = "NoneOfTheAbove";

  let [ notApplicableOption, setNotApplicableOption ] = useState(notApplicable || { "value" : (value == "numberOptions" ? "-1" : "notApplicable"),
    "label" : "None",
    "notApplicable" : false,
    "@name" : DEFAULT_NA_NODE_NAME,
    "@path" : path + "/" + DEFAULT_NA_NODE_NAME });
  let [ noneOfTheAboveOption, setNoneOfTheAboveOption ] = useState(noneOfTheAbove || { "value": (value == "numberOptions" ? "0" : "noneOfTheAbove"),
    "label" : "None of the above",
    "noneOfTheAbove" : false,
    "@name" : DEFAULT_NONEOFTHEABOVE_NODE_NAME,
    "@path" : path + "/" + DEFAULT_NONEOFTHEABOVE_NODE_NAME });
  // Update all options path on parent path change
  useEffect(() => {
    setNotApplicableOption({ ...notApplicableOption, "@path" : path + "/" + notApplicableOption["@name"] });
    setNoneOfTheAboveOption({ ...noneOfTheAboveOption, "@path" : path +  "/" + noneOfTheAboveOption["@name"] });
    setOptions(oldOptions => {
      let newOptions = oldOptions.slice();
      newOptions.map(opt => { if (opt.isNew) {
        opt["@path"] = path + "/AnswerOption" + stringToHash(opt.value);
      }
      return opt;
      });
      return newOptions;
    });
  }, [path])

  let specialOptionsInfo = [
    {
      tooltip : "This option behaves as 'None' or 'N/A', and unselects/removes all other options upon selection.",
      switchTooltip: "Enable N/A",
      data : notApplicableOption,
      setter : setNotApplicableOption,
      label: "notApplicable",
      defaultOrder: 0,
      isDuplicate: isNADuplicate,
      duplicateSetter: setIsNADuplicate
    },
    {
      tooltip : "This option behaves as 'None of the above'. When selected, it removes all existing selections except those entered by the user in the input, if applicable.",
      switchTooltip: "Enable 'None of the above'",
      data : noneOfTheAboveOption,
      setter : setNoneOfTheAboveOption,
      label: "noneOfTheAbove",
      defaultOrder: 99999,
      isDuplicate: isNoneDuplicate,
      duplicateSetter: setIsNoneDuplicate
    }
  ];

  // Clear local state when data changes
  useEffect(() => {
    setOptions(extractSortedOptions(data));
    setDeletedOptions([]);
    setTempValue('');
    setIsDuplicate(false);
  }, [data])

  let deleteOption = (index) => {
    setDeletedOptions(old => {
      let newDeletedOptions = old.slice();
      newDeletedOptions.push(options[index]);
      return newDeletedOptions;
    });

    setOptions(oldOptions => {
      let newOptions = oldOptions.slice();
      newOptions.splice(index, 1);
      return newOptions;
    });
  }

  let validateOption = (optionInput, setter, specialOption) => {
    if (optionInput) {
      setter(false);
      let inputs = (optionInput || '').trim().split(/\s*=\s*(.*)/);
      let allOptions = options.slice();
      specialOption != notApplicableOption?.notApplicable && allOptions.push(notApplicableOption);
      specialOption != noneOfTheAboveOption?.noneOfTheAbove && allOptions.push(noneOfTheAboveOption);
      let duplicateOption = allOptions.find( option => option.value === inputs[0] || inputs[1] && (option.label === inputs[1]));
      duplicateOption && setter(true);
      return !!duplicateOption;
    }
    return false;
  }

  let handleSpecialInputOption = (option, optionInput) => {
    let duplicate = validateOption(optionInput, option.duplicateSetter, option.data);
    if (optionInput && !duplicate) {
      let inputs = (optionInput || '').trim().split(/\s*=\s*(.*)/);
      option.setter({ ...option.data, "value": inputs[0].trim(), "label": inputs[1] ? inputs[1].trim() : "" });
    }
  }

  let handleInputOption = (event) => {
    let optionInput = event.target.value;
    if (optionInput && !isDuplicate) {
      // The text entered on each line should be split
      // by the first occurrence of the separator = if the separator exists
      // e.g. F=Female as <value> = <label>
      let inputs = (optionInput || '').trim().split(/\s*=\s*(.*)/);
      let newOption = {};
      newOption.value = inputs[0].trim();
      newOption["@path"] = path + "/AnswerOption" + stringToHash(newOption.value);
      newOption.label = inputs[1] ? inputs[1].trim() : "";
      newOption.isNew = true;
      setOptions(oldValue => {
        var value = oldValue.slice();
        value.push(newOption);
        return value;
      });
    }

    tempValue && setTempValue('');
    setIsDuplicate(false);

    // Have to manually invoke submit with timeout to let re-rendering of adding new answer option complete
    // Cause: Calling onBlur and mutating state can cause onClick for form submit to not fire
    // Issue details: https://github.com/facebook/react/issues/4210
    if (event?.relatedTarget?.type == "submit") {
      const timer = setTimeout(() => {
        saveButtonRef?.current?.click();
      }, 500);
    }
  }

  let generateDescriptionIcon = (item, index, isSpecialOptn) => {
    return (
      <Tooltip title={!item.description ? "Add a description" : "Edit description"}>
        <IconButton
          size="large"
          onClick={(event) => {
            setDescriptionAnchorEl(event.currentTarget);
            setDescriptionIndex(index);
            setDescriptionLabel(item.label || item.value);
            setIsSpecialOption(isSpecialOptn);
            setDescription(item.description);
          }
          }
        >
          <ComposedIcon
            size="large"
            MainIcon={NotesIcon}
            ExtraIcon={!item.description ? AddIcon : EditIcon}/>
        </IconButton>
      </Tooltip>
    )
  }

  let generateSpecialOptions = (index) => {
    let option = specialOptionsInfo[index];
    return (
      <Grid container
        justifyContent="space-between"
        alignItems="stretch"
        className={classes.answerOption}
        onClick={(event) => option.setter({ ...option.data, [option.label]: true })}
      >
        <Grid size={1}></Grid>
        <Grid size={8}>
          <Tooltip title="Selected by default">
            <Checkbox
              color="secondary"
              checked={option.data.isDefault}
              disabled={!option.data[option.label]} onChange={(event) => {
                option.setter({
                  ...option.data,
                  "isDefault": !!(event?.target?.checked)
                });
              }}
            />
          </Tooltip>
          <Tooltip title={option.tooltip}>
            <TextField
              variant="standard"
              disabled={!option.data[option.label]}
              error={option.data[option.label] && option.isDuplicate}
              helperText={option.isDuplicate ? 'duplicated value or label' : ''}
              className={classes.answerOptionInput}
              defaultValue={option.data.label? option.data.value + " = " + option.data.label : option.data.value}
              onChange={(event) => { handleSpecialInputOption(option, event.target.value); }}
            />
          </Tooltip>
        </Grid>
        <Grid size={3} className={classes.answerOptionActions}>
          {generateDescriptionIcon(option.data, index, true)}
          <Tooltip title={option.switchTooltip} className={classes.answerOptionSwitch}>
            <FormControlLabel
              control={
                <Switch
                  color="secondary"
                  size="small"
                  checked={!!option.data[option.label]}
                  onChange={(event) => option.setter({ ...option.data, [option.label]: event.target.checked })}
                />
              }
            />
          </Tooltip>
          { option.data[option.label]
            ?
            <>
              <input type='hidden' name={`${option.data['@path']}/jcr:primaryType`} value='cards:AnswerOption' />
              <input type='hidden' name={`${option.data['@path']}/value`} value={option.data.value} />
              <input type='hidden' name={`${option.data['@path']}/label`} value={option.data.label} />
              <input type='hidden' name={`${option.data['@path']}/${option.label}`} value={option.data[option.label]} />
              <input type='hidden' name={`${option.data['@path']}/defaultOrder`} value={option.defaultOrder} />
              <input type="hidden" name={`${option.data['@path']}/description`} value={option.data.description || ''} />
              <input type="hidden" name={`${option.data['@path']}/isDefault`} value={option.data.isDefault || ''} />
              <input type="hidden" name={`${option.data['@path']}/isDefault@TypeHint`} value="Boolean" />
            </>
            :
            <input type='hidden' name={`${option.data['@path']}@Delete`} value="0" />
          }
        </Grid>
      </Grid>
    )
  }

  let handlePopoverClose = () => {
    setDescriptionAnchorEl(null);
    setDescriptionIndex(null);
    setDescriptionLabel('');
    setIsSpecialOption(false);
    setDescription('');
  }

  let updateOptionDescription = () => {
    // update corresponding option description
    if (isSpecialOption) {
      specialOptionsInfo[descriptionIndex].setter({ ...specialOptionsInfo[descriptionIndex].data, "description": description });
    } else {
      setOptions(oldValue => {
        var value = oldValue.slice();
        value[descriptionIndex].description = description;
        return value;
      });
    }
    handlePopoverClose();
  }

  return (
    <EditorInput name={objectKey} hint={hint}>
      { deletedOptions.map((value, index) =>
        <input type='hidden' name={`${value['@path']}@Delete`} value="0" key={value['@path']} />
      )}
      { generateSpecialOptions(0) }
      <DroppableAnswerOptionList
        options={options}
        setOptions={setOptions}
        deleteOption={deleteOption}
        generateDescriptionIcon={generateDescriptionIcon}
        classes={classes}
      />
      <TextField
        fullWidth
        variant="standard"
        className={classes.newOptionInput}
        value={tempValue}
        error={isDuplicate}
        label="value OR value=label (e.g. F=Female)"
        helperText={isDuplicate ? 'Duplicated value or label' : 'Press ENTER to add a new line'}
        onChange={(event) => { setTempValue(event.target.value); validateOption(event.target.value, setIsDuplicate); }}
        onBlur={(event) => { handleInputOption(event); }}
        slotProps={{
          htmlInput: Object.assign({
            onKeyDown: (event) => {
              if (event.key == 'Enter') {
                // We need to stop the event so that it doesn't trigger a form submission
                event.preventDefault();
                event.stopPropagation();
                handleInputOption(event);
              }
            }
          })
        }}
        multiline
      />
      { generateSpecialOptions(1) }
      <Popover
        open={Boolean(descriptionAnchorEl)}
        anchorEl={descriptionAnchorEl}
        onClose={(event, reason) => {
          if (reason !== 'backdropClick') {
            handlePopoverClose(event);
          }
        }}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'center',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'center',
        }}
        className={classes.descriptionPopover}
      >
        <Card>
          <CardHeader title={`Description for "${descriptionLabel}"`} slotProps={{ title: { variant: "h6" } }}/>
          <CardContent>
            { descriptionIndex != null &&
            <MarkdownText value={description} onChange={setDescription} />
            }
          </CardContent>
          <CardActions>
            <Button variant='outlined' onClick={handlePopoverClose}>Cancel</Button>
            <Button variant='contained' onClick={updateOptionDescription}>Done</Button>
          </CardActions>
        </Card>
      </Popover>
    </EditorInput>
  )
}

AnswerOptions.propTypes = {
  data: PropTypes.object.isRequired,
  hint: PropTypes.string,
};

export default AnswerOptions;

QuestionComponentManager.registerQuestionComponent((definition) => {
  if (["numberOptions", "textOptions"].includes(definition?.type)) {
    return [AnswerOptions, 50];
  }
});

// View mode for answer options
let AnswerOptionList = (props) => {
  let { data } = props;
  let answerOptions = Object.values(data ||{}).filter(value => value['jcr:primaryType'] == 'cards:AnswerOption')
    .sort((option1, option2) => (option1.defaultOrder - option2.defaultOrder));
  return (
    answerOptions.map(item => <div key={item['jcr:uuid'] || item.value}>{(item.label || item.value) + (item.label ? (" (" + item.value + ")") : "")}</div>)
  );
}

ValueComponentManager.registerValueComponent((definition) => {
  if (["numberOptions", "textOptions"].includes(definition?.type)) {
    return [AnswerOptionList, 50];
  }
});
