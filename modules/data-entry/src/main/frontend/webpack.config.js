const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'LiveTable']: './src/dataHomepage/LiveTable.jsx',
    [module_name + 'Questionnaires']: './src/dataHomepage/Questionnaires.jsx',
    [module_name + 'Subjects']: './src/dataHomepage/Subjects.jsx',
    [module_name + 'SubjectTypes']: './src/dataHomepage/SubjectTypes.jsx',
    [module_name + 'Forms']: './src/dataHomepage/Forms.jsx',
    [module_name + 'multipleChoice']: './src/questionnaire/MultipleChoice.jsx',
    [module_name + 'textQuestion']: './src/questionnaire/TextQuestion.jsx',
    [module_name + 'chromosomeQuestion']: './src/questionnaire/ChromosomeQuestion.jsx',
    [module_name + 'question']: './src/questionnaire/Question.jsx',
    [module_name + 'answer']: './src/questionnaire/Answer.jsx',
    [module_name + 'booleanQuestion']: './src/questionnaire/BooleanQuestion.jsx',
    [module_name + 'numberQuestion']: './src/questionnaire/NumberQuestion.jsx',
    [module_name + 'subjectsIcon']: '@material-ui/icons/AssignmentInd.js',
    [module_name + 'subjectTypeIcon']: '@material-ui/icons/Category.js',
    [module_name + 'questionnairesIcon']: '@material-ui/icons/Assignment.js',
    [module_name + 'formsIcon']: '@material-ui/icons/Description.js',
    [module_name + 'userDashboard']: './src/dataHomepage/UserDashboard.jsx',
    [module_name + 'FormView']: './src/dataHomepage/FormView.jsx',
    [module_name + 'SubjectView']: './src/dataHomepage/SubjectView.jsx',
    [module_name + 'NewFormDialog']: './src/dataHomepage/NewFormDialog.jsx',
    [module_name + 'SubjectSelector']: './src/questionnaire/SubjectSelector.jsx',
    [module_name + 'AdminStatistics']: './src/dataHomepage/AdminStatistics.jsx',
    [module_name + 'UserStatistics']: './src/dataHomepage/UserStatistics.jsx',
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
