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

const LISTEN_HOST = '0.0.0.0';
const LISTEN_PORT = 8000;

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const bodyParser = require('body-parser');
const express = require('express');
const webApp = express();
webApp.use(bodyParser.json({type: 'application/json', limit: '10mb'}));
const webServer = require('http').createServer(webApp);

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
  "value"
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
  if (answer["jcr:primaryType"] === "cards:FileAnswer") {
    // Extract the file contents
    for (let key in answer) {
      if (isNtFile(answer[key])) {
        let fileAnswerData = getFileDataBuffer(answer[key]["jcr:content"]["jcr:data"]);
        let fileAnswerHash = sha256sum(fileAnswerData);
        simplifiedAnswer["fileDataSha256"] = fileAnswerHash;

        // Store the data in the blobs directory
        fs.writeFileSync("JsonBackups/blobs/" + fileAnswerHash + ".blob", fileAnswerData);
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
    "JsonBackups/SubjectListBackup_" + timestamp + ".json",
    JSON.stringify(req.body, null, "\t")
  );
  res.json({"success": true});
});

webApp.post("/FormListBackup", (req, res) => {
  let timestamp = new Date().toISOString();
  fs.writeFileSync(
    "JsonBackups/FormListBackup_" + timestamp + ".json",
    JSON.stringify(req.body, null, "\t")
  );
  res.json({"success": true})
});

webApp.post("/FormBackup/Forms/:formName*", (req, res) => {
  let formName = req.params['formName'];
  let formFileName = path.basename(formName);
  if (validateFormFileName(formFileName)) {
    fs.writeFileSync(
      "JsonBackups/Forms/" + formFileName + ".json",
      JSON.stringify(
        cleanupForm(req.body),
        null,
        "\t"
      )
    );
    console.log("Backed up /Forms/" + formName + " TO JsonBackups/Forms/" + formFileName + ".json");
  }
  res.json({"success": true});
});

webApp.post("/SubjectBackup/Subjects/:subjectName*", (req, res) => {
  let subjectName = req.params['subjectName'];
  let subjectFileName = path.basename(subjectName);
  if (validateFormFileName(subjectFileName)) {
    fs.writeFileSync(
      "JsonBackups/Subjects/" + subjectFileName + ".json",
      JSON.stringify(
        cleanupSubject(req.body),
        null,
        "\t"
      )
    );
    console.log("Backed up /Subjects/" + subjectName + " TO JsonBackups/Subjects/" + subjectFileName + ".json");
  }
  res.json({"success": true});
});

// TODO: We will be able to get rid of this soon
webApp.post("/DataBackup", (req, res) => {
  res.json({"success": true});
});

webServer.listen(LISTEN_PORT, LISTEN_HOST, (err) => {
  if (err) {
    console.log("Backup Recorder server failed to start");
  } else {
    console.log("Backup Recorder server listening on port " + LISTEN_PORT);
    console.log("=============================================");
    console.log("");
  }
});
