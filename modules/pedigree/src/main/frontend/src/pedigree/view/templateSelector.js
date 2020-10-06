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

import { Class, PElement } from '../shims/prototypeShim';
import PModalPopup from '../shims/phenotipsShim';
import PedigreeTemplates from './templates';

/**
 * The UI Element for browsing and selecting pre-defined Pedigree templates
 *
 * @class TemplateSelector
 * @constructor
 * @param {Boolean} isStartupTemplateSelector Set to True if no pedigree has been loaded yet
 */

var TemplateSelector = Class.create( {

  initialize: function(isStartupTemplateSelector) {
    this._isStartupTemplateSelector = isStartupTemplateSelector;
    this.mainDiv = PElement('div', {'class': 'template-picture-container'});
    this.mainDiv._p_update('Loading list of templates...');
    var closeShortcut = isStartupTemplateSelector ? [] : ['Esc'];
    this.dialog = new PModalPopup(this.mainDiv, {close: {method : this.hide.bind(this), keys : closeShortcut}}, {extraClassName: 'pedigree-template-chooser', title: 'Please select a pedigree template', displayCloseButton: !isStartupTemplateSelector, verticalPosition: 'top'});
    isStartupTemplateSelector && this.dialog.show();

    this.mainDiv._p_update();

    for (var i = 0; i < PedigreeTemplates.length; ++i) {
      var pictureBox = PElement('div', {'class': 'picture-box'});
      pictureBox._p_update('Loading...');
      this.mainDiv._p_insert(pictureBox);
      var template = PedigreeTemplates[i];
      pictureBox.innerHTML = template.image;
      pictureBox.pedigreeData = JSON.stringify(template.data);
      pictureBox.description  = template.description;
      pictureBox.title        = pictureBox.description;

      // TODO: render images with JavaScript instead
      if (window.SVGSVGElement &&
                document.implementation.hasFeature('http://www.w3.org/TR/SVG11/feature#Image', '1.1')) {
        pictureBox._p_update(template.image);
      } else {
        pictureBox.innerHTML = '<table bgcolor=\'#FFFAFA\'><tr><td><br>&nbsp;' + pictureBox.description + '&nbsp;<br><br></td></tr></table>';
      }
      pictureBox._p_observe('click', this._onTemplateSelected.bind(this, pictureBox));
    }
  },

  /**
     * Returns True if this template selector is the one displayed on startup
     *
     * @method isStartupTemplateSelector
     * @return {Boolean}
     */
  isStartupTemplateSelector: function() {
    return this._isStartupTemplateSelector;
  },

  /**
     * Loads the template once it has been selected
     *
     * @param event
     * @param pictureBox
     * @private
     */
  // FIXME: check that the order of argument sis correct after change from bindAsEventListener() to bind()
  _onTemplateSelected: function(pictureBox, event) {
    //console.log("observe onTemplateSelected");
    this.dialog.close();
    editor.getSaveLoadEngine().createGraphFromSerializedData(pictureBox.pedigreeData, false /* add to undo stack */, true /*center around 0*/);
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
    this.dialog.closeDialog();
  }
});

export default TemplateSelector;
