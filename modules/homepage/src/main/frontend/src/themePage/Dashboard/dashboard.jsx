// Taken from https://www.creative-tim.com/product/material-dashboard-react
import React from "react";
import PropTypes from "prop-types";
// @material-ui/core
import { withStyles } from "@material-ui/core/styles";
// material-dashboard-react
import Table from "material-dashboard-react/dist/components/Table/Table.js";
import Card from "material-dashboard-react/dist/components/Card/Card.js";
import CardHeader from "material-dashboard-react/dist/components/Card/CardHeader.js";
import CardBody from "material-dashboard-react/dist/components/Card/CardBody.js";
// Moment
import moment from 'moment';
import dashboardStyle from "./dashboardStyle.jsx";

// Convert a date into the given format string
// If the date is invalid (usually because it is missing), return ""
function _parseDate(date, formatString){
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
    var truncatedDOB = _parseDate(patient["Date of birth"], "YYYY-MM");
    var truncatedFollowUp = _parseDate(patient["Last follow-up"], "YYYY-MM-DD");
    var truncatedRegister = _parseDate(patient["Date registered"], "YYYY-MM-DD");
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
