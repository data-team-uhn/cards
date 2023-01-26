  // A helper recursive function to loop through the sections/questions data 
  // and collect all questions that have incomplete status among it's statusFlags
  // Recursively build a map of uuid->path of all incomplete questions
export function getIncompleteQuestionsMap (sectionJson) {
    let retFields = {};
    Object.entries(sectionJson).map(([title, object]) => {
        // We only care about children that are cards/Answers or cards:AnswerSections
        if (object["sling:resourceSuperType"] == "cards/Answer" && object.statusFlags?.includes("INCOMPLETE")) {
          // If this is an cards:Question, we copy the entire path to the array
          retFields[object.question["jcr:uuid"]] = object.question["@path"];
        } else if (object["jcr:primaryType"] == "cards:AnswerSection" && object.statusFlags?.includes("INCOMPLETE")) {
          // if this is matrix - save the section path as this section is rendered in Question component
          if ("matrix" === object.section["displayMode"]) {
            retFields[object.question["jcr:uuid"]] = object.question["@path"];
          } else {
            // If this is a normal cards:Section, recurse deeper
            retFields = {...retFields, ...getIncompleteQuestionsMap(object)};
          }
        }
        // Otherwise, we don't care about this object
    });
    return retFields;
}

  // Recursively collect all uuids of questions in the *right order*
export function parseSectionOrQuestionnaire (sectionJson) {
    let retFields = [];
    Object.entries(sectionJson).map(([title, object]) => {
        // We only care about children that are cards:Questions or cards:Sections
        if (object["jcr:primaryType"] == "cards:Question") {
          // If this is an cards:Question, copy the entire thing over to our Json value
          retFields.push(object["jcr:uuid"]);
        } else if (object["jcr:primaryType"] == "cards:Section") {
          if ("matrix" === object["displayMode"]) {
            retFields.push(object["jcr:uuid"]);
          } else {
            // If this is a normal cards:Section, recurse deeper
            retFields.push(...parseSectionOrQuestionnaire(object));
          }
        }
        // Otherwise, we don't care about this value
    });
    return retFields;
}

export function getFirstIncompleteQuestionEl (json) {
    //if the form is incomplete itself
    if (json?.statusFlags?.includes("INCOMPLETE")) {
      // Get an array of the question uuids in the order defined by questionnaire
      let allUuids = parseSectionOrQuestionnaire(json.questionnaire);
      // Get the map of uuid:path of the incomplete answers
      let incompleteQuestions = getIncompleteQuestionsMap(json);

      if (Object.keys(incompleteQuestions).length > 0) {
	    // Loop through the question uuids in the order defined by questionnaire
        for (const uuid of allUuids) {
          let firstEl = document.getElementById(incompleteQuestions[uuid]);
          // Find first visible element by path from incomplete answers map
          if (firstEl && getComputedStyle(firstEl.parentElement).display != 'none') {
            return firstEl;
          }
        }
      }

    }
    return null;
}