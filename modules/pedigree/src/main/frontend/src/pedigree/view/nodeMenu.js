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

import { Class, $, PElement, getDocumentHeight, PObserveEvent, PStopObserving, PFireEvent, getEventMatchingParentElement } from '../shims/prototypeShim';
import { PSuggestWidget, PSuggestPicker } from '../shims/suggestShim';
import Disorder from '../disorder';
import HPOTerm from '../hpoTerm';
import Helpers from '../model/helpers';
import GraphicHelpers from './graphicHelpers';
import PedigreeFuzzyDatePicker from './datepicker';
import PedigreeDate from '../PedigreeDate';

/**
 * NodeMenu is a UI Element containing options for AbstractNode elements
 *
 * @class NodeMenu
 * @constructor
 * @param {Array} data Contains objects corresponding to different menu items
 *
 {
 [
    {
        'name' : the name of the menu item,
        'label' : the text label above this menu option,
        'type' : the type of form input. (eg. 'radio', 'date-picker', 'text', 'textarea', 'disease-picker', 'select'),
        'values' : [
                    {'actual' : actual value of the option, 'displayed' : the way the option will be seen in the menu} ...
                    ]
    }, ...
 ]
 }

 Note: when an item is specified as "inactive" it is completely removed from the menu; when it
       is specified as "disabled" it is greyed-out and does not allow selection, but is still visible.
 */
