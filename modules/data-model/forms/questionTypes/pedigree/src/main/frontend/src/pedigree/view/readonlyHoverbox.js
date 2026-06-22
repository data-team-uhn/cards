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
import AbstractHoverbox from './abstractHoverbox';

/**
 * A stub hoverbox used when generating read-only pedigrees
 */
var ReadOnlyHoverbox = Class.create(AbstractHoverbox, {

  initialize: function(node, x, y, shapes) {
    this._node   = node;
    this._nodeX  = x;
    this._nodeY  = y;
    this._shapes = shapes;
  },

  getWidth: function() {
    return 0;
  },

  getHeight: function() {
    return 0;
  },

  getNode: function() {
    return this._node;
  },

  generateButtons: function() {
  },

  removeButtons: function () {
  },

  hideButtons: function() {
  },

  showButtons: function() {
  },

  getCurrentButtons: function() {
    return this._currentButtons;
  },

  removeHandles: function () {
  },

  hideHandles: function() {
  },

  showHandles: function() {
  },

  generateHandles: function() {
  },

  regenerateHandles: function() {
  },

  getBoxOnHover: function() {
    return null;
  },

  isHovered: function() {
    return false;
  },

  setHovered: function(isHovered) {
  },

  setHighlighted: function(isHighlighted) {
  },

  getHoverZoneMask: function() {
    return null;
  },

  getFrontElements: function() {
    return this._shapes;
  },

  getBackElements: function() {
    return this._shapes;
  },

  isMenuToggled: function() {
    return false;
  },

  animateDrawHoverZone: function() {
  },

  animateHideHoverZone: function() {
  },

  disable: function() {
  },

  enable: function() {
  },

  remove: function() {
  },

  onWidgetHide: function() {
  },

  onWidgetShow: function() {
  }
});

export default ReadOnlyHoverbox;
