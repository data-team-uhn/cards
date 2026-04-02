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

/**
 * Proof-of-concept tests for Form.jsx using Jest and React Testing Library.
 * This file is in modules/data-entry/src/main/frontend/src/test/ and is merged
 * into aggregated-frontend/src/main/frontend/src/test/ at build time.
 */
import { render, screen, waitFor } from '@testing-library/react';
import Form from '../questionnaire/Form.jsx';

// Mock ReLoginDialog so fetchWithReLogin is controllable
jest.mock('../login/ReLoginDialog.js', () => ({
  fetchWithReLogin: jest.fn(),
  GlobalLoginContext: {},
}));
import { fetchWithReLogin } from '../login/ReLoginDialog.js';

// Mock react-router (useNavigate, Link)
const mockNavigate = jest.fn();
jest.mock('react-router', () => {
  const React = require('react');
  return {
    useNavigate: () => mockNavigate,
    useBlocker: () => ({
      state: 'unblocked',
      proceed: jest.fn(),
      reset: jest.fn(),
    }),
    Link: ({ children, to, ...props }) => React.createElement('a', { href: to, ...props }, children),
    useLocation: () => ({ pathname: '/', search: '', hash: '' }),
  };
});

// Mock Form's heavy children so we don't pull in the full questionnaire tree
jest.mock('../questionnaire/FormEntry', () => ({
  __esModule: true,
  default: () => null,
  ENTRY_TYPES: ['cards:Question', 'cards:Section', 'cards:Information'],
}));
jest.mock('../questionnaire/FormPagination', () => () => null);
jest.mock('../questionnaire/SessionExpiryWarningModal.jsx', () => () => null);
jest.mock('../questionnaire/SubjectSelector', () => ({
  SelectorDialog: () => null,
  parseToArray: (x) => (Array.isArray(x) ? x : x ? [x] : []),
}));

beforeEach(() => {
  jest.clearAllMocks();
  Object.defineProperty(window, 'location', {
    value: {
      pathname: '/content.html/Forms/test-form-123',
      origin: 'http://localhost',
      href: 'http://localhost/content.html/Forms/test-form-123',
    },
    writable: true,
  });
});

describe('Form', () => {
  it('loads Form component and test setup works', () => {
    expect(Form).toBeDefined();
    // Form is wrapped with withStyles(), so it may be a function or a component object
    expect(typeof Form === 'function' || (typeof Form === 'object' && Form !== null)).toBe(true);
  });

  it('renders loading state initially', () => {
    fetchWithReLogin.mockImplementation(() => new Promise(() => {}));

    render(<Form />);

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('renders error state when fetch fails', async () => {
    fetchWithReLogin
      .mockImplementationOnce(() =>
        Promise.resolve({ text: () => Promise.resolve('false') }),
      )
      .mockImplementationOnce(() =>
        Promise.reject(
          Object.assign(new Error('Network error'), {
            status: 500,
            statusText: 'Internal Server Error',
          }),
        ),
      );

    render(<Form />);

    await waitFor(
      () => {
        expect(screen.getByText(/Error obtaining form data/i)).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
    expect(screen.getByText(/500/)).toBeInTheDocument();
  }, 5000);
});
