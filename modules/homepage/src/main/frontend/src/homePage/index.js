import React from 'react';
import ReactDOM from 'react-dom';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import { Button, Card, CardContent, CssBaseline, Grid, Typography, Link, withStyles } from '@material-ui/core';

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

      {/* Main Page */}
      <div className={classes.mainContent}>
        <Typography component="h1" variant="h2" align="center" color="textPrimary" gutterBottom>
          LFS
        </Typography>
        <Typography variant="h6" align="center" color="textSecondary" paragraph>
          A data gathering initiative for patients with <Link href="https://en.wikipedia.org/wiki/Li%E2%80%93Fraumeni_syndrome">Li–Fraumeni syndrome</Link>.
        </Typography>
      </div>

      {window.Sling.getSessionInfo() === null || window.Sling.getSessionInfo().userID !== 'anonymous' ? <p> Logged in!</p> : <p>Logged Out </p>}
      <div className={classNames(classes.layout, classes.cardGrid)}>
        <Grid container spacing={5}>
          <Grid item sm={6} md={4} lg={3}>
            <Card className={classes.card}>
              <CardMedia className={classes.cardMedia} image="../content/starter/sling-logo.svg" />
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

          <PlaceHolderCardComponent />
          <PlaceHolderCardComponent />

        </Grid>
      </div>
    </React.Fragment>
  );
}

{/* Static typecheck, not TypeScript but its a feature of React. */}
HomePage.propTypes = {
  classes: PropTypes.object.isRequired,
};

const HomePageComponent = withStyles(styles)(HomePage);


ReactDOM.render(<HomePageComponent />, document.querySelector('#main-container'));
