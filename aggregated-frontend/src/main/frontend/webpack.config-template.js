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

import { WebpackAssetsManifest } from 'webpack-assets-manifest';
import MinimizerPlugin from 'minimizer-webpack-plugin';
import ESLintPlugin from 'eslint-webpack-plugin';
import { defineReactCompilerLoaderOption, reactCompilerLoader } from 'react-compiler-webpack';

import path from 'path';
import { fileURLToPath } from 'url';
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const isProduction = process.argv.find(arg => arg.startsWith("--mode"))?.substring(7) == 'production';

/**
 * Helper function to format and log React Compiler events
 * @param {string} filename - The full file path
 * @param {object} event - The compiler event object
 */
function logCompilerEvent(filename, event) {
  if (!event) return;
  const filePath = filename ?? event.filename ?? event.file ?? event.path ?? "(unknown file)";
  const fileName = filePath.replace(/^.*[\\/]/, '');

  const ANSI = {
    reset: '\x1b[0m',
    bold: '\x1b[1m',
    red: '\x1b[31m',
    yellow: '\x1b[33m',
    green: '\x1b[32m',
    cyan: '\x1b[36m',
    gray: '\x1b[90m',
  };

  const kind = event.kind;

  if (kind !== 'CompileError' && kind !== 'CompileSkip') return;

  const color =
    kind === 'CompileError' ? ANSI.red :
    kind === 'CompileSkip'  ? ANSI.yellow :
    ANSI.cyan;

  const sep = `${ANSI.gray}${'-'.repeat(70)}${ANSI.reset}`;
  // If it's a skip/error but has no details, still log a minimal line
  console.log(sep);
  console.log(`${ANSI.bold}${color}[React Compiler] ${kind} ${fileName}${ANSI.reset}`);

  const options = event.detail?.options;
  if (options) {
    const reason = options.reason;
    const category = options.category;
    const desc = options.description;
    const suggestions = options.suggestions;
    const message = options.details?.[0]?.message;
    const loc = options.loc ? options.loc : options.details[0].loc;

    if (reason || category) console.log(`[${category || "-"}]: ${reason || "-"}`);
    if (message) console.log(`Message: ${message}`);
    if (desc) console.log(`Description: ${desc}`);
    if (suggestions) console.log('Suggestions:', suggestions);
    if (loc) console.log(`${ANSI.bold}Location: Line ${loc.start.line}, Column ${loc.start.column}, identifierName ${loc.identifierName || "-"}${ANSI.reset}`);
  }
}

export default (env) => {
  return {
    experiments: {
      outputModule: true,
    },
    mode: 'development',
    devtool: 'source-map',
    cache: {
      type: 'filesystem',
      // any change here invalidates the cache
      version: String(Date.now())
    },
    infrastructureLogging: {
      level: 'error' // Mask Webpack infrastructure-level warnings to silence warning when React Compiler errors on serialisation of Webpack’s persistent cache
    },
    entry: {
ENTRY_CONTENT
    },
    plugins: [
      new WebpackAssetsManifest({
        output: "assets.json"
      }),
      !env.quick && new ESLintPlugin({
        extensions: ['js', 'jsx', 'ts', 'tsx'],
        emitWarning: false,   // Show warnings in ESLint output, not as webpack warnings
        failOnError: true,  // Break build on ESLint error
      }),
    ],
    module: {
      rules: [
        {
          test: /\.(js|jsx|ts|tsx)$/,
          exclude: /node_modules/,
          resolve: { fullySpecified: false }, // disable ESM fully specified
          use: [
            { loader: 'babel-loader' },
            {
              loader: reactCompilerLoader,
              options: defineReactCompilerLoaderOption({
                compilationMode : 'annotation',
                logger: {
                  logEvent(filename, event) {
                    logCompilerEvent(filename, event);
                  }
                }
              })
            }
          ]
        },
        {
          test:/\.css$/,
          use:['style-loader','css-loader']
        },
        // Serve the PDF.js worker as a static asset (used by proposal file validation).
        // pdfjsLib.GlobalWorkerOptions.workerSrc gets this generator's URL directly, so it
        // needs the real deployed path here rather than the global publicPath ('/'), which
        // only resolves correctly when a resource is looked up server-side via assets.json.
        {
          test: /pdf\.worker(\.min)?\.mjs$/,
          type: 'asset/resource',
          generator: {
            filename: 'pdf.worker.[contenthash][ext]',
            publicPath: '/libs/cards/resources/'
          }
        }
      ]
    },
    resolve: {
      extensions: ['.*', '.js', '.jsx', '.ts', '.tsx']
    },
    optimization: {
      usedExports: true,
      minimize: isProduction,
      minimizer: [
        new MinimizerPlugin({
          minimizerOptions: {
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
            minSize: 1000,
            name: false,
            priority: -20,
            reuseExistingChunk: true
          }
        }
      }
    },
    output: {
      clean: true,
      library: {
        type: "modern-module",
      },
      path: __dirname + '/dist/SLING-INF/content/libs/cards/resources/',
      publicPath: '/',
      filename: '[name].[contenthash].js',
    }
  }
};
