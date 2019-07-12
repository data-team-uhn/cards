const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module.exports = {
  mode: 'development',
  entry: {
    redirect: './src/dataQuery/redirect.js',
    showQuery: './src/dataQuery/query.js'
  },
  plugins: [
    new CleanWebpackPlugin(),
    new WebpackAssetsManifest({
      output: "data-entry.json"
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
