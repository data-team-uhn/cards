module.exports = {
  mode: 'development',
  entry: {
    redirect: './src/dataQuery/redirect.js',
    showQuery: './src/dataQuery/query.js'
  },
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
    filename: '[name].js'
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
