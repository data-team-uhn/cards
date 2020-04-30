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

import { Class, $, PFireEvent, PRemoveAllListeners } from './shims/prototypeShim';

import Controller from './controller';
import SaveLoadEngine from './saveLoadEngine';
import View from './view';
import DynamicPositionedGraph from './model/dynamicGraph';
import Helpers from './model/helpers';
import Workspace from './view/workspace';
import DisorderLegend from './view/disorderLegend';
import HPOLegend from './view/hpoLegend';
import GeneLegend from './view/geneLegend';
import ExportSelector from './view/exportSelector';
import ImportSelector from './view/importSelector';
import NodeMenu from './view/nodeMenu';
import NodetypeSelectionBubble from './view/nodetypeSelectionBubble';
import TemplateSelector from './view/templateSelector';
import ActionStack from './undoRedo';
import PedigreeEditorParameters from './pedigreeEditorParameters';
import OkCancelDialogue from './view/okCancelDialogue';

// to use newer fontawesone that uses SVG instead of fonts (does not work with current pedigree UI, all events are broken):
// 1. add to package.json:     "@fortawesome/fontawesome-free": "^5.11.2"
// 2. import '@fortawesome/fontawesome-free/js/fontawesome', import '@fortawesome/fontawesome-free/js/solid'

import './style/font-awesome.css';  // note: edited compared to default to load font from the web
                                    //       need some webpack magic to make default work with our setup

import './style/phenotipsWidgets.css';
import './style/editor.css';

/**
 * The main class of the Pedigree Editor, responsible for initializing all the basic elements of the app.
 * Contains wrapper methods for the most commonly used functions.
 * This class should be initialized only once.
 *
 * @class PedigreeEditor
 * @constructor
 */

