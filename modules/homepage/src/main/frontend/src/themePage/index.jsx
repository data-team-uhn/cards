//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//
import { StrictMode, Suspense, useState, useEffect } from "react";

import createCache from "@emotion/cache";
import { CacheProvider } from "@emotion/react";
import { ThemeProvider } from '@mui/material/styles';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider, Routes, Route, Navigate } from "react-router";
import { withStyles } from 'tss-react/mui';

import PageStart from "../PageStart";
import { getRoutes } from '../routes';
import { appTheme } from "../themePalette.jsx";
import appStyles from "./indexStyles.jsx";
import Navbar from "./Navbars/Navbar";
import Page from "./Page";
import Sidebar from "./Sidebar/Sidebar.jsx"
import ReLoginDialog, { GlobalLoginContext } from "../login/ReLoginDialog.js";


function Main(props) {
  const { classes, ...rest } = props;

  let [ contentOffset, setContentOffset ] = useState(0);
  let [ mobileOpen, setMobileOpen ] = useState(false);
  let [ routes, setRoutes ] = useState([]);
  let [ reLoginDialogOpen, setReLoginDialogOpen ] = useState(false);
  let [ loginHandlers, setLoginHandlers ] = useState([]);

  const image = document.querySelector('meta[name="sidebarBackground"]').content;
  const docTitle = document.querySelector('meta[name="title"]').content;
  const color = document.querySelector('meta[name="themeColor"]')?.content || "blue";

  useEffect(() => {
    getRoutes().then(response => setRoutes(response));
  }, []);

  // Close the mobile menu if the window size changes
  // so that the mobile menu is out of place
  let autoCloseMobileMenus = (event) => {
    if (window.innerWidth >= appTheme.breakpoints.values.md) {
      setMobileOpen(false);
    }
  }

  // Register/unregister autoCloseMobileMenus to window resizing
  useEffect(() => {
    window.addEventListener("resize", autoCloseMobileMenus);
    return () => {
      window.removeEventListener("resize", autoCloseMobileMenus);
    };
  }, []);

  let getRenderElement = (route) => {
    let ThisComponent = route["cards:extensionRender"];
    let title = " | " + docTitle;
    return (
      <Page title={title} pageDefaultName={route["cards:extensionName"]}>
        <ThisComponent contentOffset={contentOffset} extension={route} />
      </Page>
    );
  };

  let switchRoutes = () => {
    return (<Routes>
      {routes.map((route, key) => {
        return (
          <Route
            path={route["cards:targetURL"]}
            element={getRenderElement(route)}
            key={key}
          />
        );
      })}
    </Routes>)
  };

  let handleDrawerToggle = () => {
    setMobileOpen(prevState => !prevState);
  };

  return (
    <>
      <GlobalLoginContext.Provider
        value={{
          dialogOpen: (loginHandlerFcn, discardOnFailure) => {
            let handler = ((success) => {
              success && setReLoginDialogOpen(false);
              success && loginHandlerFcn();
            });
            !reLoginDialogOpen && setReLoginDialogOpen(true);
            let shouldAddHandler = !discardOnFailure || loginHandlers.length < 1;
            shouldAddHandler && setLoginHandlers(prevState => prevState.concat(handler));
          },
          getDialogOpenStatus: () => reLoginDialogOpen
        }}
      >
        <PageStart
          setTotalHeight={(th) => {
            if (contentOffset != th) {
              setContentOffset(th);
            }
          }
          }
        />
        <ReLoginDialog
          isOpen={reLoginDialogOpen}
          handleLogin={(success) => {
            if (success) {
              loginHandlers.forEach(handler => handler(success));
              setLoginHandlers([]);
            }
          }}
        />
        <div className={classes.wrapper} style={ { position: 'relative', top: contentOffset + 'px' } }>
          <Suspense fallback={<div>Loading...</div>}>
            <Sidebar
              contentOffset={contentOffset}
              logoImage={document.querySelector('meta[name="logoDark"]').content}
              image={image}
              handleDrawerToggle={handleDrawerToggle}
              open={mobileOpen}
              color={color}
              {...rest}
            />
            <div className={classes.mainPanel} id="main-panel">
              <div className={classes.content}>
                <div className={classes.container}>
                  {switchRoutes()}
                </div>
              </div>
              <Navbar
                routes={routes}
                handleDrawerToggle={handleDrawerToggle}
                color={color}
                {...rest}
              />
            </div>
          </Suspense>
        </div>
      </GlobalLoginContext.Provider>
    </>
  );
}

const MainComponent = withStyles(Main, appStyles);

const router = createBrowserRouter([
  {
    path: "/",
    element: <Navigate to="/content.html/Questionnaires/User" replace />,
  },
  {
    path: "/content",
    element: <Navigate to="/content.html/Questionnaires/User" replace />,
  },
  {
    path: "*",
    element: <MainComponent />,
  },
]);

const cache = createCache({
  key: 'tss',
  // Enable style speedy insertion mode
  speedy: true
});

const root = createRoot(document.querySelector('#main-container'));
root.render(
  <StrictMode>
    <CacheProvider value={cache}>
      <ThemeProvider theme={appTheme}>
        <RouterProvider router={router} />
      </ThemeProvider>
    </CacheProvider>
  </StrictMode>
);

export default MainComponent;
