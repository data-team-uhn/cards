/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

import { Class, $, PElement, PObserveEvent } from './prototypeShim';
import { cloneObject } from '../model/helpers';

var XList = Class.create({
        initialize: function(items, options) {
          this.items = items || [];
          this.options = options || {}
          this.listElement = PElement(this.options.ordered ? "ol" : "ul", {
            'class' : 'xlist' + (this.options.classes ? (' ' + this.options.classes) : '')
          });
          if (this.items && this.items.length > 0) {
            for (var i=0; i<this.items.length; i++) {
              this.addItem(this.items[i]);
            }
          }
        },
        addItem: function(item){ /* future: position (top, N) */
          if (!item || !(item instanceof XListItem)) {
             item = new XListItem(item);
          }
          var listItemElement = item.getElement();
          if (this.options.itemClasses && !this.options.itemClasses.blank()) {
            listItemElement._p_addClassName(this.options.itemClasses);
          }
          this.listElement._p_insert(listItemElement);
          if (typeof this.options.eventListeners == 'object') {
            item.bindEventListeners(this.options.eventListeners);
          }
          if (this.options.icon && !this.options.icon.blank()) {
            item.setIcon(this.options.icon, this.options.overrideItemIcon);
          }
          item.list = this; // associate list item to this XList
        },
        getElement: function() {
          return this.listElement;
        }
    });

var XListItem = Class.create({
        initialize: function(content, options) {
          this.options = options || {};
          var classes = 'xitem ' + (this.options.noHighlight ? '' : 'xhighlight ');
          classes += this.options.classes ? this.options.classes: '';
          this.containerElement = PElement("div", {'class': 'xitemcontainer'})._p_insert(content || '');
          this.containerElement._p_addClassName(this.options.containerClasses || '');
          this.containerElement._p_setStyle({textIndent: '0px'});
          if (this.options.value) {
            this.containerElement._p_insert(PElement('div', {'class':'hidden value'})._p_insert(this.options.value));
          }
          this.listItemElement = PElement("li", {'class' : classes})._p_update( this.containerElement );
          if (this.options.icon && !this.options.icon.blank()) {
            this.setIcon(this.options.icon);
            this.hasIcon = true;
          }
          if (typeof this.options.eventListeners == 'object') {
            this.bindEventListeners(this.options.eventListeners);
          }
        },
        getElement: function() {
          return this.listItemElement;
        },
        setIcon: function(icon, override) {
          if (!this.hasIcon || override) {
            this.iconImage = new Image();
            this.iconImage.onload = function(){
                this.listItemElement._p_setStyle({
                  backgroundImage: "url(" + this.iconImage.src + ")",
                  backgroundRepeat: 'no-repeat',
                  // TODO: support background position as option
                  backgroundPosition : '3px 3px'
                });
                this.listItemElement._p_down(".xitemcontainer")._p_setStyle({
                  textIndent:(this.iconImage.width + 6) + 'px'
                });
            }.bind(this);
            this.iconImage.src = icon;
          }
        },
        bindEventListeners: function(eventListeners) {
          var events = Object.keys(eventListeners);
          for (var i=0; i<events.length; i++) {
            this.listItemElement._p_observe(events[i], eventListeners[events[i]].bind(this.options.eventCallbackScope ? this.options.eventCallbackScope : this));
          }
        }
    });

export { XList, XListItem };