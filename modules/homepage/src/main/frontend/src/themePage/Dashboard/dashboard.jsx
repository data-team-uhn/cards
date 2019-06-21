// Taken from https://www.creative-tim.com/product/material-dashboard-react
import React from "react";
import PropTypes from "prop-types";
// @material-ui/core
import { withStyles } from "@material-ui/core/styles";
// core components
import GridItem from "material-dashboard-react/dist/components/Grid/GridItem.js";
import GridContainer from "material-dashboard-react/dist/components/Grid/GridContainer.js";
import Table from "material-dashboard-react/dist/components/Table/Table.js";
import Card from "material-dashboard-react/dist/components/Card/Card.js";
import CardHeader from "material-dashboard-react/dist/components/Card/CardHeader.js";
import CardBody from "material-dashboard-react/dist/components/Card/CardBody.js";
// Moment
import moment from 'moment';
import dashboardStyle from "./dashboardStyle.jsx";

function _parseDate(date, formatString){
  var dateObj = moment(date);
  if (dateObj.isValid()) {
    return dateObj.format(formatString)
  }
  return "";
};

class Dashboard extends React.Component {
  constructor(props) {
    super(props);

    // Find all relevant nodes
    var text = window.Sling.httpGet("/query?query=select%20*%20from%20[lfs:Form]").responseText;
    var contentNodes = JSON.parse(text);
    const patientData = [];
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

    this.state = {
      title: "LFS Patients",
      subtitle: "",
      columnNames: ["ID", "Date of Birth", "Last Followup", "Date Registered", "Sex", "Tumour", "Maternal Ethnicity", "Paternal Ethnicity"],
      data: patientData
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
  render() {
    const { classes } = this.props;
    return (
      <div>
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
        <div id="footer-container"></div>
      </div>
    );
  }
}

Dashboard.propTypes = {
  classes: PropTypes.object.isRequired
};

export default withStyles(dashboardStyle)(Dashboard);
