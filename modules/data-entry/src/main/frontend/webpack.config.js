const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'redirect']: './src/dataQuery/redirect.js',
    [module_name + 'showQuery']: './src/dataQuery/query.js'
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
    library: 'dataQuery',
    filename: '[name].[contenthash].js'
  },
  externals: [
    {
      "react": "React",
      "react-dom": "ReactDOM",
      "lodash": "lodash",
      "prop-types": "PropTypes",
      "@material-ui/core": "MaterialUI"
    }
  ]
};
