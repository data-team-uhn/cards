const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'index']: './src/proms/index.jsx',
    [module_name + 'unsubscribe']: './src/proms/unsubscribe.jsx',
    [module_name + 'ToULink']: './src/proms/ToULink.jsx'
    [module_name + 'PromsDashboard']: './src/proms/PromsDashboard.jsx',
    [module_name + 'PromsView']: './src/proms/PromsView.jsx',
    [module_name + 'VisitView']: './src/proms/VisitView.jsx',
    [module_name + 'pmccIcon']: '@material-ui/icons/Event.js',
    [module_name + 'clinicIcon']: '@material-ui/icons/Event.js',
    [module_name + 'Clinics']: './src/proms/Clinics.jsx'
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
