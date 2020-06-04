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
  Input,
  Grid,
  LinearProgress,
  List,
  ListItem,
  TextField,
  Typography,
  makeStyles
} from "@material-ui/core";
import IconButton from '@material-ui/core/IconButton';
import AttachFile from '@material-ui/icons/AttachFile';
import BackupIcon from '@material-ui/icons/Backup';
import GetApp from '@material-ui/icons/GetApp';
import uuid from "uuid/v4";

const useStyles = makeStyles(theme => ({
  root: {
    flexGrow: 1,
  },
  fileinput: {
    display: 'none',
  },
  dialogTitle: {
    padding: theme.spacing(2,0,2,3),
  },
  buttonIcon: {
    verticalAlign: 'middle',
    paddingRight: theme.spacing(1)
  },
  uploadButton: {
    marginLeft: theme.spacing(2)
  }
}));

export default function variantsContainer() {
  const classes = useStyles();

  // This holds the full list of uploaded files JSON, once it is received from the server
  let [ uploadedFiles, setUploadedFiles ] = useState();
  // Error message set when file upload to the server fails
  let [ error, setError ] = useState();
  // Marks that a upload operation is in progress
  let [ uploadInProgress, setUploadInProgress ] = useState();
  // Indicates whether the file has been uploaded or not. This has three possible values:
  // - undefined -> no upload performed yet, or the form has been modified since the last upload
  // - true -> file has been successfully uploaded
  // - false -> the upload attempt failed
  let [ lastUploadStatus, setLastUploadStatus ] = useState(undefined);

  const [selectedFiles, setSelectedFiles] = useState();

  let constructQuery = (nodeType, query) => {
    let url = new URL("/query", window.location.origin);
    let sqlquery = "SELECT s.* FROM [" + nodeType + "] as s" + query;
    url.searchParams.set("query", sqlquery);
    return url;
  };

  // Fetch the uploaded files data as JSON from the server.
  // Once the data arrives from the server, it will be stored in the `uploadedFiles` state variable.
  let url = constructQuery("lfs:SomaticVariantsAnswer", "");
  let fetchData = () => {
    fetch(url)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(handleResponse)
      .catch(handleError);
  };

  // Callback method for the `fetchData` method, invoked when the data successfully arrived from the server.
  let handleResponse = (json) => {
    json.rows && setUploadedFiles(json.rows);
  };

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response.status + " " + response.statusText);
    setUploadedFiles([]);  // Prevent an infinite loop if data was not set
  };

  let onFileChange = (event) => {
    cleanForm();

    let chosenFiles = event.target.files;
    if (chosenFiles.length == 0) { return; }

    let files = []; 
    //Check naming convention
    var i;
    var count = 0;
    for (i = 0; i < chosenFiles.length; i++) {
      let file = chosenFiles[i];
      if (!(/^(.+)_(.+)_(.+).csv$/.test(file.name))) {
        setError("File name " + file.name + " is not in the expected name convention <[Subject]_[tumor nb]_[region nb].csv>.");
        return;
      }

      let parsed = file.name.split('.csv')[0].split('_');
      file.subject = {}
      file.subject.id = parsed[0];
      file.tumor    = {}
      file.tumor.id = parsed[1];
      file.region   = {}
      file.region.id = parsed[2];
      
      let checkSubjectExistsURL = constructQuery("lfs:Subject", ` WHERE s.'identifier'='${file.subject.id}'`);
      let checkTumorExistsURL = "";
      let checkRegionExistsURL = "";
                    
      // Fire a fetch request for a region subject with the tumor subject as its parent
      let RegionPromise = fetch( checkRegionExistsURL )
        .then( (response) => {
          // If a region subject is found
          if (response.ok && response.json()?.rows?.length > 0) {
            let subject = response.json().rows[0];
            // get the path
            file.region.path = subject["@path"];
            file.region.existed = true;
          } else {
            //if a region subject is not found
            // record in variables that a region didn’t exist, and generate a new random uuid as its path
            file.region.path = "Subjects/" + uuid();
            file.region.existed = false;
          }
          return Promise.resolve();
        })
        .catch(handleError);

      // Fire a fetch request for a tumor subject with the patient subject as its parent
      let TumorPromise = fetch( checkTumorExistsURL )
        .then( (response) => {
          // If a tumor subject is found
          if (response.ok && response.json()?.rows?.length > 0) {
            let subject = response.json().rows[0];
            // get the path
            file.tumor.path = subject["@path"];
            file.tumor.existed = true;
            checkRegionExistsURL = constructQuery("lfs:Subject", ` WHERE s.'identifier'='${file.region.id}' AND n.'jcr:reference:parent'='${file.tumor.path}'`);
            return RegionPromise();
          } else {
            //if a tumor subject is not found
            // record in variables that a tumor didn’t exist, and generate a new random uuid as its path
            file.tumor.path = "Subjects/" + uuid();
            file.tumor.existed = false;
            // record in variables that a region didn’t exist, and generate a new random uuid as its path
            file.region.path = "Subjects/" + uuid();
            file.region.existed = false;
            return Promise.resolve();
          }
        })
        .catch(handleError);

      // fetch subject, tumor, region links
      // Fire a fetch request for the patient subject
      fetch( checkSubjectExistsURL )
        .then( (response) => {
          // If a patient subject is found
          if (response.ok && response.json()?.rows?.length > 0) {
            let subject = response.json().rows[0];
            // get the path
            file.subject.path = subject["@path"];
            file.subject.existed = true;
            checkTumorExistsURL = constructQuery("lfs:Subject", ` WHERE s.'identifier'='${file.tumor.id}' AND n.'jcr:reference:parent'='${file.subject.path}'`);
            return TumorPromise();
          } else {
            // If a patient subject is not found:
            // record in variables that it didn’t exist, and generate a new random uuid as its path
            file.subject.path = "Subjects/" + uuid();
            file.subject.existed = false;
            // record in variables that a tumor didn’t exist, and generate a new random uuid as its path
            file.tumor.path = "Subjects/" + uuid();
            file.tumor.existed = false;
            // record in variables that a region didn’t exist, and generate a new random uuid as its path
            file.region.path = "Subjects/" + uuid();
            file.region.existed = false;
          }
        })
        .catch(handleError)
        .finally(() => {
          // !!!TODO find all files with this name
          //file.sameFiles = getSameFiles();

          files.push(file);
          count++;
          (count == chosenFiles.length) && setSelectedFiles(files);
        });

    }
  };


  // Event handler for the form submission event, replacing the normal browser form submission with a background fetch request.
  let upload = (event) => {
    // This stops the normal browser form submission
    event.preventDefault();

    // If the previous upload attempt failed, instead of trying to upload again, open a login popup
    if (lastUploadStatus === false) {
      loginToUpload();
      return;
    }

    setUploadInProgress(true);

    // TODO!! for each file
    let json = assembleJson(selectedFiles[0]);

    //TODO progress and drop in

    let data = new FormData();
    data.append(':contentType', 'json');
    data.append(':operation', 'import');
    data.append(':content', JSON.stringify(json));

    fetch(`/`, {
      method: "POST",
      body: data
    }).then((response) => response.ok ? true : Promise.reject(response))
      .then(() => setLastUploadStatus(true))
      .catch(() => {
        // If the user is not logged in, offer to log in
        const sessionInfo = window.Sling.getSessionInfo();
        if (sessionInfo === null || sessionInfo.userID === 'anonymous') {
          // On first attempt to upload while logged out, set status to false to make button text inform user
          setLastUploadStatus(false);
        }
      })
      .finally(() => cleanForm());
  };

  let cleanForm = () => {
    setUploadInProgress(false);
    setSelectedFiles(undefined);
    setLastUploadStatus(undefined);
    setError("");
  };

  let assembleJson = (file) => {
    let json = {};

    if (!file.subject.existed) {
      let info = {};
      info["jcr:primaryType"] = "lfs:Subject";
      info["type"] = "/SubjectTypes/Patient";
      info["identifier"] = file.subject.id;
      json[file.subject.path] = info;

      if (!file.tumor.existed) {
          let tumorInfo = {};
          tumorInfo["jcr:primaryType"] = "lfs:Subject";
          tumorInfo["type"] = "/SubjectTypes/Tumor";
          tumorInfo["jcr:reference:parent"] = file.subject.path;
          tumorInfo["identifier"] = file.tumor.id;
          json[file.tumor.path] = tumorInfo;

          if (!file.region.existed) {
              let regionInfo = {};
              regionInfo["jcr:primaryType"] = "lfs:Subject";
              regionInfo["type"] = "/SubjectTypes/TumorRegion";
              regionInfo["jcr:reference:parent"] = file.tumor.path;
              regionInfo["identifier"] = file.region.id;
              json[file.region.path] = regionInfo;
          }
      }
    }

    let formInfo = {};
    formInfo["jcr:primaryType"] = "lfs:Form";
    formInfo["questionnaire"] = "/Questionnaires/SomaticVariants";
    // The subject of the questionnaire is the region
    formInfo["subject"] = file.region.path;

    let fileInfo = {};
    fileInfo["jcr:primaryType"] = "lfs:SomaticVariantsAnswer";
    fileInfo["jcr:reference:question"] = "/Questionnaires/SomaticVariants/file";

    let fileDetails = {};
    fileDetails["jcr:primaryType"] = "nt:file";
    fileDetails["jcr:content"] = {};
    fileDetails["jcr:content"]["jcr:primaryType"] = "nt:resource";
    fileDetails["jcr:content"]["jcr:data"] = file.data;

    fileInfo[file.name] = fileDetails;

    formInfo[uuid()] = fileInfo;

    json["Forms/" + uuid()] = formInfo;

    return json;
  };

  // If the data has not yet been fetched, return an in-progress symbol
  if (!uploadedFiles) {
    fetchData();
  };

  return (
  <React.Fragment>
    <form method="POST"
          encType="multipart/form-data"
          onSubmit={upload}
          key="file-upload">
      <Typography component="h2" variant="h5" className={classes.dialogTitle}>Variants Upload</Typography>
      {uploadInProgress && (
        <Grid item className={classes.root}>
          <LinearProgress color="primary" />
        </Grid>
      )}
      {error && <Typography color='error'>{error}</Typography>}
      <label htmlFor="contained-button-file">
        <IconButton color="primary" aria-label="upload picture" component="span">
          <AttachFile />
        </IconButton>
      </label>
      <input
        accept=".csv"
        className={classes.fileinput}
        id="contained-button-file"
        type="file"
        name="*"
        multiple
        onChange={(event) => onFileChange(event)}
      />
      <input type="hidden" name="*@TypeHint" value="nt:file" />
      <label htmlFor="contained-button-file">
        <Button type="submit" variant="contained" color="primary" disabled={uploadInProgress} className={classes.uploadButton}>
          <span><BackupIcon className={classes.buttonIcon}/>
            {uploadInProgress ? 'Uploading' :
                lastUploadStatus === true ? 'Uploaded' :
                lastUploadStatus === false ? 'Upload failed, log in and try again?' :
                'Upload'}
          </span>
        </Button>
      </label>
    </form>
    <Typography variant="body1">Selected files info</Typography>
    {selectedFiles && selectedFiles.map( (file, i) => {
      return (
        <div key={file.name}>
	      <Typography variant="body1">File {file.name}:</Typography>
	      <List>
	        <ListItem>
	          <TextField
	              label="Subject id"
	              value={file.subject.id}
	              onChange={(event) => setSubject(event.target.value)}
	              required
	            />
	            </ListItem>
	            <ListItem>
	          <TextField
	              label="Tumor nb"
	              value={file.tumor.id}
	              onChange={(event) => setTumor(event.target.value)}
	              required
	            />
	            </ListItem>
	            <ListItem>
	           <TextField
	              label="Region nb"
	              value={file.region.id}
	              onChange={(event) => setRegion(event.target.value)}
	              required
	           />
	         </ListItem>
	      </List>
	      <Typography variant="body1">
	          {(!file.sameFiles || file.sameFiles.length == 0)
	              ?
	             <span>There are no versions of this file</span>
	             :
	             <span>Other versions of this file :
	                 <ul>
	                   {file.sameFiles && file.sameFiles.map( (samefile, index) => {
	                    return (
	                     <li key={index}>
	                       {samefile.date} uploaded by {samefile.user} 
	                       <IconButton size="small">
	                         <a href={samefile["jcr:lastModified"]} download={samefile["jcr:lastModifiedBy"]}></a>
	                         <GetApp />
	                        </IconButton>
	                     </li>
	                   )})}
	                 </ul>
	             </span>
	           }
	      </Typography>
        </div>
    ) } ) }
  </React.Fragment>
  );
}
