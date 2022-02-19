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
import moment from "moment";

export default class DateQuestionUtilities {

  static TIMESTAMP_TYPE = "timestamp";
  static INTERVAL_TYPE = "interval";
  static slingDateFormat = "yyyy-MM-DDTHH:mm:ss";

  static YEAR_DATE_TYPE = "year";
  static MONTH_DATE_TYPE = "month";
  static FULL_DATE_TYPE = "date";
  static DATETIME_TYPE = "datetime";
  static DEFAULT_DATE_TYPE = this.FULL_DATE_TYPE;

  static yearTag = "yyyy";
  static monthTag = "mm";
  static dayTag = "dd";
  static hourTag = "hh";
  static minuteTag = "ss";

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
      const year = dateFormat.toLowerCase().includes(this.yearTag);
      const month = dateFormat.toLowerCase().includes(this.monthTag);
      const day = dateFormat.toLowerCase().includes(this.dayTag);
      const time = dateFormat.toLowerCase().includes(this.hourTag) || dateFormat.toLowerCase().includes(this.minuteTag) ;

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

  // Truncates fields in the given moment object or date string
  // according to the given format string
  static amendMoment(date, format) {
    if (!date) {
      return null;
    }
    if (!format) {
      format = this.slingDateFormat;
    }

    let new_date = date;
    if (typeof new_date === "string") {
      new_date = moment(new_date);
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
    // Both 'd' and 'D' should truncate to month
    format = format.replaceAll("D","d");
    for (let [formatSpecifier, targetPrecision] of Object.entries(truncate)) {
      if (format.indexOf(formatSpecifier) < 0) {
        truncateTo = targetPrecision;
      }
    }

    return(new_date.startOf(truncateTo));
  }

  static momentToString(date, textFieldType) {
    return (!date || !date.isValid()) ? "" :
    textFieldType === "date" ? date.format(moment.HTML5_FMT.DATE) :
    date.format(moment.HTML5_FMT.DATETIME_LOCAL);
  }

  // Convert a moment string to a month display
  static momentStringToDisplayMonth(dateFormat, value) {
    // Switch month and year if required as Moment returns a fixed order
    let monthIndex = dateFormat.indexOf('MM');
    if (monthIndex === 0) {
      let separator = dateFormat[2];
      // Switch back from moment supported yyyy/mm to desired mm/yyyy.
      value = [value.slice(5, 7), separator, value.slice(0, 4)].join('');
    } else if (monthIndex === 5) {
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
    dateFormat = dateFormat || "yyyy-MM-dd";
    let dateType = this.getDateType(dateFormat);
    if (dateType === this.YEAR_DATE_TYPE) {
      // Year-only dates are displayed like a number
      return value;
    }
    // Quick fix for moment using a different date specifier than Java
    dateFormat = dateFormat.replaceAll('d', "D");
    let date = this.amendMoment(value, dateFormat);
    if (dateType === this.MONTH_DATE_TYPE) {
      return this.momentStringToDisplayMonth(
        dateFormat,
        !date.isValid() ? "" :
        date.format(moment.HTML5_FMT.MONTH)
        );
    } else {
      return date.format(dateFormat);
    }
  }

  static stripTimeZone(dateString) {
    // Remove the time zone (eg. "-05:00") from the end of a sling provided date string
    return dateString.replace(/[-+][0-9]{2}:[0-9]{2}$/gm, '');
  }

  static isAnswerComplete(answers, type) {
    return type == this.INTERVAL_TYPE && answers.length == 2 || answers.length == 1;
  }

  static dateDifference = (startDateInput, endDateInput) => {
    // Compute the displayed difference
    let result = {long:""}
    if (startDateInput && endDateInput) {
      let startDate = this.amendMoment(startDateInput, "yyyy-MM-dd");
      let endDate = this.amendMoment(endDateInput, "yyyy-MM-dd");

      let diff = [];
      let longDiff = [];
      for (const division of ["years", "months", "days"]) {
        let value = endDate.diff(startDate, division);
        diff.push(value);
        if (value > 0) {
          endDate = endDate.subtract(value, division);
          longDiff.push(value + division.charAt(0));
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

  static getTodayDate = (dateFormat) => {
    let today = moment().startOf('day');
    return this.formatDateAnswer(dateFormat, today);
  }
  static getTomorrowDate = (dateFormat) => {
    let tomorrow = moment().add(1, 'day').startOf('day');
    return this.formatDateAnswer(dateFormat, tomorrow);
  }
}
