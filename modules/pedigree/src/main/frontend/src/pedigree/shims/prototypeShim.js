/**
 *  Based on Prototype JavaScript framework, version 1.7.3
 *  (c) 2005-2010 Sam Stephenson
 *
 *  Prototype is freely distributable under the terms of an MIT-style license.
 *  For details, see the Prototype web site: http://www.prototypejs.org/
 *
 *--------------------------------------------------------------------------*/

/****************************************************************************
 *
 * The shim is fully compatible with React and does not overwrite base
 * classes such as Element or Array.
 *
 * - implements a fully compatible Class and Class.create
 * - implements a fully compatible $ and $$
 * - implements a mostly compatible Ajax implementation
 * - implements many Element methods such as insert/up/getStyle/observe/etc.,
 *   all with a "_p_" prefix (_p_insert(), etc.). In a few cases methods
 *   are not fully compatible, e.g. basic element.insert() is implemented
 *   fully, but .insert({'top': ...}) is implemented as insert_top()
 * - to generate compatible "Elements" use a call to "PElement(...)"
 *   instead of "new Element(...)", since default Element contructor does
 *   not support prototype-style parameters and was not overwritten
 * - all event handlers added through the shim can be removed by a single call
 * - workarounds for older browsers are not included
 *
 ****************************************************************************/

"use strict";

//==========================================================================================
// cache all event listeners established through this shim, to be able to remove all at once
class EventCache {
  constructor() {
    this._p_allEventListeners = [];
  }

  addListener(element, eventName, callback) {
    if (this._getEventlistenerIndex(element, eventName, callback) >= 0) {
      // event listener already cached and presumably active, and there can't be two installed
      return;
    }
    this._p_allEventListeners.push({"element": element, "eventName": eventName, "callback": callback})
  }

  _getEventlistenerIndex(element, eventName, callback) {
    for (var i = 0; i < this._p_allEventListeners.length; i++) {
      var eventInfo = this._p_allEventListeners[i];
      if (eventInfo.element == element && eventInfo.eventName == eventName && eventInfo.callback == callback) {
        return i;
      }
    };
    return -1;
  }

  removeListener(element, eventName, callback) {
    var index = this._getEventlistenerIndex(element, eventName, callback);
    if (index >= 0) {
      this._p_allEventListeners.splice(index, 1);
    }
  }

  getAll() {
    return this._p_allEventListeners;
  }

  getAllForElement(element) {
    result = [];
    for (var i = 0; i < this._p_allEventListeners.length; i++) {
      var eventInfo = this._p_allEventListeners[i];
      if (eventInfo.element == element) {
        result.push(eventInfo);
      }
    };
    return result;
  }
}

var eventCache = new EventCache();
//==========================================================================================

var _toString = Object.prototype.toString,
      _hasOwnProperty = Object.prototype.hasOwnProperty,
      NULL_TYPE = 'Null',
      UNDEFINED_TYPE = 'Undefined',
      BOOLEAN_TYPE = 'Boolean',
      NUMBER_TYPE = 'Number',
      STRING_TYPE = 'String',
      OBJECT_TYPE = 'Object',
      FUNCTION_CLASS = '[object Function]',
      BOOLEAN_CLASS = '[object Boolean]',
      NUMBER_CLASS = '[object Number]',
      STRING_CLASS = '[object String]',
      ARRAY_CLASS = '[object Array]',
      DATE_CLASS = '[object Date]',
      NATIVE_JSON_STRINGIFY_SUPPORT = window.JSON &&
        typeof JSON.stringify === 'function' &&
        JSON.stringify(0) === '0';

var ATTRIBUTE_TRANSLATIONS = {};

function $A(iterable) {
  if (!iterable) return [];
  if ('toArray' in Object(iterable)) return iterable.toArray();
  var length = iterable.length || 0, results = new Array(length);
  while (length--) results[length] = iterable[length];
  return results;
}

function isFunction(object) {
  return _toString.call(object) === FUNCTION_CLASS;
}

function isArray(object) {
  return _toString.call(object) === ARRAY_CLASS;
}

function isElement(object) {
  return !!(object && object.nodeType == 1);
}

function isString(object) {
  return _toString.call(object) === STRING_CLASS;
}

