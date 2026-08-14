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

/*
 * The end-to-end sources are held to the same standard as the application frontend, so this is
 * aggregated-frontend's eslint.config.js with the parts that only exist for React removed: there is no
 * JSX here, no component to check hooks or accessibility on. The rule set that remains -- import order,
 * unused imports, whitespace, complexity, code style -- is deliberately identical, down to the options,
 * so that a rule tightened over there is worth tightening here too.
 *
 * It is a separate configuration rather than a shared one because the two packages have separate
 * dependency trees: this one is installed on its own, without the frontend's plugins, and reaching across
 * to the frontend's node_modules would tie a test package to the build order of an unrelated module.
 *
 * Named .mjs, where the frontend's is .js, because this package is not declared "type": "module" -- and
 * should not be, since that is also how Playwright decides whether to load the specs as ES modules.
 */

import js from "@eslint/js";
import stylistic from "@stylistic/eslint-plugin";
import tsPlugin from "@typescript-eslint/eslint-plugin";
import tsParser from "@typescript-eslint/parser";
import { defineConfig } from "eslint/config";
import importPlugin from "eslint-plugin-import";
import unusedImports from "eslint-plugin-unused-imports";
import globals from "globals";

const importOrderRule = [
  "error",
  {
    groups: ["builtin", "external", "internal", ["parent", "sibling", "index"]],
    "newlines-between": "always",
    alphabetize: { order: "asc", caseInsensitive: true },
  },
];

const commonRules = {
  ...js.configs.recommended.rules,

  "import/order": importOrderRule,

  // unused-related rules
  "no-unused-vars": ["error", { args: "none", caughtErrors: "none" }],
  "no-undef": "off",
  "no-extra-boolean-cast": "off",
  "unused-imports/no-unused-imports": "error",

  // whitespace rules
  "@stylistic/indent": ["error", 2, { SwitchCase: 1 }],
  "no-tabs": "error",
  "no-mixed-spaces-and-tabs": ["error", "smart-tabs"],
  "linebreak-style": "off",
  "object-curly-spacing": ["error", "always"],
  "@stylistic/no-trailing-spaces": "error",
  "@stylistic/eol-last": ["error", "always"],

  // complexity rules
  "max-nested-callbacks": ["error", 3],

  // codestyle rules
  "@stylistic/max-len": ["error", { code: 120, ignoreUrls: true, ignoreStrings: true, ignoreComments: true, ignoreTemplateLiterals: true }],
  "@stylistic/no-extra-semi": "error",
};

const commonPlugins = {
  "unused-imports": unusedImports,
  import: importPlugin,
  "@stylistic": stylistic,
};

const commonLinterOptions = {
  reportUnusedInlineConfigs: "error",
};

export default defineConfig([
  {
    ignores: [
      "node_modules/**",
      // Everything Playwright writes when it is pointed somewhere other than target/
      "test-results/**",
      "playwright-report/**",
      "blob-report/**",
      ".playwright/**",
    ],
  },

  // The tests themselves, and the page objects they drive the browser through
  {
    files: ["**/*.ts"],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaVersion: "latest",
        sourceType: "module",
        project: "./tsconfig.json",
      },
      globals: { ...globals.node },
    },
    plugins: {
      ...commonPlugins,
      "@typescript-eslint": tsPlugin,
    },
    rules: commonRules,
    linterOptions: commonLinterOptions,
  },

  // `test.describe > test > test.step > callback` is one level past the shared limit before a test has
  // asserted anything
  {
    files: ["specs/**/*.spec.ts"],
    rules: {
      "max-nested-callbacks": ["error", 5],
    },
  },

  // The one script that runs under bare Node rather than through Playwright's transpiler, so it is
  // JavaScript and outside the TypeScript project
  {
    files: ["**/*.mjs"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      // nodeBuiltin rather than node: this is an ES module, so the CommonJS-only globals
      // (`require`, `module`, `__dirname`) are genuinely absent and should still be reported.
      globals: globals.nodeBuiltin,
    },
    plugins: commonPlugins,
    rules: commonRules,
    linterOptions: commonLinterOptions,
  },
]);
