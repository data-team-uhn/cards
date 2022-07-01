const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'PromsLandingPage']: './src/proms/PromsLandingPage.jsx',
    [module_name + 'index']: './src/proms/index.jsx',
    [module_name + 'unsubscribe']: './src/proms/unsubscribe.jsx',
    [module_name + 'ToULink']: './src/proms/ToULink.jsx',
    [module_name + 'PromsDashboard']: './src/proms/PromsDashboard.jsx',
    [module_name + 'PromsView']: './src/proms/PromsView.jsx',
    [module_name + 'VisitView']: './src/proms/VisitView.jsx',
    [module_name + 'clinicIcon']: '@mui/icons-material/Event.js',
    [module_name + 'Clinics']: './src/proms/Clinics.jsx',
    [module_name + 'PrintHeader']: './src/proms/PrintHeader.jsx',
    [module_name + 'WelcomeMessageConfiguration']: './src/proms/WelcomeMessageConfiguration.jsx',
    [module_name + 'WelcomeMessageConfigurationIcon']: '@mui/icons-material/ChromeReaderMode.js',
    [module_name + 'ToUConfiguration']: './src/proms/ToUConfiguration.jsx',
    [module_name + 'ToUConfigurationIcon']: '@mui/icons-material/Handshake.js'
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
