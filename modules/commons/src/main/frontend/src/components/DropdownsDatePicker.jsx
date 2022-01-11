//
//MIT License

//Copyright (c) 2020 rand0mC0d3r

//Permission is hereby granted, free of charge, to any person obtaining a copy
//of this software and associated documentation files (the "Software"), to deal
//in the Software without restriction, including without limitation the rights
//to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
//copies of the Software, and to permit persons to whom the Software is
//furnished to do so, subject to the following conditions:
//
//The above copyright notice and this permission notice shall be included in all
//copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
//SOFTWARE.
//

import React, { useState } from "react";
import { Select, MenuItem,  makeStyles } from "@material-ui/core";
import CalendarTodayIcon from '@material-ui/icons/CalendarToday';
import PropTypes from "prop-types";
import moment from "moment";

const DropdownDate = {
  year: 'year',
  month: 'month',
  day: 'day',
}

const getDaysInMonth = (year, month) => {
  if (month < 0 || year < 0) return 31;
  year = +(year);
  month = +(month) + 1;
  return new Date(year, month, 0).getDate();
};

const useStyles = makeStyles(theme => ({
  select : {
    paddingLeft: theme.spacing(1),
  },
  icon : {
    float: "right"
  },
  default : {
    opacity: "50%"
  },
  container : {
    borderBottom: "1px solid " + theme.palette.grey["800"],
  },
}));


/**
 * React-based date picker. Select date from Day, Month and Year dropdowns.
 * Code adapted & optimized from https://github.com/ssxv/react-dropdown-date
 *
 * @param {string} startDate optional, if not provided 1900-01-01 is startDate, 'yyyy-mm-dd' format only
 * @param {string} endDate optional, if not provided current date is endDate, 'yyyy-mm-dd' format only
 * @param {string} selectedDate optional, if not provided default values will be displayed, 'yyyy-mm-dd' format only
 * @param {array} order optional, Order of the dropdowns
 * @param {func} onDateChange optional, Callback on day, month or year change
 * @param {bool} yearReverse optional, If true, the year dropdown is ordered in time reverse order
 * @param {bool} disabled optional, If true, all dropdowns are disabled
 * @param {bool} monthShort optional, If true, months options are listed as 3 first characters
 * @param {bool} formatDate optional, If true, formats date according to the provided `order`
 * @param {bool} autoFocus optional, If true, autofocus of the first select
 *
 */