var NodeMenu = Class.create({
  initialize : function(data, tabs, otherCSSClass) {
    this.canvas = editor.getWorkspace().canvas || $('body');
    var cssClass = 'menu-box';
    if (otherCSSClass) {
      cssClass += ' ' + otherCSSClass;
    }
    this.menuBox = PElement('div', {'class' : cssClass});

    this.closeButton = PElement('span', {'class' : 'close-button'})._p_update('×');
    this.menuBox._p_insert(this.closeButton);  // TODO: check, was insert({top: ...})
    this.closeButton._p_observe('click', this.hide.bind(this));

    this.form = PElement('form', {'method' : 'get', 'action' : '', 'class': 'tabs-content'});

    this.tabs = {};
    this.tabHeaders = {};
    if (tabs && tabs.length > 0) {
      this.tabTop = PElement('dl', {'class':'tabs'});
      for (var i = 0; i < tabs.length; i++) {
        var tabName = tabs[i];
        var activeClass = (i == 0) ? 'active' : '';
        this.tabs[tabName] = PElement('div', {'id': 'tab_' + tabName, 'class': 'content ' + activeClass});
        this.form._p_insert(this.tabs[tabName]);

        this.tabHeaders[tabName] = PElement('dd', {'class': activeClass})._p_insert('<a>' + tabName + '</a>');
        var _this = this;
        var switchTab = function(tabName) {
          return function() {
            for (var tab in _this.tabs) {
              if (_this.tabs.hasOwnProperty(tab)) {
                if (tab != tabName) {
                  _this.tabs[tab].className = 'content';
                  _this.tabHeaders[tab].className = '';
                } else {
                  _this.tabs[tab].className = 'content active';
                  _this.tabHeaders[tab].className = 'active';
                }
              }
            }
            _this.reposition();
          };
        };
        this.tabHeaders[tabName]._p_observe('click', switchTab(tabName));
        this.tabTop._p_insert(this.tabHeaders[tabName]);
      }
      var div = PElement('div', {'class': 'tabholder'})._p_insert(this.tabTop)._p_insert(this.form);
      this.menuBox._p_insert(div);
    } else {
      this.singleTab = PElement('div', {'class': 'tabholder'})._p_insert(this.form);
      this.menuBox._p_insert(this.singleTab);
      this.closeButton._p_addClassName('close-button-old');
      this.form._p_addClassName('content');
    }

    this.fieldMap = {};
    // Generate fields
    var _this = this;
    data.forEach(function(d) {
      if (typeof (_this._generateField[d.type]) == 'function') {
        var insertLocation = _this.form;
        if (d.tab && _this.tabs.hasOwnProperty(d.tab)) {
          insertLocation = _this.tabs[d.tab];
        }
        insertLocation._p_insert(_this._generateField[d.type].call(_this, d));
      }
    });

    // Insert in document
    this.hide();
    editor.getWorkspace().getWorkArea()._p_insert(this.menuBox);

    this._onClickOutside = this._onClickOutside.bind(this);

    // Attach pickers
    this.form.querySelectorAll('.fuzzy-date').forEach(function(item) {
        if (!item.__datePicker) {
            var inputMode = "YMD"; //editor.getPreferencesManager().getConfigurationOption("dateEditFormat");
            item.__datePicker = new PedigreeFuzzyDatePicker(item, inputMode);
        }
    });

    // disorders
    this.form.querySelectorAll('input.suggest-omim').forEach(function (item) {
      if (!item._p_hasClassName('initialized')) {
        // Create the Suggest.
        item._suggest = new PSuggestWidget(item, {
          script: "FIXME",
          varname: 'q',
          noresults: 'No matching terms',
          json: true,
          resultsParameter : 'rows',
          resultId : 'id',
          resultValue : 'name',
          resultInfo : {},
          enableHierarchy: false,
          fadeOnClear : false,
          timeout : 30000,
          parentContainer : $('body')
        });
        if (item._p_hasClassName('multi') && typeof(PSuggestPicker) != 'undefined') {
          item._suggestPicker = new PSuggestPicker(item, item._suggest, {
            'showKey' : false,
            'showTooltip' : false,
            'showDeleteTool' : true,
            'enableSort' : false,
            'showClearTool' : true,
            'inputType': 'hidden',
            'listInsertionElt' : 'input',
            'listInsertionPosition' : 'after',
            'acceptFreeText' : true
          });
        }
        item._p_addClassName('initialized');
        PObserveEvent('ms:suggest:containerCreated', function(event) {
          if (event.memo && event.memo.suggest === item._suggest) {
            item._suggest.container._p_setStyle({'overflow': 'auto', 'maxHeight': getDocumentHeight() - item._suggest.container._p_cumulativeOffset().top + 'px'});
          }
        });
      }
    });
    // genes
    this.form.querySelectorAll('input.suggest-genes').forEach(function(item) {
      if (!item._p_hasClassName('initialized')) {
        var geneServiceURL = "FIXME";
        item._suggest = new PSuggestWidget(item, {
          script: geneServiceURL,
          varname: 'input',
          noresults: 'No matching terms',
          resultsParameter : 'rows',
          json: true,
          resultId : 'ensembl_gene_id',
          resultValue : 'symbol',
          resultInfo : {},
          enableHierarchy: false,
          tooltip :false,
          fadeOnClear : false,
          timeout : 30000,
          parentContainer : $('body')
        });
        if (item._p_hasClassName('multi') && typeof(PSuggestPicker) != 'undefined') {
          item._suggestPicker = new PSuggestPicker(item, item._suggest, {
            'showKey' : false,
            'showTooltip' : false,
            'showDeleteTool' : true,
            'enableSort' : false,
            'showClearTool' : true,
            'inputType': 'hidden',
            'listInsertionElt' : 'input',
            'listInsertionPosition' : 'after',
            'acceptFreeText' : true
          });
        }
        item._p_addClassName('initialized');
        PObserveEvent('ms:suggest:containerCreated', function(event) {
          if (event.memo && event.memo.suggest === item._suggest) {
            item._suggest.container._p_setStyle({'overflow': 'auto', 'maxHeight': getDocumentHeight() - item._suggest.container._p_cumulativeOffset().top + 'px'});
          }
        });
      }
    });
    // HPO terms
    this.form.querySelectorAll('input.suggest-hpo').forEach(function(item) {
      if (!item._p_hasClassName('initialized')) {
        item._suggest = new PSuggestWidget(item, {
          script: "",
          varname: 'q',
          noresults: 'No matching terms',
          json: true,
          resultsParameter : 'rows',
          resultId : 'id',
          resultValue : 'name',
          resultAltName: 'synonym',
          resultCategory : 'term_category',
          resultInfo : {},
          enableHierarchy: false,
          resultParent : 'is_a',
          fadeOnClear : false,
          timeout : 30000,
          parentContainer : $('body')
        });
        if (item._p_hasClassName('multi') && typeof(PSuggestPicker) != 'undefined') {
          item._suggestPicker = new PSuggestPicker(item, item._suggest, {
            'showKey' : false,
            'showTooltip' : false,
            'showDeleteTool' : true,
            'enableSort' : false,
            'showClearTool' : true,
            'inputType': 'hidden',
            'listInsertionElt' : 'input',
            'listInsertionPosition' : 'after',
            'acceptFreeText' : true
          });
        }
        item._p_addClassName('initialized');
        PObserveEvent('ms:suggest:containerCreated', function(event) {
          if (event.memo && event.memo.suggest === item._suggest) {
            item._suggest.container._p_setStyle({'overflow': 'auto', 'maxHeight': getDocumentHeight() - item._suggest.container._p_cumulativeOffset().top + 'px'});
          }
        });
      }
    });

    // Update disorder colors
    this._updateDisorderColor = function(id, color) {
      this.menuBox.querySelectorAll('.field-disorders li input[value="' + id + '"]').forEach(function(item) {
        var colorBubble = item._p_up('li')._p_down('.disorder-color');
        if (!colorBubble) {
          colorBubble = PElement('span', {'class' : 'disorder-color'});
          item._p_up('li')._p_insert_top(colorBubble);
        }
        colorBubble._p_setStyle({background : color});
      });
    }.bind(this);
    PObserveEvent('disorder:color', function(event) {
      if (!event.memo || !event.memo.id || !event.memo.color) {
        return;
      }
      _this._updateDisorderColor(event.memo.id, event.memo.color);
    });

    // Update gene colors
    this._updateGeneColor = function(id, color) {
      this.menuBox.querySelectorAll('.field-candidate_genes li input[value="' + id + '"]').forEach(function(item) {
        var colorBubble = item._p_up('li')._p_down('.disorder-color');
        if (!colorBubble) {
          colorBubble = PElement('span', {'class' : 'disorder-color'});
          item._p_up('li')._p_insert_top(colorBubble);
        }
        colorBubble._p_setStyle({background : color});
      });
    }.bind(this);
    PObserveEvent('gene:color', function(event) {
      if (!event.memo || !event.memo.id || !event.memo.color) {
        return;
      }
      _this._updateGeneColor(event.memo.id, event.memo.color);
    });
  },

  _generateEmptyField : function (data) {
    var result = PElement('div', {'class' : 'field-box field-' + data.name});
    var label = PElement('label', {'class' : 'field-name'})._p_update(data.label);
    result.inputsContainer = PElement('div', {'class' : 'field-inputs'});
    result._p_insert(label)._p_insert(result.inputsContainer);
    this.fieldMap[data.name] = {
      'type' : data.type,
      'element' : result,
      'default' : data['default'] || '',
      'crtValue' : data['default'] || '',
      'function' : data['function'],
      'inactive' : false
    };
    return result;
  },

  _attachFieldEventListeners : function (field, eventNames, values) {
    var _this = this;
    eventNames.forEach(function(eventName) {
      field._p_observe(eventName, function(event) {
        if (_this._updating) {
          return;
        } // otherwise a field change triggers an update which triggers field change etc
        var target = _this.targetNode;
        if (!target) {
          return;
        }
        _this.fieldMap[field.name].crtValue = field._getValue && field._getValue()[0];
        var method = _this.fieldMap[field.name]['function'];

        if (target.getSummary()[field.name].value == _this.fieldMap[field.name].crtValue) {
          return;
        }

        if (method.indexOf('set') == 0 && typeof(target[method]) == 'function') {
          var properties = {};
          properties[method] = _this.fieldMap[field.name].crtValue;
          var event = { 'nodeID': target.getID(), 'properties': properties };
          PFireEvent('pedigree:node:setproperty', event);
        } else {
          var properties = {};
          properties[method] = _this.fieldMap[field.name].crtValue;
          var event = { 'nodeID': target.getID(), 'modifications': properties };
          PFireEvent('pedigree:node:modify', event);
        }
        field._p_fire('pedigree:change');
      });
    });
  },

  update: function(newTarget) {
    if (newTarget) {
      this.targetNode = newTarget;
    }

    if (this.targetNode) {
      this._updating = true;   // needed to avoid infinite loop: update -> _attachFieldEventListeners -> update -> ...
      this._setCrtData(this.targetNode.getSummary());
      this.reposition();
      delete this._updating;
    }
  },

  _generateField : {
    'radio' : function (data) {
      var result = this._generateEmptyField(data);
      var columnClass = data.columns ? 'field-values-' + data.columns + '-columns' : 'field-values';
      var values = PElement('div', {'class' : columnClass});
      result.inputsContainer._p_insert(values);
      var _this = this;
      var _generateRadioButton = function(v) {
        var radioLabel = PElement('label', {'class' : data.name + '_' + v.actual})._p_update(v.displayed);
        var radioButton = PElement('input', {type: 'radio', name: data.name, value: v.actual});
        radioLabel._p_insert_top(radioButton);
        radioButton._getValue = function() {
          return [this.value];
        }.bind(radioButton);
        values._p_insert(radioLabel);
        _this._attachFieldEventListeners(radioButton, ['click']);
      };
      data.values.forEach(_generateRadioButton);

      return result;
    },
    'checkbox' : function (data) {
      var result = this._generateEmptyField(data);
      var checkbox = PElement('input', {type: 'checkbox', name: data.name, value: '1'});
      result._p_down('label')._p_insert(checkbox);
      checkbox._getValue = function() {
        return [this.checked];
      }.bind(checkbox);
      this._attachFieldEventListeners(checkbox, ['click']);
      return result;
    },
    'text' : function (data) {
      var result = this._generateEmptyField(data);
      var text = PElement('input', {type: 'text', name: data.name});
      if (data.tip) {
        text.placeholder = data.tip;
      }
      result.inputsContainer._p_insert(text);
      text._p_wrap('span');
      text._getValue = function() {
        return [this.value];
      }.bind(text);
      //this._attachFieldEventListeners(text, ['keypress', 'keyup'], [true]);
      this._attachFieldEventListeners(text, ['keyup'], [true]);
      return result;
    },
    'textarea' : function (data) {
      var result = this._generateEmptyField(data);
      var properties = {name: data.name};
      properties['class'] = 'textarea-'+data.rows+'-rows'; // for compatibiloity with older browsers not accepting {class: ...}
      var text = PElement('textarea', properties);
      result.inputsContainer._p_insert(text);
      //text._p_wrap('span');
      text._getValue = function() {
        return [this.value];
      }.bind(text);
      this._attachFieldEventListeners(text, ['keyup'], [true]);
      return result;
    },
    'date-picker' : function (data) {
        var result = this._generateEmptyField(data);
        var datePicker = PElement('input', {type: 'text', 'class': 'fuzzy-date', name: data.name, 'title': data.format || '', alt : '' });
        datePicker._getValue = function() { /*console.log("DATE UPDATE: " + this.value);*/ return [new PedigreeDate(JSON.parse(this.value))]; }.bind(datePicker);
        this._attachFieldEventListeners(datePicker, ['xwiki:date:changed']);

        var inputErrorDescription = PElement('span', {'class': 'date-field-input-error'});
        inputErrorDescription._p_hide();

        result.inputsContainer._p_insert(datePicker);
        result._p_insert(inputErrorDescription);
        return result;
    },
    'disease-picker' : function (data) {
      var result = this._generateEmptyField(data);
      var diseasePicker = PElement('input', {type: 'text', 'class': 'suggest multi suggest-omim', name: data.name});
      result._p_insert(diseasePicker);
      diseasePicker._getValue = function() {
        var results = [];
        var container = this._p_up('.field-box');
        if (container) {
          container.querySelectorAll('input[type=hidden][name=' + data.name + ']').forEach(function(item){
            results.push(new Disorder(item.value, item._p_next('.value') && item._p_next('.value').firstChild.nodeValue || item.value));
          });
        }
        return [results];
      }.bind(diseasePicker);
      // Forward the 'custom:selection:changed' to the input
      var _this = this;
      PObserveEvent('custom:selection:changed', function(event) {
        if (event.memo && event.memo.fieldName == data.name && event.memo.trigger && getEventMatchingParentElement(event) != event.memo.trigger && !event.memo.trigger._silent) {
          event.memo.trigger._p_fire('custom:selection:changed');
          _this.reposition();
        }
      });
      this._attachFieldEventListeners(diseasePicker, ['custom:selection:changed']);
      return result;
    },
    'hpo-picker' : function (data) {
      var result = this._generateEmptyField(data);
      var hpoPicker = PElement('input', {type: 'text', 'class': 'suggest multi suggest-hpo', name: data.name});
      result._p_insert(hpoPicker);
      hpoPicker._getValue = function() {
        var results = [];
        var container = this._p_up('.field-box');
        if (container) {
          container.querySelectorAll('input[type=hidden][name=' + data.name + ']').forEach(function(item){
            results.push(new HPOTerm(item.value, item._p_next('.value') && item._p_next('.value').firstChild.nodeValue || item.value));
          });
        }
        return [results];
      }.bind(hpoPicker);
      // Forward the 'custom:selection:changed' to the input
      var _this = this;
      PObserveEvent('custom:selection:changed', function(event) {
        if (event.memo && event.memo.fieldName == data.name && event.memo.trigger && getEventMatchingParentElement(event) != event.memo.trigger && !event.memo.trigger._silent) {
          event.memo.trigger._p_fire('custom:selection:changed');
          _this.reposition();
        }
      });
      this._attachFieldEventListeners(hpoPicker, ['custom:selection:changed']);
      return result;
    },
    'gene-picker' : function (data) {
      var result = this._generateEmptyField(data);
      var genePicker = PElement('input', {type: 'text', 'class': 'suggest multi suggest-genes', name: data.name});
      result._p_insert(genePicker);
      genePicker._getValue = function() {
        var results = [];
        var container = this._p_up('.field-box');
        if (container) {
          container.querySelectorAll('input[type=hidden][name=' + data.name + ']').forEach(function(item){
            results.push(item._p_next('.value') && item._p_next('.value').firstChild.nodeValue || item.value);
          });
        }
        return [results];
      }.bind(genePicker);
      // Forward the 'custom:selection:changed' to the input
      var _this = this;
      PObserveEvent('custom:selection:changed', function(event) {
        if (event.memo && event.memo.fieldName == data.name && event.memo.trigger && getEventMatchingParentElement(event) != event.memo.trigger && !event.memo.trigger._silent) {
          event.memo.trigger._p_fire('custom:selection:changed');
          _this.reposition();
        }
      });
      this._attachFieldEventListeners(genePicker, ['custom:selection:changed']);
      return result;
    },
    'select' : function (data) {
      var result = this._generateEmptyField(data);
      var select = PElement('select', {'name' : data.name});
      result.inputsContainer._p_insert(select);
      select._p_wrap('span');
      var _generateSelectOption = function(v) {
        var option = PElement('option', {'value' : v.actual})._p_update(v.displayed);
        select._p_insert(option);
      };
      if(data.nullValue) {
        _generateSelectOption({'actual' : '', displayed : '-'});
      }
      if (data.values) {
        data.values.forEach(_generateSelectOption);
      } else if (data.range) {
        // generate array of numbers (including endpoints) [data.range.start, data.range.end]
        var range = Array.from(new Array(data.range.end - data.range.start + 1), (x, i) => i + data.range.start);
        range.forEach(function(i) {
          _generateSelectOption({'actual': i, 'displayed' : i + ' ' + data.range.item[+(i!=1)]});
        });
      }
      select._getValue = function() {
        return [(this.selectedIndex >= 0) && this.options[this.selectedIndex].value || ''];
      }.bind(select);
      this._attachFieldEventListeners(select, ['change']);
      return result;
    },
    'hidden' : function (data) {
      var result = this._generateEmptyField(data);
      result._p_addClassName('hidden');
      var input = PElement('input', {type: 'hidden', name: data.name, value: ''});
      result._p_update(input);
      return result;
    }
  },

  show : function(node, x, y) {
    this._onscreen = true;
    this.targetNode = node;
    this._setCrtData(node.getSummary());
    this.menuBox._p_show();
    this.reposition(x, y);
    PObserveEvent('mousedown', this._onClickOutside);
  },

  hide : function() {
    this.hideSuggestPicker();
    this._onscreen = false;
    PStopObserving('mousedown', this._onClickOutside);
    if (this.targetNode) {
      this.targetNode.onWidgetHide();
      delete this.targetNode;
    }
    this.menuBox._p_hide();
    this._clearCrtData();
  },

  hideSuggestPicker: function() {
    this.form.querySelectorAll('input.suggest').forEach(function(item) {
      if (item._suggest) {
        item._suggest.clearSuggestions();
      }
    });
  },

  isVisible: function() {
    return this._onscreen;
  },

  _onClickOutside: function (event) {
    if (!getEventMatchingParentElement(event, '.menu-box') &&
        !getEventMatchingParentElement(event, '.calendar_date_select') &&
      !getEventMatchingParentElement(event, '.suggestItems')) {
      this.hide();
    }
  },

  reposition : function(x, y) {
    x = Math.floor(x);
    if (x !== undefined && isFinite(x)) {
      if (this.canvas && x + this.menuBox._p_getWidth() > (this.canvas._p_getWidth() + 10)) {
        var delta = x + this.menuBox._p_getWidth() - this.canvas._p_getWidth();
        editor.getWorkspace().panByX(delta, true);
        x -= delta;
      }
      this.menuBox.style.left = x + 'px';
    }

    this.menuBox.style.height = '';
    var height = '';
    var top    = '';
    if (y !== undefined && isFinite(y)) {
      y = Math.floor(y);
    } else {
      if (this.menuBox.style.top.length > 0) {
        y  = parseInt(this.menuBox.style.top.match( /^-?(\d+)/g )[0]);
      }
      if (y === undefined || !isFinite(y) || y < 0) {
        y = 0;
      }
    }

    // Make sure the menu fits inside the screen
    if (this.canvas && this.menuBox._p_getHeight() >= (this.canvas._p_getHeight() - 1)) {
      // menu is too big to fit the screen
      top    = 0;
      height = (this.canvas._p_getHeight() - 1) + 'px';
    } else if (this.canvas._p_getHeight() < y + this.menuBox._p_getHeight() + 1) {
      // menu fits the screen, but have to move it higher for that
      var diff = y + this.menuBox._p_getHeight() - this.canvas._p_getHeight() + 1;
      var position = (y - diff);
      if (position < 0) {
        top    = 0;
        height = (this.canvas._p_getHeight() - 1) + 'px';
      } else {
        top    = position + 'px';
        height = '';
      }
    } else {
      top = y + 'px';
      height = '';
    }

    this.menuBox.style.top      = top;
    this.menuBox.style.height   = height;
    this.menuBox.style.overflow = 'auto';
  },

  _clearCrtData : function () {
    var _this = this;
    Object.keys(this.fieldMap).forEach(function (name) {
      _this.fieldMap[name].crtValue = _this.fieldMap[name]['default'];
      _this._setFieldValue[_this.fieldMap[name].type].call(_this, _this.fieldMap[name].element, _this.fieldMap[name].crtValue);
      _this.fieldMap[name].inactive = false;
    });
  },

  _setCrtData : function (data) {
    var _this = this;
    Object.keys(this.fieldMap).forEach(function (name) {
      _this.fieldMap[name].crtValue = data && data[name] && typeof(data[name].value) != 'undefined' ? data[name].value : _this.fieldMap[name].crtValue || _this.fieldMap[name]['default'];
      _this.fieldMap[name].inactive = (data && data[name] && (typeof(data[name].inactive) == 'boolean' || typeof(data[name].inactive) == 'object')) ? data[name].inactive : _this.fieldMap[name].inactive;
      _this.fieldMap[name].disabled = (data && data[name] && (typeof(data[name].disabled) == 'boolean' || typeof(data[name].disabled) == 'object')) ? data[name].disabled : _this.fieldMap[name].disabled;
      _this._setFieldValue[_this.fieldMap[name].type].call(_this, _this.fieldMap[name].element, _this.fieldMap[name].crtValue);
      _this._setFieldInactive[_this.fieldMap[name].type].call(_this, _this.fieldMap[name].element, _this.fieldMap[name].inactive);
      _this._setFieldDisabled[_this.fieldMap[name].type].call(_this, _this.fieldMap[name].element, _this.fieldMap[name].disabled);
    });
  },

  _setFieldValue : {
    'radio': function (container, value) {
      var useValue = (value == '') ? '""' : value;
      var target = container._p_down('input[type=radio][value=' + useValue + ']');
      if (target) {
        target.checked = true;
      }
    },
    'checkbox' : function (container, value) {
      var checkbox = container._p_down('input[type=checkbox]');
      if (checkbox) {
        checkbox.checked = value;
      }
    },
    'text' : function (container, value) {
      var target = container._p_down('input[type=text]');
      if (target) {
        target.value = value;
      }
    },
    'textarea' : function (container, value) {
      var target = container._p_down('textarea');
      if (target) {
        target.value = value;
      }
    },
    'date-picker' : function (container, value) {
        if (!value) {
            value = {};
        }

        var range = value.hasOwnProperty("range") && value.range.hasOwnProperty("years") ? value.range.years : 1;
        var year  = value.hasOwnProperty("year") && value.year ? value.year.toString() : "";
        if (range > 1 && year != "") {
            year = year + "s";
        }
        var month = "";
        var day   = "";

        var dateEditFormat = "YMD";
        var dmyInputMode = (dateEditFormat == "DMY" || dateEditFormat == "MY");
        if ((dmyInputMode || value.year) && value.month) {
            month = value.hasOwnProperty("month") ? value.month.toString() : "";
        }
        if ((dmyInputMode || (value.year && value.month)) && value.day) {
            day = value.hasOwnProperty("day") ? value.day.toString() : "";
        }

        var updated = true;
        var yearSelect = container._p_down('select.year');
        if (yearSelect) {
            var option = yearSelect._p_down('option[value="' + year + '"]');
            if (!option) {
                option = PElement("option", {"value": year})._p_update(year.toString());
                yearSelect._p_insert(option);
            }
            if (option && !option.selected) {
                option.selected = true;
                updated = true;
            }
        }
        var monthSelect = container._p_down('select.month');
        if (monthSelect) {
            var option = monthSelect._p_down('option[value="' + month + '"]');
            if (option && !option.selected) {
                option.selected = true;
                updated = true;
            }
        }
        var daySelect = container._p_down('select.day');
        if (daySelect) {
            var option = daySelect._p_down('option[value="' + day + '"]');
            if (option && !option.selected) {
                option.selected = true;
                updated = true;
            }
        }
        // TODO: replace the code above with an even request to change year-month-date
        if (updated) {
            var updateElement = container._p_down('.fuzzy-date-picker');
            if (updateElement) {
                updateElement._p_fire('datepicker:date:changed');
            }
        }
    },
    'disease-picker' : function (container, values) {
      var _this = this;
      var target = container._p_down('input[type=text].suggest-omim');
      if (target && target._suggestPicker) {
        target._silent = true;
        target._suggestPicker.clearAcceptedList();
        if (values) {
          values.forEach(function(v) {
            target._suggestPicker.addItem(v.id, v.value, '');
            _this._updateDisorderColor(v.id, editor.getDisorderLegend().getObjectColor(v.id));
          });
        }
        target._silent = false;
      }
    },
    'hpo-picker' : function (container, values) {
      var _this = this;
      var target = container._p_down('input[type=text].suggest-hpo');
      if (target && target._suggestPicker) {
        target._silent = true;
        target._suggestPicker.clearAcceptedList();
        if (values) {
          values.forEach(function(v) {
            target._suggestPicker.addItem(v.id, v.value, '');
          });
        }
        target._silent = false;
      }
    },
    'gene-picker' : function (container, values) {
      var _this = this;
      var target = container._p_down('input[type=text].suggest-genes');
      if (target && target._suggestPicker) {
        target._silent = true;
        target._suggestPicker.clearAcceptedList();
        if (values) {
          values.forEach(function(v) {
            target._suggestPicker.addItem(v, v, '');
            _this._updateGeneColor(v, editor.getGeneLegend().getObjectColor(v));
          });
        }
        target._silent = false;
      }
    },
    'select': function (container, value) {
      var useValue = (value == '') ? '""' : value;
      var target = container._p_down('select option[value=' + useValue + ']');
      if (target) {
        target.selected = 'selected';
      }
    },
    'hidden' : function (container, value) {
      var target = container._p_down('input[type=hidden]');
      if (target) {
        target.value = value;
      }
    }
  },

  _toggleFieldVisibility : function(container, doHide) {
    if (doHide) {
      container._p_addClassName('hidden');
    } else {
      container._p_removeClassName('hidden');
    }
  },

  _setFieldInactive : {
    'radio' : function (container, inactive) {
      if (inactive === true) {
        container._p_addClassName('hidden');
      } else {
        container._p_removeClassName('hidden');
        container.querySelectorAll('input[type=radio]').forEach(function(item) {
          if (inactive && Object.prototype.toString.call(inactive) === '[object Array]') {
            item.disabled = (inactive.indexOf(item.value) >= 0);
            if (item.disabled) {
              item._p_up()._p_addClassName('hidden');
            } else {
              item._p_up()._p_removeClassName('hidden');
            }
          } else if (!inactive) {
            item.disabled = false;
            item._p_up()._p_removeClassName('hidden');
          }
        });
      }
    },
    'checkbox' : function (container, inactive) {
      this._toggleFieldVisibility(container, inactive);
    },
    'text' : function (container, inactive) {
      this._toggleFieldVisibility(container, inactive);
    },
    'textarea' : function (container, inactive) {
      this._toggleFieldVisibility(container, inactive);
    },
    'date-picker' : function (container, inactive) {
      this._toggleFieldVisibility(container, inactive);
    },
    'disease-picker' : function (container, inactive) {
      this._toggleFieldVisibility(container, inactive);
    },
    'hpo-picker' : function (container, inactive) {
      this._toggleFieldVisibility(container, inactive);
    },
    'gene-picker' : function (container, inactive) {
      this._toggleFieldVisibility(container, inactive);
    },
    'select' : function (container, inactive) {
      this._toggleFieldVisibility(container, inactive);
    },
    'hidden' : function (container, inactive) {
      this._toggleFieldVisibility(container, inactive);
    }
  },

  _setFieldDisabled : {
    'radio' : function (container, disabled) {
      if (disabled === true) {
        container._p_addClassName('hidden');
      } else {
        container._p_removeClassName('hidden');
        container.querySelectorAll('input[type=radio]').forEach(function(item) {
          if (disabled && Object.prototype.toString.call(disabled) === '[object Array]') {
            item.disabled = (disabled.indexOf(item.value) >= 0);
          }
          if (!disabled) {
            item.disabled = false;
          }
        });
      }
    },
    'checkbox' : function (container, disabled) {
      var target = container._p_down('input[type=checkbox]');
      if (target) {
        target.disabled = disabled;
      }
    },
    'text' : function (container, disabled) {
      var target = container._p_down('input[type=text]');
      if (target) {
        target.disabled = disabled;
      }
    },
    'textarea' : function (container, inactive) {
      // FIXME: Not implemented
    },
    'date-picker' : function (container, inactive) {
      // FIXME: Not implemented
    },
    'disease-picker' : function (container, inactive) {
      // FIXME: Not implemented
    },
    'hpo-picker' : function (container, inactive) {
      // FIXME: Not implemented
    },
    'gene-picker' : function (container, inactive) {
      // FIXME: Not implemented
    },
    'select' : function (container, inactive) {
      // FIXME: Not implemented
    },
    'hidden' : function (container, inactive) {
      // FIXME: Not implemented
    }
  }
});

export default NodeMenu;
