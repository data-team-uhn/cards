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

import { Class, PElement, $$ } from '../shims/prototypeShim';
import PModalPopup from '../shims/phenotipsShim';

/**
 * The UI Element for importing pedigrees from text representationin various formats
 *
 * @class ImportSelector
 */

var ImportSelector = Class.create( {

  initialize: function() {
    if (editor.isReadOnlyMode()) {
      return;
    }

    var _this = this;

    var mainDiv = PElement('div', {'class': 'import-selector'});

    var promptImport = PElement('div', {'class': 'import-section'})._p_update('Import data:');
    this.importValue = PElement('textarea', {'id': 'import', 'value': '', 'class': 'import-textarea'});
    mainDiv._p_insert(promptImport)._p_insert(this.importValue);

    if (!!window.FileReader && !!window.FileList) {
      // only show the upload link if browser supports FileReader/DOM File API
      // Of the browsers suported by pedigree editor, IE9 and Safari 4 & 5 do not support file API
      var uploadFileSelector = PElement('input', {'type' : 'file', 'id': 'pedigreeInputFile', 'style': 'display:none'});
      uploadFileSelector._p_observe('change', function(event) {
        _this.handleFileUpload(this.files);
        try {
          this.value = '';  // clear file selector
        } catch (err) {
          // some older browsers do not allow setting value of a file input element and may generate a security error
        }
      });
      var uploadLink = PElement('div', {'class': 'import-upload'})._p_update('<input type="button" name="selectfile" value="Select a local file to be imported" class="button">');
      uploadLink._p_observe('click', function(event) {
        var fileElem = document.getElementById('pedigreeInputFile');
        fileElem.click();
      });
      mainDiv._p_insert(uploadFileSelector)._p_insert(uploadLink);
    }

    var _addTypeOption = function (checked, labelText, value) {
      var optionWrapper = PElement('tr');
      var input = PElement('input', {'type' : 'radio', 'value': value, 'name': 'select-type'});
      input._p_observe('click', _this.disableEnableOptions );
      if (checked) {
        input.checked = true;
      }
      var label = PElement('label', {'class': 'import-type-label'})._p_insert(input)._p_insert(labelText);
      optionWrapper._p_insert(label._p_wrap('td'));
      return optionWrapper;
    };
    var typeListElement = PElement('table');
    //TODO: typeListElement._p_insert(_addTypeOption(true,  "Autodetect", "auto"));
    typeListElement._p_insert(_addTypeOption(true,  'PED or LINKAGE (pre- or post- makeped)', 'ped'));
    typeListElement._p_insert(_addTypeOption(false, 'GEDCOM', 'gedcom'));
    typeListElement._p_insert(_addTypeOption(false, 'BOADICEA', 'BOADICEA'));

    var promptType = PElement('div', {'class': 'import-section'})._p_update('Data format:');
    var dataSection2 = PElement('div', {'class': 'import-block'});
    dataSection2._p_insert(promptType)._p_insert(typeListElement);
    mainDiv._p_insert(dataSection2);

    var _addConfigOption = function (checked, labelText, value) {
      var optionWrapper = PElement('tr');
      var input = PElement('input', {'type' : 'radio', 'value': value, 'name': 'select-options' });
      if (checked) {
        input.checked = true;
      }
      var label = PElement('label', {'class': 'import-config-label'})._p_insert(input)._p_insert(labelText);
      optionWrapper._p_insert(label._p_wrap('td'));
      return optionWrapper;
    };
    var configListElement = PElement('table', {id : 'import-type'});
    configListElement._p_insert(_addConfigOption(true,  'Treat non-standard phenotype values as new disorders', 'accept'));
    configListElement._p_insert(_addConfigOption(false, 'Treat non-standard phenotype values as "no information"', 'dontaccept'));

    var markEvaluated = PElement('input', {'type' : 'checkbox', 'value': '1', 'name': 'mark-evaluated'});
    var markLabel1     = PElement('label', {'class': 'import-mark-label1'})._p_insert(markEvaluated)._p_insert('Mark all patients with known disorder status with \'documented evaluation\' mark')._p_wrap('td')._p_wrap('tr');
    configListElement._p_insert(markLabel1);
    var markExternal = PElement('input', {'type' : 'checkbox', 'value': '1', 'name': 'mark-external'});
    markExternal.checked = true;
    var markLabel2   = PElement('label', {'class': 'import-mark-label2'})._p_insert(markExternal)._p_insert('Save individual IDs as given in the input data as \'external ID\'')._p_wrap('td')._p_wrap('tr');
    configListElement._p_insert(markLabel2);

    var promptConfig = PElement('div', {'class': 'import-section'})._p_update('Options:');
    var dataSection3 = PElement('div', {'class': 'import-block'});
    dataSection3._p_insert(promptConfig)._p_insert(configListElement);
    mainDiv._p_insert(dataSection3);

    //TODO: [x] auto-combine multiple unaffected children when the number of children is greater than [5]

    var buttons = PElement('div', {'class' : 'buttons import-block-bottom'});
    buttons._p_insert(PElement('input', {type: 'button', name : 'import', 'value': 'Import', 'class' : 'button', 'id': 'import_button'})._p_wrap('span', {'class' : 'buttonwrapper'}));
    buttons._p_insert(PElement('input', {type: 'button', name : 'cancel', 'value': 'Cancel', 'class' : 'button secondary'})._p_wrap('span', {'class' : 'buttonwrapper'}));
    mainDiv._p_insert(buttons);

    var cancelButton = buttons._p_down('input[name="cancel"]');
    cancelButton._p_observe('click', function(event) {
      _this.hide();
    });
    var importButton = buttons._p_down('input[name="import"]');
    importButton._p_observe('click', function(event) {
      _this._onImportStarted();
    });

    var closeShortcut = ['Esc'];
    this.dialog = new PModalPopup(mainDiv, {close: {method : this.hide.bind(this), keys : closeShortcut}}, {extraClassName: 'pedigree-import-chooser', title: 'Pedigree import', displayCloseButton: true, verticalPosition : "top"});
  },

  /*
     * Populates the text input box with the selected file content (asynchronously)
     */
  handleFileUpload: function(files) {
    for (var i = 0, numFiles = files.length; i < numFiles; i++) {
      var nextFile = files[i];
      console.log('loading file: ' + nextFile.name + ', size: ' + nextFile.size);

      var _this = this;
      var fr = new FileReader();
      fr.onload = function(e) {
        _this.importValue.value = e.target.result;  // e.target.result should contain the text
      };
      fr.readAsText(nextFile);
    }
  },

  /*
     * Disables unapplicable options on input type selection
     */
  disableEnableOptions: function () {
    var importType = $$('input:checked[type=radio][name="select-type"]')[0].value;
    var importType = 'ped';
    //console.log("Import type: " + importType);

    var pedOnlyOptions = $$('input[type=radio][name="select-options"]');
    var pedOnlyOptions = [];
    for (var i = 0; i < pedOnlyOptions.length; i++) {
      if (importType != 'ped') {
        pedOnlyOptions[i].disabled = true;
      } else {
        pedOnlyOptions[i].disabled = false;
      }
    }
    var pedAndGedcomOption = $$('input[type=checkbox][name="mark-evaluated"]')[0];
    if (importType != 'ped' && importType != 'gedcom') {
      pedAndGedcomOption.disabled = true;
    } else {
      pedAndGedcomOption.disabled = false;
    }

    var saveExternalID = $$('input[type=checkbox][name="mark-external"]')[0];
    saveExternalID.disabled = false;
  },

  /**
     * Loads the template once it has been selected
     *
     * @param event
     * @param pictureBox
     * @private
     */
  _onImportStarted: function() {
    var importValue = this.importValue.value;
    console.log('Importing:\n' + importValue);

    this.hide();

    if (!importValue || importValue == '') {
      alert('Nothing to import!');
      return;
    }

    var importType = $$('input:checked[type=radio][name="select-type"]')[0].value;
    console.log('Import type: ' + importType);

    var importMark = $$('input[type=checkbox][name="mark-evaluated"]')[0].checked;

    var externalIdMark = $$('input[type=checkbox][name="mark-external"]')[0].checked;

    var optionSelected = $$('input:checked[type=radio][name="select-options"]')[0].value;
    var acceptUnknownPhenotypes = (optionSelected == 'accept');

    var importOptions = { 'markEvaluated': importMark, 'externalIdMark': externalIdMark, 'acceptUnknownPhenotypes': acceptUnknownPhenotypes };

    editor.getSaveLoadEngine().createGraphFromImportData(importValue, importType, importOptions,
      false /* add to undo stack */, true /*center around 0*/);
  },

  /**
     * Displays the template selector
     *
     * @method show
     */
  show: function() {
    this.dialog.show();
  },

  /**
     * Removes the the template selector
     *
     * @method hide
     */
  hide: function() {
    this.importValue.value = '';
    this.dialog.closeDialog();
  }
});

export default ImportSelector;
