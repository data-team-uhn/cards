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

import React, { useContext, useState } from "react";

import {
  Box,
  Button,
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
} from "@mui/material";
import makeStyles from '@mui/styles/makeStyles';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import BackupIcon from '@mui/icons-material/Backup';
import CloseIcon from '@mui/icons-material/Close';
import GetApp from '@mui/icons-material/GetApp';
import MaterialReactTable from "material-react-table";
import { v4 as uuidv4 } from 'uuid';
import { DateTime } from "luxon";
import DragAndDrop from "./components/dragAndDrop.jsx";
import { escapeJQL } from "./escape.jsx";
import { fetchWithReLogin, GlobalLoginContext } from "./login/loginDialogue.js";

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
  fileList: {
    "& .MuiGrid-item" : {
      paddingLeft: theme.spacing(4),
    }
  },
  fileFormSection : {
    display: "flex",
    alignItems: "center",
    flexWrap: "wrap",
  },
  fileDetail: {
    marginRight: theme.spacing(1),
    marginTop: theme.spacing(1)
  },
  fileProgress: {
    maxWidth: "750px",
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

  const globalLoginDisplay = useContext(GlobalLoginContext);

  // Error message set when file upload to the server fails
  let [ error, setError ] = useState();

  // uuids of the Subjects and the SomaticVariants questionnaire
  // To be fetch on page load
  let [ somaticVariantsUUID, setSomaticVariantsUUID ] = useState();
  let [ somaticVariantsTitle, setSomaticVariantsTitle ] = useState("");
  let [ patientSubjectLabel, setPatientSubjectLabel ] = useState();
  let [ tumorSubjectLabel, setTumorSubjectLabel ] = useState();
  let [ regionSubjectLabel, setRegionSubjectLabel ] = useState();

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
    fetchWithReLogin(globalLoginDisplay, "/Questionnaires/SomaticVariants.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setSomaticVariantsUUID(json["jcr:uuid"]);
        setSomaticVariantsTitle(json["title"]);
      })
      .catch(handleError);

    fetchWithReLogin(globalLoginDisplay, "/SubjectTypes/Patient.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setPatientSubjectLabel(json["label"]);
      })
      .catch(handleError);

    fetchWithReLogin(globalLoginDisplay, "/SubjectTypes/Patient/Tumor.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setTumorSubjectLabel(json["label"]);
      })
      .catch(handleError);

    fetchWithReLogin(globalLoginDisplay, "/SubjectTypes/Patient/Tumor/TumorRegion.json")
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((json) => {
        setRegionSubjectLabel(json["label"]);
      })
      .catch(handleError);
  };

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response.status + " " + response.statusText);
  };

  let updateFileExistsStatus = (processedFile, fileName) => {
    if (processedFile.tumor.existed && processedFile.tumor.id) {
      // query data about all of the already uploaded files
      let url = new URL("/query", window.location.origin);
      let sqlquery = `select f.* from [cards:Form] as n inner join [nt:file] as f on isdescendantnode(f, n) where n.questionnaire = '${somaticVariantsUUID}' and n.subject = '${processedFile.region?.uuid || processedFile.tumor.uuid}'`;
      url.searchParams.set("query", sqlquery);

      return fetchWithReLogin(globalLoginDisplay, url)
        .then((response) => response.ok ? response.json() : Promise.reject(response))
        .then((json) => {
          processedFile.sameFiles = json.rows.filter((row) => row["@name"] === fileName);
          return processedFile;
        });
    } else {
      return new Promise((resolve, reject) => resolve(processedFile));
    }
  }

  // Fetch information about patient, tumor and region subjects from file name
  // on file drop to the drag&drop zone
  let onDrop = (accepted) => {
    cleanForm();
    // Clone the chosenFiles fileList, in order to prevent weirdness
    let chosenFiles = [...accepted];
    if (chosenFiles.length == 0) {
      setError("Please submit valid file type");
      return;
    }

    let files = [];
    // Can not use await due to `regeneratorRuntime is not defined` error
    // as a result - using function passing itself as resolution-callback
    let allErroneousFiles = [];
    (function loop(i) {
        if (i < chosenFiles.length) {
          new Promise((resolve, reject) => {
            let file = chosenFiles[i];

            let parsed = file.name.split('.csv')[0].split('_');
            if (parsed.length < 2 || parsed[0] == "" || parsed[1] == "") {
              allErroneousFiles.push(file.name);
              resolve();
              return;
            }
            file.subject  = {"id" : parsed[0]};
            file.tumor    = {"id" : parsed[1]};
            if (parsed.length > 2) {
              file.region = {"id" : parsed.slice(2).join("_")};
            } else {
              file.region = {};
            }
            file.sent = false;
            file.uploading = false;

            setSingleFileSubjectData(file, files)
              .then((processedFile) => updateFileExistsStatus(processedFile, file.name))
              .then((processedFile) => {
                files = files.slice();
                files.push(processedFile);
                setSelectedFiles(files);
              })
              .catch((err) => {setError("Internal server error while fetching file versions"); console.log(err);})
              .finally(() => resolve());
          })
          .then(loop.bind(null, i+1))
        } else if (allErroneousFiles.length > 0) {
          // Display an error for each incorrect filename
          // There are three cases for pluralizing a list of strings
          // 1: there is no plural
          let fileString = allErroneousFiles.length == 1 ? allErroneousFiles[0]
          // 2: it is a list of two (no oxford comma)
            : allErroneousFiles.length == 2 ? allErroneousFiles.join(" and ")
          // 3: it is a list of three or more (with oxford comma)
            : allErroneousFiles.splice(0, allErroneousFiles.length-1).join(", ") + ", and " + allErroneousFiles[allErroneousFiles.length-1];
          let plural = allErroneousFiles.length > 1;
          setError(`File name${plural ? "s" : ""} ${fileString} do${plural ? "" : "es"} not follow the name convention <subject>_<tumour nb>***.csv`);
        };
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

      if (fileEl.region.id && file.region.id && fileEl.subject.id === file.subject.id && fileEl.tumor.id === file.tumor.id && fileEl.region.id === file.region.id) {
        file.region = generateSubject(file.region, fileEl.region.path, fileEl.region.existed, fileEl.region.uuid, fileEl.region.type);
      }
    }
    return file;
  };

  // Fetch existed subjects data from back-end
  let setSingleFileSubjectData = (file, files) => {

    // 1. Check whether we already have any subjects info not to duplicate
    file = setExistedFileSubjectData(file, files);

    let checkSubjectExistsURL = constructQuery("cards:Subject", ` WHERE s.'identifier'='${escapeJQL(file.subject.id)}'`);
    let checkTumorExistsURL = "";
    let checkRegionExistsURL = "";

    //  Fetch other missing subjects data - subject, tumor, region links
    return new Promise((resolve, reject) => {

        if (!file.subject.path) {

          // Fire a fetch request for the patient subject
          fetchWithReLogin(globalLoginDisplay, checkSubjectExistsURL)
            .then((response) => response.ok ? response.json() : reject(response))
            .then((json) => {
              // If a patient subject is found
              if (json.rows && json.rows.length > 0) {
                let subject = json.rows[0];
                // get the path
                file.subject = generateSubject(file.subject, subject["@path"], true, subject["jcr:uuid"], subject.type);
                file.subject.type = subject["type"];
                checkTumorExistsURL = constructQuery("cards:Subject", ` WHERE s.'identifier'='${escapeJQL(file.tumor.id)}' AND s.'parents'='${subject['jcr:uuid']}'`);

                // Fire a fetch request for a tumor subject with the patient subject as its parent
                fetchWithReLogin(globalLoginDisplay, checkTumorExistsURL)
                    .then((response) => response.ok ? response.json() : reject(response))
                    .then((json) => {
                      // If a tumor subject is found and region subject is defined
                      if (json.rows && json.rows.length > 0) {
                        let subject = json.rows[0];
                        // get the path
                        file.tumor = generateSubject(file.tumor, subject["@path"], true, subject["jcr:uuid"], subject.type);

                        // If a region subject is defined
                        if (file.region?.id) {
                          checkRegionExistsURL = constructQuery("cards:Subject", ` WHERE s.'identifier'='${escapeJQL(file.region.id)}' AND s.'parents'='${subject['jcr:uuid']}'`);

                          // Fire a fetch request for a region subject with the tumor subject as its parent
                          fetchWithReLogin(globalLoginDisplay, checkRegionExistsURL)
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
                        file.region = generateSubject(file.region);
                        resolve(file);
                      }
                    })
                    .catch((err) => {console.log(err); reject(err);})

              } else {
                // If a patient subject is not found:
                // fetch existing or record in variables that it didn’t exist, and generate a new random uuid as its path
                file.subject = generateSubject(file.subject);
                file.tumor   = generateSubject(file.tumor);
                file.region = generateSubject(file.region);
                resolve(file);
              }
            })
            .catch((err) => {console.log(err); reject(err);})


        } else {
          if (!file.tumor.path) {
            checkTumorExistsURL = constructQuery("cards:Subject", ` WHERE s.'identifier'='${escapeJQL(file.tumor.id)}' AND s.'parents'='${file.subject.uuid}'`);

            // Fire a fetch request for a tumor subject with the patient subject as its parent
            fetchWithReLogin(globalLoginDisplay, checkTumorExistsURL)
                .then((response) => response.ok ? response.json() : reject(response))
                .then((json) => {
                  // If a tumor subject is found and region subject is defined
                  if (json.rows && json.rows.length > 0) {
                    let subject = json.rows[0];
                    // get the path
                    file.tumor = generateSubject(file.tumor, subject["@path"], true, subject["jcr:uuid"], subject.type);

                    // If a region subject is defined
                    if (file.region?.id) {
                      checkRegionExistsURL = constructQuery("cards:Subject", ` WHERE s.'identifier'='${escapeJQL(file.region.id)}' AND s.'parents'='${subject['jcr:uuid']}'`);

                      // Fire a fetch request for a region subject with the tumor subject as its parent
                      fetchWithReLogin(globalLoginDisplay, checkRegionExistsURL)
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
                    file.region = generateSubject(file.region);
                    resolve(file);
                  }
                })
                .catch((err) => {console.log(err); reject(err);})

          } else {
            if (file.region?.id && !file.region.path) {
              checkRegionExistsURL = constructQuery("cards:Subject", ` WHERE s.'identifier'='${escapeJQL(file.region.id)}' AND s.'parents'='${file.tumor.uuid}'`);

              // Fire a fetch request for a region subject with the tumor subject as its parent
              fetchWithReLogin(globalLoginDisplay, checkRegionExistsURL)
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
    if (subject.id) {
      subject.existed = existed;
      subject.path = path || "/Subjects/" + uuidv4();
      subject.uuid = uuid;
      subject.type = existed && type;
    }
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
      .then((file) => updateFileExistsStatus(file, fileName))
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
      .then((file) => updateFileExistsStatus(file, fileName))
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
      .then((file) => updateFileExistsStatus(file, fileName))
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

  let uploadJSON = (json, url='/', additionalArgs={}) => {
    let data = new FormData();
    data.append(':contentType', 'json');
    data.append(':operation', 'import');
    data.append(':content', JSON.stringify(json));
    return fetchWithReLogin(
      globalLoginDisplay,
      url,
      { method: 'POST', body: data, ...additionalArgs }
    );
  }

  /**
   * Create each subject in a single request, to prevent concurrency issues involved in creating multiple subjects.
   *
   * @param {array} toUpload Array of File objects to upload. These file objects should be processed via onDrop first
   * @returns {Promise} a POST request with the subject upload
   */
  let uploadSubjectsFirst = (toUpload) => {
    // Create a JSON representation of each subject that needs to be created.
    let newSubjects = {};
    toUpload.forEach((file) => {
      let [newJson, subjectPath, tumorPath, regionPath] = assembleSubjectJson(file);
      // Add this JSON to ours, making sure not to overwrite anything already existing
      if (subjectPath in newSubjects) {
        if (tumorPath in newSubjects[subjectPath]) {
          if (file.region?.id) {
            if (regionPath in newSubjects[subjectPath][tumorPath]) {
              // This region already exists in the list of things we need to create
              // So, we ignore it
            } else {
              newSubjects[subjectPath][tumorPath][regionPath] = newJson[subjectPath][tumorPath][regionPath];
            }
            file.region.existed = true;
          }
        } else {
          newSubjects[subjectPath][tumorPath] = newJson[subjectPath][tumorPath];
        }
        file.tumor.existed = true;
      } else {
        newSubjects[subjectPath] = newJson[subjectPath];
      }
      file.subject.existed = true;
    })

    // Upload each file's subject in one batch
    return uploadJSON(newSubjects);
  }

   // Find the icon and load them
  let uploadAllFiles = () => {
    return uploadSubjectsFirst(selectedFiles).then(() => {
      let promises = [];
      selectedFiles.forEach(file => {
        if (!file.uploading && !file.sent) {
          promises.push(uploadSingleFile(file, false));
        }
      });
      return Promise.all(promises);
    });
  };

  // Event handler for the form submission event, replacing the normal browser form submission with a background fetch request.
  let upload = (event) => {
    // Stop the normal browser form submission
    event.preventDefault();

    setUploadInProgress(true);
    setUploadProgress((old) => {
      let progressCopy = {...old};
      selectedFiles.forEach((file) => {
        if (!file.sent && !file.uploading) {
          progressCopy[file.name] = { state: "pending", percentage: 0 };
        }
      });
      return progressCopy;
    });
    setError("");

    uploadAllFiles()
      .then(() => {
        setUploadInProgress(false);
      })
      .catch( (error) => {

        handleError(error);
        setUploadInProgress(false);
    });
  };

  /**
   * Upload a single variant file.
   *
   * @param {File} file File object to upload. This file objects should be processed via onDrop first
   * @param {Boolean} generateSubjects If true, uploads the data corresponding to the subject of the file (if it does not already exist)
   */
  let uploadSingleFile = (file, generateSubjects=true) => {
    let [subjectJson, subjectPath, tumorPath, regionPath] = assembleSubjectJson(file);
    file.uploading = true;
    let retPromise = () => new Promise((resolve, reject) => {
      // get the file data
      let data = new FormData();

      // Assemble the questionnaire into our payload FormData
      let formPath = "Forms/" + uuidv4();
      data.append(formPath + "/jcr:primaryType", "cards:Form");
      data.append(formPath + "/questionnaire", "/Questionnaires/SomaticVariants");
      data.append(formPath + "/questionnaire@TypeHint", "Reference");
      data.append(formPath + "/subject", `/${subjectPath}/${tumorPath}` + (file?.region?.path ? `/${regionPath}` : ""));
      data.append(formPath + "/subject@TypeHint", "Reference");

      // Assemble the FileAnswer
      let answerPath = formPath + "/" + uuidv4();
      data.append(answerPath + "/jcr:primaryType", "cards:FileAnswer");
      data.append(answerPath + "/question", "/Questionnaires/SomaticVariants/file");
      data.append(answerPath + "/question@TypeHint", "Reference");
      data.append(answerPath + "/value", "/" + answerPath + "/" + file.name);

      // Assemble the details about the file itself
      let filePath = answerPath + "/" + file.name;
      data.append(filePath, file);
      data.append(filePath + "/@TypeHint", "nt:file");

      var xhr = new XMLHttpRequest();
      xhr.open('POST', '/');

      xhr.onload = function() {

        if (xhr.status != 200) {
          uploadProgress[file.name] = { state: "error", percentage: 0 };
          console.log("Error", xhr.statusText)
        } else {
          // state: "done" change should turn all subject inputs into the link text
          uploadProgress[file.name] = { state: "done", percentage: 100 };

          file.formPath = formPath;
          file.sent = true;
          selectedFiles[selectedFiles.findIndex(el => el.name === file.name)] = file;
          setSelectedFiles(selectedFiles);
        }

        setUploadProgress(uploadProgress);
        resolve(xhr.response);
      };

      xhr.onerror = function() {
        uploadProgress[file.name] = { state: "error", percentage: 0 };
        file.uploading = false;
        setUploadProgress(uploadProgress);
        // Note that we don't want to reject, lest the Promises.all() call aborts
        // another concurrent file upload
        resolve(xhr.response);
      };

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
    });

    if (generateSubjects) {
      // Upload the subject first, then the form
      return uploadJSON(subjectJson).then(retPromise);
    } else {
      // Just upload the form
      return retPromise();
    }
  };

  /**
   * Generate the JSON representation of a single subject
   *
   * @param {string} refType refType The type for this subject, without the `/SubjectType/` path prefix, e.g. "Patient/Tumor"
   * @param {string} id The identifier for this subject
   * @param {string} parent If given, supplies a parent for this subject
   */
  let generateSubjectJson = (refType, id, parent) => {
    let info = {};
    info["jcr:primaryType"] = "cards:Subject";
    info["jcr:reference:type"] = "/SubjectTypes/" + refType;
    info["identifier"] = id;
    if (parent) { info["jcr:reference:parents"] = parent; }
    return info;
  };

  /**
   * Generate the JSON representation of the subjects from a file
   * This will not generate details for any subject that already exists
   *
   * @param {File} file the File object from which we can derive subject information. This should be processed via the
   * functions in onDrop prior to using this function
   * @returns {array} An array of [JSON of the subjects, subject's key in the JSON object, tumor key in the subject, region key]
   */
  let assembleSubjectJson = (file) => {
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

    if (file.region?.id && !file.region.existed) {
      json[subjectPath][tumorPath][regionPath] = generateSubjectJson("Patient/Tumor/TumorRegion", file.region.id, file.tumor.path);
    }
    return [json, subjectPath, tumorPath, regionPath];
  }

  if (!somaticVariantsUUID) {
    fetchBasicData();
  }

  // Only show the upload all button if more than one file is selected
  let showUploadAllButton = selectedFiles.length > 1;
  // and only enable it if there is at least one file that has yet to be sent
  let showUploadDisabled = !selectedFiles.some((file) => !file.uploading && !file.sent);
  let uploadAllComplete = !selectedFiles.some((file) => !file.sent);

  return (
  <React.Fragment>
    <Typography variant="h2">Variants Upload</Typography>
      <form method="POST"
            encType="multipart/form-data"
            onSubmit={upload}
            key="file-upload"
            id="variantForm">
        <Grid container direction="row-reverse" justifyContent="flex-end" spacing={3} alignItems="stretch" className={classes.dragAndDropContainer}>
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
                multifile={true}
                handleDrop={onDrop}
                error={error}
              />
            </div>

            <input type="hidden" name="*@TypeHint" value="nt:file" />
          </Grid>
        </Grid>
      </form>

      { selectedFiles && selectedFiles.length > 0 && <Grid container direction="column" spacing={4} className={classes.fileList}>
        { selectedFiles.map( (file, i) => {
            const upprogress = uploadProgress ? uploadProgress[file.name] : null;
            let subjectPath = file.subject.path?.replace("/Subjects", "Subjects");
            let tumorPath = file.tumor.path && `${subjectPath}/${file.tumor.path.replace(new RegExp(".+/"), "")}`;
            let regionPath = file.region.path && `${tumorPath}/${file.region.path?.replace(new RegExp(".+/"), "")}`;
            let isDataValid = subjectPath && tumorPath;

            return (
              <Grid item key={file.name}>
                <Typography variant="h6">{file.name}</Typography>
                { upprogress && upprogress.state != "error" &&
                  <Box display="flex" alignItems="center" className={classes.fileProgress}>
                    <Box width="100%" mr={1}>
                      <LinearProgress variant="determinate" value={upprogress.percentage} />
                    </Box>
                    <Box minWidth={35}>
                      <Typography variant="body2" color="textSecondary">{upprogress.percentage + "%"}</Typography>
                    </Box>
                  </Box>
                }
                { upprogress && upprogress.state == "error" && <Typography color='error'>Error uploading file</Typography> }
                { uploadProgress && uploadProgress[file.name] && uploadProgress[file.name].state === "done" ?
                  <Typography variant="overline" component="div">
                    {patientSubjectLabel} <Link href={subjectPath} target="_blank" underline="hover"> {file.subject.id} </Link> /&nbsp;
                    {tumorSubjectLabel} <Link href={tumorPath} target="_blank" underline="hover"> {file.tumor.id} </Link>
                    { file?.region?.path && <> / {regionSubjectLabel} <Link href={regionPath} target="_blank" underline="hover"> {file.region.id} </Link> </> }
                    { file.formPath && <> : <Link href={file.formPath} target="_blank" underline="hover">{somaticVariantsTitle}</Link> </>}
                  </Typography>
                : <div className={classes.fileFormSection}>
                  <TextField
                    variant="standard"
                    label={patientSubjectLabel}
                    value={file.subject.id}
                    onChange={(event) => setSubject(event.target.value, file.name)}
                    className={classes.fileDetail}
                    required
                    error={!subjectPath}
                    helperText="Required"
                  />
                  <TextField
                    variant="standard"
                    label={tumorSubjectLabel}
                    value={file.tumor.id}
                    onChange={(event) => setTumor(event.target.value, file.name)}
                    className={classes.fileDetail}
                    required
                    error={!tumorPath}
                    helperText="Required"
                  />
                  <TextField
                    variant="standard"
                    label={regionSubjectLabel}
                    value={file?.region?.id}
                    onChange={(event) => setRegion(event.target.value, file.name)}
                    className={classes.fileDetail}
                    helperText="Optional"
                  />
                  <label htmlFor="contained-button-file">
                    <Button variant={selectedFiles?.length > 1 ? "outlined" : "contained"} color="primary" disabled={!isDataValid || file.uploading} onClick={() => uploadSingleFile(file, true)}>
                      <span><BackupIcon className={classes.buttonIcon}/>
                        {file.uploading ? 'Uploading' : 'Upload'}
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
              </Grid>
          ) } ) }
      { showUploadAllButton ?
      <Grid item>
      <Button type="submit" variant="contained" color="primary" disabled={showUploadDisabled} form="variantForm">
        <span><BackupIcon className={classes.buttonIcon}/>
          {uploadAllComplete ? 'Uploaded' :
           uploadInProgress ? 'Uploading' : 'Upload all'}
        </span>
      </Button>
      </Grid>
      : <></>}
    </Grid>}
    <Dialog open={showVersionsDialog} onClose={() => setShowVersionsDialog(false)}>
      <DialogTitle>
        <span className={classes.dialogTitle}>Versions of {fileSelected?.name}</span>
        <IconButton onClick={() => setShowVersionsDialog(false)} className={classes.closeButton} size="large">
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent className={classes.dialogContent}>
        <MaterialReactTable
          data={fileSelected?.sameFiles}
          enableColumnActions={false}
          enableColumnFilters={false}
          enableSorting={false}
          enableTopToolbar={false}
          enableToolbarInternalActions={false}
          muiTableBodyRowProps={({ row }) => ({
            sx: {
              verticalAlign: 'top',
            },
          })}
          columns={[
            { header: 'Created',
              muiTableBodyCellProps: ({ cell }) => ({
	            sx: {
                  paddingLeft: 0,
                  fontWeight: "bold",
                  width: '1%',
                  whiteSpace: 'nowrap',
                }
              }),
              Cell: ({ renderedCellValue, row }) =>
                                <Link href={row.original["@path"]} underline="hover">
                                  {DateTime.fromISO(row.original['jcr:created']).toFormat("yyyy-MM-dd")}
                                </Link> },
            { header: 'Uploaded By',
              muiTableBodyCellProps: ({ cell }) => ({
	            sx: {
                  width: '50%',
                  whiteSpace: 'pre-wrap',
                  paddingBottom: "8px",
                }
              }),
              Cell: ({ renderedCellValue, row }) => row.original["jcr:createdBy"] },
            { header: 'Actions',
              muiTableBodyCellProps: ({ cell }) => ({
	            sx: {
                  padding: '0',
                  width: '20px',
                  textAlign: 'end'
                }
              }),
              enableSorting: false,
              Cell: ({ renderedCellValue, row }) =>
                                <Tooltip title={"Download"}>
                                  <IconButton size="large">
                                    <Link underline="none" color="inherit" href={row.original["@path"]} download>
                                      <GetApp />
                                    </Link>
                                  </IconButton>
                                </Tooltip> },
          ]}
        />
      </DialogContent>
    </Dialog>
  </React.Fragment>
  );
}
