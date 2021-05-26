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

/**
 * The UI Element for displaying prompts to the user in a movable/semi-transparent dialogue
 *
 * @class OkCancelDialogue
 */
var OkCancelDialogue = Class.create( {

      initialize: function() {
          var _this = this;

          this._onButtonActions = [undefined, undefined, undefined];
          this._buttons         = [undefined, undefined, undefined];

          var mainDiv = PElement('div', {'class': 'ok-cancel-dialogue'});

          this._promptBody = PElement('div', {'class': 'ok-cancel-body'});
          mainDiv._p_insert(this._promptBody);

          this._buttons[0] = PElement('input', {type: 'button', name : 'ok',     'value': 'OK', 'class' : 'button min-width80px', 'id': 'OK_button'});
          this._buttons[1] = PElement('input', {type: 'button', name : 'cancel', 'value': 'Cancel', 'class' : 'button secondary min-width80px'});
          this._buttons[2] = PElement('input', {type: 'button', name : 'other',  'value': 'Other', 'class' : 'button secondary min-width80px'});

          var buttons = PElement('div', {'class' : 'buttons import-block-bottom'});
          for (var i = 0; i < this._buttons.length; i++) {
              buttons._p_insert(this._buttons[i]._p_wrap('span', {'class' : 'buttonwrapper'}));
              this._buttons[i].index = i;
              this._buttons[i]._p_observe('click', function(event) {
                      _this.hide();
                      _this._onButtonActions[this.index] && _this._onButtonActions[this.index]();
              });
          }
          mainDiv._p_insert(buttons);

          var closeShortcut = ['Esc'];
          this.dialog = new PModalPopup(mainDiv, {close: {method : this.hide.bind(this), keys : closeShortcut}}, {extraClassName: "pedigree-okcancel", title: "?", displayCloseButton: false, verticalPosition: "top"});
      },

      /**
       * Same as show but also displays a checkbox with the given title and cals the
       * onOK and onCancel functions with the boolean value indicating if the checkbox was pressed or not
       *
       * @method show
       */
      showWithCheckbox: function(message, title, checkboxText, defaultState,
                                 okButtonText, onOKFunction,
                                 cancelButtonText, onCancelFunction,
                                 optionalThirdButtonText, optionalThirdButtonFunction) {
          // add checkbox
          var message = message + '<br/><input ' + (defaultState ? 'checked ' : '') + 'type="checkbox" id ="okcancelcheckbox" value="checked">' +
                                  '<label class="field-no-user-select" for="okcancelcheckbox">' + checkboxText + '</label>';

          var getState = function() {
              // read checkbox state & clal original onOK with the state as the parameter
              var checkbox = $$('input[type=checkbox][id="okcancelcheckbox"]');
              return (checkbox ? checkbox[0].checked : defaultState);
          }
          var onOK = function() {
              onOKFunction(getState());
          }
          var onCancel = function() {
              onCancelFunction(getState());
          }
          var onThirdButton = function() {
              optionalThirdButtonFunction(getState());
          }
          this.showCustomized(message, title, okButtonText, onOK, cancelButtonText, onCancelFunction,
                  optionalThirdButtonText, onThirdButton, true);
      },

      /**
       * Displays the dialogue (vanilla OK\Cancel sytyle)
       *
       * @method show
       */
      show: function(message, title, onOKFunction, onCancelFunction) {
          this.showCustomized(message, title, "OK", onOKFunction, "Cancel", onCancelFunction);
      },

      /**
       * Displays the template selector
       *
       * @method show
       */
      showCustomized: function( message, title,
                                button1title, on1Function,
                                button2title, on2Function,
                                button3title, on3Function, bottomRight ) {
          this._configButton(0, button1title, on1Function);
          this._configButton(1, button2title, on2Function);
          this._configButton(2, button3title, on3Function, bottomRight);
          this._promptBody._p_update(message);
          this.dialog.show();
          this.dialog.dialogBox._p_down("div.msdialog-title")._p_update(title);  // this.dialog.dialogBox is available only after show()
      },

      /**
       * Displays a dialogue with a red error icon and only one button
       *
      showError: function(message, title, buttonTitle, onOKFunction) {
          this.showCustomized(message, title, buttonTitle, onOKFunction);
          this.dialog.dialogBox._p_down("div.msdialog-title")._p_update("<img src='/resources/icons/silk/error.png' height='13'>&nbsp;&nbsp;" + title);
      },*/

      /**
       * Hides the dialogue
       *
       * @method hide
       */
      hide: function() {
          this.dialog.closeDialog();
      },

      _configButton: function(buttonID, buttonTitle, actionFunction, bottomRightButton) {
          if (!buttonTitle || buttonTitle == "") {
              this._buttons[buttonID]._p_hide();
          } else {
              this._buttons[buttonID]._p_show();
              this._buttons[buttonID]._p_writeAttribute("value", buttonTitle);
              this._onButtonActions[buttonID] = actionFunction;
          }
          this._buttons[buttonID]._p_setStyle({"marginLeft": (buttonID == 0 ? "0px" : "10px")});
      }
});

export default OkCancelDialogue;