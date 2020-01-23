const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');

module_name = require("./package.json").name + ".";

module.exports = {
  mode: 'development',
  entry: {
ENTRY_CONTENT
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
  optimization: {
    runtimeChunk: 'single',
    splitChunks: {
      chunks: 'all',
      cacheGroups: {
        defaultVendors: {
          minChunks: 2,
          minSize: 20000000,
          filename: '[name].[contenthash].bundle.js',
          test: /[\\/]node_modules[\\/]/,
          name: 'vendor',
          enforce: true,
          priority: -10
        },
        default: {
          minChunks: 2,
          minSize: 10000000,
          name: false,
          priority: -20,
          reuseExistingChunk: true
        }
      }
    }
  },
  output: {
    path: __dirname + '/dist/SLING-INF/content/libs/lfs/resources/',
    publicPath: '/',
    filename: '[name].[contenthash].js',
  }
};