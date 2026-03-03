/**
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */

import { Class, $, PFireEvent } from './shims/prototypeShim';
import TemplateSelector from './view/templateSelector';

/**
 * SaveLoadEngine is responsible for automatic and manual save and load operations.
 *
 * @class SaveLoadEngine
 * @constructor
 */

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

    var jsonData = this.serialize();

    var svg = editor.getWorkspace().getSVGCopy();
    var svgText = svg.getSVGText();

    onSaveCallback(jsonData, svgText);

    editor.getActionStack().addSaveEvent();
  },

  load: function (initialPedigreeJSONString) {
    console.log('[PEDIGREE] initiating load process');
    var didLoadData = false;

    if (initialPedigreeJSONString) {

      try {
        this.createGraphFromSerializedData(initialPedigreeJSONString);

        editor.getActionStack().addSaveEvent();

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
