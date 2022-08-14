//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//

import PropTypes from "prop-types";
import { DateTime } from "luxon";

export default class DateQuestionUtilities {

  static TIMESTAMP_TYPE = "timestamp";
  static INTERVAL_TYPE = "interval";
  static slingDateFormat = "yyyy-MM-dd\'T\'HH:mm:ss";
  static VIEW_DATE_FORMAT = "yyyy/MM/dd";
  static YEAR_DATE_TYPE = "year";
  static MONTH_DATE_TYPE = "month";
  static FULL_DATE_TYPE = "date";
  static DATETIME_TYPE = "datetime";
  static DEFAULT_DATE_TYPE = this.FULL_DATE_TYPE;

  static yearTag = "yyyy";
  static monthTag = "MM";
  static dayTag = "dd";
  static hourTag = "hh";
  static minuteTag = "mm";
  static secondTag = "ss";

  static PROP_TYPES = {
    classes: PropTypes.object.isRequired,
    questionDefinition: PropTypes.shape({
      text: PropTypes.string,
      dateFormat: PropTypes.string,
      type: PropTypes.oneOf([DateQuestionUtilities.TIMESTAMP_TYPE, DateQuestionUtilities.INTERVAL_TYPE]),
      lowerLimit: PropTypes.object,
      upperLimit: PropTypes.object,
    })
  };

  static getDateType(dateFormat) {
    if (typeof(dateFormat) === "string") {
      const year = dateFormat.includes(this.yearTag);
      const month = dateFormat.includes(this.monthTag);
      const day = dateFormat.includes(this.dayTag);
      const time = dateFormat.toLowerCase().includes(this.hourTag) || dateFormat.includes(this.minuteTag);

      if (time) return this.DATETIME_TYPE;
      if (day) return this.FULL_DATE_TYPE;
      if (month) return this.MONTH_DATE_TYPE;
      if (year) return this.YEAR_DATE_TYPE;
    }

    return this.DEFAULT_DATE_TYPE;
  }

  static getFieldType(dateFormat) {
    let dateType = this.getDateType(dateFormat)
    let result;
    switch (dateType) {
      case this.YEAR_DATE_TYPE:
        result = "long";
        break;
      case this.MONTH_DATE_TYPE:
        result = "string";
        break;
      case this.DATETIME_TYPE:
        result = "datetime-local";
        break;
      default:
        result = "date"
        break;
    }
    return result;
  }

  // Truncates fields in the given DateTime object or date string
  // according to the given format string
  static toPrecision(date, toFormat, fromFormat) {
    if (!date) {
      return null;
    }
    if (!toFormat) {
      toFormat = this.slingDateFormat;
    }

    let new_date = date;
    if (typeof new_date === "string") {
      new_date = fromFormat ? DateTime.fromFormat(new_date, fromFormat) : DateTime.fromISO(new_date);
    }
    if (!new_date.isValid) {
      return null;
    }

    // Determine the coarsest measure to truncate the input to
    const truncate = {
      'S':'second',
      's':'minute',
      'm':'hour',
      'H':'day',
      'd':'month',
      'M':'year'
    };
    let truncateTo;
    for (let [formatSpecifier, targetPrecision] of Object.entries(truncate)) {
      if (toFormat.indexOf(formatSpecifier) < 0) {
        truncateTo = targetPrecision;
      }
    }

    return(new_date.startOf(truncateTo));
  }

  static dateToFormattedString(date, textFieldType) {
    return (!date?.isValid) ? "" :
    textFieldType === "date" ? date.toFormat(this.VIEW_DATE_FORMAT) : date.toFormat("yyyy-MM-dd\'T\'HH:mm");
  }

  // Convert a moment string to a month display
  static dateStringToDisplayMonth(dateFormat, value) {
    let monthIndex = dateFormat.indexOf('MM');
    if (monthIndex === 5) {
      value = value.replaceAll("-", dateFormat[4]);
    }
    if (value.length > 7) {
      // Cut off any text beyond "yyyy/mm"
      value = value.substring(0, 7);
    }
    return value;
  }

