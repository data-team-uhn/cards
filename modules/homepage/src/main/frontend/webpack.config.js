const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'themeindex']: './src/themePage/index.jsx',
    [module_name + 'modelOrganismsIcon']: '@material-ui/icons/Pets.js',
    [module_name + 'variantsIcon']: '@material-ui/icons/Subtitles.js',
    [module_name + 'adminIcon']: '@material-ui/icons/Settings.js',
    [module_name + 'adminDashboard']: './src/adminDashboard/AdminDashboard.jsx',
    [module_name + 'QuickSearchResults']: './src/themePage/QuickSearchResults.jsx',
    [module_name + 'QuickSearchConfigurationIcon']: '@material-ui/icons/Pageview.js',
    [module_name + 'QuickSearchConfiguration']: './src/themePage/QuickSearchConfiguration',
    [module_name + 'DowntimeWarningConfigurationIcon']: '@material-ui/icons/Announcement.js',
    [module_name + 'DowntimeWarningConfiguration']: './src/themePage/DowntimeWarningConfiguration',
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
