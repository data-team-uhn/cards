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
import PersonHoverbox from './personHoverbox';
import PedigreeEditorParameters from '../pedigreeEditorParameters';

/**
 * PersonGroupHoverbox is a class for all the UI elements and graphics surrounding a PersonGroup node and
 * its labels. This includes the box that appears around the node when it's hovered by a mouse.
 *
 * @class GroupHoverbox
 * @extends AbstractHoverbox
 * @constructor
 * @param {PersonGroup} node The node PersonGroup for which the hoverbox is drawn
 * @param {Number} centerX The x coordinate for the hoverbox
 * @param {Number} centerY The y coordinate for the hoverbox
 * @param {Raphael.st} nodeShapes Raphaël set containing the graphical elements that make up the node
 */

var PersonGroupHoverbox = Class.create(PersonHoverbox, {
  initialize: function ($super, personNode, centerX, centerY, nodeShapes) {
    var radius = PedigreeEditorParameters.attributes.radius * 2;
    $super(personNode, centerX, centerY, nodeShapes);
  },

  /**
    * Creates the handles used in this hoverbox - overriden to generate no handles
    *
    * @method generateHandles
    * @return {Raphael.st} A set of handles
    */
  generateHandles: function() {
    if (this._currentHandles !== null) {
      return;
    }
    // else: no handles
  },

  /**
     * Creates the buttons used in this hoverbox
     *
     * @method generateButtons
     */
  generateButtons: function ($super) {
    if (this._currentButtons !== null) {
      return;
    }
    $super();

    // note: no call to super as we don't want default person buttons
    this.generateMenuBtn();
    this.generateDeleteBtn();
  },

  /**
     * Returns true if the menu for this node is open
     *
     * @method isMenuToggled
     * @return {Boolean}
     */
  isMenuToggled: function() {
    return this._isMenuToggled;
  },

  /**
     * Shows/hides the menu for this node
     *
     * @method toggleMenu
     */
  toggleMenu: function(isMenuToggled) {
    if (this._justClosedMenu) {
      return;
    }
    this._isMenuToggled = isMenuToggled;
    if(isMenuToggled) {
      this.getNode().getGraphics().unmark();
      var optBBox = this.getBoxOnHover().getBBox();
      var x = optBBox.x2;
      var y = optBBox.y;
      var position = editor.getWorkspace().canvasToDiv(x+5, y);
      editor.getNodeGroupMenu().show(this.getNode(), position.x, position.y);
    }
  },

  /**
     * Hides the hoverbox with a fade out animation
     *
     * @method animateHideHoverZone
     */
  animateHideHoverZone: function ($super) {
    this._hidden = true;
    if(!this.isMenuToggled()){
      var parentPartnershipNode = editor.getGraph().getParentRelationship(this.getNode().getID());
      if (parentPartnershipNode && editor.getNode(parentPartnershipNode)) {
        editor.getNode(parentPartnershipNode).getGraphics().unmarkPregnancy();
      }
      $super();
    }
  },

  /**
     * Displays the hoverbox with a fade in animation
     *
     * @method animateDrawHoverZone
     */
  animateDrawHoverZone: function ($super) {
    this._hidden = false;
    if(!this.isMenuToggled()){
      var parentPartnershipNode = editor.getGraph().getParentRelationship(this.getNode().getID());
      if (parentPartnershipNode && editor.getNode(parentPartnershipNode)) {
        editor.getNode(parentPartnershipNode).getGraphics().markPregnancy();
      }
      $super();
    }
  }
});

export default PersonGroupHoverbox;
