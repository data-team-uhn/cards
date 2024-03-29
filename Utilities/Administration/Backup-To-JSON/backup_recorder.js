// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const process = require('process');
const bodyParser = require('body-parser');
const express = require('express');
const webApp = express();
webApp.use(bodyParser.json({type: 'application/json', limit: '10mb'}));
const webServer = require('http').createServer(webApp);

const LISTEN_HOST = process.env.LISTEN_HOST || "127.0.0.1";
const LISTEN_PORT = process.env.LISTEN_PORT || 8012;

if (process.argv.length < 3) {
  console.error("Please specify a backup directory location.");
  console.error("Exiting.");
  process.exit(1);
}
const BACKUP_DIRECTORY = process.argv[2];

const sha256sum = (data) => {
  let h = crypto.Hash('sha256');
  h.update(data);
  return h.digest().toString('hex');
};

const getFileDataBuffer = (jsonValue) => {
  let bytesArray = [];
  for (let i = 0; i < jsonValue.length; i++) {
    bytesArray.push(jsonValue.charCodeAt(i));
  }
  return Buffer.from(bytesArray);
};

const RESPONSE_KEEP_PROPERTIES = [
  "jcr:primaryType",
  "jcr:createdBy",
  "question",
  "value",
  "note",
  "image"
];

// CARDS Answer nodes that contain one or more nt:file children
const FILE_LIKE_ANSWER_TYPES = [
  "cards:FileAnswer",
  "cards:DicomAnswer"
];

const isNtFile = (obj) => {
  if (typeof(obj) !== "object") {
    return false;
  }
  if (!("jcr:primaryType" in obj)) {
    return false;
  }
  if (obj["jcr:primaryType"] !== "nt:file") {
    return false;
  }
  return true;
};

const simplifyAnswerProperties = (answer) => {
  let simplifiedAnswer = {};
  for (let key in answer) {
    if (RESPONSE_KEEP_PROPERTIES.indexOf(key) >= 0) {
      simplifiedAnswer[key] = answer[key];
    }
  }
  if (FILE_LIKE_ANSWER_TYPES.indexOf(answer["jcr:primaryType"]) >= 0) {
    // Extract the file contents
    simplifiedAnswer["fileDataSha256"] = {};
    for (let key in answer) {
      if (isNtFile(answer[key])) {
        let fileAnswerData = getFileDataBuffer(answer[key]["jcr:content"]["jcr:data"]);
        let fileAnswerHash = sha256sum(fileAnswerData);
        simplifiedAnswer["fileDataSha256"][key] = fileAnswerHash;

        // Store the data in the blobs directory
        fs.writeFileSync(BACKUP_DIRECTORY + "/blobs/" + fileAnswerHash + ".blob", fileAnswerData);
      }
    }
  }
  return simplifiedAnswer;
};

const flattenAnswerSection = (answerSection) => {
  let answers = [];
  for (let key in answerSection) {
    if (typeof(answerSection[key]) !== "object") {
      continue;
    }
    if (!("sling:resourceSuperType" in answerSection[key])) {
      continue;
    }
    if (answerSection[key]["sling:resourceSuperType"] === "cards/Answer") {
      answers.push(answerSection[key]);
    }
    if (answerSection[key]["sling:resourceType"] === "cards/AnswerSection") {
      answers = answers.concat(flattenAnswerSection(answerSection[key]));
    }
  }
  return answers;
};

const cleanupForm = (formObject) => {
  let cleanForm = {}
  cleanForm["subject"] = undefined;
  cleanForm["questionnaire"] = undefined;
  cleanForm["responses"] = {};
  for (let key in formObject) {
    if (key === "questionnaire") {
      cleanForm["questionnaire"] = formObject["questionnaire"]["@path"];
      continue;
    }
    if (key === "subject") {
      cleanForm["subject"] = formObject["subject"]["@path"];
      continue;
    }
    if (key === "jcr:lastModified") {
      cleanForm["jcr:lastModified"] = formObject["jcr:lastModified"];
      continue;
    }
    if (key === "@path") {
      cleanForm["@path"] = formObject["@path"];
      continue;
    }
    if (typeof(formObject[key]) !== "object") {
      continue;
    }
    if (!("sling:resourceSuperType" in formObject[key])) {
      continue;
    }
    if (formObject[key]["sling:resourceSuperType"] === "cards/Answer") {
      let fullPath = formObject[key]["@path"];
      cleanForm["responses"][fullPath] = simplifyAnswerProperties(formObject[key]);
      cleanForm["responses"][fullPath]["question"] = formObject[key]["question"]["@path"];
    }
    if (formObject[key]["sling:resourceType"] === "cards/AnswerSection") {
      let sectionResponses = flattenAnswerSection(formObject[key]);
      for (let i = 0; i < sectionResponses.length; i++) {
        let response = sectionResponses[i];
        let fullPath = response["@path"];
        cleanForm["responses"][fullPath] = simplifyAnswerProperties(response);
        cleanForm["responses"][fullPath]["question"] = response["question"]["@path"];
      }
    }
  }
  return cleanForm;
};

