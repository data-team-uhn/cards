// Taken from https://www.creative-tim.com/product/material-dashboard-react
import React from "react";
import PropTypes from "prop-types";
// @material-ui/core
import { withStyles } from "@material-ui/core/styles";
<<<<<<< HEAD
// material-dashboard-react
=======
// core components
import GridItem from "material-dashboard-react/dist/components/Grid/GridItem.js";
import GridContainer from "material-dashboard-react/dist/components/Grid/GridContainer.js";
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
import Table from "material-dashboard-react/dist/components/Table/Table.js";
import Card from "material-dashboard-react/dist/components/Card/Card.js";
import CardHeader from "material-dashboard-react/dist/components/Card/CardHeader.js";
import CardBody from "material-dashboard-react/dist/components/Card/CardBody.js";
<<<<<<< HEAD
// Moment
import moment from 'moment';
import dashboardStyle from "./dashboardStyle.jsx";

// Convert a date into the given format string
// If the date is invalid (usually because it is missing), return ""
function _formatDate(date, formatString){
  var dateObj = moment(date);
  if (dateObj.isValid()) {
    return dateObj.format(formatString)
  }
  return "";
};

// Find all patient data forms, and extract the given data
function getPatientData(){
  var contentNodes = window.Sling.getContent("/content/forms", 1, "");
  var patientData = [];
  for (var id in contentNodes) {
    var patient = contentNodes[id];
    if (patient["Disease"] !== "LFS")
      continue;
    var truncatedDOB = _formatDate(patient["Date of birth"], "YYYY-MM");
    var truncatedFollowUp = _formatDate(patient["Last follow-up"], "YYYY-MM-DD");
    var truncatedRegister = _formatDate(patient["Date registered"], "YYYY-MM-DD");
    patientData.push([patient["Patient ID"], truncatedDOB, truncatedFollowUp,
            truncatedRegister, patient["Sex"], patient["Tumor"], patient["Maternal Ethnicity"], patient["Paternal Ethnicity"]]);
  }
  return patientData;
}
=======

import dashboardStyle from "./dashboardStyle.jsx";
>>>>>>> 78bff40... LFS-34: UI for adding/removing users

class Dashboard extends React.Component {
  constructor(props) {
    super(props);

<<<<<<< HEAD
    const patientData = getPatientData();

    this.state = {
      title: "LFS Patients",
      subtitle: "",
      columnNames: ["ID", "Date of Birth", "Last Followup", "Date Registered", "Sex", "Tumour", "Maternal Ethnicity", "Paternal Ethnicity"],
      data: patientData
    };
  }

=======
    this.state = {
      title: "Patients",
      subtitle: "?!!",
      columnNames: ["ID", "Name", "TP53 status"],
      data: [
        ["1", "Alice", "WT"],
        ["2", "Bob", "WT"],
        ["3", "Charlie", "LOF"],
        ["4", "Eve", "NULL"]
      ]
    };
  }

  state = {
    value: 0
  };
  handleChange = (event, value) => {
    this.setState({ value });
  };

  handleChangeIndex = index => {
    this.setState({ value: index });
  };
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
  render() {
    const { classes } = this.props;
    return (
      <div>
<<<<<<< HEAD
        <Card>
          <CardHeader color="warning">
            <h4 className={classes.cardTitleWhite}>{this.state.title}</h4>
            <p className={classes.cardCategoryWhite}>{this.state.subtitle}</p>
          </CardHeader>
          <CardBody>
            <Table
              tableHeaderColor="warning"
              tableHead={this.state.columnNames}
              tableData={this.state.data}
            />
          </CardBody>
        </Card>
=======
        <GridContainer>
          <GridItem xs={12} sm={12} md={12}>
            <Card>
              <CardHeader color="warning">
                <h4 className={classes.cardTitleWhite}>{this.state.title}</h4>
                <p className={classes.cardCategoryWhite}>{this.state.subtitle}</p>
              </CardHeader>
              <CardBody>
                <Table
                  tableHeaderColor="warning"
                  tableHead={this.state.columnNames}
                  tableData={this.state.data}
                />
              </CardBody>
            </Card>
          </GridItem>
        </GridContainer>
>>>>>>> 78bff40... LFS-34: UI for adding/removing users
      </div>
    );
  }
}

Dashboard.propTypes = {
  classes: PropTypes.object.isRequired
};

export default withStyles(dashboardStyle)(Dashboard);
