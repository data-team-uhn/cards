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

import {
  Button,
  Card,
  CardContent,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  Grid,
  LinearProgress,
  Link,
  TextField,
  Tooltip,
  Typography,
  makeStyles
} from "@material-ui/core";
import { Alert, AlertTitle } from '@material-ui/lab';
import BackupIcon from '@material-ui/icons/Backup';
import CloseIcon from '@material-ui/icons/Close';
import GetApp from '@material-ui/icons/GetApp';
import MaterialTable from "material-table";
import { v4 as uuidv4 } from 'uuid';
import moment from "moment";
import DragAndDrop from "./dragAndDrop.jsx";
import { escapeJQL } from "./escape.jsx";

const useStyles = makeStyles(theme => ({
  root: {
    flexGrow: 1,
  },
  fileinput: {
    display: 'none',
  },
  buttonIcon: {
    verticalAlign: 'middle',
    paddingRight: theme.spacing(1)
  },
  uploadButton: {
    marginTop: theme.spacing(2),
  },
  fileInfo: {
    padding: theme.spacing(1),
    margin: theme.spacing(3, 0),
    fontFamily: "'Roboto', 'Helvetica', 'Arial', sans-serif",
  },
  fileFormSection : {
    display: "flex",
    alignItems: "center",
    flexWrap: "wrap",
  },
  fileName: {
    display: 'inline'
  },
  fileDetail: {
    marginRight: theme.spacing(1),
    marginTop: theme.spacing(1)
  },
  progressBar: {
    width: "40%",
    height: theme.spacing(2),
    backgroundColor: theme.palette.primary.main,
    borderRadius: "2px",
    display: "inline-block",
    marginLeft: theme.spacing(1),
    marginRight: theme.spacing(1),
    verticalAlign: "text-top"
  },
  progress: {
    backgroundColor: theme.palette.primary.main,
    height: "100%",
    margin: "0",
    borderRadius: "2px",
  },
  dragAndDropContainer: {
    margin: theme.spacing(3, 0),
    "& .MuiAlert-root": {
      boxSizing: "border-box",
      height: "100%",
    },
  },
  dragAndDrop: {
    "& > div" : {
      width: "100%",
    },
  },
  active: {
    display: "flex",
    justifyContent: "center",
    alignItems: "center",
    color: theme.palette.primary.main,
    background: theme.palette.action.hover,
    boxSizing: "border-box",
    width: "100%",
    border: "2px dashed",
    borderColor: theme.palette.primary.light,
    padding: "2rem",
    paddingLeft: "0",
    textAlign: "center",
    borderRadius: theme.spacing(1),
    cursor: "pointer"
  },
  dropzone: {
    display: "flex",
    justifyContent: "center",
    alignItems: "center",
    boxSizing: "border-box",
    width: "100%",
    border: "2px dashed",
    borderColor: theme.palette.primary.main,
    padding: "2rem",
    paddingLeft: "0",
    textAlign: "center",
    borderRadius: theme.spacing(1),
    cursor: "pointer"
  },
  dialogTitle: {
    marginRight: theme.spacing(5)
  },
  dialogContent: {
    minWidth: "500px"
  },
  closeButton: {
      position: 'absolute',
      right: theme.spacing(1),
      top: theme.spacing(1),
      color: theme.palette.grey[500]
  },
  variantFileCard: {
    "& .MuiCardHeader-root" : {
      padding: theme.spacing(1, 3, 0, 3),
    },
    "& .MuiCardContent-root" : {
      paddingLeft: theme.spacing(3),
      paddingRight: theme.spacing(3),
    },
    "& .MuiList-root": {
      marginLeft: theme.spacing(-2),
    },
  }
}));

