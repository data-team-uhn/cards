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

import React, { useState, useEffect } from "react";
import { Autocomplete, Stack, TextField } from "@mui/material";
import { createFilterOptions } from "@mui/material/Autocomplete";
import makeStyles from '@mui/styles/makeStyles';
import PropTypes from "prop-types";
import { Info } from "luxon";

const DropdownDate = {
  year: 'year',
  month: 'month',
  day: 'day',
}

const getDaysInMonth = (year, month) => {
  if (month == null || year == null) return 31;
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
    "& .MuiAutocomplete-root" : {
      display: "inline-block",
      paddingLeft: theme.spacing(2),
      marginLeft: theme.spacing(-2),
    }
  },
  stretch: {
    "& > .dropdowndate-year" : {
      minWidth: "30%",
    },
    "& > .dropdowndate-month" : {
      minWidth: "50%",
    },
    "& > .dropdowndate-day" : {
      minWidth: "20%",
    },
  },
  withShortMonth : {
    "& > *" : {
      minWidth: "33.33% !important"
    }
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

  const [ years, setYears ] = useState([]);
  const [ months, setMonths ] = useState([]);
  const [ days, setDays ] = useState([]);

  const [ selectedYear, setSelectedYear ] = useState(selDate?.getFullYear() ?? null);
  const [ selectedMonth, setSelectedMonth ] = useState(selDate?.getMonth()) ?? null;
  const [ selectedDay, setSelectedDay ] = useState(selDate?.getDate() ?? null);

  const [ focusedDateComponent, setFocusedDateComponent ] = useState();

  let getStringArray = (from, to) => Array.from({length: (to - from + 1)}, (_, i) => `${i + from}`)

  // Generate the year options in the beginning
  useEffect(() => {
    let yearOptions = getStringArray(startYear, endYear);
    yearReverse && yearOptions.reverse();
    setYears(yearOptions);
  }, []);

  // Generate month options whenever the selected year changes
  useEffect(() => {
    let monthOptions = [];
    let start = selectedYear == startYear ? startMonth : 0;
    let end = selectedYear == endYear ? endMonth : 11;
    for (let i = start; i <= end; i++) {
      monthOptions.push({ value: i, label: (monthShort ? Info.months('short')[i] : Info.months()[i])});
    }
    setMonths(monthOptions);
  }, [selectedYear]);

  // Generate day options whenever selected year or month changes
  useEffect(() => {
    let start = (selectedYear == startYear && selectedMonth == startMonth) ? startDay : 1;
    const monthDays = getDaysInMonth(selectedYear, selectedMonth);
    let end = (selectedYear == endYear && selectedMonth == endMonth) ? endDay : monthDays;
    setDays(getStringArray(start, end));
  }, [selectedYear, selectedMonth]);

  // Change handlers

  let handleDateChange = (type, value) => {
    value !== null && focusNextDateComponent(type);

    let dateObj = {
      [DropdownDate.year] : (type === DropdownDate.year) ? value : selectedYear,
      [DropdownDate.month] : (type === DropdownDate.month) ? value : selectedMonth,
      [DropdownDate.day] : (type === DropdownDate.day) ? value : selectedDay
    };

    let date = null;

    if (dateObj[DropdownDate.year] !== null && dateObj[DropdownDate.month] !== null && dateObj[DropdownDate.day] !== null) {
      date = new Date(dateObj[DropdownDate.year], dateObj[DropdownDate.month], dateObj[DropdownDate.day]);

      if (formatDate) {
        dateObj[DropdownDate.month] = dateObj[DropdownDate.month] + 1;
        if (dateObj[DropdownDate.month] < 10) dateObj[DropdownDate.month] = '0' + dateObj[DropdownDate.month];
        if (dateObj[DropdownDate.day] < 10) dateObj[DropdownDate.day] = '0' + dateObj[DropdownDate.day];
        date = order.map(part => { return dateObj[part] }).join('-');
      }
    }

    onDateChange?.(date);
  }

  let handleYearChange = (event, value) => {
    const year = value ?? null;
    setSelectedYear(year);
    handleDateChange(DropdownDate.year, year);
  }

  let handleMonthChange = (event, value) => {
    const month = value?.value ?? null;
    setSelectedMonth(month);
    handleDateChange(DropdownDate.month, month);
  }

  let handleDayChange = (event, value) => {
    const day = value ?? null;
    setSelectedDay(day);
    handleDateChange(DropdownDate.day, day);
  }

  // Clear the Day dropdown value if it is out of range based on the current year/month selection
  useEffect(() => {
    if (
      (selectedYear == startYear && selectedMonth == startMonth && selectedDay < startDay) ||
      (selectedYear == endYear && selectedMonth == endMonth && selectedDay > endDay) ||
      (selectedDay > getDaysInMonth(selectedYear, selectedMonth))
    ) {
      setSelectedDay(null);
    }
  }, [selectedYear, selectedMonth, selectedDay]);

  let hasAutoFocus = (dateComponent) => autoFocus && order?.indexOf(dateComponent) == 0;

  let focusNextDateComponent = (dateComponent) => setFocusedDateComponent(order[order.indexOf(dateComponent) + 1]);

  let aParams = {
    autoComplete: true,
    autoHighlight: true,
    autoSelect: true,
    disabled: disabled
  };

  const filterOptions = createFilterOptions({
    matchFrom: 'start',
  })

  let renderInput = (params, dateComponent) =>
    <TextField
      {...params}
      variant="standard"
      placeholder={dateComponent.charAt(0).toUpperCase() + dateComponent.slice(1)}
      autoFocus={hasAutoFocus(dateComponent)}
      onFocus={() => setFocusedDateComponent()}
      inputRef={focusedDateComponent == dateComponent ? (input) => input?.focus() : null}
    />

  let renderYear = () => {
    return (
      <Autocomplete
        key="year"
        {...aParams}
        options={years}
        onChange={handleYearChange}
        value={selectedYear}
        className="dropdowndate-year"
        renderInput={(params) => renderInput(params, DropdownDate.year)}
      />
    )
  }

  let renderMonth = () => {
    return (
      <Autocomplete
        key="month"
        openOnFocus
        {...aParams}
        options={months}
        filterOptions={filterOptions}
        onChange={handleMonthChange}
        value={months.find(option => option.value == selectedMonth) ?? null}
        className="dropdowndate-month"
        renderInput={(params) => renderInput(params, DropdownDate.month)}
      />
    )
  }

  let renderDay = () => {
    return (
      <Autocomplete
        key="day"
        openOnFocus
        {...aParams}
        options={days}
        filterOptions={filterOptions}
        onChange={handleDayChange}
        value={selectedDay}
        className="dropdowndate-day"
        renderInput={(params) => renderInput(params, DropdownDate.day)}
      />
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
    <Stack direction="row" id="dropdown-date" className={containerClasses.join(' ')}>
      { order.map(part => { return renderParts[part]() }) }
    </Stack>
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
