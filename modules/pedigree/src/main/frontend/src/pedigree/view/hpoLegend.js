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

import { Class, $ } from '../shims/prototypeShim';
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
     * Retrieve the color associated with the given object	
     *	
     * @method getObjectColor	
     * @param {String|Number} id ID of the object	
     * @return {String} CSS color value for that disorder	
     */	
  getObjectColor: function(id) {	
    return '#CCCCCC';	
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
