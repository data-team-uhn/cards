/**
 * Jest config for frontend tests.
 * Tests run from aggregated-frontend after webpack_script.py has merged all
 * module sources into src/main/frontend/src. Roots point to that src tree.
 */
/** @type {import('jest').Config} */
module.exports = {
  testEnvironment: 'jsdom',
  roots: ['<rootDir>/src'],
  modulePaths: ['<rootDir>/node_modules'],

  // Where tests live - in src/test/ directory
  testMatch: ['<rootDir>/src/test/**/*.test.{js,jsx,ts,tsx}'],
  moduleFileExtensions: ['js', 'jsx', 'ts', 'tsx', 'json'],

  // jsdom + jest-dom matchers, etc.
  setupFilesAfterEnv: ['<rootDir>/jest.setup.js'],

  // Babel transform for JS/TS/JSX/TSX based on "babel" config from package.json
  transform: {
    '^.+\\.(js|jsx|ts|tsx)$': ['babel-jest', { configFile: './babel.config.jest.js' }],
  },

  // Allows specific packages through Babel
  transformIgnorePatterns: ['/node_modules/(?!(@mui|@emotion|tss-react)/)'],

  // Handle CSS imports and static assets in components
  moduleNameMapper: {
    '\\.css$': '<rootDir>/__mocks__/styleMock.js',
    '^(\\.\\./)+propTypes$': '<rootDir>/__mocks__/propTypes.jsx',
    '^\\.\\./components/(.+)$': '<rootDir>/__mocks__/components/$1',
  },
  testEnvironmentOptions: {
    url: 'http://localhost/Forms/test-form-id',
  },

  testTimeout: 10000,

  // Useful noise reduction
  clearMocks: true,
  restoreMocks: false,
};
