/*!
=========================================================
* Material Dashboard React - v1.7.0
=========================================================
* Product Page: https://www.creative-tim.com/product/material-dashboard-react
* Copyright 2019 Creative Tim (https://www.creative-tim.com)
* Licensed under MIT (https://github.com/creativetimofficial/material-dashboard-react/blob/master/LICENSE.md)
* Coded by Creative Tim
=========================================================
* The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
*/
import React from "react";
import PropTypes from "prop-types";
// @material-ui/core
import { withStyles } from "@material-ui/core";
// core components
import {Card, CardHeader, CardBody, Table} from "MaterialDashboardReact";
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

class Dashboard extends React.Component {
  constructor(props) {
    super(props);

    const patientData = getPatientData();

    this.state = {
      title: "LFS Patients",
      subtitle: "",
      columnNames: ["ID", "Date of Birth", "Last Followup", "Date Registered", "Sex", "Tumour", "Maternal Ethnicity", "Paternal Ethnicity"],
      data: patientData
    };
  }

  render() {
    const { classes } = this.props;
    return (
      <div>
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
      </div>
    );
  }
}

Dashboard.propTypes = {
  classes: PropTypes.object.isRequired
};

export default withStyles(dashboardStyle)(Dashboard);
