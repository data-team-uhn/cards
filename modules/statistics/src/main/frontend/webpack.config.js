const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'subjectsIcon']: '@material-ui/icons/AssignmentInd.js',
    [module_name + 'subjectTypeIcon']: '@material-ui/icons/Category.js',
    [module_name + 'AdminStatisticsAgain']: './src/Statistics/AdminStatisticsAgain.jsx',
    [module_name + 'UserStatisticsAgain']: './src/Statistics/UserStatisticsAgain.jsx'
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
    path: __dirname + '/dist/SLING-INF/content/libs/lfs/resources/',
    publicPath: '/',
    filename: '[name].[contenthash].js'
  }
};
