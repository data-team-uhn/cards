/*
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at
  http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
*/
import PropTypes from "prop-types";
import {Hidden, IconButton} from "@material-ui/core";
import {FirstPage, KeyboardArrowLeft, KeyboardArrowRight, LastPage} from "@material-ui/icons";

class PaginationActions extends React.Component {
    handleFirstPage = (event) => {
      this.props.onChangePage(event, 0);
    }

    handleNextPage = (event) => {
      if (this.props.page < Math.ceil(this.props.count/this.props.rowsPerPage) - 1) {
        this.props.onChangePage(event, this.props.page + 1);
      }
    }

    handlePrevPage = (event) => {
      if (this.props.page > 0) {
        this.props.onChangePage(event, this.props.page - 1);
      }
    }

    handleLastPage = (event) => {
      this.props.onChangePage(event, Math.max(0, Math.ceil(this.props.count/this.props.rowsPerPage) - 1));
    }

    render () {
      const {count, page, rowsPerPage} = this.props;

      return (
        <div style={{flexShrink: 0}}>
          <IconButton
            onClick={this.handleFirstPage}
            disabled={page === 0}
          >
            <FirstPage/>
          </IconButton>
          <IconButton
            onClick={this.handlePrevPage}
            disabled={page === 0}
          >
            <KeyboardArrowLeft/>
          </IconButton>
          <IconButton
            onClick={this.handleNextPage}
            disabled={page >= Math.ceil(count / rowsPerPage) -1}
          >
            <KeyboardArrowRight/>
          </IconButton>
          <IconButton
            onClick={this.handleLastPage}
            disabled={page >= Math.ceil(count / rowsPerPage) -1}
          >
            <LastPage/>
          </IconButton>
        </div>
      );
    }
  }

  PaginationActions.propTypes = {
    count: PropTypes.number.isRequired,
    onChangePage: PropTypes.func.isRequired,
    page: PropTypes.number.isRequired,
    rowsPerPage: PropTypes.number.isRequired,
  };

  export default PaginationActions;