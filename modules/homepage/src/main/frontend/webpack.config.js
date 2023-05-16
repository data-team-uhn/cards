const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'themeindex']: './src/themePage/index.jsx',
    [module_name + 'modelOrganismsIcon']: '@mui/icons-material/Pets.js',
    [module_name + 'variantsIcon']: '@mui/icons-material/Subtitles.js',
    [module_name + 'adminIcon']: '@mui/icons-material/Settings.js',
    [module_name + 'adminDashboard']: './src/adminDashboard/AdminDashboard.jsx',
    [module_name + 'QuickSearchResults']: { 'dependOn': ['cards-dataentry.Forms'], 'import': './src/themePage/QuickSearchResults.jsx' },
    [module_name + 'QuickSearchConfigurationIcon']: '@mui/icons-material/Pageview.js',
    [module_name + 'QuickSearchConfiguration']: { 'dependOn': ['cards-login.loginDialogue'], 'import': './src/themePage/QuickSearchConfiguration' },
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
