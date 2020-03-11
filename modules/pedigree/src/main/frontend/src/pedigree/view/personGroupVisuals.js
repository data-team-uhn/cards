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
import PersonGroupHoverbox from './personGroupHoverbox';
import PersonVisuals from './personVisuals';
import ReadOnlyHoverbox from './readonlyHoverbox';
import PedigreeEditorParameters from '../pedigreeEditorParameters';

/**
 * Class for organizing graphics for PersonGroup nodes.
 *
 * @class PersonGroupVisuals
 * @constructor
 * @extends AbstractPersonVisuals
 * @param {PersonGroup} node The node for which this graphics are handled
 * @param {Number} x The x coordinate on the canvas
 * @param {Number} y The y coordinate on the canvas
 */

var PersonGroupVisuals = Class.create(PersonVisuals, {

  initialize: function ($super, node, x, y) {
    $super(node,x,y);
    this.setNumPersons(node.getNumPersons());
  },

  generateHoverbox: function(x, y) {
    if (editor.isReadOnlyMode()) {
      return new ReadOnlyHoverbox(this.getNode(), x, y, this.getGenderGraphics());
    } else {
      return new PersonGroupHoverbox(this.getNode(), x, y, this.getGenderGraphics());
    }
  },

  /**
     * Returns all the graphics associated with this PersonGroup
     *
     * @method getAllGraphics
     * @return {Raphael.st} Raphael set containing graphics elements
     */
  getAllGraphics: function ($super) {
    return $super().push(this._label);
  },

  /**
     * Changes the label for the number of people in this group
     *
     * @method setNumPersons
     * @param {Number} numPersons The number of people in this group
     */
  setNumPersons: function(numPersons) {
    this._label && this._label.remove();
    var text = (numPersons && numPersons > 1) ? String(numPersons) : 'n';
    var y = (this.getNode().getLifeStatus() == 'aborted' || this.getNode().getLifeStatus() == 'miscarriage') ? this.getY() - 12 : this.getY();
    var x = (this.getNode().getLifeStatus() == 'aborted') ? this.getX() + 8  : this.getX();
    this._label = editor.getPaper().text(x, y, text).attr(PedigreeEditorParameters.attributes.descendantGroupLabel);
    this._label.node.setAttribute('class', 'no-mouse-interaction');
  }
});

export default PersonGroupVisuals;
