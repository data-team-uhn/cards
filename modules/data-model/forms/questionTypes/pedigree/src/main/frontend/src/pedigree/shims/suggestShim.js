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

import { Class, $, PElement, Ajax, PObserveEvent, PFireEvent, PStopObservingAll, isArray, getEventMatchingParentElement } from './prototypeShim';
import { cloneObject } from '../model/helpers';
import { XList, XListItem } from './xwikiShim';

function _p_extend(destination, source) {
  for (var property in source)
    destination[property] = source[property];
  return destination;
}

function _p_strip(str) {
  return str.replace(/^\s+/, '').replace(/\s+$/, '');
}

function _p_escapeHTML(str) {
  return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

//function _p_flatten(array, value) {
//  if (isArray(value))
//    return array.concat(_p_flatten(value));
//  array.push(value);
//  return array;
//}

 var PSuggestWidget = Class.create({
  options : {
    // The minimum number of characters after which to trigger the suggest
    minchars : 1,
    // The HTTP method for the AJAX request
    method : "get",
    // The name of the request parameter holding the input stub
    varname : "input",
    // The name of the request parameter holding the input stub for exact term search
    matchVarname : "id",
    // The CSS classname of the suggest list
    className : "ajaxsuggest",
    timeout : 30000,
    delay : 500,
    offsety : 0,
    // Display a "no results" message, or simply hide the suggest box when no suggestions are available
    shownoresults : true,
    // The message to display as the "no results" message
    noresults : "No results!",
    maxheight : 250,
    cache : false,
    seps : "",
    icon : null,
    // The name of the JSON variable or XML element holding the results.
    // "results" for the old suggest, "searchResults" for the REST search.
    resultsParameter : "results",
    // The name of the JSON parameter or XML attribute holding the result identifier.
    // "id" for both the old suggest and the REST search.
    resultId : "id",
    // The name of the JSON parameter or XML attribute holding the result value.
    // "value" for the old suggest, "pageFullName" for the REST page search.
    resultValue : "value",
    // The name of the JSON parameter or XML attribute holding the result auxiliary information.
    // "info" for the old suggest, "pageFullName" for the REST search.
    resultInfo : "info",
    // The name of the JSON parameter or XML attribute holding the result category.
    resultCategory : "category",
    // The name of the JSON parameter or XML attribute holding the result alternative name.
    resultAltName : "",
    // The name of the JSON parameter or XML attribute holding the result icon.
    resultIcon: "icon",
    // The name of the JSON parameter or XML attribute holding a potential result hint (displayed next to the value).
    resultHint: "hint",
    // What kind of tooltip (if any) should be attached to each entry. Default: none.
    tooltip: false,
    // If free text entries allowed, mark them as such. Default: false.
    markFreeText: false,
    // The id of the element that will hold the suggest element
    parentContainer : "pedigreeEditor",
    // Should results fragments be highlighted when matching typed input
    highlight: true,
    // Fade the suggestion container on clear
    fadeOnClear: false,
    // Show a 'hide suggestions' button
    enableHideButton: true,
    insertBeforeSuggestions: null,
    // Should id be displayed or hidden
    displayId: false,
    // If multiple ids are available will return only the first one iff set to true, all otherwise.
    forceFirstId : false,
    // Should value be displayed as a hint
    displayValue: false,
    // Display value prefix text
    displayValueText: "Value :",
    // How to align the suggestion list when its width is different from the input field width
    align: "left",
    // When there are several suggest sources, should the widget displays only one, unified, "loading" indicator for all requests undergoing,
    // Or should it displays one loading indicator per request next to the corresponding source.
    unifiedLoader: false,
    // The DOM node to use to display the loading indicator when in mode unified loader (it will receive a "loading" class name for the time of the loading)
    // Default is null, which falls back on the input itself. This option is used only when unifiedLoader is true.
    loaderNode: null,
    // A function returning true or false for each fetched suggestion. If defined, only suggestions for which 'true' is returned
    // are added to the list
    filterFunc: null,
    // The expected data format, XML or JSON by default
    json: true
  },
  sInput : "",
  nInputChars : 0,
  aSuggestions : [],
  iHighlighted : null,
  isActive : false,
  // True iff the suggest dropdown is displayed, or an item from it was just selected.
  suggestionSelected: false,
  // Input that has been submitted.
  lastSubmitted: "",

  /**
   * Initialize the suggest
   *
   * @param {Object} fld the suggest field
   * @param {Object} param the options
   */
  initialize: function (fld, param){

    if (!fld) {
      return false;
    }
    this.setInputField(fld);

    // Clone default options from the prototype so that they are not shared and extend options with passed parameters
    this.options = _p_extend(cloneObject(this.options), param || { });
    this.sources = this.options;

    this.sources = [ this.sources ];
    // Flatten sources
    //this.sources = [ this.sources ].flatten().compact();

    // Reset the container if the configured parameter is not valid
    if (!$(this.options.parentContainer)) {
      this.options.parentContainer = $(document.body);
    }

    if (this.options.seps) {
      this.seps = this.options.seps;
    } else {
      this.seps = "";
    }

    // Initialize a request number that will keep track of the latest request being fired.
    // This will help to discard potential non-last requests callbacks ; this in order to have better performance
    // (less unneccessary DOM manipulation, and less unneccessary highlighting computation).
    this.latestRequest = 0;

    this.addInputObservers();
  },

  /**
   * Sets or replace the input field associated with this suggest.
   */
  setInputField: function(input){
    if (this.fld) {
      PStopObservingAll(this.fld);
    }
    this.fld = $(input);
    this.fld._suggestWidget = this;
    // Bind the key listeners on the input field.
    this.fld._p_observe("keyup", this.onKeyUp.bind(this));
    this.fld._p_observe("keydown", this.onKeyPress.bind(this));
    this.fld._p_observe("paste", this.onPaste.bind(this));

    // Prevent normal browser autocomplete
    this.fld.setAttribute("autocomplete", "off");

    this.fld._p_observe("blur", function(event){
      // Make sure any running request will be dropped after the input field has been left
      this.latestRequest++;
      this.fld._p_removeClassName("loading");
    }.bind(this));
  },

  addInputObservers: function() {
    this.fld._p_observe("ms:suggest:containerCreated", this.onSuggestCreated.bind(this));

    this.fld._p_observe("ms:suggest:selected", this.onDataSubmitted.bind(this));

    this.fld._p_observe("change", this.onInputChanged.bind(this));
  },

  /**
   * Treats normal characters and triggers the autocompletion behavior. This is needed since the field value is not
   * updated when keydown/keypress are called, so the suggest would work with the previous value. The disadvantage is
   * that keyUp is not fired for each stroke in a long keypress, but only once at the end. This is not a real problem,
   * though.
   */
  onKeyUp: function(event)
  {
    var key = event.keyCode;
    switch(key) {
      // Ignore special keys, which are treated in onKeyPress
      case Event.KEY_RETURN:
      case Event.KEY_TAB:
      case Event.KEY_ESC:
      case Event.KEY_UP:
      case Event.KEY_DOWN:
        break;
      default: {
        // If there are separators in the input string, get suggestions only for the text after the last separator
        // TODO The user might be typing in the middle of the field, not in the last item. Do a better detection by
        // comparing the new value with the old one.
        if(this.seps) {
          var lastIndx = -1;
          for(var i = 0; i < this.seps.length; i++) {
            if(this.fld.value.lastIndexOf(this.seps.charAt(i)) > lastIndx) {
              lastIndx = this.fld.value.lastIndexOf(this.seps.charAt(i));
            }
          }
          if(lastIndx == -1) {
            this.getSuggestions(this.fld.value);
          } else {
            this.getSuggestions(this.fld.value.substring(lastIndx+1));
          }
        } else {
          this.getSuggestions(this.fld.value);
        }
      }
    }
  },
  /**
   * Use the key press routine to search as if some "other" key was pressed;
   * Pasted value is not yet available at the time of the "paste" event, so schedule
   * the handler to fire immediately after paste processing is done.
   */
  onPaste: function(event) {
    setTimeout(function () {
        this.onKeyUp({"keyCode": null});
      }.bind(this),0);
  },
  /**
   * Treats Up and Down arrows, Enter and Escape, affecting the UI meta-behavior. Enter puts the currently selected
   * value inside the target field, Escape closes the suggest dropdown, Up and Down move the current selection.
   */
  onKeyPress: function(event) {
    if(!$(this.isActive)) {
      // Stop Return from submitting the form
      if (event.keyCode == Event.KEY_RETURN) {
        Event.stop(event);
      }
      // Let all other key events pass through if the UI is not displayed
      return;
    }
    var key = event.keyCode;

    switch(key) {
      case Event.KEY_RETURN:
        this.setHighlightedValue();
        Event.stop(event);
        break;
      case Event.KEY_TAB:
        this.clearSuggestions();
        break;
      case Event.KEY_ESC:
        this.clearSuggestions();
        Event.stop(event);
        break;
      case Event.KEY_UP:
        this.changeHighlight(key);
        Event.stop(event);
        break;
      case Event.KEY_DOWN:
        this.changeHighlight(key);
        Event.stop(event);
        break;
      default:
        break;
    }
  },

  /**
   * Attaches mousedown observers on newly created suggests.
   *
   * @param event the event being observed
   */
  onSuggestCreated : function (event) {
    this.suggestionSelected = false;
    var _this = this;
    // A mousedown event occurred in the suggest. Don't want onInputChanged to perform a search on this loss of focus
    event.memo.container && $(event.memo.container)._p_observe("mousedown", function(event) {
      _this.suggestionSelected = true;
    });
  },

  /**
   * Reset the suggest variable.
   *
   * @param event the event being observed
   */
  onDataSubmitted : function (event) {
    // Prevents the onInputChanged performing searches unnecessarily, as an item was just selected from dropdown.
    this.suggestionSelected = true;
    this.lastSubmitted = event.memo.value || _p_strip(this.fld.value);
  },

  /**
   * If no items are selected from the suggest dropdown, checks the suggest widget suggestions for exact matches.
   * If no exact match is found, performs a server-side search for the exact match.
   *
   * @param event the event being observed
   */
  onInputChanged : function (event) {
    if (this.suggestionSelected || this.fld.value.length < this.options.minchars) {
      this.suggestionSelected = false;
      return;
    }
    // Prepare the query.
    var query = _p_strip(this.fld.value);

    // Try to find a match among the suggest items.
    var matchSubmitted = this.submitDropdownMatch(query);

    // If no match found in suggest widget, perform a server-side search for the exact match.
    // This will most likely never happen.
    if (!matchSubmitted) {
      this.fld._p_fire("ms:suggest:not-selected");
      // Our input didn't change. No need to search again.
      if (query == this.lastSubmitted && query.length > 1) {
        return;
      }
      clearTimeout(this.ajID);
      this.submitAjaxSearchMatch(query, ++this.latestRequest);
    }
  },

  /**
   * Finds a match among the dropdown menu items, if exists, and submits it through the suggest widget.
   *
   * @param query the search query
   * @return {boolean} true iff a match was found, false otherwise
   */
  submitDropdownMatch : function (query) {
    if (typeof this.aSuggestions == 'undefined' || this.aSuggestions.length <= 0) {
      return false;
    }
    var modQuery = query.toUpperCase();
    for (var i = 0; i < this.aSuggestions.length; i++) {
      var item = this.aSuggestions[i];
      if (_p_strip(item.value).toUpperCase() === modQuery || _p_strip(item.id).toUpperCase() === modQuery) {
        this.acceptEntry(item, item.value, item.value);
        return true;
      }
    }
    return false;
  },

  /**
   * Performs a server-side search for the query, and submits if an exact match exists.
   *
   * @param query the search query
   * @param requestId the id of the current request
   */
  submitAjaxSearchMatch : function (query, requestId) {
    this.lastSubmitted = _p_strip(this.fld.value);
    if (requestId < this.latestRequest || _p_strip(this.fld.value).length < this.options.minchars) {
      return;
    }

    for (var i = 0; i < this.sources.length; i++) {
      var source = this.sources[i];

      if (!source.matchScript) {
        continue;
      }

      var url = source.matchScript + source.matchVarname + "=" + encodeURIComponent(query);
      var method = source.method || "get";
      var headers = {};
      if (source.json) {
        headers.Accept = "application/json";
      } else {
        headers.Accept = "application/xml";
      }

      var setSearchDataonSuccess = function(response) {
        this.setSearchData(response, source, query, requestId);
      }.bind(this);

      var ajx = new Ajax.Request(url, {
        method: method,
        requestHeaders: headers,
        onCreate : function () {
          this.fld._p_addClassName("loading");
        }.bind(this),
        onSuccess: setSearchDataonSuccess,
        onComplete : function () {
          if (requestId < this.latestRequest) {
            return;
          }
          this.fld._p_removeClassName("loading");
        }.bind(this)
      });
    }
  },

  /**
   * Process and set the data obtained from the ajax request.
   *
   * @param response the response from the ajax request
   * @param source
   * @param query the user-entered query term
   * @param requestId the ID of the current request
   * @return {boolean} false iff no json or xml data is available
   */
  setSearchData : function (response, source, query, requestId) {
    if (requestId < this.latestRequest) {
      return false;
    }

    var result, _getResultFieldValue, _getResultFieldValueAsArray;
    if (source && source.json) {
      var result = response.responseJSON;
      if (!result) {
        return false;
      }

      _getResultFieldValue = function(data, fieldName) {
        var result = data && (data[fieldName + "_translated"] || data[fieldName]) || '';
        if (isArray(result))
          return result[0];
        return result;
      };

      _getResultFieldValueAsArray = function(data, fieldName) {
        return data && (data[fieldName + "_translated"] || data[fieldName]) || [];
      };
    } else {
      var result = response.responseXML;
      if (!result) {
        return false;
      }

      _getResultFieldValue = function(data, selector) {
        var element = data && data._p_down(selector);
      return element && element.firstChild && element.firstChild.nodeValue || '';
      };

      _getResultFieldValueAsArray = function(data, selector) {
        var result = [];
        if (data) {
          data.querySelectorAll(selector).forEach(function(item) {
            var value = item.firstChild && item.firstChild.nodeValue;
            if (value) {
              result.push(value);
            }
          });
        }
        return result;
      };
    }
    var data = {
      'suggest' : this,
      'id': this._processId(_getResultFieldValue(result, source.resultId || this.options.resultId), source.forceFirstId || this.options.forceFirstId),
      'value': _getResultFieldValue(result, source.resultValue || this.options.resultValue),
      'info'    : this.generateResultInfo(result, _getResultFieldValueAsArray),
      'icon': _getResultFieldValue(result, source.resultIcon || this.options.resultIcon),
      'category': this.generateResultCategory(result, _getResultFieldValueAsArray)
    };

    this.acceptTypedInput(data, data.value)
  },

  /**
   * Accept the data provided.
   *
   * @param data the provided data
   * @param newValue the new displayed value for the input
   * @param silent true if the data should not be displayed in the input
   */
  acceptTypedInput : function (data, newValue, silent) {
    var event = this.fld._p_fire("ms:suggest:selected", data);
    if (!event.stopped) {
      if (!silent) {
        this.fld.value = newValue || this.fld.defaultValue || '';
      }
      // pass selected object to callback function, if exists
      if (typeof(this.options.callback) == "function") {
        this.options.callback(data);
      }
    }
  },

  /**
   * Get suggestions
   *
   * @param {Object} val the value to get suggestions for
   */
  getSuggestions: function (val)
  {
    // if input stays the same, do nothing
    //
    val = _p_strip(val).toLowerCase();
    if (val == this.sInput && val.length > 1) {
      return false;
    }

    if (val.length == 0) {
      this.sInput = "";
      this.clearSuggestions();
      return false;
    }
    // input length is less than the min required to trigger a request
    // reset input string
    // do nothing
    //
    if (val.length < this.options.minchars) {
      this.sInput = "";
      return false;
    }

    // if caching enabled, and user is typing (ie. length of input is increasing)
    // filter results out of aSuggestions from last request
    //
    if (val.length>this.nInputChars && this.aSuggestions.length && this.options.cache)
    {
      var arr = [];
      for (var i=0;i<this.aSuggestions.length;i++) {
        if (this.aSuggestions[i].value.substr(0,val.length).toLowerCase() == val) {
          arr.push( this.aSuggestions[i] );
        }
      }

      this.sInput = val;
      this.nInputChars = val.length;
      this.aSuggestions = arr;

      this.createList(this.aSuggestions);

      return false;
    } else  {
      // do new request
      this.sInput = val;
      this.nInputChars = val.length;

      this.prepareContainer();

      this.latestRequest++;
      var pointer = this;
      var requestId = this.latestRequest;
      clearTimeout(this.ajID);
      this.ajID = setTimeout( function() { pointer.doAjaxRequests(requestId) }, this.options.delay );

    }
    return false;
  },

  /**
   * Fire the AJAX Request(s) that will get suggestions
   */
  doAjaxRequests: function (requestId)
  {
    if (requestId < this.latestRequest) {
      return;
    }

    if (this.fld.value.length < this.options.minchars) {
      return;
    }

    for (var i=0; i<this.sources.length; i++) {
      var source = this.sources[i];

      // create ajax request
      var query = _p_strip(this.fld.value);
      var url = source.script + source.varname + "=" + encodeURIComponent(query);
      var method = source.method || "get";
      var headers = {};
      if (source.json) {
        headers.Accept = "application/json";
      } else {
        headers.Accept = "application/xml";
      }

      var updateSuggestions = function(response) {
        this.setSuggestions(response, source, requestId);
      }.bind(this);

      var ajx = new Ajax.Request(url, {
        method: method,
        requestHeaders: headers,
        onCreate : function () {
          this.fld._p_addClassName("loading");
        }.bind(this),
        onSuccess: updateSuggestions,
        onFailure: function (response) {
          //new PhenoTips.widgets.Notification("Failed to retrieve suggestions : ')" + response.statusText, "error", {timeout: 5});
          console.log("Failed to retrieve suggestions : " + response.statusText);

          // even if there are no suggestions => at least dsplay "use your own text" option
          var result = {};
          result[source.resultsParameter] = [];
          var fakeResponse = { "responseJSON": result };
          updateSuggestions(fakeResponse);
        },
        onComplete : function () {
          if (requestId < this.latestRequest) {
            return;
          }
          this.fld._p_removeClassName("loading");
        }.bind(this)
      });
    }
  },

  /**
   * Set suggestions
   *
   * @param {Object} req
   * @param {Object} source
   * @param {Number} requestId the identifier of the request for which this callback is triggered.
   */
  setSuggestions: function (req, source, requestId)
  {

    // If there has been one or several requests fired in the mean time (between the time the request for which this callback
    // has been triggered and the time of the callback itself) ; we don't do anything and leave it to following callbacks to
    // set potential suggestions
    if (requestId < this.latestRequest) {
      return;
    }

    this.aSuggestions = this.getSuggestionList(req, source);
    this.createList(this.aSuggestions, source);
  },

  getSuggestionList : function (req, source) {
    var aSuggestions = [];
    if (source && source.json) {
      var jsondata = req.responseJSON;
      if (!jsondata) {
        return false;
      }
      var results = jsondata[source.resultsParameter || this.options.resultsParameter];

      var _getResultFieldValue = function(data, fieldName) {
        var result = data && (data[fieldName + "_translated"] || data[fieldName]) || '';
        if (isArray(result))
          return result[0];
        return result;
      }

      var _getResultFieldValueAsArray = function(data, fieldName) {
        return data && (data[fieldName + "_translated"] || data[fieldName]) || [];
      };
    } else {
      var xmldata = req.responseXML;
      if (!xmldata) {
        return false;
      }
      var results = xmldata.getElementsByTagName((source && source.resultsParameter) || this.options.resultsParameter);

      var _getResultFieldValue = function(data, selector) {
        var element = data && data._p_down(selector);
        return element && element.firstChild && element.firstChild.nodeValue || '';
      }

      var _getResultFieldValueAsArray = function(data, selector) {
        var result = new Array();
        if (data) {
          data.querySelectorAll(selector).forEach(function(item) {
            var value = item.firstChild && item.firstChild.nodeValue;
            if (value) {
              result.push(value);
            }
          });
        }
        return result;
      };
    }

    for (var i = 0; i < results.length; i++) {
      var info = this.generateResultInfo(results[i], _getResultFieldValueAsArray);
      var category = this.generateResultCategory(results[i], _getResultFieldValueAsArray);

      if (this.options.resultAltName) {
        var bestNameMatch = '';
        var name =  _getResultFieldValue(results[i], source.resultValue || this.options.resultValue);
        var altNames = _getResultFieldValueAsArray(results[i], source.resultAltName || this.options.resultAltName);
        var nameMatchScore = this.computeSimilarity(name, this.sInput);
        for (var k = 0; k < altNames.length; ++k) {
           var altNameMatchScore = this.computeSimilarity(altNames[k], this.sInput);
           if (altNameMatchScore > nameMatchScore) {
             bestNameMatch = altNames[k];
             nameMatchScore = altNameMatchScore;
           }
        }
      }

      aSuggestions.push({
        'id': this._processId(_getResultFieldValue(results[i], source.resultId || this.options.resultId), source.forceFirstId || this.options.forceFirstId),
        'value': _getResultFieldValue(results[i], source.resultValue || this.options.resultValue),
        'icon': _getResultFieldValue(results[i], source.resultIcon || this.options.resultIcon),
        'altName': bestNameMatch,
        'info'    : info,
        'category': category
      });
    }
    return aSuggestions;
  },

  _processId : function (identifier, forceFirstId) {
      if (identifier.constructor === Array && forceFirstId) {
        return identifier.length > 0 ? identifier[0] : '';
      } else {
        return identifier;
      }
  },

  /**
   * Generates HTML containing the result information.
   *
   * @param result the ajax search result
   * @param getResultValueAsArray callback function
   * @return {*} the info html string
   * @private
   */
  generateResultInfo : function (result, getResultValueAsArray) {
    var info = PElement("dl");
    var _this = this;
    for (var section in this.options.resultInfo) {
      var sOptions = this.options.resultInfo[section];
      var sectionClass = _p_strip(section).toLowerCase().replace(/[^a-z0-9 ]/gi, '').replace(/\s+/gi, "-");
      var sectionState = "";
      if (sOptions.collapsed) {
        sectionState = "collapsed";
      }
      var processingFunction = sOptions.processor;
      if (sOptions.extern) {
        var trigger = PElement("a").update(section);
        trigger._processingFunction = processingFunction;
        info._p_insert(PElement("dt", {'class' : sectionState + " " + sectionClass})
          ._p_insert(trigger));
        trigger._processingFunction.call(this, trigger);
        continue;
      }
      var selector = sOptions.selector;
      if (!selector) {
        continue;
      }
      var sectionContents = null;
      getResultValueAsArray(result, selector).forEach(function(item) {
        var text = item || '';
        if (typeof (processingFunction) == "function") {
          text = processingFunction(text);
        }
        if (text == '') {return;}
        if (!sectionContents) {
          var trigger = PElement("a", {'class' : 'expand-tool'})
            ._p_update(_this._getExpandCollapseTriggerSymbol(sOptions.collapsed));
          info._p_insert(PElement("dt", {'class' : sectionState})
            ._p_insert_top(trigger)
            ._p_insert(section));
          sectionContents = PElement("dd", {'class' : 'expandable'});
          info._p_insert(sectionContent);
          trigger._p_observe('click', function(event) {
            event.stop();
            trigger._p_up()._p_toggleClassName('collapsed');
            trigger._p_update(_this._getExpandCollapseTriggerSymbol(trigger._p_up()._p_hasClassName('collapsed')));
          }.bind(this));
        }
        sectionContents._p_insert(PElement("div")._p_update(text));
      });
    }
    if (!info.hasChildNodes()) {
      info = '';
    }
    return info;
  },

  /**
   * Generates HTML if result category is set.
   *
   * @param result the ajax search result
   * @param getResultValueAsArray callback function
   * @return {*} the category html string
   * @private
   */
  generateResultCategory : function (result, getResultValueAsArray) {
    if (this.options.resultCategory) {
      var category = PElement("span", {'class' : 'hidden term-category'});
      getResultValueAsArray(result, this.options.resultCategory).forEach(function(c) {
        category._p_insert(PElement('input', {'type' : 'hidden', 'value' : c}));
      });
    }
    if (!this.options.resultCategory || !category.hasChildNodes()) {
      category = '';
    }
    return category;
  },

  _getExpandCollapseTriggerSymbol : function(isCollapsed) {
    if (isCollapsed) return "&#x25B8;";
    return "&#x25BE;";
  },

  /**
   * Compute the Smith Waterman similarity between two strings
   */
  computeSimilarity: function(str1, str2) {
    var score;
    var maxSoFar=0;
    var gapCost = 2;

    // get values
    var a = str1;
    var m = a.length;

    //n is the length of currFieldValue
    var b = str2;
    var n = b.length;

    //declare the matrix
    var d = new Array();

    for (var i = 0; i < n; i++) {
      d[i] = new Array();

      // get the substitution score
      score = (a.charAt(i) == b.charAt(0))? 1: -1;

      if (i == 0) {
        d[0][0] = Math.max(0,-gapCost,score);
      }else {
        d[i][0] = Math.max(0,d[i - 1][0] - gapCost,score);
      }

      //update max possible if available
      if (d[i][0] > maxSoFar) {
        maxSoFar = d[i][0];
      }
    }

    for (var j = 0; j < m; j++) {
      // get the substitution score
      score = (a.charAt(0) == b.charAt(j))? 1: -1;

      if (j == 0) {
        d[0][0] = Math.max(0,-gapCost,score);
      }else {
        d[0][j] = Math.max(0,d[0][j - 1] - gapCost,score);
      }

      //update max possible if available
      if (d[0][j] > maxSoFar) {
        maxSoFar = d[0][j];
      }
    }

    // cycle through rest of table filling values from the lowest cost value of the three part cost function
    for (var i = 1; i < n; i++) {
      for (var j = 1; j < m; j++) {
        // get the substitution score
        score = (a.charAt(i) == b.charAt(j))? 1: -1;

        // find lowest cost at point from three possible
        d[i][j] = Math.max(0,d[i - 1][j] - gapCost,d[i][j - 1] - gapCost,d[i - 1][j - 1] + score);
        //update max possible if available
        if (d[i][j] > maxSoFar) {
          maxSoFar = d[i][j];
        }
      }
    }
    // return max value within matrix as holds the maximum edit score
    return maxSoFar;
  },


  /**
   * Creates the container that will hold one or multiple source results.
   */
  prepareContainer: function() {

    var crtContainer = $(this.options.parentContainer)._p_down('.suggestItems');

    if (crtContainer && crtContainer.__targetField != this.fld) {
       if (crtContainer.__targetField) {
         crtContainer.__targetField._suggest.clearSuggestions();
       } else {
         crtContainer.remove();
       }
       crtContainer = false;
    }

    if (!crtContainer) {
      // If the suggestion top container is not in the DOM already, we create it and inject it

      var div = PElement("div", { 'class': "suggestItems "+ this.options.className });

      // Get position of target textfield
      var pos = this.fld._p_cumulativeOffset();

      // Container width is passed as an option, or field width if no width provided.
      // The 2px substracted correspond to one pixel of border on each side of the field,
      // this allows to have the suggestion box borders well aligned with the field borders.
      // FIXME this should be computed instead, since border might not always be 1px.
      var containerWidth = this.options.width ? this.options.width : (this.fld.offsetWidth - 2)

      if (this.options.align == 'left') {
        // Align the box on the left
        div.style.left = pos.left + "px";
      } else if (this.options.align == "center") {
        // Align the box to the center
        div.style.left = pos.left + (this.fld.getWidth() - containerWidth - 2) / 2 + "px";
      } else {
        // Align the box on the right.
        // This has a visible effect only when the container width is not the same as the input width
        div.style.left = (pos.left - containerWidth + this.fld.offsetWidth - 2) + "px";
      }

      div.style.top = (pos.top + this.fld.offsetHeight + this.options.offsety) + "px";
      div.style.width = containerWidth + "px";

      // set mouseover functions for div
      // when mouse pointer leaves div, set a timeout to remove the list after an interval
      // when mouse enters div, kill the timeout so the list won't be removed
      var pointer = this;
      div.onmouseover = function(){ pointer.killTimeout() }
      div.onmouseout = function(){ pointer.resetTimeout() }

      this.resultContainer = PElement("div", {'class':'resultContainer'});
      div.appendChild(this.resultContainer);

      // add DIV to document
      $(this.options.parentContainer)._p_insert(div);

      this.container = div;

      if (this.options.insertBeforeSuggestions) {
        this.resultContainer._p_insert(this.options.insertBeforeSuggestions);
      }

      this.fld._p_fire("ms:suggest:containerCreated", {
        'container' : this.container,
        'suggest' : this
      });
    }

    if (this.sources.length > 1) {
      // If we are in multi-source mode, we need to prepare a sub-container for each of the suggestion source
      for (var i=0;i<this.sources.length;i++) {

        var source = this.sources[i];
        source.id = i

        if(this.resultContainer._p_down('.results' + source.id)) {
          // If the sub-container for this source is already present, we just re-initialize it :
          // - remove its content
          // - set it as loading
          if (this.resultContainer._p_down('.results' + source.id)._p_down('ul')) {
            this.resultContainer._p_down('.results' + source.id)._p_down('ul').remove();
          }
          if (!this.options.unifiedLoader) {
            this.resultContainer._p_down('.results' + source.id)._p_down('.sourceContent')._p_addClassName('loading');
          }
          else {
            (this.options.loaderNode || this.fld)._p_addClassName("loading");
            this.resultContainer._p_down('.results' + source.id)._p_addClassName('hidden loading');
          }
        }
        else {
          // The sub-container for this source has not been created yet
          // Really create the subcontainer for this source and inject it in the global container
          var sourceContainer = PElement('div', {'class' : 'results results' + source.id}),
              sourceHeader = PElement('div', {'class':'sourceName'});

          if (this.options.unifiedLoader) {
            sourceContainer._p_addClassName('hidden loading');
          }

          if (typeof source.icon != 'undefined') {
            // If there is an icon for this source group, set it as background image
            var iconImage = new Image();
            iconImage.onload = function(){
              this.sourceHeader.setStyle({
                backgroundImage: "url(" + this.iconImage.src + ")"
              });
              this.sourceHeader.setStyle({
                textIndent:(this.iconImage.width + 6) + 'px'
              });
            }.bind({
              sourceHeader:sourceHeader,
              iconImage:iconImage
            });
            iconImage.src = source.icon;
          }
          sourceHeader._p_insert(source.name)
          sourceContainer._p_insert( sourceHeader );
          var classes = "sourceContent " + (this.options.unifiedLoader ? "" : "loading");
          sourceContainer._p_insert( PElement('div', {'class':classes}));

          if (typeof source.before !== 'undefined') {
            this.resultContainer._p_insert(source.before);
          }
          this.resultContainer._p_insert(sourceContainer);
          if (typeof source.after !== 'undefined') {
            this.resultContainer._p_insert(source.after);
          }
        }
      }
    } else {
      // In mono-source mode, reset the list if present
      if (this.resultContainer._p_down("ul")) {
        this.resultContainer._p_down("ul").remove();
      }
    }

    var ev = this.container._p_fire("ms:suggest:containerPrepared", {
      'container' : this.container,
      'suggest' : this
    });

    this.container.__targetField = this.fld;
    if (this.options.enableHideButton && !this.container._p_down('.hide-button')) {
      var hideButton = PElement('span', {'class' : 'hide-button'})._p_update("hide suggestions");
      hideButton._p_observe('click', this.clearSuggestions.bind(this));
      this.container._p_insert_top(PElement('div', {'class' : 'hide-button-wrapper'})._p_update(hideButton));

      hideButton = PElement('span', {'class' : 'hide-button'})._p_update("hide suggestions");
      hideButton._p_observe('click', this.clearSuggestions.bind(this));
      this.container._p_insert(PElement('div', {'class' : 'hide-button-wrapper'})._p_update(hideButton));
    }
    return this.container;
  },

  /**
   * Create the HTML list of suggestions.
   *
   * @param {Object} arr
   * @param {Object} source the source for data for which to create this list of results.
   */
  createList: function(arr, source)
  {
    this.isActive = true;
    var pointer = this;

    this.killTimeout();
    this.clearHighlight();

    // create holding div
    //
    if (this.sources.length > 1) {
      var div = this.resultContainer._p_down(".results" + source.id);
      if (arr.length > 0 || this.options.shownoresults) {
        div._p_down('.sourceContent')._p_removeClassName('loading');
        this.resultContainer._p_down(".results" + source.id)._p_removeClassName("hidden loading");
      }

      // If we are in mode "unified loader" (showing one loading indicator for all requests and not one per request)
      // and there aren't any source still loading, we remove the unified loading status.
      if (this.options.unifiedLoader && !this.resultContainer._p_down("loading")) {
        (this.options.loaderNode || this.fld)._p_removeClassName("loading");
      }
    }
    else {
      var div = this.resultContainer;
    }

    // if no results, and shownoresults is false, go no further
    if (arr.length == 0 && !this.options.shownoresults) {
      return false;
    }

    // Ensure any previous list of results for this source gets removed
    if (div._p_down('ul')) {
      div._p_down('ul').remove();
    }

    // create and populate list
    var list = this.createListElement(arr, pointer);
    div.appendChild(list);
    //Event.fire(document, "xwiki:dom:updated", {elements : [list]})

    this.suggest = div;

    // remove list after an interval
    var pointer = this;
    if (this.options.timeout > 0) {
      this.toID = setTimeout(function () { pointer.clearSuggestions() }, this.options.timeout);
    }
    this.highlightFirst();
  },

  createListElement : function(arr, pointer) {
    var list = new XList([], {
       icon: this.options.icon,
       classes: 'suggestList',
       eventListeners: {
          'click' : function () { pointer.setHighlightedValue(); return false; },
          'mouseover' : function () { pointer.setHighlight( this.getElement() ); }
       }
    });

    // loop throught arr of suggestions
    // creating an XlistItem for each suggestion
    //
    // at the same time check if any of the suggestions
    // is the exact case-insensitive match of the input
    //
    var exactMatch = false;
    for (var i = 0,len = arr.length; i < len; i++)
    {
       if (arr[i].value.toLowerCase() == _p_strip(this.fld.value).toLowerCase()
           || arr[i].id.toLowerCase() == _p_strip(this.fld.value).toLowerCase()) {
           exactMatch = arr[i];
       }
       if (!this.options.filterFunc || this.options.filterFunc(arr[i])) {
           list.addItem(this.generateListItem(arr[i]));
       }
    }
    // no results
    if (arr.length == 0)
    {
      list.addItem( new XListItem(this.options.noresults, {
                          'classes' : 'noSuggestion',
                          noHighlight :true }) );
    }
    if (this.fld._p_hasClassName('accept-value') && !exactMatch) {
       var customItemId = this.fld.value.replace(/[^a-z0-9_]+/gi, "_");
       var customItemCategoryInfo = this.fld._p_next('input[name="_category"]');
       var customItemCategories = customItemCategoryInfo && customItemCategoryInfo.value.split(",") || [];
       var customItemCategoriesElt = PElement('div', {'class' : 'hidden term-category'});
       var categoryFieldName = this.fld.name + "__" + customItemId + "__category";
       customItemCategories.forEach(function (c) {
         if (c) {
           customItemCategoriesElt._p_insert(PElement('input', {'type' : 'hidden', name: categoryFieldName, value: c}));
         }
       });
       list.addItem(this.generateListItem({
         id: this.fld.value,
         value: this.fld.value,
         category: customItemCategoriesElt,
         custom: true,
         info: PElement('div', {'class' : 'hint'})._p_update('(your text, not a standard term)')
       }, 'custom-value', true));
    }
    return list.getElement();
  },

  generateListItem : function(data, cssClass, disableTooltip) {
    var displayNode = PElement("div", {'class': 'tooltip-'+this.options.tooltip});
    // If the search result contains an icon information, we insert this icon in the result entry.
    if (data.icon) {
      displayNode._p_insert(PElement("img", {'src' : data.icon, 'class' : 'icon' }));
    }
    if (this.options.displayId) {
        displayNode._p_insert(PElement('span', {'class':'suggestId'})._p_update(_p_escapeHTML(data.id)));
    }
    displayNode._p_insert(PElement('span', {'class':'suggestValue'})._p_update(_p_escapeHTML(data.value)));

    if (this.options.tooltip && !disableTooltip) {
      var infoTool = PElement('span', {'class' : 'fa fa-info-circle xHelpButton ' + this.options.tooltip, 'title' : data.id, 'data-source' : this.fld.id});
      infoTool._p_observe('click', function(event) {event.stop()});
      displayNode._p_insert(' ')._p_insert(infoTool);
    }
    var displayInfo = PElement('div', {'class':'suggestInfo'})._p_update(data.info);
    displayNode._p_insert(displayInfo);
    if(data.altName){
        displayInfo._p_insert({'top' : PElement('span', {'class':'matching-alternative-name'})._p_update(_p_escapeHTML(data.altName))});
    }

    var valueNode = PElement('div')
            ._p_insert(PElement('span', {'class':'suggestId'})._p_update(_p_escapeHTML(data.id)))
            ._p_insert(PElement('span', {'class':'suggestValue'})._p_update(_p_escapeHTML(data.value)))
            ._p_insert(PElement('div', {'class':'suggestCategory'})._p_update(data.category));
    data.custom && valueNode._p_insert(PElement('div', {'class':'isCustom'})._p_update(data.custom));
    valueNode["_p_itemData"] = data;

    var item = new XListItem( displayNode , {
        containerClasses: 'suggestItem ' + (cssClass || ''),
        value: valueNode,
        noHighlight: true // we do the highlighting ourselves
    });

    this.fld._p_fire("ms:suggest:suggestionCreated", {element: item.getElement(), suggest: this});

    return item;
  },

  /**
   * Emphesize the elements in passed value that matches one of the words typed as input by the user.
   *
   * @param String input the (typed) input
   * @param String value the value to emphasize
   */
  emphasizeMatches:function(input, value)
  {
    // If the source declares that results are matching, we highlight them in the value
    var output = value,
        // Separate words (called fragments hereafter) in user input
        fragments = input.split(' ').uniq().compact(),
        offset = 0,
        matches = {};

    for (var j=0,flen=fragments.length;j<flen;j++) {
      // We iterate over each fragments, and try to find one or several matches in this suggestion
      // item display value.
      var index = output.toLowerCase().indexOf(fragments[j].toLowerCase());
      while (index >= 0) {
        // As long as we have matches, we store their index and replace them in the output string with the space char
        // so that they don't get matched for ever.
        // Note that the space char is the only one safe to use, as it cannot be part of a fragment.
        var match = output.substring(index, index + fragments[j].length),
            placeholder = "";
        fragments[j].length.times(function(){
          placeholder += " ";
        });
        matches[index] = match;
        output = output.substring(0, index) + placeholder + output.substring(index + fragments[j].length);
        index = output.toLowerCase().indexOf(fragments[j].toLowerCase());
      }
    }
    // Now that we have found all matches for all possible fragments, we iterate over them
    // to construct the final "output String" that will be injected as a suggestion item,
    // with all matches emphasized
    Object.keys(matches).sortBy(function(s){return parseInt(s)}).forEach(function(key){
      var before = output.substring(0, parseInt(key) + offset);
      var after = output.substring(parseInt(key) + matches[key].length + offset);
      // Emphasize the match in the output string that will be displayed
      output = before + "<em>" + matches[key] + "</em>" + after;
      // Increase the offset by 9, which correspond to the number of chars in the opening and closing "em" tags
      // we have introduced for this match in the output String
      offset += 9;
    });

    return output;
  },

  /**
   * Change highlight
   *
   * @param {Object} key
   */
  changeHighlight: function(key)
  {
    var list = this.resultContainer;
    if (!list)
      return false;

    var n, elem;

    if (this.iHighlighted) {
      // If there is already a highlighted element, we look for the next or previous highlightable item in the list
      // of results, according to which key has been pressed.
      if (key == Event.KEY_DOWN) {
        elem = this.iHighlighted._p_next();
        if (!elem && this.iHighlighted._p_up('div.results')) {
          // if the next item could not be found and multi-source mode, find the next not empty source
          var source = this.iHighlighted._p_up('div.results')._p_next();
          while (source && !elem) {
            elem = source._p_down('li');
            source = source._p_next();
          }
        }
        if(!elem) {
          elem = list._p_down('li');
        }
      }
      else if (key == Event.KEY_UP) {
        elem = this.iHighlighted._p_previous();
        if (!elem && this.iHighlighted._p_up('div.results')) {
          // if the previous item could not be found and multi-source mode, find the previous not empty source
          var source = this.iHighlighted._p_up('div.results')._p_previous();
          while(source && !elem) {
            elem = source._p_down('li:last-child');
            source = source._p_previous();
          }
        }
        if (!elem) {
          elem =  list.querySelectorAll('ul')[list.querySelectorAll('ul').length - 1]._p_down('li:last-child');
        }
      }
    }
    else {
      // No item is highlighted yet, so we just look for the first or last highlightable item,
      // according to which key, up or down, has been pressed.
      if (key == Event.KEY_DOWN) {
        if (list._p_down('div.results')) {
          elem = list._p_down('div.results')._p_down('li')
        }
        else {
          elem = list._p_down('li');
        }
      }
      else if (key == Event.KEY_UP)
        if (list.querySelectorAll('li').length > 0) {
          elem = list.querySelectorAll('li')[list.querySelectorAll('li').length - 1];
        }
    }

    if (elem) {
      this.setHighlight(elem);
    }
  },

  /**
   * Set highlight
   *
   * @param {Object} n
   */
  setHighlight: function(highlightedItem)
  {
    if (this.iHighlighted)
      this.clearHighlight();

    highlightedItem._p_addClassName("xhighlight");

    this.iHighlighted = highlightedItem;

    this.killTimeout();
  },

  /**
   * Clear highlight
   */
  clearHighlight: function()
  {
    if (this.iHighlighted) {
      this.iHighlighted._p_removeClassName("xhighlight");
      delete this.iHighlighted;
    }
  },

  highlightFirst: function()
  {
    if (this.suggest && this.suggest._p_down('ul')) {
      var first = this.suggest._p_down('ul')._p_down('li');
      if (first) {
        this.setHighlight(first);
      }
    }
  },

  /**
   * return true if a suggestion is highlighted, false otherwise
   */
  hasActiveSelection: function(){
    return this.iHighlighted;
  },

  setHighlightedValue: function ()
  {
    if (this.iHighlighted && !this.iHighlighted._p_hasClassName('noSuggestion'))
    {
      var selection, newFieldValue
      if(this.sInput == "" && this.fld.value == "")
        selection = newFieldValue = this.iHighlighted._p_down(".suggestValue").innerHTML;
      else {
        if(this.seps) {
           var lastIndx = -1;
           for(var i = 0; i < this.seps.length; i++)
             if(this.fld.value.lastIndexOf(this.seps.charAt(i)) > lastIndx)
               lastIndx = this.fld.value.lastIndexOf(this.seps.charAt(i));
            if(lastIndx == -1)
              selection = newFieldValue = this.iHighlighted._p_down(".suggestValue").innerHTML;
            else
            {
               newFieldValue = this.fld.value.substring(0, lastIndx+1) + this.iHighlighted._p_down(".suggestValue").innerHTML;
               selection = newFieldValue.substring(lastIndx+1);
           }
        }
        else
          selection = newFieldValue = this.iHighlighted._p_down(".suggestValue").innerHTML;
      }

      var inputData = this.iHighlighted._p_down('.value div')["_p_itemData"];
      var data = {
        suggest  : this,
        id       : inputData.id || this.iHighlighted._p_down(".suggestId").innerHTML,
        value    : inputData.value || this.iHighlighted._p_down(".suggestValue").innerHTML,
        info     : inputData.info || this.iHighlighted._p_down(".suggestInfo").innerHTML,
        icon     : inputData.icon || (this.iHighlighted._p_down('img.icon') ? this.iHighlighted._p_down('img.icon').src : ''),
        category : this.iHighlighted._p_down(".suggestCategory").innerHTML,
        custom   : inputData.custom || false
      };
      this.acceptEntry(data, selection, newFieldValue);
    }
  },

  acceptEntry : function(data, selection, newFieldValue, silent) {
      var event = this.fld._p_fire("ms:suggest:selected", data);

      if (!event.stopped) {
        if (!silent) {
          this.sInput = selection;
          this.fld.value = newFieldValue || this.fld.defaultValue || '';
          this.fld.focus();
          this.clearSuggestions();
        }
        // pass selected object to callback function, if exists
        if (typeof(this.options.callback) == "function") {
          this.options.callback(data);
        }

        //there is a hidden input
        if(this.fld.id.indexOf("_suggest") > 0) {
          var hidden_id = this.fld.id.substring(0, this.fld.id.indexOf("_suggest"));
          var hidden_inp = $(hidden_id);
          if (hidden_inp) {
            hidden_inp.value =  info;
          }
        }
      }
  },

  /**
   * Kill timeout
   */
  killTimeout: function()
  {
    clearTimeout(this.toID);
  },

  /**
   * Reset timeout
   */
  resetTimeout: function()
  {
    clearTimeout(this.toID);
    var pointer = this;
    this.toID = setTimeout(function () { pointer.clearSuggestions() }, 1000000);
  },

  /**
   * Clear suggestions
   */
  clearSuggestions: function() {
    this.killTimeout();
    this.isActive = false;
    var ele = $(this.container);
    var pointer = this;
    if (ele && ele.parentNode) {

      // if clearSuggestions() was called not from inside widget
      // when user clicked outside suggestion container - no suggestion was explicitly selected.
      // Calling onInputChanged() to check the suggest widget suggestions for exact matches
      // and if find one - the code subsequently fires "ms:suggest:selected" event
      if (!this.suggestionSelected) {
        this.onInputChanged();
      }

      if (this.options.fadeOnClear) {
        var fade = new Effect.Fade(ele, {duration: "0.25", afterFinish : function() {
          if($(pointer.container)) {
            $(pointer.container).remove();
          }
        }});
      }
      else {
        if (ele && ele.parentNode) { ele.remove(); }
      }
      PFireEvent("ms:suggest:clearSuggestions", { 'suggest' : this});
      this.suggestionSelected = false;
    }
  }

 });


//===============================================================================================================

var PSuggestPicker = Class.create({

  options : {
    'showKey' : true,
    'showTooltip' : false,
    'showDeleteTool' : true,
    'enableSort' : true,
    'showClearTool' : true,
    'inputType': 'hidden',
    'listInsertionElt' : null,
    'listInsertionPosition' : 'after',   // FIXME: the only one supported
    'predefinedEntries' : null,
    'acceptFreeText' : false
  },
  initialize: function(element, suggest, options, serializedDataInput) {
    this.options = _p_extend(cloneObject(this.options), options || { });
    this.serializedDataInput = serializedDataInput;
    this.input = element;
    this.suggest = suggest;
    this.inputName = this.input.name;
    if (!this.options.acceptFreeText) {
      this.input.name = this.input.name + "__suggested";
    } else {
      this.input._p_addClassName("accept-value");
    }
    this.suggest.options.callback = this.acceptSuggestion.bind(this);
    this.list = PElement('ul', {'class' : 'accepted-suggestions'});
    var listInsertionElement;
    if (this.options.listInsertionElt) {
      if (typeof(this.options.listInsertionElt) == "string") {
        listInsertionElement = this.input._p_up()._p_down(this.options.listInsertionElt);
      } else {
        listInsertionElement = this.options.listInsertionElt;
      }
    }
    if (!listInsertionElement) {
      listInsertionElement = this.input;
    }

    listInsertionElement._p_insert_after(this.list);

    this.predefinedEntries = this.options.predefinedEntries ? $(this.options.predefinedEntries) : null;
    if (this.options.showClearTool) {
      this.clearTool = PElement('span', {'class' : 'clear-tool delete-tool invisible', 'title' : "Clear the list of selected suggestions"}).
                       _p_update('Delete all &#x2716;');
      this.clearTool._p_observe('click', this.clearAcceptedList.bind(this));
      this.list._p_insert_after(this.clearTool);
    }
    if (typeof(this.options.onItemAdded) == "function") {
      this.onItemAdded = this.options.onItemAdded;
    }
  },

  acceptAddItem : function (key, negative) {
    var searchFor = 'input[id="' + this.getInputId(key, negative).replace(/[^a-zA-Z0-9_-]/g, '\\$&') + '"]';
    var input = this.predefinedEntries ? this.predefinedEntries._p_down(searchFor) : this.list ? this.list._p_down(searchFor) : $(this.getInputId(key, negative));
    if (input) {
      input.checked = true;
      input._p_fire('suggest:change');
      //this.ensureVisible(input._p_up(), true);
      this.synchronizeSelection(input);
      return false;
    }
    return true;
  },

  ensureVisible : function (element, force) {
    if (this.silent || (!force && this.options.silent) || element._p_up('.hidden')) {return;}
    var section = element._p_up('.collapsed:not(.force-collapse)');
    while (section) {
      section._p_removeClassName('collapsed');
      if (section._p_down('.expand-tool')) {
        section._p_down('.expand-tool').update('▼');
      }
      section = section._p_up('.collapsed:not(.force-collapse)');
    }

    //FIXME
    /*
    if (element.viewportOffset().top > this.input.viewportOffset().top) {
      if (element.viewportOffset().top > document.viewport.getHeight()) {
        if (element.viewportOffset().top - this.input.viewportOffset().top < document.viewport.getHeight()) {
          this.input.scrollTo();
        } else {
          element.scrollTo();
        }
      }
    } else {
      if (element._p_cumulativeOffset().top < document.viewport.getScrollOffsets().top) {
        element.scrollTo();
      }
    }
    */
  },

  acceptSuggestion : function(obj) {
    this.input.value = this.input.defaultValue || "";
    if (this.acceptAddItem(obj.id || obj.value, obj.negative)) {
      this.addItem(obj.id || obj.value, obj.value, obj.info, obj.category, obj.custom);
    }
    return false;
  },

  addItem : function(key, value, info, category, custom) {
    if (!key) {
      return;
    }
    var id = this.getInputId(key);
    var listItem = PElement("li");
    var displayedValue = PElement("label", {"class": "accepted-suggestion", "for" : id});
    // insert input
    var inputOptions = {"type" : this.options.inputType, "name" : this.inputName, "id" : id, "value" : key};
    if (this.options.inputType == 'checkbox') {
      inputOptions.checked = true;
    }
    displayedValue._p_insert(PElement("input", inputOptions));
    // if the key should be displayed, insert it
    if (this.options.showKey && !custom) {
      displayedValue._p_insert(PElement("span", {"class": "key"})._p_update("[" + _p_escapeHTML(key) + "]"));
      displayedValue._p_insert(PElement("span", {"class": "sep"})._p_update(" "));
    }
    // insert the displayed value
    displayedValue._p_insert(PElement("span", {"class": "value"})._p_update(_p_escapeHTML(value)));
    listItem._p_insert(displayedValue);
    if(this.suggest.options.tooltip) {
      var infoTool = this.suggest.options.markFreeText && custom ? PElement('span', {'class' : 'fa fa-fw fa-exclamation-triangle', 'title' : this.suggest.options.freeTextTooltipHint || ''}) : PElement('span', {'class' : 'fa fa-info-circle xHelpButton ' + this.suggest.options.tooltip, title : key, 'data-source' : this.input && this.input.id || ''});
      listItem._p_insert(infoTool);
    }
    if (category && category != '') {
      listItem._p_insert(category);
    }
    // delete tool
    if (this.options.showDeleteTool) {
      var deleteTool = PElement("span", {'class': "delete-tool", "title" : "Delete this term"})._p_update('&#x2716;');
      deleteTool._p_observe('click', this.removeItem.bind(this));
      listItem.appendChild(deleteTool);
    }
    // tooltip, if information exists and the options state there should be a tooltip
    //FIXME
    /*
    if (this.options.showTooltip && info) {
      listItem.appendChild(PElement("div", {'class' : "tooltip"}).update(info));
      listItem.select('.expand-tool').invoke('observe', 'click', function(event){event.stop();});
    }*/
    this.list._p_insert(listItem);
    var newItem = this.list ? this.list._p_down('input[id="' + id.replace(/[^a-zA-Z0-9_-]/g, '\\$&') + '"]') : $(id);
    //this.ensureVisible(newItem);
    this.synchronizeSelection(newItem);
    newItem._p_observe('change', this.synchronizeSelection.bind(this, newItem));
    this.updateListTools();
    //Event.fire(document, 'xwiki:dom:updated', {'elements' : [listItem]});
    this.onItemAdded(newItem)
    return newItem;
  },

  onItemAdded : function (element) {
  },

  removeItem : function(e) {
    // The parameter can be an event or a target element, depending on how it is called
    var item = e.srcElement ? getEventMatchingParentElement(e, 'li') : e;
    if (!item) {
      console.log("Event target not found");
      return;
    }
    this.synchronizeSelection({
      value   : (item._p_down('input[type=checkbox]') || item._p_down('input')).value,
      checked : false
    });
    item.remove();
    this.notifySelectionChange(item);
    this.input.value = this.input.defaultValue || "";
    this.updateListTools();
  },

  clearAcceptedList : function () {
    var _this = this;
    // note: removal of one item may trigger (via an event and event handler in client code) removal or addition of
    // other items, so need to use a while() loop to remove whatever elements happen to be there after each removeItem()
    while(this.list.querySelectorAll('li').length > 0) {
      _this.removeItem(this.list.querySelectorAll('li')[0]);
    };
  },

  updateListTools : function () {
    if (this.clearTool) {
      if (this.list.querySelectorAll('li .accepted-suggestion').length > 0) {
        this.clearTool._p_removeClassName('invisible');
      } else {
        this.clearTool._p_addClassName('invisible');
      }
    }
    if (this.options.enableSort && this.list.querySelectorAll('li .accepted-suggestion').length > 0 && typeof(Sortable) != "undefined") {
      Sortable.create(this.list);
    }
    if (this.serializedDataInput) {
      var value = '';
      this.list.querySelectorAll('li .accepted-suggestion input[type=checkbox]').forEach(function (entry) {
        value += entry.value + '|';
      });
      this.serializedDataInput.value = value;
    }
  },

  getInputId : function(key, negative) {
    return (negative ? this.inputName.replace(/(_\d+)_/, "$1_negative_") : this.inputName) + "_" + key;
  },

  synchronizeSelection : function (input) {
    var element = (typeof (input._p_up) == 'function') && input._p_up('li');
    if (element) {
      this.notifySelectionChange(element);
    }
  },

  notifySelectionChange : function(elt) {
    if (!elt.__categoryArray) {
      elt.__categoryArray = [];
      elt.querySelectorAll('.term-category input[type=hidden]').forEach(function(c) {
        elt.__categoryArray.push(c.value);
      });
    }
    PFireEvent("custom:selection:changed", {
       'categories' : elt.__categoryArray,
       'trigger'    : this.input,
       'fieldName'  : this.inputName,
       'customElement' : elt
    });
  }
});

export { PSuggestWidget, PSuggestPicker };