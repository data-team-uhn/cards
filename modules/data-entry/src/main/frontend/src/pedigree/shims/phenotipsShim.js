import { Class, $, PElement, PObserveEvent } from './prototypeShim';
import { cloneObject } from '../model/helpers';
//import { Draggable } from './effectsShim';

function _p_extend(destination, source) {
  for (var property in source)
    destination[property] = source[property];
  return destination;
}

var PModalPopup = Class.create({
  /** Configuration. Empty values will fall back to the CSS. */
  options : {
    idPrefix : "modal-popup-",
    title : "",
    displayCloseButton : true,
    screenColor : "",
    borderColor : "",
    titleColor : "",
    backgroundColor : "",
    screenOpacity : "0.5",
    verticalPosition : "center",
    horizontalPosition : "center",
    resetPositionOnShow: true,
    removeOnClose : false,
    onClose: function () { },
  },
  /** Constructor. Registers the key listener that pops up the dialog. */
  initialize : function(content, shortcuts, options) {
    /** Shortcut configuration. Action name -&amp;gt; {method: function(evt), keys: string[]}. */
    this.shortcuts = {
      "show" : { method : this.showDialog, keys : []},
      "close" : { method : this.closeDialog, keys : ['Esc']}
    },

    this.content = content || "Hello world!";
    // Add the new shortcuts
    this.shortcuts = _p_extend(cloneObject(this.shortcuts), shortcuts || { });
    // Add the custom options
    this.options = _p_extend(cloneObject(this.options), options || { });
    // Register a shortcut for showing the dialog.
    this.registerShortcuts("show");

    this.id = document.querySelectorAll(".msdialog-modal-container").length + 1;
  },

  getBoxId : function() {
    return this.options.idPrefix + this.id;
  },

  /** Create the dialog, if it is not already loaded. Otherwise, just make it visible again. */
  createDialog : function(event) {
    this.dialog = PElement('div', {'class': 'msdialog-modal-container'});
    if (this.options.extraDialogClassName) {
      this.dialog._p_addClassName(this.options.extraDialogClassName);
    }
    // A full-screen semi-transparent screen covering the main document
    this.screen = PElement('div', {'class': 'msdialog-screen'})._p_setStyle({
      opacity : this.options.screenOpacity,
      backgroundColor : this.options.screenColor
    });
    this.dialog._p_update(this.screen);
    // The dialog chrome
    this.dialogBox = PElement('div', {'class': 'msdialog-box', 'id' : this.getBoxId()});
    if (this.options.extraClassName) {
      this.dialogBox._p_addClassName(this.options.extraClassName);
    }
    // Insert the content
    this.dialogBox._x_contentPlug = PElement('div', {'class' : 'content'});
    this.dialogBox._p_update(this.dialogBox._x_contentPlug);
    this.dialogBox._x_contentPlug._p_update(this.content);
    // Add the dialog title
    if (this.options.title) {
      var title = PElement('div', {'class': 'msdialog-title'})._p_update(this.options.title);
      title._p_setStyle({"color" : this.options.titleColor, "backgroundColor" : this.options.borderColor});
      this.dialogBox._p_insert_top(title);
    }
    // Add the close button
    if (this.options.displayCloseButton) {
      var closeButton = PElement('div', {'class': 'msdialog-close', 'title': 'Close'})._p_update("x");
      closeButton._p_setStyle({"color": this.options.titleColor});
      closeButton._p_observe("click", this.closeDialog.bind(this));
      this.dialogBox._p_insert_top(closeButton);
    }
    this.dialog.appendChild(this.dialogBox);
    this.dialogBox._p_setStyle({
      "textAlign": "left",
      "borderColor": this.options.borderColor,
      "backgroundColor" : this.options.backgroundColor
    });
    this.positionDialog();

    // Append to the end of the document body.
    //document.body.appendChild(this.dialog);
    // FIXME: pass via options
    $(window.editor._topElementID).appendChild(this.dialog);

    if (typeof (Draggable) != 'undefined') {
      new Draggable(this.getBoxId(), {
        handle: $(this.getBoxId()).down('.msdialog-title'),
        scroll: window,
        change: this.updateScreenSize.bind(this)
      });
    }
    this.dialog._p_hide();
    var __enableUpdateScreenSize = function (event) {
      if (this.dialog._p_visible()) {
        this.updateScreenSize();
      }
    }.bind(this);
    ['resize', 'scroll'].forEach(function(eventName) {
      PObserveEvent(eventName, __enableUpdateScreenSize);
    }.bind(this));
    PObserveEvent('ms:popup:content-updated', __enableUpdateScreenSize);
  },
  positionDialog : function() {
    switch(this.options.verticalPosition) {
      case "top":
        //FIXME
        //this.dialogBox._p_setStyle({"top": (document.viewport.getScrollOffsets().top + 6) + "px"});
        this.dialogBox._p_setStyle({ "top": "16px" });
        break;
      case "bottom":
        this.dialogBox._p_setStyle({"bottom": ".5em"});
        break;
      default:
        var targetPercentage = 35;
        var targetPosition = targetPercentage + "%";
        try {
          // if parent element is longer than the screen (e.g. patient form) make sure dialog pops up within the
          // area currently visible by the user, which can be achieved by absolute positioning
          if (this.dialogBox.parentElement.clientHeight > window.innerHeight) {
              targetPosition = Math.floor(window.pageYOffset + window.innerHeight * targetPercentage / 100) + "px";
          }
        } catch (err) {
          // something went wrong, maybe parent element is undefined
        }
        this.dialogBox._p_setStyle({"top": targetPosition});
        break;
    }
    this.dialogBox._p_setStyle({"left": "", "right" : ""});
    switch(this.options.horizontalPosition) {
      case "left":
        this.dialog._p_setStyle({"textAlign": "left"});
        break;
      case "right":
        this.dialog._p_setStyle({"textAlign": "right"});
        break;
      default:
        this.dialog._p_setStyle({"textAlign": "center"});
        this.dialogBox._p_setStyle({"margin": "auto"});
      break;
    }
  },
  positionDialogInViewport : function(left, top) {
    this.dialogBox._p_setStyle({
      // FIXME
      "left": (/*document.viewport.getScrollOffsets().left*/ 10 + left) + "px",
      "top" : (/*document.viewport.getScrollOffsets().top*/  10 + top ) + "px",
      "margin" : "0"
    });
  },
  
  updateScreenSize: function () {
    // TODO: check if this is needed, seems like it works well without this code
    /*
    var __getNewDimension = function (eltToFit, dimensionAccessFunction, position) {
      // FIXME
      return '600px';
    };
    this.screen.style.width  = __getNewDimension(this.dialogBox, 'getWidth', 'left');
    this.screen.style.height = __getNewDimension(this.dialogBox, 'getHeight', 'top');
    */
  },
  /** Set a class name to the dialog box */
  setClass : function(className) {
    this.dialogBox._p_addClassName('msdialog-box-' + className);
  },
  /** Remove a class name from the dialog box */
  removeClass : function(className) {
    this.dialogBox._p_removeClassName('msdialog-box-' + className);
  },
  /** Set the content of the dialog box */
  setContent : function(content) {
     this.content = content;
     this.dialogBox._x_contentPlug._p_update(this.content);
     this.updateScreenSize();
  },
  /** Called when the dialog is displayed. Enables the key listeners and gives focus to the (cleared) input. */
  showDialog : function(event) {
    if (event) {
      event.stopPropagation();
    }
    // Only do this if the dialog is not already active.
    //if (!widgets.ModalPopup.active) {
    //  widgets.ModalPopup.active = true;
    if (!this.active) {
      this.active = true;
      if (!this.dialog) {
        // The dialog wasn't loaded, create it.
        this.createDialog();
      }
      // Start listening to keyboard events
      this.attachKeyListeners();      
      // Display the dialog
      this.dialog._p_show();
      if (this.options.resetPositionOnShow) {
        this.positionDialog();
      }
      this.updateScreenSize();
    }
  },
onScroll: function(event) {
    //FIXME
    //this.dialog._p_setStyle({top : document.viewport.getScrollOffsets().top + "px"});
  },
  /** Called when the dialog is closed. Disables the key listeners, hides the UI and re-enables the 'Show' behavior. */
  closeDialog : function(event) {
    if (event) {
      event.stopPropagation();
    }
    // Call optional callback
    this.options.onClose.call(this);
    // Hide the dialog, without removing it from the DOM.
    this.dialog._p_hide();
    if (this.options.removeOnClose) {
      this.dialog.remove();
    }
    // Stop the UI shortcuts (except the initial Show Dialog one).
    this.detachKeyListeners();
    // Re-enable the 'show' behavior.
    // widgets.ModalPopup.active = false;
    this.active = false;
  },
  /** Enables all the keyboard shortcuts, except the one that opens the dialog, which is already enabled. */
  attachKeyListeners : function() {
    for (var action in this.shortcuts) {
      if (action != "show") {
        this.registerShortcuts(action);
      }
    }
  },
  /** Disables all the keyboard shortcuts, except the one that opens the dialog. */
  detachKeyListeners : function() {
    for (var action in this.shortcuts) {
      if (action != "show") {
        this.unregisterShortcuts(action);
      }
    }
  },
  /**
   * Enables the keyboard shortcuts for a specific action.
   *
   * @param {String} action The action to register
   * {@see #shortcuts}
   */
  registerShortcuts : function(action) {
    var shortcuts = this.shortcuts[action].keys;
    var method = this.shortcuts[action].method;
    for (var i = 0; i < shortcuts.length; ++i) {
      // FIXME: what is "shortcut"?
      //shortcut.add(shortcuts[i], method.bind(this, action), {type: 'keyup'});
    }
  },
  /**
   * Disables the keyboard shortcuts for a specific action.
   *
   * @param {String} action The action to unregister {@see #shortcuts}
   */
  unregisterShortcuts : function(action) {
    for (var i = 0; i < this.shortcuts[action].keys.length; ++i) {
      // FIXME: what is "shortcut"?
      //shortcut.remove(this.shortcuts[action].keys[i]);
    }
  },
  createButton : function(type, text, title, id) {
    var wrapper = PElement("span", {"class" : "buttonwrapper"});
    var button = PElement("input", {
      "type" : type,
      "class" : "button",
      "value" : text,
      "title" : title,
      "id" : id
    });
    wrapper._p_update(button);
    return wrapper;
  },
  show : function(event) {
    this.showDialog(event);
  },
  close : function(event) {
    this.closeDialog(event);
  }
});

export default PModalPopup;