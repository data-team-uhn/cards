/**
 * Mock for tests only. The real implementation lives in the login module
 * and is merged into aggregated-frontend at build time.
 */
export const fetchWithReLogin = jest.fn();
export const GlobalLoginContext = {
  Provider: ({ children }) => children,
  Consumer: ({ children }) => children(null),
};