var PedigreeEditor = Class.create({
  initialize: function(options) {
    options = options || {};

    this.DEBUG_MODE = Boolean(options.DEBUG_MODE);

    window.editor = this;

    // (optional) patient pedigree to start with
    var initialPedigreeJSON = options.pedigreeJSON || null;

    // (optional) URL to redirect the browser to on cancel/close
    var returnUrl = options.returnUrl || null;
    // if returnUrl above is not defined the pedigree removes itself form the DOM and calls this method with no parameters
    this._onCloseCallback = options.onCloseCallback;

    this._readOnlyMode = options.readOnlyMode ? options.readOnlyMode : false;
    this._onSaveCallback = options.onPedigreeSaved;
    this._topElementID = options.pedigreeDiv ? options.pedigreeDiv : "pedigreeEditor";

    // initialize main data structure which holds the graph structure
    this._graphModel = DynamicPositionedGraph.makeEmpty(PedigreeEditorParameters.attributes.layoutRelativePersonWidth, PedigreeEditorParameters.attributes.layoutRelativeOtherWidth);

    //initialize the elements of the app
    this._workspace = new Workspace(this._topElementID);
    this._nodeMenu = this.generateNodeMenu();
    this._nodeGroupMenu = this.generateNodeGroupMenu();
    this._partnershipMenu = this.generatePartnershipMenu();
    this._nodetypeSelectionBubble = new NodetypeSelectionBubble(false);
    this._siblingSelectionBubble  = new NodetypeSelectionBubble(true);
    this._okCancelDialogue = new OkCancelDialogue();
    this._disorderLegend = new DisorderLegend();
    this._geneLegend = new GeneLegend();
    this._hpoLegend = new HPOLegend();

    this._view = new View();

    this._actionStack = new ActionStack();
    this._templateSelector = new TemplateSelector();
    this._importSelector = new ImportSelector();
    this._exportSelector = new ExportSelector();
    this._saveLoadEngine = new SaveLoadEngine();

    // load proband data and load the graph after proband data is available
    this._saveLoadEngine.load(initialPedigreeJSON, this._saveLoadEngine);

    this._controller = new Controller();

    //attach actions to buttons on the top bar
    var undoButton = $('action-undo');
    undoButton && undoButton._p_on('click', function(event) {
      PFireEvent('pedigree:undo');
    });
    var redoButton = $('action-redo');
    redoButton && redoButton._p_on('click', function(event) {
      PFireEvent('pedigree:redo');
    });

    var autolayoutButton = $('action-layout');
    autolayoutButton && autolayoutButton._p_on("click", function (event) {
      PFireEvent("pedigree:autolayout");
    });
    var clearButton = $('action-clear');
    clearButton && clearButton._p_on('click', function(event) {
      PFireEvent('pedigree:graph:clear');
    });

    var templatesButton = $('action-templates');
    templatesButton && templatesButton._p_on('click', function(event) {
      editor.getTemplateSelector().show();
    });
    var importButton = $('action-import');
    importButton && importButton._p_on('click', function(event) {
      editor.getImportSelector().show();
    });
    var exportButton = $('action-export');
    exportButton && exportButton._p_on('click', function(event) {
      editor.getExportSelector().show();
    });

    var performPedigreeClose = function() {
      if (returnUrl) {
        window.location = returnUrl;
      } else {
        editor.unload();
      }
    };
    var savePedigree = function() {
      editor.getSaveLoadEngine().save(editor._onSaveCallback);
    };

    var saveButton = $('action-save');
    saveButton && saveButton._p_on('click', function(event) {
      savePedigree();
    });

    var closeButton = $('action-close');
    closeButton && closeButton._p_on('click', function(event) {
      if (editor.getActionStack().hasUnsavedChanges()) {
         var saveAndQuitFunc = function() {
           savePedigree();
           performPedigreeClose();
         };
         editor.getOkCancelDialogue().showCustomized('There are unsaved changes, do you want to save the pedigree before closing the pedigree editor?',
            'Save before closing?',
            " Save and quit ", saveAndQuitFunc,
            " Don't save and quit ", performPedigreeClose,
            " Keep editing pedigree ", undefined, true );
      } else {
         performPedigreeClose();
      }
    });

    if (!editor.isReadOnlyMode()) {
      var onLeavePageFunc = function() {
          if (!editor.isReadOnlyMode() && editor.getActionStack().hasUnsavedChanges()) {
              return "All changes will be lost when navigating away from this page.";
          }
      };
      editor._initialBeforeUnloadFunction = window.onbeforeunload;
      window.onbeforeunload = onLeavePageFunc;
    }

    var readOnlyButton = $('action-readonlymessage');
    readOnlyButton && readOnlyButton._p_on('click', function(event) {
      if (editor.isUnsupportedBrowser()) {
        alert('Your browser does not support all the features required for ' +
              'Pedigree Editor, so pedigree is displayed in read-only mode (and may have quirks)');
      } else {
        alert("Pedigree editor is running in read-only mode");
      }
    });

  },

  /**
   * Completely removes pedigree eeditor form the DOM:
   * removes all elements, unregisters all event listeners.
   */
  unload: function () {
    if (window.editor && !window.editor._unloaded) {
      console.log("pedigree editor unloading..");
      PRemoveAllListeners();
      $(editor._topElementID)._p_update("");
      window.onbeforeunload = window.editor._initialBeforeUnloadFunction;
      window._unloaded = true;
      editor._onCloseCallback && editor._onCloseCallback();
      delete window.editor;
    }
  },

  /**
     * Returns the graph node with the corresponding nodeID
     * @method getNode
     * @param {Number} nodeID The id of the desired node
     * @return {AbstractNode} the node whose id is nodeID
     */
  getNode: function(nodeID) {
    return this.getView().getNode(nodeID);
  },

  /**
     * @method getView
     * @return {View} (responsible for managing graphical representations of nodes and interactive elements)
     */
  getView: function() {
    return this._view;
  },

  /**
     * @method getGraph
     * @return {DynamicPositionedGraph} (data model: responsible for managing nodes and their positions)
     */
  getGraph: function() {
    return this._graphModel;
  },

  /**
     * @method getController
     * @return {Controller} (responsible for managing user input and corresponding data changes)
     */
  getController: function() {
    return this._controller;
  },

  /**
     * @method getActionStack
     * @return {ActionStack} (responsible for undoing and redoing actions)
     */
  getActionStack: function() {
    return this._actionStack;
  },

  /**
  * @method getOkCancelDialogue
  * @return {OkCancelDialogue} (responsible for displaying ok/cancel prompts)
  */
  getOkCancelDialogue: function() {
    return this._okCancelDialogue;
  },

  /**
     * @method getNodetypeSelectionBubble
     * @return {NodetypeSelectionBubble} (floating window with initialization options for new nodes)
     */
  getNodetypeSelectionBubble: function() {
    return this._nodetypeSelectionBubble;
  },

  /**
     * @method getSiblingSelectionBubble
     * @return {NodetypeSelectionBubble} (floating window with initialization options for new sibling nodes)
     */
  getSiblingSelectionBubble: function() {
    return this._siblingSelectionBubble;
  },

  /**
     * @method getWorkspace
     * @return {Workspace}
     */
  getWorkspace: function() {
    return this._workspace;
  },

  /**
     * @method getDisorderLegend
     * @return {Legend} Responsible for managing and displaying the disorder legend
     */
  getDisorderLegend: function() {
    return this._disorderLegend;
  },

  /**
     * @method getHPOLegend
     * @return {Legend} Responsible for managing and displaying the phenotype/HPO legend
     */
  getHPOLegend: function() {
    return this._hpoLegend;
  },

  /**
     * @method getGeneLegend
     * @return {Legend} Responsible for managing and displaying the candidate genes legend
     */
  getGeneLegend: function() {
    return this._geneLegend;
  },

  /**
   * Returns a list of all available legends
   */
  getAllLegends: function() {
    return [ this.getHPOLegend(), this.getDisorderLegend(), this.getGeneLegend() ];
  },

  /**
     * @method getPaper
     * @return {Workspace.paper} Raphael paper element
     */
  getPaper: function() {
    return this.getWorkspace().getPaper();
  },

  /**
     * @method isReadOnlyMode
     * @return {Boolean} True iff pedigree drawn should be read only with no handles
     *                   (read-only mode is used for IE8 as well as for template display and
     *                   print and export versions).
     */
  isReadOnlyMode: function() {
    if (this.isUnsupportedBrowser()) {
      return true;
    }
    if (this._readOnlyMode) {
      return true;
    }
    return false;
  },

  isUnsupportedBrowser: function() {
    // http://voormedia.com/blog/2012/10/displaying-and-detecting-support-for-svg-images
    if (!document.implementation.hasFeature('http://www.w3.org/TR/SVG11/feature#BasicStructure', '1.1')) {
      // implies unpredictable behavior when using handles & interactive elements,
      // and most likely extremely slow on any CPU
      return true;
    }
    // http://kangax.github.io/es5-compat-table/
    if (!window.JSON) {
      // no built-in JSON parser - can't proceed in any way; note that this also implies
      // no support for some other functions such as parsing XML.
      //
      // TODO: include free third-party JSON parser and replace XML with JSON when loading data;
      //       (e.g. https://github.com/douglascrockford/JSON-js)
      //
      //       => at that point all browsers which suport SVG but are treated as unsupported
      //          should theoreticaly start working (FF 3.0, Safari 3 & Opera 9/10 - need to test).
      //          IE7 does not support SVG and JSON and is completely out of the running;
      alert('Your browser is not supported and is unable to load and display any pedigrees.\n\n' +
                  'Suported browsers include Internet Explorer version 9 and higher, Safari version 4 and higher, '+
                  'Firefox version 3.6 and higher, Opera version 10.5 and higher, any version of Chrome and most '+
                  'other modern browsers (including mobile). IE8 is able to display pedigrees in read-only mode.');
      window.stop && window.stop();
      return true;
    }
    return false;
  },

  /**
     * @method getSaveLoadEngine
     * @return {SaveLoadEngine} Engine responsible for saving and loading operations
     */
  getSaveLoadEngine: function() {
    return this._saveLoadEngine;
  },

  /**
     * @method getTemplateSelector
     * @return {TemplateSelector}
     */
  getTemplateSelector: function() {
    return this._templateSelector;
  },

  /**
     * @method getImportSelector
     * @return {ImportSelector}
     */
  getImportSelector: function() {
    return this._importSelector;
  },

  /**
     * @method getExportSelector
     * @return {ExportSelector}
     */
  getExportSelector: function() {
    return this._exportSelector;
  },

  /**
     * Returns true if any of the node menus are visible
     * (since some UI interactions should be disabled while menu is active - e.g. mouse wheel zoom)
     *
     * @method isAnyMenuVisible
     */
  isAnyMenuVisible: function() {
    if (this.isReadOnlyMode()) {
      return false;
    }
    if (this.getNodeMenu().isVisible() || this.getNodeGroupMenu().isVisible() || this.getPartnershipMenu().isVisible()) {
      return true;
    }
    return false;
  },

  /**
     * Creates the context menu for Person nodes
     *
     * @method generateNodeMenu
     * @return {NodeMenu}
     */
  generateNodeMenu: function() {
    if (this.isReadOnlyMode()) {
      return null;
    }
    var _this = this;
    return new NodeMenu([
      {
        'name' : 'identifier',
        'label' : '',
        'type'  : 'hidden',
        'tab': 'Personal'
      },
      {
        'name' : 'gender',
        'label' : 'Gender',
        'type' : 'radio',
        'tab': 'Personal',
        'columns': 3,
        'values' : [
          { 'actual' : 'M', 'displayed' : 'Male' },
          { 'actual' : 'F', 'displayed' : 'Female' },
          { 'actual' : 'U', 'displayed' : 'Unknown' }
        ],
        'default' : 'U',
        'function' : 'setGender'
      },
      {
        'name' : 'first_name',
        'label': 'First name',
        'type' : 'text',
        'tab': 'Personal',
        'function' : 'setFirstName'
      },
      {
        'name' : 'last_name',
        'label': 'Last name',
        'type' : 'text',
        'tab': 'Personal',
        'function' : 'setLastName'
      },
      {
        'name' : 'external_id',
        'label': 'Identifier',
        'type' : 'text',
        'tab': 'Personal',
        'function' : 'setExternalID'
      },
      {
        'name' : 'carrier',
        'label' : 'Carrier status',
        'type' : 'radio',
        'tab': 'Clinical',
        'values' : [
          { 'actual' : '', 'displayed' : 'Not affected' },
          { 'actual' : 'carrier', 'displayed' : 'Carrier' },
          { 'actual' : 'affected', 'displayed' : 'Affected' },
          { 'actual' : 'presymptomatic', 'displayed' : 'Pre-symptomatic' }
        ],
        'default' : '',
        'function' : 'setCarrierStatus'
      },
      {
        'name' : 'evaluated',
        'label' : 'Documented evaluation',
        'type' : 'checkbox',
        'tab': 'Clinical',
        'function' : 'setEvaluated'
      },
      {
        'name' : 'disorders',
        'label' : 'Disorders',
        'type' : 'disease-picker',
        'tab': 'Clinical',
        'function' : 'setDisorders'
      },
      {
        'name' : 'candidate_genes',
        'label' : 'Genes',
        'type' : 'gene-picker',
        'tab': 'Clinical',
        'function' : 'setGenes'
      },
      {
        'name' : 'hpo_positive',
        'label' : 'Phenotypic features',
        'type' : 'hpo-picker',
        'tab': 'Clinical',
        'function' : 'setHPO'
      },
      {
        'name' : 'date_of_birth',
        'label' : 'Date of birth',
        'type' : 'date-picker',
        'tab': 'Personal',
        'format' : 'dd/MM/yyyy',
        'function' : 'setBirthDate'
      },
      {
        'name' : 'date_of_death',
        'label' : 'Date of death',
        'type' : 'date-picker',
        'tab': 'Personal',
        'format' : 'dd/MM/yyyy',
        'function' : 'setDeathDate'
      },
      {
        'name' : 'state',
        'label' : 'Individual is',
        'type' : 'radio',
        'tab': 'Personal',
        'columns': 3,
        'values' : [
          { 'actual' : 'alive', 'displayed' : 'Alive' },
          { 'actual' : 'stillborn', 'displayed' : 'Stillborn' },
          { 'actual' : 'deceased', 'displayed' : 'Deceased' },
          { 'actual' : 'miscarriage', 'displayed' : 'Miscarriage' },
          { 'actual' : 'unborn', 'displayed' : 'Unborn' },
          { 'actual' : 'aborted', 'displayed' : 'Aborted' }
        ],
        'default' : 'alive',
        'function' : 'setLifeStatus'
      },
      {
        'name' : 'gestation_age',
        'label' : 'Gestation age',
        'type' : 'select',
        'tab': 'Personal',
        'range' : {'start': 0, 'end': 50, 'item' : ['week', 'weeks']},
        'nullValue' : true,
        'function' : 'setGestationAge'
      },
      {
        'label' : 'Heredity options',
        'name' : 'childlessSelect',
        'values' : [{'actual': 'none', displayed: 'None'},{'actual': 'childless', displayed: 'Childless'},{'actual': 'infertile', displayed: 'Infertile'}],
        'type' : 'select',
        'tab': 'Personal',
        'function' : 'setChildlessStatus'
      },
      {
        'name' : 'adopted',
        'label' : 'Adopted',
        'type' : 'checkbox',
        'tab': 'Personal',
        'function' : 'setAdopted'
      },
      {
        'name' : 'monozygotic',
        'label' : 'Monozygotic twin',
        'type' : 'checkbox',
        'tab': 'Personal',
        'function' : 'setMonozygotic'
      },
      {
        'name' : 'nocontact',
        'label' : 'Not in contact with proband',
        'type' : 'checkbox',
        'tab': 'Personal',
        'function' : 'setLostContact'
      },
      {
        'name' : 'placeholder',
        'label' : 'Placeholder node',
        'type' : 'checkbox',
        'tab': 'Personal',
        'function' : 'makePlaceholder'
      },
      {
        'name' : 'comments',
        'label' : 'Comments',
        'type' : 'textarea',
        'tab': 'Clinical',
        'rows' : 2,
        'function' : 'setComments'
      }
    ], ['Personal', 'Clinical']);
  },

  /**
     * @method getNodeMenu
     * @return {NodeMenu} Context menu for nodes
     */
  getNodeMenu: function() {
    return this._nodeMenu;
  },

  /**
     * Creates the context menu for PersonGroup nodes
     *
     * @method generateNodeGroupMenu
     * @return {NodeMenu}
     */
  generateNodeGroupMenu: function() {
    if (this.isReadOnlyMode()) {
      return null;
    }
    var _this = this;
    return new NodeMenu([
      {
        'name' : 'identifier',
        'label' : '',
        'type'  : 'hidden'
      },
      {
        'name' : 'gender',
        'label' : 'Gender',
        'type' : 'radio',
        'columns': 3,
        'values' : [
          { 'actual' : 'M', 'displayed' : 'Male' },
          { 'actual' : 'F', 'displayed' : 'Female' },
          { 'actual' : 'U', 'displayed' : 'Unknown' }
        ],
        'default' : 'U',
        'function' : 'setGender'
      },
      {
        'name' : 'numInGroup',
        'label': 'Number of persons in this group',
        'type' : 'select',
        'values' : [{'actual': 1, displayed: 'N'}, {'actual': 2, displayed: '2'}, {'actual': 3, displayed: '3'},
          {'actual': 4, displayed: '4'}, {'actual': 5, displayed: '5'}, {'actual': 6, displayed: '6'},
          {'actual': 7, displayed: '7'}, {'actual': 8, displayed: '8'}, {'actual': 9, displayed: '9'}],
        'function' : 'setNumPersons'
      },
      {
        'name' : 'external_ids',
        'label': 'Identifier(s)',
        'type' : 'text',
        'function' : 'setExternalID'
      },
      {
        'name' : 'disorders',
        'label' : 'Known disorders<br>(common to all individuals in the group)',
        'type' : 'disease-picker',
        'function' : 'setDisorders'
      },
      {
        'name' : 'comments',
        'label' : 'Comments',
        'type' : 'textarea',
        'rows' : 2,
        'function' : 'setComments'
      },
      {
        'name' : 'state',
        'label' : 'All individuals in the group are',
        'type' : 'radio',
        'values' : [
          { 'actual' : 'alive', 'displayed' : 'Alive' },
          { 'actual' : 'aborted', 'displayed' : 'Aborted' },
          { 'actual' : 'deceased', 'displayed' : 'Deceased' },
          { 'actual' : 'miscarriage', 'displayed' : 'Miscarriage' }
        ],
        'default' : 'alive',
        'function' : 'setLifeStatus'
      },
      {
        'name' : 'evaluatedGrp',
        'label' : 'Documented evaluation',
        'type' : 'checkbox',
        'function' : 'setEvaluated'
      },
      {
        'name' : 'adopted',
        'label' : 'Adopted',
        'type' : 'checkbox',
        'function' : 'setAdopted'
      }
    ], []);
  },

  /**
     * @method getNodeGroupMenu
     * @return {NodeMenu} Context menu for nodes
     */
  getNodeGroupMenu: function() {
    return this._nodeGroupMenu;
  },

  /**
     * Creates the context menu for Partnership nodes
     *
     * @method generatePartnershipMenu
     * @return {NodeMenu}
     */
  generatePartnershipMenu: function() {
    if (this.isReadOnlyMode()) {
      return null;
    }
    var _this = this;
    return new NodeMenu([
      {
        'label' : 'Heredity options',
        'name' : 'childlessSelect',
        'values' : [{'actual': 'none', displayed: 'None'},{'actual': 'childless', displayed: 'Childless'},{'actual': 'infertile', displayed: 'Infertile'}],
        'type' : 'select',
        'function' : 'setChildlessStatus'
      },
      {
        'name' : 'consangr',
        'label' : 'Consanguinity of this relationship',
        'type' : 'radio',
        'values' : [
          { 'actual' : 'A', 'displayed' : 'Automatic' },
          { 'actual' : 'Y', 'displayed' : 'Yes' },
          { 'actual' : 'N', 'displayed' : 'No' }
        ],
        'default' : 'A',
        'function' : 'setConsanguinity'
      },
      {
        'name' : 'broken',
        'label' : 'Separated',
        'type' : 'checkbox',
        'function' : 'setBrokenStatus'
      }
    ], [], 'relationship-menu');
  },

  /**
     * @method getPartnershipMenu
     * @return {NodeMenu} The context menu for Partnership nodes
     */
  getPartnershipMenu: function() {
    return this._partnershipMenu;
  },

  /**
     * @method convertGraphCoordToCanvasCoord
     * @return [x,y] coordinates on the canvas
     */
  convertGraphCoordToCanvasCoord: function(x, y) {
    var scale = PedigreeEditorParameters.attributes.layoutScale;
    return { x: x * scale.xscale,
      y: y * scale.yscale };
  }
});

export default PedigreeEditor;
