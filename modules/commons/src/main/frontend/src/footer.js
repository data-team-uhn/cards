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
import React from 'react';
import ReactDOM from 'react-dom';
import { Typography, withStyles } from '@material-ui/core';

const styles = theme => ({
  footer: {
    backgroundColor: theme.palette.background.paper,
    padding: theme.spacing(6),
  },
});

class GlobalFooter extends React.Component {
  constructor(props) {
    super(props);
  }

  render() {
    return (
      <footer className={this.props.classes.footer}>
        <Typography variant="overline" align="center" color="textSecondary" component="p">
          LFS Data Repository version 0.1-SNAPSHOT
        </Typography>
      </footer>
    );
  }
}

const GlobalFooterComponent = withStyles(styles)(GlobalFooter);

ReactDOM.render(<GlobalFooterComponent />, document.querySelector('#footer-container'));
