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
import { Select, MenuItem } from "@mui/material";
import makeStyles from '@mui/styles/makeStyles';
import PropTypes from "prop-types";
import { Info } from "luxon";

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
  container : {
    overflow: "hidden",
    "& > *:not(:first-child)" : {
      paddingLeft: theme.spacing(2),
    },
    "& .MuiSelect-root" : {
      paddingLeft: theme.spacing(2),
      marginLeft: theme.spacing(-2),
    }
  },
  stretch: {
    "& > *" : {
      minWidth: "25%",
    },
    "& > .dropdowndate-month" : {
      minWidth: "50%",
    }
  },
  withShortMonth : {
    "& > *" : {
      minWidth: "33.33% !important"
    }
  },
  emptyOption : {
    opacity: "50%",
    padding: theme.spacing(2),
  },
  placeholder : {
    opacity: "50%"
  },
}));


/**
 * React-based date picker. Select date from Day, Month and Year dropdowns.
 * Code adapted & optimized from https://github.com/ssxv/react-dropdown-date
 *
 * @param {string} startDate optional, if not provided 1900-01-01 is startDate, 'yyyy-MM-dd' format only
 * @param {string} endDate optional, if not provided current date is endDate, 'yyyy-MM-dd' format only
 * @param {string} selectedDate optional, if not provided default values will be displayed, 'yyyy-MM-dd' format only
 * @param {array} order optional, Order of the dropdowns
 * @param {func} onDateChange optional, Callback on day, month or year change
 * @param {bool} yearReverse optional, If true, the year dropdown is ordered in time reverse order
 * @param {bool} disabled optional, If true, all dropdowns are disabled
 * @param {bool} monthShort optional, If true, months options are listed as 3 first characters
 * @param {bool} formatDate optional, If true, formats date according to the provided `order`
 * @param {bool} autoFocus optional, If true, autofocus of the first select
 * @param {bool} fullWidth optional, If true, the dropdowns stretch to the full width of the container
 *
 */

function DropdownsDatePicker(props) {
  const { startDate, endDate, selectedDate, order, onDateChange, yearReverse, disabled, monthShort, formatDate, autoFocus, fullWidth, ...rest } = props;
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
    yearOptions.push(<MenuItem key={-1} value="-1" className={classes.emptyOption}>Year</MenuItem>);

    let years = []
    for (let i = startYear; i <= endYear; i++) {
      years.push( <MenuItem key={i} value={i}>{i}</MenuItem> );
    }
    yearReverse && years.reverse();

    return yearOptions.concat(years);
  }

  let generateMonthOptions = () => {
    let monthOptions = [];
    monthOptions.push( <MenuItem key={-1} value="-1" className={classes.emptyOption}>Month</MenuItem> );

    let start = selectedYear === startYear ? startMonth : 0;
    let end = selectedYear === endYear ? endMonth : 11;

    for (let i = start; i <= end; i++) {
      monthOptions.push(
        <MenuItem key={i} value={i}>
          {monthShort ? Info.months('short')[i] : Info.months()[i]}
        </MenuItem>
      );
    }

    return monthOptions;
  }

  let generateDayOptions = () => {
    let dayOptions = [];
    dayOptions.push(<MenuItem key={-1} value="-1" className={classes.emptyOption}>Day</MenuItem>);

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
        variant="standard"
        key="year"
        onChange={handleYearChange}
        value={selectedYear}
        disabled={disabled}
        className="dropdowndate-year"
        autoFocus={autoFocus && order.indexOf("year") == 0}
        renderValue={(selected) => {
            if (selected < 0 ) {
              return <div className={classes.placeholder}>Year</div>;
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
        variant="standard"
        key="month"
        onChange={handleMonthChange}
        value={selectedMonth}
        disabled={disabled}
        className="dropdowndate-month"
        autoFocus={autoFocus && order.indexOf("month") == 0}
        renderValue={(selected) => {
            if (selected < 0 ) {
              return <div className={classes.placeholder}>Month</div>;
            }
            return monthShort ? Info.months('short')[selected] : Info.months()[selected];
        }}
      >
        {generateMonthOptions()}
      </Select>
    )
  }

  let renderDay = () => {
    return (
      <Select
        variant="standard"
        key="day"
        value={selectedDay}
        onChange={handleDayChange}
        disabled={disabled}
        className="dropdowndate-day"
        autoFocus={autoFocus && order.indexOf("day") == 0}
        renderValue={(selected) => {
            if (selected < 0 ) {
              return <div className={classes.placeholder}>Day</div>;
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

  let containerClasses = [classes.container];
  if (fullWidth) {
    containerClasses.push(classes.stretch);
    if (monthShort) {
      containerClasses.push(classes.withShortMonth);
    }
  }

  return (
    <div id="dropdown-date" className={containerClasses.join(' ')}>
      { order.map(part => { return renderParts[part]() }) }
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
  fullWidth: PropTypes.bool,
};

DropdownsDatePicker.defaultProps = {
  startDate: "1900-01-01",
  order: [DropdownDate.year, DropdownDate.month, DropdownDate.day],
  yearReverse: true
};

export default DropdownsDatePicker;
