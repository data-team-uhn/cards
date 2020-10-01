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

import { Class, Ajax } from './shims/prototypeShim';
import { isInt } from './model/helpers';
/*
 * Disorder is a class for storing genetic disorder info and loading it from the
 * the disorder database. These disorders can be attributed to an individual in the Pedigree.
 *
 * @param disorderID the id number for the disorder, taken from the disorder database
 * @param name a string representing the name of the disorder e.g. "Down Syndrome"
 */

var Disorder = Class.create( {

  initialize: function(disorderID, name, callWhenReady) {
    // user-defined disorders
    if (name == null && !Disorder.isValidID(disorderID)) {
      name = Disorder.desanitizeID(disorderID);
    }

    this._disorderID = Disorder.sanitizeID(disorderID);
    this._name       = name ? name : 'loading...';

    if (!name && callWhenReady) {
      this.load(callWhenReady);
    }
  },

  /*
     * Returns the disorderID of the disorder
     */
  getDisorderID: function() {
    return this._disorderID;
  },

  /*
     * Returns the name of the disorder
     */
  getName: function() {
    return this._name;
  },

  load: function(callWhenReady) {
    var baseDisorderServiceURL = Disorder.getDisorderServiceURL();
    var queryURL           = baseDisorderServiceURL + this._disorderID + '.json';
    //console.log("queryURL: " + queryURL);
    new Ajax.Request(queryURL, {
      method: 'GET',
      onSuccess: this.onDataReady.bind(this),
      //onComplete: complete.bind(this)
      onComplete: callWhenReady ? callWhenReady : {}
    });
  },

  onDataReady : function(response) {
    try {
      var parsed = JSON.parse(response.responseText);
      //console.log(JSON.stringify(parsed));
      console.log('LOADED DISORDER: disorder id = ' + this._disorderID + ', name = ' + parsed.label);
      this._name = parsed.label;
    } catch (err) {
      console.log('[LOAD DISORDER] Error: ' +  err);
    }
  }
});

/*
 * IDs are used as part of HTML IDs in the Legend box, which breaks when IDs contain some non-alphanumeric symbols.
 * For that purpose these symbols in IDs are converted in memory (but not in the stored pedigree) to some underscores.
 */
Disorder.sanitizeID = function(disorderID) {
  if (Disorder.isValidID(disorderID)) {
    return disorderID;
  }
  var temp = disorderID.replace(/[\(\[]/g, '_L_');
  temp = temp.replace(/[\)\]]/g, '_J_');
  return temp.replace(/[^a-zA-Z0-9,;_\-*]/g, '__');
};

Disorder.desanitizeID = function(disorderID) {
  var temp = disorderID.replace(/__/g, ' ');
  temp = temp.replace(/_L_/g, '(');
  return temp.replace(/_J_/g, ')');
};

Disorder.isValidID = function(id) {
  var pattern = /^Orphanet_(\d)+$/i;
  return pattern.test(id);
};

Disorder.getDisorderServiceURL = function() {
  return window.location.origin + "/Vocabularies/ORDO/";
};

export default Disorder;
