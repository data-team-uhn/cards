import { Class, $, PFireEvent } from './shims/prototypeShim';
import TemplateSelector from './view/templateSelector';

/**
 * SaveLoadEngine is responsible for automatic and manual save and load operations.
 *
 * @class SaveLoadEngine
 * @constructor
 */

function unescapeRestData (data) {
  // http://stackoverflow.com/questions/4480757/how-do-i-unescape-html-entities-in-js-change-lt-to
  var tempNode = document.createElement('div');
  tempNode.innerHTML = data.replace(/&amp;/, '&');
  return tempNode.innerText || tempNode.text || tempNode.textContent;
}

function getSelectorFromXML(responseXML, selectorName, attributeName, attributeValue) {
  if (responseXML.querySelector) {
    // modern browsers
    return responseXML.querySelector(selectorName + '[' + attributeName + '=\'' + attributeValue + '\']');
  } else {
    // IE7 && IE8 && some other older browsers
    // http://www.w3schools.com/XPath/xpath_syntax.asp
    // http://msdn.microsoft.com/en-us/library/ms757846%28v=vs.85%29.aspx
    var query = '//' + selectorName + '[@' + attributeName + '=\'' + attributeValue + '\']';
    try {
      return responseXML.selectSingleNode(query);
    } catch (e) {
      // Firefox v3.0-
      alert('your browser is unsupported');
      window.stop && window.stop();
      throw 'Unsupported browser';
    }
  }
}

function getSubSelectorTextFromXML(responseXML, selectorName, attributeName, attributeValue, subselectorName) {
  var selector = getSelectorFromXML(responseXML, selectorName, attributeName, attributeValue);

  var value = selector.innerText || selector.text || selector.textContent;

  if (!value)     // fix IE behavior where (undefined || "" || undefined) == undefined
  {
    value = '';
  }

  return value;
}

var SaveLoadEngine = Class.create( {

  initialize: function() {
    this._saveInProgress = false;
  },

  /**
     * Saves the state of the graph
     *
     * @return Serialization data for the entire graph
     */
  serialize: function() {
    return editor.getGraph().toJSON();
  },

  createGraphFromSerializedData: function(JSONString, noUndo, centerAround0) {
    console.log('---- load: parsing data ----');
    PFireEvent('pedigree:load:start');

    try {
      var changeSet = editor.getGraph().fromJSON(JSONString);
    } catch(err) {
      console.log('ERROR loading the graph: ', err);
      alert('Error loading the graph');
      PFireEvent('pedigree:graph:clear');
      PFireEvent('pedigree:load:finish');
      return;
    }

    if (editor.getView().applyChanges(changeSet, false)) {
      editor.getWorkspace().adjustSizeToScreen();
    }

    if (centerAround0) {
      editor.getWorkspace().centerAroundNode(0);
    }

    if (!noUndo) {
      editor.getActionStack().addState(null, null, JSONString);
    }

    PFireEvent('pedigree:load:finish');
  },

  createGraphFromImportData: function(importString, importType, importOptions, noUndo, centerAround0) {
    console.log('---- import: parsing data ----');
    PFireEvent('pedigree:load:start');

    try {
      var changeSet = editor.getGraph().fromImport(importString, importType, importOptions);
      if (changeSet == null) {
        throw 'unable to create a pedigree from imported data';
      }
    } catch(err) {
      alert('Error importing pedigree: ' + err);
      PFireEvent('pedigree:load:finish');
      return;
    }

    if (!noUndo) {
      var JSONString = editor.getGraph().toJSON();
    }

    if (editor.getView().applyChanges(changeSet, false)) {
      editor.getWorkspace().adjustSizeToScreen();
    }

    if (centerAround0) {
      editor.getWorkspace().centerAroundNode(0);
    }

    if (!noUndo) {
      editor.getActionStack().addState(null, null, JSONString);
    }

    PFireEvent('pedigree:load:finish');
  },

  save: function(onSaveCallback) {
    if (!onSaveCallback) {
      return;
    }   // nothing to do

    editor.getView().unmarkAll();

    var me = this;

    var jsonData = this.serialize();

    var svg = editor.getWorkspace().getSVGCopy();
    var svgText = svg.getSVGText();

    console.log('[SAVE] data: ' + JSON.stringify(jsonData));

    onSaveCallback(jsonData, svgText);
  },

  load: function (initialPedigreeJSONString) {
    console.log('[PEDIGREE] initiating load process');
    var didLoadData = false;

    if (initialPedigreeJSONString) {
      console.log('[LOAD] recived JSON: ' + initialPedigreeJSONString);

      try {
        this.createGraphFromSerializedData(initialPedigreeJSONString);

        didLoadData = true;
      } catch (ex) {
        console.log('[LOAD] ERROR rendering provided JSON: ' + ex);
      }
    }

    if (!didLoadData) {
      // If load failed, just open templates
      new TemplateSelector(true);
    }
  }
});

export default SaveLoadEngine;
