const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'LiveTable']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/dataHomepage/LiveTable.jsx' },
    [module_name + 'Forms']: { 'dependOn': ['cards-dataentry.LiveTable', 'cards-pedigree.Pedigree'], 'import': './src/dataHomepage/Forms.jsx' },
    [module_name + 'Questionnaires']: { 'dependOn': ['cards-dataentry.Forms'], 'import': './src/dataHomepage/Questionnaires.jsx' },
    [module_name + 'Subjects']: { 'dependOn': ['cards-dataentry.LiveTable', 'cards-dataentry.Forms'], 'import': './src/dataHomepage/Subjects.jsx' },
    [module_name + 'SubjectTypes']: { 'dependOn': ['cards-dataentry.LiveTable'], 'import': './src/dataHomepage/SubjectTypes.jsx' },
    [module_name + 'subjectsIcon']: '@mui/icons-material/AssignmentInd.js',
    [module_name + 'subjectTypeIcon']: '@mui/icons-material/Category.js',
    [module_name + 'questionnairesIcon']: '@mui/icons-material/Assignment.js',
    [module_name + 'formsIcon']: '@mui/icons-material/Description.js',
    [module_name + 'FormView']: { 'dependOn': ['cards-dataentry.Forms'], 'import': './src/dataHomepage/FormView.jsx' },
    [module_name + 'SubjectView']: { 'dependOn': ['cards-dataentry.Subjects'], 'import': './src/dataHomepage/SubjectView.jsx' },
    [module_name + 'SubjectSelector']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/questionnaire/SubjectSelector.jsx' },
    [module_name + 'NewFormDialog']: { 'dependOn': ['cards-login.loginDialogue', 'cards-dataentry.SubjectSelector'], 'import': './src/dataHomepage/NewFormDialog.jsx' },
    [module_name + 'userDashboard']: { 'dependOn': ['cards-login.loginDialogue', 'cards-dataentry.NewFormDialog', 'cards-dataentry.SubjectSelector'], 'import': './src/dataHomepage/UserDashboard.jsx' },
    [module_name + 'GoogleApiKey']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/questionnaireEditor/googleApiKeyAdminPage.jsx' },
    [module_name + 'googleIcon']: '@mui/icons-material/Google',
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
    filename: '[name].[contenthash].js'
  }
};