function isNumber(object) {
  return _toString.call(object) === NUMBER_CLASS;
}

function isUndefined(object) {
  return typeof object === "undefined";
}

function isBody(element) {
  return element.nodeName.toUpperCase() === 'BODY';
}

function emptyFunction() { }

function _p_update(array, args) {
  var arrayLength = array.length, length = args.length;
  while (length--) array[arrayLength + length] = args[length];
  return array;
}

function _p_extend(destination, source) {
  for (var property in source)
    destination[property] = source[property];
  return destination;
}

var _p_cloneObject = function(obj) {
  var target = {};
  for (var i in obj) {
    if (obj.hasOwnProperty(i)) {
      target[i] = obj[i];
    }
  }
  return target;
};

function _p_argumentNames(o) {
  var names = o.toString().match(/^[\s\(]*function[^(]*\(([^)]*)\)/)[1]
    .replace(/\/\/.*?[\r\n]|\/\*(?:.|[\r\n])*?\*\//g, '')
    .replace(/\s+/g, '').split(',');
  return names.length == 1 && !names[0] ? [] : names;
}

function _p_wrap(wrapper) {
  var __method = this;
  return function () {
    var a = _p_update([__method.bind(this)], arguments);
    return wrapper.apply(this, a);
  }
}

Function.prototype._p_wrap = _p_wrap;

//================================================================

var Class = (function () {

  function subclass() { };
  function create() {
    var parent = null, properties = $A(arguments);
    if (isFunction(properties[0]))
      parent = properties.shift();

    function klass() {
      this.initialize.apply(this, arguments);
    }

    _p_extend(klass, Class.Methods);
    klass.superclass = parent;
    klass.subclasses = [];

    if (parent) {
      subclass.prototype = parent.prototype;
      klass.prototype = new subclass;
      parent.subclasses.push(klass);
    }

    for (var i = 0, length = properties.length; i < length; i++)
      klass._p_addMethods(properties[i]);

    if (!klass.prototype.initialize)
      klass.prototype.initialize = function() {};

    klass.prototype.constructor = klass;
    return klass;
  }

  function _p_addMethods(source) {
    var ancestor = this.superclass && this.superclass.prototype,
      properties = Object.keys(source);

    for (var i = 0, length = properties.length; i < length; i++) {
      var property = properties[i], value = source[property];
      if (ancestor && isFunction(value) &&
        _p_argumentNames(value)[0] == "$super") {
        var method = value;
        value = (function (m) {
          return function () { return ancestor[m].apply(this, arguments); };
        })(property)._p_wrap(method);

        value.valueOf = (function (method) {
          return function () { return method.valueOf.call(method); };
        })(method);

        value.toString = (function (method) {
          return function () { return method.toString.call(method); };
        })(method);
      }
      this.prototype[property] = value;
    }

    return this;
  }

  return {
    create: create,
    Methods: {
      _p_addMethods: _p_addMethods
    }
  };
})();

//================================================================

function PElement(tagName, attributes) {
    attributes = attributes || {};
    tagName = tagName.toLowerCase();

    var node = document.createElement(tagName);

    writeAttribute(node, attributes);

    return node;
}


ATTRIBUTE_TRANSLATIONS.write = {
  names: {
    className: 'class',
    htmlFor: 'for',
    cellpadding: 'cellPadding',
    cellspacing: 'cellSpacing'
  },

  values: {
    checked: function (element, value) {
      value = !!value;
      element.checked = value;
      return value ? 'checked' : null;
    },

    style: function (element, value) {
      element.style.cssText = value ? value : '';
    }
  }
};

function writeAttribute(element, name, value) {
  //element = $(element);
  var attributes = {}, table = ATTRIBUTE_TRANSLATIONS.write;

  if (typeof name === 'object') {
    attributes = name;
  } else {
    attributes[name] = isUndefined(value) ? true : value;
  }

  for (var attr in attributes) {
    name = table.names[attr] || attr;
    value = attributes[attr];
    if (table.values[attr]) {
      value = table.values[attr](element, value);
      if (isUndefined(value)) continue;
    }
    if (value === false || value === null)
      element.removeAttribute(name);
    else if (value === true)
      element.setAttribute(name, name);
    else element.setAttribute(name, value);
  }

  return element;
}

window.Element.prototype._p_writeAttribute = function(name, value) {
  writeAttribute(this, name, value);
}

function camelize(str) {
  return str.replace(/-+(.)?/g, function (match, chr) {
    return chr ? chr.toUpperCase() : '';
  });
}

function normalizeStyleName(style) {
  if (style === 'float' || style === 'styleFloat')
    return 'cssFloat';
  return camelize(style);
}

window.Element.prototype._p_getStyle = function(style) {
  style = normalizeStyleName(style);

  var value = this.style[style];
  if (!value || value === 'auto') {
    var css = document.defaultView.getComputedStyle(this, null);
    value = css ? css[style] : null;
  }

  if (style === 'opacity') return value ? parseFloat(value) : 1.0;
  return value === 'auto' ? null : value;
}

window.Element.prototype._p_setOpacity = function(value) {
  if (value == 1 || value === '') value = '';
  else if (value < 0.00001) value = 0;
  this.style.opacity = value;
  return this;
}

window.Element.prototype._p_setStyle = function(styles) {
  var elementStyle = this.style, match;

  if (isString(styles)) {
    elementStyle.cssText += ';' + styles;
    if (styles.includes('opacity')) {
      var opacity = styles.match(/opacity:\s*(\d?\.?\d*)/)[1];
      this._p_setOpacity(opacity);
    }
    return this;
  }

  for (var property in styles) {
    if (property === 'opacity') {
      this._p_setOpacity(styles[property]);
    } else {
      var value = styles[property];
      if (property === 'float' || property === 'cssFloat') {
        property = isUndefined(elementStyle.styleFloat) ?
          'cssFloat' : 'styleFloat';
      }
      elementStyle[property] = value;
    }
  }

  return this;
}

window.Element.prototype._p_visible = function () {
    return this._p_getStyle('display') !== 'none';
}

window.Element.prototype._p_toggle = function (bool) {
    if (typeof bool !== 'boolean')
      bool = !this.visible();
    Element[bool ? 'show' : 'hide'](this);
}

window.Element.prototype._p_hide = function () {
    this.style.display = 'none';
}

window.Element.prototype._p_show = function () {
  this.style.display = '';
}

function purgeElement(element) {
  //Element.stopObserving(element);
}

function toHTML(object) {
  return object == null ? '' : String(object);
}

window.Element.prototype._p_update = function(content) {

  var descendants = this.getElementsByTagName('*'),
    i = descendants.length;
  while (i--) purgeElement(descendants[i]);

  if (isElement(content))
    return this._p_update()._p_insert(content);

  content = toHTML(content);

  this.innerHTML = content;

  return this;
}

window.Element.prototype._p_insert_top = function (content) {
  if (isElement(content)) {
    this.insertBefore(content, this.childNodes[0]);
    return this;
  }
  content = toHTML(content);
  // https://developer.mozilla.org/en-US/docs/Web/API/Element/insertAdjacentHTML
  this.insertAdjacentHTML("afterbegin", content);
  return this;
}
window.Element.prototype._p_insert = function(content) {
  if (isElement(content)) {
    this.appendChild(content);
    return this;
  }
  content = toHTML(content);
  // https://developer.mozilla.org/en-US/docs/Web/API/Element/insertAdjacentHTML
  this.insertAdjacentHTML("beforeend", content);
  return this;
}
window.Element.prototype._p_insert_after = function (content) {
  // insert after this element in parent
  if (isElement(content)) {
    this.parentNode.insertBefore(content, this.nextSibling);
    return this;
  }
  content = toHTML(content);
  // https://developer.mozilla.org/en-US/docs/Web/API/Element/insertAdjacentHTML
  this.insertAdjacentHTML("afterend", content);
  return this;
}

window.Element.prototype._p_wrap = function(wrapper, attributes) {

  if (isString(wrapper)) {
    wrapper = PElement(wrapper, attributes);
  }

  if (this.parentNode)
    this.parentNode.replaceChild(wrapper, this);

  wrapper.appendChild(this);

  return wrapper;
}

//================================================================

function _p_invoke(method) {
  var args = $A(arguments).slice(1);
  return this.map(function (value) {
    return value[method].apply(value, args);
  });
}

// React-compatible way to modify Array prototype
Object.defineProperty(Array.prototype, '_p_invoke', {
  value: _p_invoke,
  enumerable: false
});

//================================================================

var POffset = Class.create({
  initialize: function (left, top) {
    this.left = Math.round(left);
    this.top = Math.round(top);

    this[0] = this.left;
    this[1] = this.top;
  },

  relativeTo: function (offset) {
    return new Offset(
      this.left - offset.left,
      this.top - offset.top
    );
  },

  toString: function () {
    return "[#{left}, #{top}]".interpolate(this);
  },

  toArray: function () {
    return [this.left, this.top];
  }
});

window.Element.prototype._p_cumulativeOffset = function() {
  var element = this;
  var valueT = 0, valueL = 0;
  if (element.parentNode) {
    do {
      valueT += element.offsetTop || 0;
      valueL += element.offsetLeft || 0;
      element = element.offsetParent;
    } while (element);
  }
  return new POffset(valueL, valueT);
}

window.Element.prototype._p_positionedOffset = function() {
  var element = this;

  //var layout = element.getLayout();

  var valueT = 0, valueL = 0;
  do {
    valueT += element.offsetTop  || 0;
    valueL += element.offsetLeft || 0;
    element = element.offsetParent;
    if (element) {
      if (isBody(element)) break;
      var p = element._p_getStyle('position');
      if (p !== 'static') break;
    }
  } while (element);

  //valueL -= layout.get('margin-left');
  //valueT -= layout.get('margin-top');

  return new POffset(valueL, valueT);
}

window.Element.prototype._p_getHeight = function() {
  return getElementDimensions(this).height;
}

window.Element.prototype._p_getWidth = function() {
  return getElementDimensions(this).width;
}

function getElementDimensions(element) {
  var display = element._p_getStyle('display');

  if (display && display !== 'none') {
    return { width: element.offsetWidth, height: element.offsetHeight };
  }

  var style = element.style;
  var originalStyles = {
    visibility: style.visibility,
    position: style.position,
    display: style.display
  };

  var newStyles = {
    visibility: 'hidden',
    display: 'block'
  };

  if (originalStyles.position !== 'fixed')
    newStyles.position = 'absolute';

  element._p_setStyle(newStyles);

  var dimensions = {
    width: element.offsetWidth,
    height: element.offsetHeight
  };

  element._p_setStyle(originalStyles);

  return dimensions;
}

function getDocumentDimensions() {
  return { width: getDocumentWidth(), height: getDocumentHeight() };
}

function getDocumentWidth() {
  return document.documentElement.clientWidth;
}

function getDocumentHeight() {
  return document.documentElement.clientHeight;
}

//================================================================

function fireEvent_DOM(element, eventName, memo, bubble) {
  var event = document.createEvent('Event');
  event.initEvent(eventName, bubble, true);

  event.eventName = eventName;
  event.memo = memo;

  element.dispatchEvent(event);
  return event;
}

function findEventElement(event) {
  var node = event.srcElement, type = event.type,
    currentTarget = event.currentTarget;

  if (currentTarget && currentTarget.tagName) {
    if (type === 'load' || type === 'error' ||
      (type === 'click' && currentTarget.tagName.toLowerCase() === 'input'
        && currentTarget.type === 'radio'))
      node = currentTarget;
  }

  return node.nodeType == Node.TEXT_NODE ? node.parentNode : node;
}

var PHandler = Class.create({
  initialize: function (element, eventName, callback) {
    this.element = element;
    this.eventName = eventName;
    this.callback = callback;
    this.handler = this.handleEvent.bind(this);
  },

  start: function () {
    _p_observe(this.element, this.eventName, this.handler);
    return this;
  },

  stop: function () {
    _p_stopObserving(this.element, this.eventName, this.handler);
    return this;
  },

  handleEvent: function (event) {
    var element = findEventElement(event);
    if (element) this.callback.call(this.element, event, element);
  }
});


function PFireEvent (eventName, memo, bubble) {
  if (isUndefined(bubble)) bubble = true;
  memo = memo || {};

  // or document.documentElement?
  var event = fireEvent_DOM(document, eventName, memo, bubble);
  return event;
}

function _p_observe(element, eventName, handler) {
  element.addEventListener(eventName, handler, false);

  // only cache global events that wont be removed together with elements that belong to
  if (element == document || element == window) {
    eventCache.addListener(element, eventName, handler);
  }
}

function _p_stopObserving(element, eventName, handler) {
  element.removeEventListener(eventName, handler);

  eventCache.removeListener(element, eventName, handler);
}

function PObserveEvent(eventName, callback, element = document) {
  return _p_observe(element, eventName, callback);
}

function PStopObserving(eventName, callback, element = document) {
  return _p_stopObserving(element, eventName, callback);
}

function PStopObservingAll(element) {
  var allElementObservations = eventCache.getAllForElement(element);
  allElementObservations.forEach(function (eventInfo) {
    _p_stopObserving(eventInfo.element, eventInfo.eventName, eventInfo.callback);
  });
}

function PRemoveAllListeners() {
  var events = eventCache.getAll().slice();
  events.forEach(function (eventInfo) {
    _p_stopObserving(eventInfo.element, eventInfo.eventName, eventInfo.callback);
  });
}

window.Element.prototype._p_fire = function (eventName, memo, bubble) {
  if (isUndefined(bubble)) bubble = true;
  memo = memo || {};

  var event = fireEvent_DOM(this, eventName, memo, bubble);
  return event;
}

window.Element.prototype._p_on = function(eventName, callback) {
  return new PHandler(this, eventName, callback).start();
}

window.Element.prototype._p_observe = function (eventName, callback) {
  return _p_observe(this, eventName, callback);
}

//================================================================

window.Element.prototype._p_hasClassName = function(className) {
  return this.classList.contains(className);
}

window.Element.prototype._p_addClassName = function(className) {
  // .add() does not work when className is a list of class names separated by a space
  if (!className || className.length == 0) return this;
  var classes = className.trim().split(" ");
  classes.forEach(function(cName) {
    this.classList.add(cName);
  }.bind(this));
  return this;
}

window.Element.prototype._p_removeClassName = function(className) {
  this.classList.remove(className);
  return this;
}

//================================================================

function _recursivelyFind(element, property, expression) {
  expression = expression || null;
  while (element = element[property]) {
    if (element.nodeType !== 1) continue;
    if (expression && !element.matches(expression))
      continue;
    return element;
  }
}

function firstDescendant(element) {
  element = element.firstChild;
  while (element && element.nodeType !== Node.ELEMENT_NODE)
    element = element.nextSibling;
  return element;
}

window.Element.prototype._p_up = function(expression) {
  if (arguments.length === 0)
    return this.parentNode;
  return _recursivelyFind(this, 'parentNode', expression);
}

window.Element.prototype._p_down = function(expression) {
  if (arguments.length === 0)
    return firstDescendant(this);
  expression = expression || null;
  return this.querySelector(expression);
}

window.Element.prototype._p_previous = function(expression) {
  return _recursivelyFind(this, 'previousSibling', expression);
}

window.Element.prototype._p_next = function(expression) {
  return _recursivelyFind(this, 'nextSibling', expression);
}

//================================================================

// This method returns the first DOM element with a given tag name, upwards from the one on which the event occurred.
function getEventMatchingParentElement(event, expression) {
  var element = findEventElement(event);
  if (!expression)
    return element;

  return _recursivelyFind(element, 'parentNode', expression);
}

//================================================================

function $(element) {
    if (arguments.length > 1) {
      for (var i = 0, elements = [], length = arguments.length; i < length; i++)
        elements.push($(arguments[i]));
      return elements;
    }

    if (isString(element))
      element = document.getElementById(element);
    
    return element;
}

function $$(expression) {
  return $A(document.querySelectorAll(expression));
};

//================================================================

window.Function.prototype._p_delay = function(timeout) {
  var slice = Array.prototype.slice;
  var __method = this, args = slice.call(arguments, 1);
  timeout = timeout * 1000;
  return window.setTimeout(function() {
    return __method.apply(__method, args);
  }, timeout);
}

window.Function.prototype._p_defer = function() {
  var args = _p_update([0.01], arguments);
  return this._p_delay.apply(this, args);
}

window.Function.prototype._p_bindAsEventListener = function(context) {
  var slice = Array.prototype.slice;
  var __method = this, args = slice.call(arguments, 1);
  return function(event) {
    var a = _p_update([event || window.event], args);
    return __method.apply(context, a);
  }
}

//================================================================

/*function toQueryParams(param, separator) {
  var match = _p_strip(param).match(/([^?#]*)(#.*)?$/);
  if (!match) return { };

  return match[1].split(separator || '&').inject({ }, function(hash, pair) {
    if ((pair = pair.split('='))[0]) {
      var key = decodeURIComponent(pair.shift()),
          value = pair.length > 1 ? pair.join('=') : pair[0];

      if (value != undefined) {
        value = value.gsub('+', ' ');
        value = decodeURIComponent(value);
      }

      if (key in hash) {
        if (!Object.isArray(hash[key])) hash[key] = [hash[key]];
        hash[key].push(value);
      }
      else hash[key] = value;
    }
    return hash;
  });
}*/

function toQueryPair(key, value) {
  if (Object.isUndefined(value)) return key;

  value = (value == null) ? "" : value;

  value = value.replace(/(\r)?\n/g, '\r\n');
  value = encodeURIComponent(value);
  value = value.replace(/%20/g, '+');
  return key + '=' + value;
}

function toQueryString(obj) {
  var paramList = [];
  for (var prop in obj) {
    if (obj.hasOwnProperty(prop)) {
      var key = encodeURIComponent(prop);
      var value = obj[prop];
      // FIXME: for simplicity there no support when a value is an array, which is not used in pedigree
      paramList.push(toQueryPair(key, value));
    }
    return paramList.join('&');
  }
}

var Ajax = {};

Ajax.Base = Class.create({
  initialize: function(options) {
    this.options = {
      method:       'post',
      asynchronous: true,
      contentType:  'application/x-www-form-urlencoded',
      encoding:     'UTF-8',
      parameters:   '',
      evalJSON:     true,
      evalJS:       true
    };
    this.options = _p_extend(_p_cloneObject(this.options), options || { });

    this.options.method = this.options.method.toLowerCase();
  }
});

Ajax.Request = Class.create(Ajax.Base, {
  _complete: false,

  initialize: function($super, url, options) {
    $super(options);
    this.transport = new XMLHttpRequest();
    this.request(url);
  },

  request: function(url) {
    this.url = url;
    this.method = this.options.method;
    var params = isString(this.options.parameters) ?
          this.options.parameters :
          toQueryString(this.options.parameters);

    if (this.method != 'get' && this.method != 'post') {
      params += (params ? '&' : '') + "_method=" + this.method;
      this.method = 'post';
    }

    if (params && this.method === 'get') {
      this.url += (this.url.includes('?') ? '&' : '?') + params;
    }

    //FIXME: not used in pedigree
    /*this.parameters = params.toQueryParams();*/

    try {
      var response = new Ajax.Response(this);
      if (this.options.onCreate) this.options.onCreate(response);

      this.transport.open(this.method.toUpperCase(), this.url,
        this.options.asynchronous);

      if (this.options.asynchronous) this.respondToReadyState.bind(this)._p_defer(1);

      this.transport.onreadystatechange = this.onStateChange.bind(this);
      this.setRequestHeaders();

      this.body = this.method == 'post' ? (this.options.postBody || params) : null;
      this.transport.send(this.body);

      /* Force Firefox to handle ready state 4 for synchronous requests */
      if (!this.options.asynchronous && this.transport.overrideMimeType)
        this.onStateChange();

    }
    catch (e) {
      this.dispatchException(e);
    }
  },

  onStateChange: function() {
    var readyState = this.transport.readyState;
    if (readyState > 1 && !((readyState == 4) && this._complete))
      this.respondToReadyState(this.transport.readyState);
  },

  setRequestHeaders: function() {
    var headers = {
      'X-Requested-With': 'XMLHttpRequest',
      'Accept': 'text/javascript, text/html, application/xml, text/xml, */*'
    };

    if (this.method == 'post') {
      headers['Content-type'] = this.options.contentType +
        (this.options.encoding ? '; charset=' + this.options.encoding : '');
    }

    if (typeof this.options.requestHeaders == 'object') {
      var extras = this.options.requestHeaders;

      for (var key in extras) {
        if (extras.hasOwnProperty(key)) {
          headers[key] = extras[key];
        }
      }
    }
  },

  success: function() {
    var status = this.getStatus();
    return !status || (status >= 200 && status < 300) || status == 304;
  },

  getStatus: function() {
    try {
      if (this.transport.status === 1223) return 204;
      return this.transport.status || 0;
    } catch (e) { return 0 }
  },

  respondToReadyState: function(readyState) {
    var state = Ajax.Request.Events[readyState], response = new Ajax.Response(this);

    if (state == 'Complete') {
      try {
        this._complete = true;
        (this.options['on' + response.status]
         || this.options['on' + (this.success() ? 'Success' : 'Failure')]
         || emptyFunction)(response, response.headerJSON);
      } catch (e) {
        this.dispatchException(e);
      }

      var contentType = response.getHeader('Content-type');
      if (this.options.evalJS == 'force'
          || (this.options.evalJS && this.isSameOrigin() && contentType
          && contentType.match(/^\s*(text|application)\/(x-)?(java|ecma)script(;.*)?\s*$/i)))
        this.evalResponse();
    }

    try {
      (this.options['on' + state] || emptyFunction)(response, response.headerJSON);
    } catch (e) {
      this.dispatchException(e);
    }

    if (state == 'Complete') {
      this.transport.onreadystatechange = emptyFunction;
    }
  },

  isSameOrigin: function() {
    return true;
  },

  getHeader: function(name) {
    try {
      return this.transport.getResponseHeader(name) || null;
    } catch (e) { return null; }
  },

  evalResponse: function() {
    try {
      return eval((this.transport.responseText || '').unfilterJSON());
    } catch (e) {
      this.dispatchException(e);
    }
  },

  dispatchException: function(exception) {
    (this.options.onException || emptyFunction)(this, exception);
  }
});

Ajax.Request.Events =
  ['Uninitialized', 'Loading', 'Loaded', 'Interactive', 'Complete'];

Ajax.Response = Class.create({
  initialize: function(request){
    this.request = request;
    var transport  = this.transport  = request.transport,
        readyState = this.readyState = transport.readyState;

    if ((readyState > 2 /*&& !Prototype.Browser.IE&*/) || readyState == 4) {
      this.status       = this.getStatus();
      this.statusText   = this.getStatusText();
      this.responseText = (transport.responseText == null) ? "" : transport.responseText;
      this.headerJSON   = this._getHeaderJSON();
    }

    if (readyState == 4) {
      var xml = transport.responseXML;
      this.responseXML  = isUndefined(xml) ? null : xml;
      this.responseJSON = this._getResponseJSON();
    }
  },

  status:      0,

  statusText: '',

  getStatus: Ajax.Request.prototype.getStatus,

  getStatusText: function() {
    try {
      return this.transport.statusText || '';
    } catch (e) { return '' }
  },

  getHeader: Ajax.Request.prototype.getHeader,

  getAllHeaders: function() {
    try {
      return this.getAllResponseHeaders();
    } catch (e) { return null }
  },

  getResponseHeader: function(name) {
    return this.transport.getResponseHeader(name);
  },

  getAllResponseHeaders: function() {
    return this.transport.getAllResponseHeaders();
  },

  _getHeaderJSON: function() {
    var json = this.getHeader('X-JSON');
    if (!json) return null;

    try {
      json = decodeURIComponent(escape(json));
    } catch(e) {
    }

    try {
      return eval('(' + json + ')');
    } catch (e) {
      this.request.dispatchException(e);
    }
  },

  _getResponseJSON: function() {
    var options = this.request.options;
    if (!options.evalJSON || (options.evalJSON != 'force' &&
      !(this.getHeader('Content-type') || '').includes('application/json')) ||
        /^\s*$/.test(this.responseText))
          return null;
    try {
      return eval('(' + this.responseText + ')');
    } catch (e) {
      this.request.dispatchException(e);
    }
  }
}); 

//================================================================

export {
  Class, $, $$, $A, PElement, Ajax,
  isFunction, isArray, isElement, isString, isNumber, isUndefined,
  PObserveEvent, PStopObserving, PStopObservingAll, PFireEvent, PRemoveAllListeners, getEventMatchingParentElement,
  getDocumentDimensions, getDocumentWidth, getDocumentHeight
};