const cleanupSubject = (subjectObject) => {
  let cleanSubject = {};
  cleanSubject["type"] = subjectObject["type"]["@path"];
  cleanSubject["@path"] = subjectObject["@path"];
  cleanSubject["identifier"] = subjectObject["identifier"];
  return cleanSubject;
};

const validateFormFileName = (formFileName) => {
  const allowedChars = "0123456789abcdef-";
  if (typeof(formFileName) !== "string") {
    return false;
  }
  for (let i = 0; i < formFileName.length; i++) {
    if (allowedChars.indexOf(formFileName[i]) < 0) {
      return false;
    }
  }
  return true;
};

webApp.post("/SubjectListBackup", (req, res) => {
  let timestamp = new Date().toISOString();
  fs.writeFileSync(
    BACKUP_DIRECTORY + "/SubjectListBackup_" + timestamp + ".json",
    JSON.stringify(req.body, null, "\t")
  );
  res.json({"success": true});
});

webApp.post("/FormListBackup", (req, res) => {
  let timestamp = new Date().toISOString();
  fs.writeFileSync(
    BACKUP_DIRECTORY + "/FormListBackup_" + timestamp + ".json",
    JSON.stringify(req.body, null, "\t")
  );
  res.json({"success": true})
});

webApp.post("/FormBackup/Forms/:formName*", (req, res) => {
  let formName = req.params['formName'];
  let formFileName = path.basename(formName);
  if (validateFormFileName(formFileName)) {
    fs.writeFileSync(
      BACKUP_DIRECTORY + "/Forms/" + formFileName + ".json",
      JSON.stringify(
        cleanupForm(req.body),
        null,
        "\t"
      )
    );
    console.log("Backed up /Forms/" + formName
      + " TO " + BACKUP_DIRECTORY + "/Forms/" + formFileName + ".json");
  }
  res.json({"success": true});
});

webApp.post("/SubjectBackup/Subjects/:subjectName*", (req, res) => {
  let subjectName = req.params['subjectName'];
  let subjectFileName = path.basename(subjectName);
  if (validateFormFileName(subjectFileName)) {
    fs.writeFileSync(
      BACKUP_DIRECTORY + "/Subjects/" + subjectFileName + ".json",
      JSON.stringify(
        cleanupSubject(req.body),
        null,
        "\t"
      )
    );
    console.log("Backed up /Subjects/" + subjectName + " TO " + BACKUP_DIRECTORY + "/Subjects/" + subjectFileName + ".json");
  }
  res.json({"success": true});
});


// Create the backup directories on the file system if they do not already exist
if (!fs.existsSync(BACKUP_DIRECTORY)) {
  console.log("Backup directory: " + BACKUP_DIRECTORY + " does not exist...creating it...");
  fs.mkdirSync(BACKUP_DIRECTORY);
}

if (!fs.existsSync(BACKUP_DIRECTORY + "/blobs")) {
  console.log("Backup directory: " + BACKUP_DIRECTORY + "/blobs does not exist...creating it...");
  fs.mkdirSync(BACKUP_DIRECTORY + "/blobs");
}

if (!fs.existsSync(BACKUP_DIRECTORY + "/Forms")) {
  console.log("Backup directory: " + BACKUP_DIRECTORY + "/Forms does not exist...creating it...");
  fs.mkdirSync(BACKUP_DIRECTORY + "/Forms");
}

if (!fs.existsSync(BACKUP_DIRECTORY + "/Subjects")) {
  console.log("Backup directory: " + BACKUP_DIRECTORY + "/Subjects does not exist...creating it...");
  fs.mkdirSync(BACKUP_DIRECTORY + "/Subjects");
}

// Listen for web server connections
webServer.listen(LISTEN_PORT, LISTEN_HOST, (err) => {
  if (err) {
    console.log("Backup Recorder server failed to start");
  } else {
    console.log("Backup Recorder server listening on " + LISTEN_HOST + ":" + LISTEN_PORT);
    console.log("Backups will be saved to " + BACKUP_DIRECTORY);
    console.log("")
    console.log("===");
    console.log("");
  }
});
