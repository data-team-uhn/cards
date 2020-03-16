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

import { Class, $, $$, PElement } from '../shims/prototypeShim';
import { removeFirstOccurrenceByValue } from '../model/helpers';

/**
 * Base class for various "legend" widgets
 *
 * @class Legend
 * @constructor
 */

var Legend = Class.create( {

  initialize: function(title) {
    this._affectedNodes  = {};     // for each object: the list of affected person nodes

    this._objectColors = {};       // for each object: the corresponding object color

    var legendContainer = $('legend-container');
    if (legendContainer == undefined) {
      var legendContainer = PElement('div', {'class': 'legend-container', 'id': 'legend-container'});
      editor.getWorkspace().getWorkArea()._p_insert(legendContainer);
    }

    this._legendBox = PElement('div', {'class' : 'legend-box', id: 'legend-box'});
    this._legendBox._p_hide();
    legendContainer._p_insert(this._legendBox);

    var legendTitle= PElement('h2', {'class' : 'legend-title'})._p_update(title);
    this._legendBox._p_insert(legendTitle);

    this._list = PElement('ul', {'class' : 'disorder-list'});
    this._legendBox._p_insert(this._list);

    this._legendBox._p_observe('mouseover', function() {
      $$('.menu-box')._p_invoke('_p_setOpacity', .1);
    });
    this._legendBox._p_observe('mouseout', function() {
      $$('.menu-box')._p_invoke('_p_setOpacity', 1);
    });
  },

  /**
     * Returns the prefix to be used on elements related to the object
     * (of type tracked by this legend) with the given id.
     *
     * @method _getPrefix
     * @param {String|Number} id ID of the object
     * @return {String} some identifier which should be a valid HTML id value (e.g. no spaces)
     */
  _getPrefix: function(id) {
    // To be overwritten in derived classes
    throw 'prefix not defined';
  },

  /**
     * Retrieve the color associated with the given object
     *
     * @method getObjectColor
     * @param {String|Number} id ID of the object
     * @return {String} CSS color value for the object, displayed on affected nodes in the pedigree and in the legend
     */
  getObjectColor: function(id) {
    if (!this._objectColors.hasOwnProperty(id)) {
      return '#ff0000';
    }
    return this._objectColors[id];
  },

  /**
     * Returns True if there are nodes reported to have the object with the given id
     *
     * @method _hasAffectedNodes
     * @param {String|Number} id ID of the object
     * @private
     */
  _hasAffectedNodes: function(id) {
    return this._affectedNodes.hasOwnProperty(id);
  },

  /**
     * Registers an occurrence of an object type being tracked by this legend.
     *
     * @method addCase
     * @param {String|Number} id ID of the object
     * @param {String} Name The description of the object to be displayed
     * @param {Number} nodeID ID of the Person who has this object associated with it
     */
  addCase: function(id, name, nodeID) {
    if(Object.keys(this._affectedNodes).length == 0) {
      this._legendBox._p_show();
    }
    if(!this._hasAffectedNodes(id)) {
      this._affectedNodes[id] = [nodeID];
      var listElement = this._generateElement(id, name);
      this._list._p_insert(listElement);
    } else {
      this._affectedNodes[id].push(nodeID);
    }
    this._updateCaseNumbersForObject(id);
  },

  /**
     * Removes an occurrence of an object, if there are any. Removes the object
     * from the 'Legend' box if this object is not registered in any individual any more.
     *
     * @param {String|Number} id ID of the object
     * @param {Number} nodeID ID of the Person who has/is affected by this object
     */
  removeCase: function(id, nodeID) {
    if (this._hasAffectedNodes(id)) {
      removeFirstOccurrenceByValue(this._affectedNodes[id], nodeID);
      if(this._affectedNodes[id].length == 0) {
        delete this._affectedNodes[id];
        delete this._objectColors[id];
        var htmlElement = this._getListElementForObjectWithID(id);
        htmlElement.remove();
        if(Object.keys(this._affectedNodes).length == 0) {
          this._legendBox._p_hide();
        }
      } else {
        this._updateCaseNumbersForObject(id);
      }
    }
  },

  _getListElementForObjectWithID: function(id) {
    return $(this._getPrefix() + '-' + id);
  },

  /**
   * Updates internal references to nodes when node ids is/are changed (e.g. after a node deletion)
   */
  replaceIDs: function(changedIdsSet) {
    for (var abnormality in this._affectedNodes) {
      if (this._affectedNodes.hasOwnProperty(abnormality)) {
        var affectedList = this._affectedNodes[abnormality];
        for (var i = 0; i < affectedList.length; i++) {
          var oldID = affectedList[i];
          var newID = changedIdsSet.hasOwnProperty(oldID) ? changedIdsSet[oldID] : oldID;
          affectedList[i] = newID;
        }
      }
    }
  },

  /**
     * Updates the displayed number of nodes assocated with/affected by the object
     *
     * @method _updateCaseNumbersForObject
     * @param {String|Number} id ID of the object
     * @private
     */
  _updateCaseNumbersForObject : function(id) {
    var label = this._legendBox._p_down('li#' + this._getPrefix() + '-' + id + ' .disorder-cases');
    if (label) {
      var cases = this._affectedNodes.hasOwnProperty(id) ? this._affectedNodes[id].length : 0;
      label._p_update(cases + '&nbsp;case' + ((cases - 1) && 's' || ''));
    }
  },

  /**
     * Generate the element that will display information about the given object in the legend
     *
     * @method _generateElement
     * @param {String|Number} id ID of the object
     * @param {String} name The human-readable object name or description
     * @return {HTMLLIElement} List element to be insert in the legend
     */
  _generateElement: function(id, name) {
    var color = this.getObjectColor(id);
    var bubble = PElement('span', {'class' : 'disorder-color'});
    bubble.style.backgroundColor = color;
    var item = PElement('li', {'class' : 'disorder', 'id' : this._getPrefix() + '-' + id})
       ._p_update(bubble)
       ._p_insert(PElement('span', {'class' : 'disorder-name'})._p_update(name));  
    var countLabel = PElement('span', {'class' : 'disorder-cases'});
    var countLabelContainer = PElement('span', {'class' : 'disorder-cases-container'})._p_insert('(')._p_insert(countLabel)._p_insert(')');
    item._p_insert(' ')._p_insert(countLabelContainer);
    var me = this;
    item._p_observe('mouseover', function() {
      //item._p_setStyle({'text-decoration':'underline', 'cursor' : 'default'});
      item._p_down('.disorder-name')._p_setStyle({'background': color, 'cursor' : 'default'});
      me._affectedNodes[id] && me._affectedNodes[id].forEach(function(nodeID) {
        var node = editor.getNode(nodeID);
        node && node.getGraphics().highlight();
      });
    });
    item._p_observe('mouseout', function() {
      //item._p_setStyle({'text-decoration':'none'});
      item._p_down('.disorder-name')._p_setStyle({'background':'', 'cursor' : 'default'});
      me._affectedNodes[id] && me._affectedNodes[id].forEach(function(nodeID) {
        var node = editor.getNode(nodeID);
        node && node.getGraphics().unHighlight();
      });
    });
    return item;
  }
});

export default Legend;
