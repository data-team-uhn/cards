const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'statsIcon']: '@mui/icons-material/BarChart.js',
    [module_name + 'AdminStatistics']: { 'dependOn': ['cards-dataentry.Forms', 'cards-dataentry.Questionnaires'], 'import': './src/Statistics/AdminStatistics.jsx' },
    [module_name + 'UserStatistics']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/Statistics/UserStatistics.jsx' },
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
