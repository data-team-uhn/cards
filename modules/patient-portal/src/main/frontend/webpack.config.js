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
    [module_name + 'ClinicDashboard']: './src/patient-portal/ClinicDashboard.jsx',
    [module_name + 'ClinicForms']: './src/patient-portal/ClinicForms.jsx',
    [module_name + 'ClinicVisits']: './src/patient-portal/ClinicVisits.jsx',
    [module_name + 'clinicIcon']: '@mui/icons-material/Event.js',
    [module_name + 'Clinics']: './src/patient-portal/Clinics.jsx',
    [module_name + 'PrintHeader']: './src/patient-portal/PrintHeader.jsx',
    [module_name + 'PatientIdentificationConfiguration']: './src/patient-portal/PatientIdentificationConfiguration.jsx',
    [module_name + 'PatientIdentificationConfigurationIcon']: '@mui/icons-material/MedicalInformation.js',
    [module_name + 'ToUConfiguration']: './src/patient-portal/ToUConfiguration.jsx',
    [module_name + 'ToUConfigurationIcon']: '@mui/icons-material/Handshake.js',
    [module_name + 'SurveyInstructionsConfiguration']: './src/patient-portal/SurveyInstructionsConfiguration.jsx',
    [module_name + 'SurveyInstructionsConfigurationIcon']: '@mui/icons-material/Quiz.js',
    [module_name + 'DashboardSettingsConfiguration']: './src/patient-portal/DashboardSettingsConfiguration.jsx',
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
