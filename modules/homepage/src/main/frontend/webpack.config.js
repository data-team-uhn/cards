const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
    [module_name + 'themeindex']: './src/themePage/index.jsx',
    [module_name + 'dashboard']: './src/themePage/Dashboard/dashboard.jsx'
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
  },
  externals: [
    {
      "moment": "moment",
      "react": "React",
      "react-dom": "ReactDOM",
      "react-router-dom": "ReactRouterDOM",
      "formik": "Formik",
      "lodash": "lodash",
      "prop-types": "PropTypes",
      "jss": "jss",
      "@material-ui/core": "MaterialUI",
      "MaterialDashboardReact/Card/Card": "window['MaterialDashboard-lfs-material-dashboard.card'].default",
      "MaterialDashboardReact/Card/CardHeader": "window['MaterialDashboard-lfs-material-dashboard.cardHeader'].default",
      "MaterialDashboardReact/Card/CardBody": "window['MaterialDashboard-lfs-material-dashboard.cardBody'].default",
      "MaterialDashboardReact/CustomButtons/Button": "window['MaterialDashboard-lfs-material-dashboard.button'].default",
      "MaterialDashboardReact/CustomInput/CustomInput": "window['MaterialDashboard-lfs-material-dashboard.customInput'].default",
      "MaterialDashboardReact/Table/Table": "window['MaterialDashboard-lfs-material-dashboard.table'].default",
    }
  ]
};
