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

import { fileURLToPath } from 'url';
import { dirname } from 'path';
import RuntimeGlobals from "webpack/lib/RuntimeGlobals.js";
import { CleanWebpackPlugin } from "clean-webpack-plugin";
import { WebpackAssetsManifest } from "webpack-assets-manifest";
import TerserPlugin from "terser-webpack-plugin";
import ESLintPlugin from "eslint-webpack-plugin";

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

import packageJson from "./package.json" with { type: "json" };

const module_name = packageJson.name + ".";
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const isProduction = process.argv.find(arg => arg.startsWith("--mode"))?.substring(7) == 'production';

export default {
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
    }),
	new ESLintPlugin({
      extensions: ['js', 'jsx', 'ts', 'tsx'],
      emitWarning: true,   // show warnings in console but don’t fail build
      failOnError: false,  // set true if you want to break build on lint error
    }),
  ],
  module: {
    rules: [
      {
        test: /\.(js|jsx|ts|tsx)$/,
        exclude: /node_modules/,
        resolve: { fullySpecified: false }, // disable ESM fully specified
        use: ['babel-loader']
      },
      {
        test:/\.css$/,
        use:['style-loader','css-loader']
      }
    ]
  },
  resolve: {
    extensions: ['.*', '.js', '.jsx', '.ts', '.tsx']
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