function DropdownsDatePicker(props) {
  const { startDate, endDate, selectedDate, order, onDateChange, yearReverse, disabled, monthShort, formatDate, autoFocus, ...rest } = props;
  const classes = useStyles();

  const sDate = new Date(startDate);
  const eDate = endDate ? new Date(endDate) : new Date();
  const selDate = selectedDate ? new Date(selectedDate) : null;

  const startYear = sDate.getFullYear();
  const startMonth = sDate.getMonth();
  const startDay = sDate.getDate();
  const endYear = eDate.getFullYear();
  const endMonth = eDate.getMonth();
  const endDay = eDate.getDate();

  const [ selectedYear, setSelectedYear ] = useState( selDate ? selDate.getFullYear() : -1);
  const [ selectedMonth, setSelectedMonth ] = useState( selDate ? selDate.getMonth() : -1);
  const [ selectedDay, setSelectedDay ] = useState( selDate ? selDate.getDate() : -1);


  let generateYearOptions = () => {
    let yearOptions = [];
    yearOptions.push(<MenuItem key={-1} value="-1">yyyy</MenuItem>);

    let years = []
    for (let i = startYear; i <= endYear; i++) {
      years.push( <MenuItem key={i} value={i}>{i}</MenuItem> );
    }
    yearReverse && years.reverse();

    return yearOptions.concat(years);
  }

  let generateMonthOptions = () => {
    let monthOptions = [];
    monthOptions.push( <MenuItem key={-1} value="-1">mm</MenuItem> );

    let start = selectedYear === startYear ? startMonth : 0;
    let end = selectedYear === endYear ? endMonth : 11;

    for (let i = start; i <= end; i++) {
      monthOptions.push(
        <MenuItem key={i} value={i}>
          {monthShort ? moment().month(i).format("MMM") : moment().month(i).format("MMMM")}
        </MenuItem>
      );
    }

    return monthOptions;
  }

  let generateDayOptions = () => {
    let dayOptions = [];
    dayOptions.push(<MenuItem key={-1} value="-1">dd</MenuItem>);

    let start = (selectedYear === startYear && selectedMonth === startMonth) ? startDay : 1;
    const monthDays = getDaysInMonth(selectedYear, selectedMonth);
    let end = (selectedYear === endYear && selectedMonth === endMonth) ? endDay : monthDays;

    for (let i = start; i <= end; i++) {
      dayOptions.push(<MenuItem key={i} value={i}>{i}</MenuItem>);
    }

    return dayOptions;
  }

  let handleDateChange = (type, value) => {
    let dateObj = {
      [DropdownDate.year] : (type === DropdownDate.year) ? value : selectedYear,
      [DropdownDate.month] : (type === DropdownDate.month) ? value : selectedMonth,
      [DropdownDate.day] : (type === DropdownDate.day) ? value : selectedDay
    };

    if (dateObj[DropdownDate.year] !== -1 && dateObj[DropdownDate.month] !== -1 && dateObj[DropdownDate.day] !== -1) {
      let date = new Date(dateObj[DropdownDate.year], dateObj[DropdownDate.month], dateObj[DropdownDate.day]);

      if (formatDate) {
        dateObj[DropdownDate.month] = dateObj[DropdownDate.month] + 1;
        if (dateObj[DropdownDate.month] < 10) dateObj[DropdownDate.month] = '0' + dateObj[DropdownDate.month];
        if (dateObj[DropdownDate.day] < 10) dateObj[DropdownDate.day] = '0' + dateObj[DropdownDate.day];
        date = order.map(part => { return dateObj[part] }).join('-');
      }

      onDateChange(date);
    }
  }

  let handleYearChange = (event) => {
    const year = parseInt(event.target.value);
    setSelectedYear(year);
    const monthDays = getDaysInMonth(year, selectedMonth);
    if (selectedDay > monthDays) {
      setSelectedDay(-1);
    } else {
      onDateChange && handleDateChange(DropdownDate.year, year);
    }
  }

  let handleMonthChange = (event) => {
    const month = parseInt(event.target.value);
    setSelectedMonth(month);
    const monthDays = getDaysInMonth(selectedYear, month);
    if (selectedDay > monthDays) {
      setSelectedDay(-1);
    } else {
      onDateChange && handleDateChange(DropdownDate.month, month);
    }
  }

  let handleDayChange = (event) => {
    const day = parseInt(event.target.value);
    setSelectedDay(day);
    onDateChange && handleDateChange(DropdownDate.day, day);
  }

  let renderYear = () => {
    return (
      <Select
        key="year"
        onChange={handleYearChange}
        value={selectedYear}
        disabled={disabled}
        className={order.indexOf("year") != 0 ? classes.select : ''}
        autoFocus={autoFocus && order.indexOf("year") == 0}
        renderValue={(selected) => {
            if (selected < 0 ) {
              return <div className={classes.default}>yyyy</div>;
            }
            return selected;
        }}
      >
        {generateYearOptions()}
      </Select>
    )
  }

  let renderMonth = () => {
    return (
      <Select
        key="month"
        onChange={handleMonthChange}
        value={selectedMonth}
        disabled={disabled}
        className={order.indexOf("month") != 0 ? classes.select : ''}
        autoFocus={autoFocus && order.indexOf("month") == 0}
        renderValue={(selected) => {
            if (selected < 0 ) {
              return <div className={classes.default}>mm</div>;
            }
            if (selected < 9 ) {
              return "0" + (selected+1);
            }
            return selected+1;
        }}
      >
        {generateMonthOptions()}
      </Select>
    )
  }

  let renderDay = () => {
    return (
      <Select
        key="day"
        value={selectedDay}
        onChange={handleDayChange}
        disabled={disabled}
        className={order.indexOf("day") != 0 ? classes.select : ''}
        autoFocus={autoFocus && order.indexOf("day") == 0}
        renderValue={(selected) => {
            if (selected < 0 ) {
              return <div className={classes.default}>dd</div>;
            }
            if (selected < 10 ) {
              return "0" + selected;
            }
            return selected;
        }}
      >
        {generateDayOptions()}
      </Select>
    )
  }

  const renderParts = {
    [DropdownDate.year]: renderYear,
    [DropdownDate.month]: renderMonth,
    [DropdownDate.day]: renderDay,
  }

  return (
    <div id="dropdown-date" className={classes.container}>
      { order.map(part => { return renderParts[part]() }) }
      <CalendarTodayIcon fontSize="small" className={classes.icon}/>
    </div>
  );
}


DropdownsDatePicker.propTypes = {
  startDate: PropTypes.string,
  endDate: PropTypes.string,
  selectedDate: PropTypes.string,
  order: PropTypes.arrayOf(PropTypes.string),
  onDateChange: PropTypes.func,
  yearReverse: PropTypes.bool,
  disabled: PropTypes.bool,
  monthShort: PropTypes.bool,
  formatDate: PropTypes.bool,
  autoFocus: PropTypes.bool,
};

DropdownsDatePicker.defaultProps = {
  startDate: "1900-01-01",
  order: [DropdownDate.year, DropdownDate.month, DropdownDate.day],
  yearReverse: true
};

export default DropdownsDatePicker;
