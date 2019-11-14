const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');
module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'extensionPoint']: './src/uiextension/extensionPoint.jsx'
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
    filename: '[name].js',
  },
  externals: [
    {
      "react": "React",
      "react-dom": "ReactDOM",
      "formik": "Formik",
      "lodash": "lodash",
      "prop-types": "PropTypes",
      "jss": "jss",
      "@material-ui/core": "window['MaterialUI']"
    }
  ]
};