export default function VariantFilesContainer() {
  const classes = useStyles();

  // Error message set when file upload to the server fails
  let [ error, setError ] = useState();

  // uuids of the Subjects and the SomaticVariants questionnaire
  // To be fetch on page load
  let [ somaticVariantsUUID, setSomaticVariantsUUID ] = useState();
  let [ somaticVariantsTitle, setSomaticVariantsTitle ] = useState("");
  let [ patientSubjectUUID, setPatientSubjectUUID ] = useState();
  let [ tumorSubjectUUID, setTumorSubjectUUID ] = useState();
  let [ regionSubjectUUID, setRegionSubjectUUID ] = useState();

  let [ showVersionsDialog, setShowVersionsDialog ] = useState(false);
  let [ fileSelected, setFileSelected ] = useState(null);

  // Numerical upload progress object measured in %, for all files
  let [ uploadProgress, setUploadProgress ] = useState({});
  // Marks that a upload operation is in progress
  let [ uploadInProgress, setUploadInProgress ] = useState();

  // Main container to store info about files selected for upload
  // All corresponding subjects info derived from file and fetched will be stored here
  let [selectedFiles, setSelectedFiles] = useState([]);

  let constructQuery = (nodeType, query) => {
    let url = new URL("/query", window.location.origin);
    let sqlquery = "SELECT s.* FROM [" + nodeType + "] as s" + query;
    url.searchParams.set("query", sqlquery);
    return url;
  };

  /* FETCHING SUBJECTS INFO SECTION */

  // Fetch the SomaticVariants questionnaire and Subjects uuids
  let fetchBasicData = () => {
    fetch("/Questionnaires/SomaticVariants.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setSomaticVariantsUUID(json["jcr:uuid"]);
        setSomaticVariantsTitle(json["title"]);
      })
      .catch(handleError);

    fetch("/SubjectTypes/Patient.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {setPatientSubjectUUID(json["jcr:uuid"])})
      .catch(handleError);

    fetch("/SubjectTypes/Patient/Tumor.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {setTumorSubjectUUID(json["jcr:uuid"])})
      .catch(handleError);

    fetch("/SubjectTypes/Patient/Tumor/TumorRegion.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {setRegionSubjectUUID(json["jcr:uuid"])})
      .catch(handleError);
  };

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response.status + " " + response.statusText);
  };

  // Fetch information about patient, tumor and region subjects from file name
  // on file drop to the drag&drop zone
  let onDrop = (accepted) => {
    cleanForm();
    let chosenFiles = accepted;
    if (chosenFiles.length == 0) {
      setError("Please submit valid file type");
      return;
    }

    let files = [];
    // Can not use await due to `regeneratorRuntime is not defined` error
    // as a result - using function passing itself as resolution-callback
    (function loop(i) {
        if (i < chosenFiles.length) new Promise((resolve, reject) => {
          let file = chosenFiles[i];

          let parsed = file.name.split('.csv')[0].split('_');
          if (parsed.length < 2 || parsed[0] == "" || parsed[1] == "") {
            setError("File name " + file.name + " does not follow the name convention <subject>_<tumour nb>***.csv");
            return;
          }
          file.subject  = {"id" : parsed[0]};
          file.tumor    = {"id" : parsed[1]};
          if (parsed.length > 2) {
            file.region = {"id" : parsed.slice(2).join("_")};
          }

          setSingleFileSubjectData(file, files)
            .then( (processedFile) => {

              if (processedFile.tumor.existed && processedFile.tumor.id) {

                // query data about all of the already uploaded files
                let url = new URL("/query", window.location.origin);
                let sqlquery = `select f.* from [lfs:Form] as n inner join [nt:file] as f on isdescendantnode(f, n) where n.questionnaire = '${somaticVariantsUUID}' and n.subject = '${processedFile?.region?.uuid || processedFile.tumor.uuid}'`;
                url.searchParams.set("query", sqlquery);

                fetch(url)
                  .then((response) => response.ok ? response.json() : Promise.reject(response))
                  .then((json) => {
                    processedFile.sameFiles = json.rows.filter((row) => row["@name"] === file.name);
                    files.push(processedFile);
                    setSelectedFiles(files);
                  })
                  .finally( () => {resolve();} );
              } else {
                files.push(processedFile);
                setSelectedFiles(files);
                resolve();
              }
            })
            .catch((err) => {setError("Internal server error while fetching file versions");});
        })
        .then(loop.bind(null, i+1));
    })(0);
  };

  // Need to check among already processed files if we have any SAME existing/created subjects
  // we want to avoid multiple creations of:
  //   -- same patient subject,
  //   -- or same tuples of tumor-patient subjects where parent is patient,
  //   -- or same tuples of region-tumor subjects, where parent is tumor.
  let setExistedFileSubjectData = (file, files) => {
    for (var i in files) {
      let fileEl = files[i];
      if (file.name === fileEl.name) { continue; }

      if (fileEl.subject.id === file.subject.id) {
        file.subject = generateSubject(file.subject, fileEl.subject.path, fileEl.subject.existed, fileEl.subject.uuid, fileEl.subject.type);
      }

      if (fileEl.subject.id === file.subject.id && fileEl.tumor.id === file.tumor.id) {
        file.tumor = generateSubject(file.tumor, fileEl.tumor.path, fileEl.tumor.existed, fileEl.tumor.uuid, fileEl.tumor.type);
      }

      if (fileEl.region && file.region && fileEl.tumor.id === file.tumor.id && fileEl.region.id === file.region.id) {
        file.region = generateSubject(file.region, fileEl.region.path, fileEl.region.existed, fileEl.region.uuid, fileEl.region.type);
      }
    }
    return file;
  };

  // Fetch existed subjects data from back-end
  let setSingleFileSubjectData = (file, files) => {

    // 1. Check whether we already have any subjects info not to duplicate
    file = setExistedFileSubjectData(file, files);

    let checkSubjectExistsURL = constructQuery("lfs:Subject", ` WHERE s.'identifier'='${escapeJQL(file.subject.id)}'`);
    let checkTumorExistsURL = "";
    let checkRegionExistsURL = "";

    //  Fetch other missing subjects data - subject, tumor, region links
    return new Promise((resolve, reject) => {

        if (!file.subject.path) {

          // Fire a fetch request for the patient subject
          fetch( checkSubjectExistsURL )
            .then((response) => response.ok ? response.json() : reject(response))
            .then((json) => {
              // If a patient subject is found
              if (json.rows && json.rows.length > 0) {
                let subject = json.rows[0];
                // get the path
                file.subject = generateSubject(file.subject, subject["@path"], true, subject["jcr:uuid"], subject.type);
                file.subject.type = subject["type"];
                checkTumorExistsURL = constructQuery("lfs:Subject", ` WHERE s.'identifier'='${escapeJQL(file.tumor.id)}' AND s.'parents'='${subject['jcr:uuid']}'`);

                // Fire a fetch request for a tumor subject with the patient subject as its parent
                fetch( checkTumorExistsURL )
                    .then((response) => response.ok ? response.json() : reject(response))
                    .then((json) => {
                      // If a tumor subject is found and region subject is defined
                      if (json.rows && json.rows.length > 0) {
                        let subject = json.rows[0];
                        // get the path
                        file.tumor = generateSubject(file.tumor, subject["@path"], true, subject["jcr:uuid"], subject.type);

                        // If a region subject is defined
                        if (file.region) {
                          checkRegionExistsURL = constructQuery("lfs:Subject", ` WHERE s.'identifier'='${escapeJQL(file.region.id)}' AND s.'parents'='${subject['jcr:uuid']}'`);

                          // Fire a fetch request for a region subject with the tumor subject as its parent
                          fetch( checkRegionExistsURL )
                            .then((response) => response.ok ? response.json() : reject(response))
                            .then((json) => {
                              // If a region subject is found
                              if (json.rows && json.rows.length > 0) {
                                let subject = json.rows[0];
                                // get the path
                                file.region = generateSubject(file.region, subject["@path"], true, subject["jcr:uuid"], subject.type);
                              } else {
                                // if a region subject is not found
                                // record in variables that a region didn’t exist and generate a new random uuid as its path
                                file.region = generateSubject(file.region);
                              }
                              resolve(file);
                            })
                            .catch((err) => {console.log(err); reject(err);})
                        } else {
                          resolve(file);
                        }

                      } else {
                        // if a tumor subject is not found
                        // record in variables that a tumor and a region didn’t exist and generate a new random uuid as their path
                        file.tumor  = generateSubject(file.tumor);
                        if (file.region) {
                            file.region = generateSubject(file.region);
                        }
                        resolve(file);
                      }
                    })
                    .catch((err) => {console.log(err); reject(err);})

              } else {
                // If a patient subject is not found:
                // fetch existing or record in variables that it didn’t exist, and generate a new random uuid as its path
                file.subject = generateSubject(file.subject);
                file.tumor   = generateSubject(file.tumor);
                if (file.region) {
                    file.region = generateSubject(file.region);
                }
                resolve(file);
              }
            })
            .catch((err) => {console.log(err); reject(err);})


        } else {
          if (!file.tumor.path) {
            checkTumorExistsURL = constructQuery("lfs:Subject", ` WHERE s.'identifier'='${escapeJQL(file.tumor.id)}' AND s.'parents'='${file.subject.uuid}'`);

            // Fire a fetch request for a tumor subject with the patient subject as its parent
            fetch( checkTumorExistsURL )
                .then((response) => response.ok ? response.json() : reject(response))
                .then((json) => {
                  // If a tumor subject is found and region subject is defined
                  if (json.rows && json.rows.length > 0) {
                    let subject = json.rows[0];
                    // get the path
                    file.tumor = generateSubject(file.tumor, subject["@path"], true, subject["jcr:uuid"], subject.type);

                    // If a region subject is defined
                    if (file.region) {
                      checkRegionExistsURL = constructQuery("lfs:Subject", ` WHERE s.'identifier'='${escapeJQL(file.region.id)}' AND s.'parents'='${subject['jcr:uuid']}'`);

                      // Fire a fetch request for a region subject with the tumor subject as its parent
                      fetch( checkRegionExistsURL )
                        .then((response) => response.ok ? response.json() : reject(response))
                        .then((json) => {
                          // If a region subject is found
                          if (json.rows && json.rows.length > 0) {
                            let subject = json.rows[0];
                            // get the path
                            file.region = generateSubject(file.region, subject["@path"], true, subject["jcr:uuid"], subject.type);
                          } else {
                            // if a region subject is not found
                            // record in variables that a region didn’t exist, and generate a new random uuid as its path
                            file.region = generateSubject(file.region);
                          }
                          resolve(file);
                        })
                        .catch((err) => {console.log(err); reject(err);})
                    } else {
                      resolve(file);
                    }

                  } else {
                    // if a tumor subject is not found
                    // record in variables that a tumor and a region didn’t exist, and generate a new random uuid as their path
                    file.tumor  = generateSubject(file.tumor);
                    if (file.region) {
                      file.region = generateSubject(file.region);
                    }
                    resolve(file);
                  }
                })
                .catch((err) => {console.log(err); reject(err);})

          } else {
            if (file.region && !file.region.path) {
              checkRegionExistsURL = constructQuery("lfs:Subject", ` WHERE s.'identifier'='${escapeJQL(file.region.id)}' AND s.'parents'='${file.tumor.uuid}'`);

              // Fire a fetch request for a region subject with the tumor subject as its parent
              fetch( checkRegionExistsURL )
                .then((response) => response.ok ? response.json() : reject(response))
                .then((json) => {
                  // If a region subject is found
                  if (json.rows && json.rows.length > 0) {
                    let subject = json.rows[0];
                    // get the path
                    file.region = generateSubject(file.region, subject["@path"], true, subject["jcr:uuid"], subject.type);
                  } else {
                    // if a region subject is not found
                    // record in variables that a region didn’t exist, and generate a new random uuid as its path
                    file.region = generateSubject(file.region);
                  }
                  resolve(file);
                })
                .catch((err) => {console.log(err); reject(err);})
            } else {
              resolve(file);
            }
          }
        }

    });
  };

  // Generate subject JSON in a form 'subject: {exists: true, id: <id>, @path: <path>, uuid: <uuid>}'
  // note - 'id' is already in subject object recorded at the load stage parsed from the filename
  //
  // Possible 3 scenarios:
  // 1. subject existed and was just fetched
  // 2. subject did not exist, need to create completely new
  // 3. subject did not exist, but was just created for one of previous loaded files, so it has path but no uuid
  //
  let generateSubject = (subject, path, existed, uuid, type) => {
    subject.existed = existed;
    subject.path = path || "/Subjects/" + uuidv4();
    subject.uuid = uuid;
    subject.type = existed && type;
    return subject;
  };

  // Change of subject id implies reset patient subject info and re-fetching all data
  let setSubject = (id, fileName) => {
    let newFiles = selectedFiles.slice();
    let index = newFiles.findIndex(file => file.name === fileName);
    newFiles[index].subject.id = id;
    newFiles[index].subject.existed = false;
    newFiles[index].subject.uuid = null;
    newFiles[index].subject.path = null;

    setSingleFileSubjectData(newFiles[index], selectedFiles)
      .then((file) => {
          // find all files with this name
          newFiles[index] = file;
          setSelectedFiles(newFiles);
      })
      .catch((err) => {setError("Internal server error while fetching file versions for " + fileName);});
  };

  // Change of subject id implies reset tumor subject info and re-fetching all data
  let setTumor = (id, fileName) => {
    let newFiles = selectedFiles.slice();
    let index = newFiles.findIndex(file => file.name === fileName);
    newFiles[index].tumor.id = id;
    newFiles[index].tumor.existed = false;
    newFiles[index].tumor.uuid = null;
    newFiles[index].tumor.path = null;

    setSingleFileSubjectData(newFiles[index], selectedFiles)
      .then((file) => {
          // find all files with this name
          newFiles[index] = file;
          setSelectedFiles(newFiles);
        })
      .catch((err) => {setError("Internal server error while fetching file versions for " + fileName);});
  };

  // Change of subject id implies reset region subject info and re-fetching all data
  let setRegion = (id, fileName) => {
    let newFiles = selectedFiles.slice();
    let index = newFiles.findIndex(file => file.name === fileName);
    newFiles[index].region.id = id;
    newFiles[index].region.existed = false;
    newFiles[index].region.uuid = null;
    newFiles[index].region.path = null;

    setSingleFileSubjectData(newFiles[index], selectedFiles)
      .then((file) => {
          // find all files with this name
          newFiles[index] = file;
          setSelectedFiles(newFiles);
        })
      .catch((err) => {setError("Internal server error while fetching file versions for " + fileName);});
  };

  let cleanForm = () => {
    setUploadInProgress(false);
    setSelectedFiles([]);
    setError("");
    setUploadProgress({});
  };

   // Find the icon and load them
  let uploadAllFiles = (selectedFiles) => {
    const promises = [];
    selectedFiles.forEach(file => {
      promises.push(uploadSingleFile(file));
    });

    return Promise.all(promises);
  };

  // Event handler for the form submission event, replacing the normal browser form submission with a background fetch request.
  let upload = (event) => {
    // This stops the normal browser form submission
    event.preventDefault();

    // TODO - handle possible logged out situation here - open a login popup

    setUploadInProgress(true);
    setUploadProgress({});
    setError("");

    uploadAllFiles(selectedFiles)
      .then(() => {

        setUploadInProgress(false);
      })
      .catch( (error) => {

        handleError(error);
        setUploadInProgress(false);
    });
  };

  let uploadSingleFile = (file) => {
    return new Promise((resolve, reject) => {

      var reader = new FileReader();
      reader.readAsText(file);

      //When the file finish load
      reader.onload = function(event) {

        // get the file data
        var csv = event.target.result;
        let json = assembleJson(file, csv);

        let data = new FormData();
        data.append(':contentType', 'json');
        data.append(':operation', 'import');
        data.append(':content', JSON.stringify(json));

        var xhr = new XMLHttpRequest()
        xhr.open('POST', '/')

        xhr.onload = function() {

          if (xhr.status != 201) {
            uploadProgress[file.name] = { state: "error", percentage: 0 };
            console.log("Error", xhr.statusText)
          } else {
            // state: "done" change should turn all subject inputs into the link text
            uploadProgress[file.name] = { state: "done", percentage: 100 };

            file.formPath = "/" + Object.keys(json).find(str => str.startsWith("Forms/"));
            selectedFiles[selectedFiles.findIndex(el => el.name === file.name)] = file;
            setSelectedFiles(selectedFiles);
          }

          setUploadProgress(uploadProgress);
          resolve(xhr.response);
        }

        xhr.onerror = function() {
          uploadProgress[file.name] = { state: "error", percentage: 0 };
          setUploadProgress(uploadProgress);
          resolve(xhr.response);
        }

        xhr.upload.onprogress = function (event) {

          if (event.lengthComputable) {
            let done = event.position || event.loaded;
            let total = event.totalSize || event.total;
            let percent = Math.round((done / total) * 100);
            const copy = { ...uploadProgress };
            copy[file.name] = { state: "pending", percentage: percent };
            setUploadProgress(copy);
          }
        }
        xhr.send(data);
      }
    });
  };

  let generateSubjectJson = (refType, id, parent) => {
    let info = {};
    info["jcr:primaryType"] = "lfs:Subject";
    info["jcr:reference:type"] = "/SubjectTypes/" + refType;
    info["identifier"] = id;
    if (parent) { info["jcr:reference:parents"] = parent; }
    return info;
  };

  let assembleJson = (file, csvData) => {
      let json = {};

      let subjectPath = file.subject?.path?.replace("/Subjects", "Subjects");
      let tumorPath = file.tumor?.path?.replace(new RegExp(".+/"), "");
      let regionPath = file.region?.path?.replace(new RegExp(".+/"), "");
      if (!file.subject.existed) {
        json[subjectPath] = generateSubjectJson("Patient", file.subject.id);
      } else {
        json[subjectPath] = {};
      }

      if (!file.tumor.existed) {
        json[subjectPath][tumorPath] = generateSubjectJson("Patient/Tumor", file.tumor.id, file.subject.path);
      } else {
        json[subjectPath][tumorPath] = {};
      }

      if (file.region && !file.region.existed) {
        json[subjectPath][tumorPath][regionPath] = generateSubjectJson("Patient/Tumor/TumorRegion", file.region.id, file.tumor.path);
      }

      let formPath = "Forms/" + uuidv4();
      let formInfo = {};
      formInfo["jcr:primaryType"] = "lfs:Form";
      formInfo["jcr:reference:questionnaire"] = "/Questionnaires/SomaticVariants";
      // The subject of the questionnaire is the region
      formInfo["jcr:reference:subject"] = `/${subjectPath}/${tumorPath}` + (file?.region?.path ? `/${regionPath}` : "");

      let fileID = uuidv4();
      let fileInfo = {};
      fileInfo["jcr:primaryType"] = "lfs:FileResourceAnswer";
      fileInfo["jcr:reference:question"] = "/Questionnaires/SomaticVariants/file";
      fileInfo["value"] = "/" + formPath + "/" + fileID + "/" + file.name;

      let fileDetails = {};
      fileDetails["jcr:primaryType"] = "nt:file";
      fileDetails["jcr:content"] = {};
      fileDetails["jcr:content"]["jcr:primaryType"] = "nt:resource";

      fileDetails["jcr:content"]["jcr:data"] = csvData;

      fileInfo[file.name] = fileDetails;

      formInfo[fileID] = fileInfo;

      json[formPath] = formInfo;

      return json;
  };

  if (!somaticVariantsUUID) {
    fetchBasicData();
  }

  return (
  <React.Fragment>
    <Typography variant="h2">Variants Upload</Typography>
      <form method="POST"
            encType="multipart/form-data"
            onSubmit={upload}
            key="file-upload"
            id="variantForm">
        <Grid container direction="row-reverse" justify="flex-end" spacing={3} alignItems="stretch" className={classes.dragAndDropContainer}>
          <Grid item xs={12} lg={6}>
            <Alert severity="info">
              <AlertTitle>Expected file name format:</AlertTitle>
              <div>Patient_Tumor.csv (e.g. AB12345_1.csv)</div>
              <div>Patient_Tumor_TumorRegion.csv (e.g. AB12345_1_a.csv)</div>
            </Alert>
          </Grid>
          <Grid item xs={12} lg={6}>
          { uploadInProgress && (
              <Grid item className={classes.root}>
                <LinearProgress color="primary" />
              </Grid>
            ) }

            <div className={classes.dragAndDrop}>
              <DragAndDrop
                accept={".csv"}
                multifile={false}
                handleDrop={onDrop}
                classes={classes}
                error={error}
              />
            </div>

            <input type="hidden" name="*@TypeHint" value="nt:file" />
          </Grid>
        </Grid>
      </form>

      { selectedFiles && selectedFiles.length > 0 && <>
        { selectedFiles.slice(0, 1).map( (file, i) => {
            //          ^ Temporarily ignore all but the first selected file until concurency issues are solved

            const upprogress = uploadProgress ? uploadProgress[file.name] : null;
            let subjectPath = file.subject.path.replace("/Subjects", "Subjects");
            let tumorPath = `${subjectPath}/${file.tumor.path.replace(new RegExp(".+/"), "")}`;
            let regionPath = file.region?.path && `${tumorPath}/${file.region.path.replace(new RegExp(".+/"), "")}`;

            return (
              <div key={file.name} className={classes.fileInfo}>
                <div>
                  <Typography variant="h6" className={classes.fileName}>{file.name}:</Typography>
                  { upprogress && upprogress.state != "error" &&
                    <span>
                      <div className={classes.progressBar}>
                        <div className={classes.progress} style={{ width: upprogress.percentage + "%" }} />
                      </div>
                      { upprogress.percentage + "%" }
                    </span>
                  }
                  { upprogress && upprogress.state == "error" && <Typography color='error'>Error uploading file</Typography> }
                </div>
                { uploadProgress && uploadProgress[file.name] && uploadProgress[file.name].state === "done" ?
                  <Typography variant="overline" component="div" className={classes.fileDetail}>
                    {file.subject?.type?.label || "Patient"} <Link href={subjectPath} target="_blank"> {file.subject.id} </Link> /&nbsp;
                    {file.tumor?.type?.label || "Tumor"} <Link href={tumorPath} target="_blank"> {file.tumor.id} </Link>
                    { file?.region?.path && <> / {file.region?.type?.label || "Tumor Region"}: <Link href={regionPath} target="_blank"> {file.region.id} </Link> </> }
                    { file.formPath && <> : <Link href={file.formPath.replace("/Forms", "Forms")} target="_blank">{somaticVariantsTitle}</Link> </>}
                  </Typography>
                : <div className={classes.fileFormSection}>
                  <TextField
                    label="Patient"
                    value={file.subject.id}
                    onChange={(event) => setSubject(event.target.value, file.name)}
                    className={classes.fileDetail}
                    required
                  />
                  <TextField
                    label="Tumor"
                    value={file.tumor.id}
                    onChange={(event) => setTumor(event.target.value, file.name)}
                    className={classes.fileDetail}
                    required
                  />
                  <TextField
                    label="Tumor Region"
                    value={file?.region?.id}
                    onChange={(event) => setRegion(event.target.value, file.name)}
                    className={classes.fileDetail}
                  />
                  <label htmlFor="contained-button-file">
                    <Button type="submit" variant="contained" color="primary" disabled={uploadInProgress || !!error && selectedFiles.length == 0} className={classes.uploadButton} form="variantForm">
                      <span><BackupIcon className={classes.buttonIcon}/>
                        {uploadInProgress ? 'Uploading' :
                            // TODO - Make this a per-upload button, pending the completion of LFS-535
                            // TODO - judge upload status button message over all upload statuses of all files ??
                            // uploadProgress[file.name].state =="done" ? 'Uploaded' :
                            // uploadProgress[file.name].state =="error" ? 'Upload failed, try again?' :
                            'Upload'}
                      </span>
                    </Button>
                  </label>
                  </div>
                }
                {(!file.sameFiles || file.sameFiles.length == 0)
                  ?
                    <Typography variant="caption" component="p">There are no versions of this file.</Typography>
                  :
                    <Link
                      variant="caption"
                      underline="none"
                      href="#"
                      onClick={() => {
                        setShowVersionsDialog(true);
                        setFileSelected(file);
                      }}>
                        There {file.sameFiles.length == 1 ? "is one other version " : <>are {file.sameFiles.length} other versions </>}
                        of this file
                      </Link>
                }
              </div>
          ) } ) }
      </>
      }
    <Dialog open={showVersionsDialog} onClose={() => setShowVersionsDialog(false)}>
      <DialogTitle>
        <span className={classes.dialogTitle}>Versions of {fileSelected?.name}</span>
        <IconButton onClick={() => setShowVersionsDialog(false)} className={classes.closeButton}>
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent className={classes.dialogContent}>
        <MaterialTable
          data={fileSelected?.sameFiles}
          style={{ boxShadow : 'none' }}
          options={{
            toolbar: false,
            rowStyle: {
              verticalAlign: 'top',
            }
          }}
          title={""}
          columns={[
            { title: 'Created',
              cellStyle: {
                paddingLeft: 0,
                fontWeight: "bold",
                width: '1%',
                whiteSpace: 'nowrap',
              },
              render: rowData => <Link href={rowData["@path"]}>
                                  {moment(rowData['jcr:created']).format("YYYY-MM-DD")}
                                </Link> },
            { title: 'Uploaded By',
              cellStyle: {
                width: '50%',
                whiteSpace: 'pre-wrap',
                paddingBottom: "8px",
              },
              render: rowData => rowData["jcr:createdBy"] },
            { title: 'Actions',
              cellStyle: {
                padding: '0',
                width: '20px',
                textAlign: 'end'
              },
              sorting: false,
              render: rowData => <Tooltip title={"Download"}>
                                  <IconButton>
                                    <Link underline="none" color="inherit" href={rowData["@path"]} download><GetApp /></Link>
                                  </IconButton>
                                </Tooltip> },
          ]}
          />
      </DialogContent>
    </Dialog>
  </React.Fragment>
  );
}