  // Format a DateAnswer given the given dateFormat
  static formatDateAnswer(dateFormat, value) {
    if (!value || value.length === 0) {
      return "";
    }
    if (Array.isArray(value)) {
      return `${this.formatDateAnswer(dateFormat, value[0])} to ${this.formatDateAnswer(dateFormat, value[1])}`;
    }
    dateFormat = dateFormat || this.VIEW_DATE_FORMAT;
    let dateType = this.getDateType(dateFormat);
    if (dateType === this.YEAR_DATE_TYPE) {
      // Year-only dates are displayed like a number
      return value;
    }
    let date = this.toPrecision(value, dateFormat);
    if (dateType === this.MONTH_DATE_TYPE) {
      return this.dateStringToDisplayMonth(
        dateFormat,
        !date?.isValid ? "" : date.toFormat("yyyy-MM")
        );
    } else {
      return date.toFormat(dateFormat);
    }
  }

  static stripTimeZone(dateString) {
    // Remove the time zone (eg. "-05:00") from the end of a sling provided date string
    return dateString?.replace(/[-+][0-9]{2}:[0-9]{2}$/gm, '');
  }

  static getClientTimezoneOffset = () => {
    const padTwo = (s) => {
      if (s.length < 2) {
        return '0' + s;
      }
      return s;
    };
    let totalOffsetMinutes = new Date().getTimezoneOffset();
    let offsetSign = (totalOffsetMinutes < 0) ? '+' : '-';
    let offsetMinute = Math.abs(totalOffsetMinutes) % 60;
    let offsetHour = Math.floor(Math.abs(totalOffsetMinutes) / 60);
    return offsetSign + padTwo(offsetHour.toString()) + ":" + padTwo(offsetMinute.toString());
  };

  static isAnswerComplete(answers, type) {
    return type == this.INTERVAL_TYPE && answers.length == 2 || answers.length == 1;
  }

  static dateDifference = (startDateInput, endDateInput) => {
    // Compute the displayed difference
    let result = {long:""}
    if (startDateInput && endDateInput) {
      let startDate = this.toPrecision(startDateInput, this.VIEW_DATE_FORMAT);
      let endDate = this.toPrecision(endDateInput, this.VIEW_DATE_FORMAT);

      let diff = [];
      let longDiff = [];
      for (const division of ["years", "months", "days"]) {
        let value = Math.round(endDate.diff(startDate, division).values[division]);
        if (value > 0) {
          diff.push(value);
          let timeSlot = {};
          timeSlot[division] = value;
          endDate = endDate.minus(timeSlot);
          longDiff.push(value + division.charAt(0));
        } else {
          diff.push(0);
        }
      }

      if (diff[0] > 0) {
        result.short = `${diff[0] + (diff[1] > 6 ? 1 : 0)}y`;
      } else if (diff[1] > 0) {
        result.short = `${diff[1] + (diff[2] > 15 ? 1 : 0)}m`;
      } else if (diff[2] > 0) {
        result.short = `${diff[2]}d`;
      }
      result.long = longDiff.join(" ");
    }
    return result;
  }

  static getPickerViews(dateFormat) {
    let views = [];
    if (typeof(dateFormat) === "string") {
      dateFormat.toLowerCase().includes(this.yearTag) && views.push('year');
      dateFormat.includes(this.monthTag) && views.push('month');
      dateFormat.includes(this.dayTag) && views.push('day');
      dateFormat.toLowerCase().includes(this.hourTag) && views.push('hours');
      dateFormat.includes(this.minuteTag) && views.push('minutes');
      dateFormat.includes(this.secondTag) && views.push('seconds');
    }
    return views;
  }

  static formatIsMeridiem(dateFormat) {
    return typeof(dateFormat) === "string" && dateFormat.includes(this.hourMeridiemTag) && dateFormat.includes("a");
  }

  static formatIsMinuteSeconds(dateFormat) {
    return typeof(dateFormat) === "string" && dateFormat.toLowerCase() === "mm:ss";
  }

  static timeQuestionFieldType(dateFormat) {
    return this.formatIsMinuteSeconds(dateFormat) ? "string" : "time";
  }

  static getPickerViews(dateFormat) {
    let views = [];
    if (typeof(dateFormat) === "string") {
      dateFormat.includes(this.yearTag) && views.push('year');
      dateFormat.includes(this.monthTag) && views.push('month');
      dateFormat.includes(this.dayTag) && views.push('day');
      dateFormat.includes(this.hourTag) && views.push('hours');
      dateFormat.includes(this.minuteTag) && views.push('minutes');
      dateFormat.includes(this.secondTag) && views.push('seconds');
    }
    return views;
  }
}
