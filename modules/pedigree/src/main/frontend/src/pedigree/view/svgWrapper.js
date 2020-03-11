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

import { Class } from '../shims/prototypeShim';
import { cloneObject } from '../model/helpers';

/**
 * SVGWrapper is a wrapper around textual representation of an SVG with methods to manipulate the SVG
 *
 * @class SVGWrapper
 * @constructor
 */

var SVGWrapper = Class.create({

        // needs to get bbox as an input as this needs an external compiutation by the browser
        initialize: function(svgText, boundingBox, scale) {
            this._svgText = svgText;
            this._bbox    = {"x": boundingBox.x,
                             "y": boundingBox.y,
                             "height": boundingBox.height,
                             "width":  boundingBox.width};
            this._scale   = scale ? scale : 1.0;
        },

        /**
         * Returns the text string representing the SVG
         *
         * @method getSVGText
         * @return {Object} Raphael Paper element
         */
        getSVGText: function() {
            return this._svgText;
        },

        /**
         * Returns the bounding box of the wrapped SVG
         *
         * @method getBBox
         * @return {Object} {x,y, width, height}
         */
        getBBox: function() {
            return cloneObject(this._bbox);
        },

        getCopy: function() {
            return new SVGWrapper(this._svgText, this._bbox, this._scale);
        },

        setNoAspectRatioPreservation: function() {
            this._svgText = this._svgText.replace(/preserveAspectRatio="xMinYMin"/, "preserveAspectRatio=\"none\"");
            return this;
        },

        move: function(xShift, yShift) {
            this._bbox.x += xShift;
            this._bbox.y += yShift;
        },

        scale: function(scaleFactor) {
            this._scale = scaleFactor;

            this._bbox.width  = Math.ceil(this._bbox.width  * scaleFactor);
            this._bbox.height = Math.ceil(this._bbox.height * scaleFactor);

            this._svgText = this._svgText.replace(/(<svg [^<>]+) width=["-]?\d+"? height=["-]?\d+"?/g, "$1 width=\"" +
                                                  (this._bbox.width) + "\" height=\"" + (this._bbox.height) + "\"");
            return this;
        },

        setViewBox: function(xOffset, yOffset, xWidth, yWidth) {
            xWidth = Math.ceil(xWidth);
            yWidth = Math.ceil(yWidth);
            this._svgText = this._svgText.replace(/(<svg[^<>]+) viewBox="[^<>"]*"/g,
                            "$1 viewBox=\"" + Math.floor(this._bbox.x + xOffset/this._scale)+ " " + Math.floor(this._bbox.y + yOffset/this._scale) + " " + xWidth/this._scale + " " + yWidth/this._scale + "\"");
            this._svgText = this._svgText.replace(/(<svg [^<>]+) width=["-]?\d+"? height=["-]?\d+"?/g, "$1 width=\"" +
                            (xWidth) + "\" height=\"" + (yWidth) + "\"");
            return this;
        },

        addCenteringCSS: function() {
            this._svgText = this._svgText.replace(/(<svg [^<>]+) style="/, "$1 style=\"display:block; margin: auto; ");
            return this;
        }
});

export default SVGWrapper;