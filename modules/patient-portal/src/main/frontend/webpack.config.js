const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'LandingPage']: './src/patient-portal/LandingPage.jsx',
    [module_name + 'index']: './src/patient-portal/index.jsx',
    [module_name + 'unsubscribe']: './src/patient-portal/unsubscribe.jsx',
    [module_name + 'ToULink']: './src/patient-portal/ToULink.jsx',
    [module_name + 'UnsubscribeLink']: './src/patient-portal/UnsubscribeLink.jsx',
    [module_name + 'ClinicianVisit']: { 'import': './src/patient-portal/ClinicianVisit.jsx' },
    [module_name + 'ClinicianPatient']: { 'import': './src/patient-portal/ClinicianPatient.jsx' },
    [module_name + 'ClinicianForm']: { 'import': './src/patient-portal/ClinicianForm.jsx' },
    [module_name + 'ClinicForms']: { 'dependOn': ['cards-dataentry.LiveTable'], 'import': './src/patient-portal/ClinicForms.jsx' },
    [module_name + 'ClinicVisits']: { 'dependOn': ['cards-dataentry.LiveTable'], 'import': './src/patient-portal/ClinicVisits.jsx' },
    [module_name + 'ClinicDashboard']: { 'dependOn': ['cards-dataentry.Questionnaires', 'patient-portal.ClinicForms', 'patient-portal.ClinicVisits'], 'import': './src/patient-portal/ClinicDashboard.jsx' },
    [module_name + 'clinicIcon']: '@mui/icons-material/Event.js',
    [module_name + 'Clinics']: { 'dependOn': ['cards-dataentry.Questionnaires'], 'import': './src/patient-portal/Clinics.jsx' },
    [module_name + 'PrintHeader']: './src/patient-portal/PrintHeader.jsx',
    [module_name + 'PatientAccessConfiguration']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/patient-portal/PatientAccessConfiguration.jsx' },
    [module_name + 'PatientAccessConfigurationIcon']: '@mui/icons-material/MedicalInformation.js',
    [module_name + 'ToUConfiguration']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/patient-portal/ToUConfiguration.jsx' },
    [module_name + 'ToUConfigurationIcon']: '@mui/icons-material/Handshake.js',
    [module_name + 'SurveyInstructionsConfiguration']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/patient-portal/SurveyInstructionsConfiguration.jsx' },
    [module_name + 'SurveyInstructionsConfigurationIcon']: '@mui/icons-material/Quiz.js',
    [module_name + 'DashboardSettingsConfiguration']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/patient-portal/DashboardSettingsConfiguration.jsx' },
    [module_name + 'DashboardSettingsConfigurationIcon']: '@mui/icons-material/Dashboard.js'
  },
  plugins: [
    new CleanWebpackPlugin(),
    new WebpackAssetsManifest({
      output: "assets.json"
    })
  ],
  module: {
    rules: [
      {
        test: /\.(js|jsx)$/,
        exclude: /node_modules/,
        use: ['babel-loader']
      }
    ]
  },
  resolve: {
    extensions: ['*', '.js', '.jsx']
  },
  output: {
    path: __dirname + '/dist/SLING-INF/content/libs/cards/resources/',
    publicPath: '/',
    filename: '[name].[contenthash].js',
  }
};
