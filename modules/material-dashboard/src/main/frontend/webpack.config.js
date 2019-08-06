const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'button']: './src/MaterialDashboardReact/CustomButtons/Button.jsx',
    [module_name + 'card']: './src/MaterialDashboardReact/Card/Card.jsx',
    [module_name + 'cardHeader']: './src/MaterialDashboardReact/Card/CardHeader.jsx',
    [module_name + 'cardBody']: './src/MaterialDashboardReact/Card/CardBody.jsx',
    [module_name + 'customInput']: './src/MaterialDashboardReact/CustomInput/CustomInput.jsx',
    [module_name + 'table']: './src/MaterialDashboardReact/Table/Table.jsx',
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
    filename: '[name].[contenthash].js',
    library: 'MaterialDashboard-[name]',
    libraryTarget: 'umd'
  },
  externals: [
    {
      "react": "React",
      "react-dom": "ReactDOM",
      "formik": "Formik",
      "lodash": "lodash",
      "prop-types": "PropTypes",
      "jss": "jss",
      "@material-ui/core": "MaterialUI"
    }
  ]
};
