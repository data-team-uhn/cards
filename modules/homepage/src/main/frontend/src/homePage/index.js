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
import PropTypes from 'prop-types';
import classNames from 'classnames';
import { AppBar, Button, Card, CardContent, CssBaseline, Grid, Toolbar, Typography, Link, withStyles } from '@material-ui/core';

const styles = theme => ({
  appBar: {
    position: 'relative',
  },
  icon: {
    marginRight: theme.spacing(2),
  },
  heroUnit: {
    backgroundColor: theme.palette.background.paper,
  },
  mainContent: {
    maxWidth: 600,
    margin: '0 auto',
    padding: `${theme.spacing(8)}px 0 ${theme.spacing(6)}px`,
  },
  heroButtons: {
    marginTop: theme.spacing(4),
  },
  layout: {
    width: 'auto',
    marginLeft: theme.spacing(3),
    marginRight: theme.spacing(3),
    [theme.breakpoints.up(1100 + theme.spacing(6))]: {
      width: 1100,
      marginLeft: 'auto',
      marginRight: 'auto',
    },
  },
  cardGrid: {
    padding: `${theme.spacing(8)}px 0`,
  },
  card: {
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    maxHeight: '400',
  },
  cardContent: {
    flexGrow: 1,
  },
  footer: {
    backgroundColor: theme.palette.background.paper,
    padding: theme.spacing(6),
  },
});

{/* Functional component without state. Fine for homepage */}
function HomePage(props) {
  const { classes } = props;

  return (
    <React.Fragment>
      {/* Override <body> and <html> tags with Material UI default css
            - Remove margins, default background color applied */}
      <CssBaseline />

      {/* Blank navbar */}
      <AppBar position="static" className={classes.appBar}>
        <Toolbar>
          <Typography variant="h6" color="inherit" noWrap>
            LFS Repository
          </Typography>
        </Toolbar>
      </AppBar>

      {/* Main Page */}
      <div className={classes.mainContent}>
        <Typography component="h1" variant="h2" align="center" color="textPrimary" gutterBottom>
          LFS
        </Typography>
        <Typography variant="h6" align="center" color="textSecondary" paragraph>
          A data gathering initiative for patients with <Link href="https://en.wikipedia.org/wiki/Li%E2%80%93Fraumeni_syndrome">Li–Fraumeni syndrome</Link>.
        </Typography>
        <Grid container spacing={16} justify="center">
          <Grid item>
            <Button variant="contained" href="../content/slingshot.html" color="primary">
              Login
            </Button>
          </Grid>
          <Grid item>
            <Button variant="outlined" href="../content/slingshot.html" color="primary">
              Sign Up
            </Button>
          </Grid>
        </Grid>
      </div>

      <div className={classNames(classes.layout, classes.cardGrid)}>
        <Grid container spacing={5}>
          <Grid item sm={6} md={4} lg={3}>
            <Card className={classes.card}>
              <CardContent>
                <Typography variant="h5"> Some Dev Links </Typography>
                <Typography variant="h6">
                  <ul>
                    <li><Link href="../bin/browser.html"> JCR Content Browser </Link></li>
                    <li><Link href="../system/console/bundles"> System Console </Link></li>
                    <li><Link href="../system/console/configMgr"> System Configuration </Link></li>
                  </ul>
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid item sm={6} md={4} lg={3}>
            <Card className={classes.card}>
              <CardContent>
                <Typography variant="h5"> Data entries </Typography>
                <Typography variant="h6">
                  <ul>
                    <li><Link href="/view"> See all data entries </Link></li>
                  </ul>
                </Typography>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </div>

      {/* Footer */}
      <footer className={classes.footer}>
        <Typography variant="h6" align="center" gutterBottom>
          Footer - Some Sling Magic below
        </Typography>
        <Typography variant="subtitle1" align="center" color="textSecondary" component="p">
          These admin tools will significantly change this page.
        </Typography>
      </footer>
      {/* End footer */}
    </React.Fragment>
  );
}

{/* Static typecheck, not TypeScript but its a feature of React. */}
HomePage.propTypes = {
  classes: PropTypes.object.isRequired,
};

// export default withStyles(styles)(Album);
const HomePageComponent = withStyles(styles)(HomePage);

ReactDOM.render(<HomePageComponent />, document.querySelector('#main-container'));
