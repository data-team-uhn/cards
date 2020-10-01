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

import { Class, $, PFireEvent } from '../shims/prototypeShim';
import HPOTerm from '../hpoTerm';
import Legend from './legend';

/**
 * Class responsible for keeping track of HPO terms and their properties, and for
 * caching disorders data as loaded from the disorder database.
 * This information is graphically displayed in a 'Legend' box
 *
 * @class HPOLegend
 * @constructor
 */
var HPOLegend = Class.create( Legend, {

  initialize: function ($super) {
    $super('Phenotypes');

    this._termCache = {};
  },

  _getPrefix: function(id) {
    return 'phenotype';
  },

  /**
     * Returns the HPOTerm object with the given ID. If object is not in cache yet
     * returns a newly created one which may have the term name & other attributes not loaded yet
     *
     * @method getTerm
     * @return {Object}
     */
  getTerm: function(hpoID) {
    hpoID = HPOTerm.sanitizeID(hpoID);
    if (!this._termCache.hasOwnProperty(hpoID)) {
      var whenNameIsLoaded = function() {
        this._updateTermName(hpoID);
      };
      this._termCache[hpoID] = new HPOTerm(hpoID, null, whenNameIsLoaded.bind(this));
    }
    return this._termCache[hpoID];
  },

  /**
     * Registers an occurrence of a phenotype.
     *
     * @method addCase
     * @param {Number|String} id ID for this term taken from the HPO database
     * @param {String} name The description of the phenotype
     * @param {Number} nodeID ID of the Person who has this phenotype
     */
  addCase: function ($super, id, name, nodeID) {
    if (!this._termCache.hasOwnProperty(id)) {
      this._termCache[id] = new HPOTerm(id, name);
    }

    $super(id, name, nodeID);
  },

  /**
     * Generate the element that will display information about the given phenotype in the legend
     *
     * @method _generateElement
     * @param {Number} hpoID The id for the phenotype
     * @param {String} name Human-readable name
     * @return {HTMLLIElement} List element to be insert in the legend
   */
  _generateElement: function($super, hpoID, name) {
    if (!this._objectColors.hasOwnProperty(hpoID)) {
        var color = this._generateColor(hpoID);
        this._objectColors[hpoID] = color;
        PFireEvent('hpo:color', {'id' : hpoID, color: color});
    }
    return $super(hpoID, name);
  },

  /**
     * Generates a CSS color.
     * Has preference for some predefined colors that can be distinguished in gray-scale
     * and are distint from disorder colors.
     *
     * @method generateColor
     * @return {String} CSS color
     */
  _generateColor: function(geneID) {
    if(this._objectColors.hasOwnProperty(geneID)) {
      return this._objectColors[geneID];
    }

    var usedColors = Object.values(this._objectColors),
      // green palette
      prefColors = ['#81a270', '#c4e8c4', '#56a270', '#b3b16f', '#4a775a', '#65caa3'];
    usedColors.forEach( function(color) {
      removeFirstOccurrenceByValue(prefColors, color);
    });
    if(prefColors.length > 0) {
      return prefColors[0];
    } else {
      var randomColor = Raphael.getColor();
      while(randomColor == '#ffffff' || usedColors.indexOf(randomColor) != -1) {
        randomColor = '#'+((1<<24)*Math.random()|0).toString(16);
      }
      return randomColor;
    }
  },

  /**
     * Updates the displayed phenotype name for the given phenotype
     *
     * @method _updateTermName
     * @param {Number} id The identifier of the phenotype to update
     * @private
     */
  _updateTermName: function(id) {
    //console.log("updating phenotype display for " + id + ", name = " + this.getTerm(id).getName());
    var name = this._legendBox._p_down('li#' + this._getPrefix() + '-' + id + ' .disorder-name');
    name._p_update(this.getTerm(id).getName());
  }
});

export default HPOLegend;
