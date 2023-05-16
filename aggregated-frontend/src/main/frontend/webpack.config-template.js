/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

const RuntimeGlobals = require("webpack/lib/RuntimeGlobals");
const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const WebpackAssetsManifest = require('webpack-assets-manifest');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const TerserPlugin = require('terser-webpack-plugin');

/*
 * Webpack 5.25.0 changed how the code is generated to no longer return the module by default when eval-ing it.
 * Our dynamic UIX loading depends on this, so this is a simple library plugin that forces webpack to "return" the module.
 */
class ReturnModulePlugin {
  constructor() {
    this.pluginName = 'returnModule';
  }
  apply(compiler) {
    compiler.hooks.thisCompilation.tap(this.pluginName, compilation => {
      compilation.hooks.additionalChunkRuntimeRequirements.tap(
        this.pluginName,
        (chunk, set, { chunkGraph }) => {
          set.add(RuntimeGlobals.returnExportsFromRuntime);
        }
      );
      }
    );
  }
}

module_name = require("./package.json").name + ".";

const isProduction = process.argv.find(arg => arg.startsWith("--mode"))?.substring(7) == 'production';

module.exports = {
  mode: 'development',
  devtool: 'eval-cheap-module-source-map',
  cache: {
    type: 'filesystem'
  },
  entry: {
ENTRY_CONTENT
  },
  plugins: [
    new ReturnModulePlugin(),
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
      },
      {
        test:/\.css$/,
        use:['style-loader','css-loader']
      }
    ]
  },
  resolve: {
    extensions: ['*', '.js', '.jsx']
  },
  optimization: {
    usedExports: false,
    minimize: isProduction,
    minimizer: [
      new TerserPlugin({
        terserOptions: {
          mangle: {
            reserved: ['$super']
          }
        }
      })
    ],
    runtimeChunk: 'single',
    splitChunks: {
      chunks: 'all',
      cacheGroups: {
        defaultVendors: {
          minChunks: 1,
          minSize: 200,
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
    path: __dirname + '/dist/SLING-INF/content/libs/cards/resources/',
    publicPath: '/',
    filename: '[name].[contenthash].js',
  }
};
