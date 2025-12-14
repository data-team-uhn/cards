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

import js from "@eslint/js";
import { defineConfig } from "eslint/config";
import tsParser from "@typescript-eslint/parser";
import tsPlugin from "@typescript-eslint/eslint-plugin";
import react from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";
import importPlugin from "eslint-plugin-import";
import jsxA11y from "eslint-plugin-jsx-a11y";
import globals from "globals";
import unusedImports from "eslint-plugin-unused-imports";
import stylistic from "@stylistic/eslint-plugin";

// For ESLint rules specs see  https://eslint.org/docs/latest/rules/

// for compiler to avoid build error for globals having heading or trailing whitespace
const cleanGlobals = Object.fromEntries(
  Object.entries({ ...globals.browser, ...globals.es2021 }).map(
    ([key, value]) => [key.trim(), value]
  )
);

// --- Shared parts defined here for maximum clarity and DRYness (Don’t Repeat Yourself) ---

const commonGlobals = {
  ...cleanGlobals,
  process: "readonly",
  module: "readonly"
};

const commonPlugins = {
  react,
  "react-hooks": reactHooks,
  "unused-imports": unusedImports,
  import: importPlugin,
  "@stylistic": stylistic,
  "jsx-a11y": jsxA11y,
};

const commonReactSettings = { react: { version: "detect" } };

const importOrderRule = [
  "error",
  {
    groups: ["builtin", "external", "internal", ["parent", "sibling", "index"]],
    pathGroups: [{ pattern: "react", group: "external", position: "before" }],
    pathGroupsExcludedImportTypes: ["react"],
    "newlines-between": "always",
    alphabetize: { order: "asc", caseInsensitive: true },
  },
];

const commonRules = {
  // extend recommended rules via spreading, custom rules below will override them
  ...js.configs.recommended.rules,
  ...react.configs.recommended.rules,
  ...jsxA11y.configs.recommended.rules,

  "import/order": importOrderRule,

  // React rules
  "react/jsx-uses-react": "off",
  "react/react-in-jsx-scope": "off",
  "react/prop-types": "off",
  "react-hooks/rules-of-hooks": "error",

  // ununsed-related rules
  "no-unused-vars": ["error", { args: "none", caughtErrors: "none" }],
  "no-undef": "off",
  "no-extra-boolean-cast": "off",
  "unused-imports/no-unused-imports": "error",
  "react/no-unused-prop-types": "error",

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
  "@stylistic/max-len": ["error", { code: 120, ignoreUrls: true, ignoreStrings: true, ignoreComments: true }],

  "jsx-a11y/no-autofocus": "off",
};

const commonLinterOptions = {
  reportUnusedInlineConfigs: "error",
};

const commonParserOptions = {
  ecmaVersion: "latest",
  sourceType: "module",
  ecmaFeatures: { jsx: true },
};

const commonConfigs = {
  settings: commonReactSettings,
  rules: commonRules,
  linterOptions: commonLinterOptions,
};

// --- Main config ---

export default defineConfig([
  // Ignore folders/files
  {
    ignores: [
      "dist/**",
      "node/**",
      "node_modules/**",
      "webpack.config.js",
      "webpack.config-template.js",
      "src/pedigree/**",
    ],
  },

  // JS / JSX
  {
    files: ["src/**/*.{js,jsx}"],
    languageOptions: {
      parserOptions: commonParserOptions,
      globals: commonGlobals,
    },
    plugins: {
      ...commonPlugins,
    },
    ...commonConfigs,
  },

  // TS / TSX
  {
    files: ["src/**/*.{ts,tsx}"],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ...commonParserOptions,
        project: "./tsconfig.json",
      },
      globals: commonGlobals,
    },
    plugins: {
      ...commonPlugins,
	  "@typescript-eslint": tsPlugin,
    },
    ...commonConfigs,
  },
]);
