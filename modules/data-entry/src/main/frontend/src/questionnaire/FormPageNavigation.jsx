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

import React, { useState, useEffect } from "react";

import {
  Fab,
  Grid,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Tooltip,
} from "@mui/material";

import CheckIcon from '@mui/icons-material/Check';
import WarningIcon from '@mui/icons-material/Warning';

import { useTheme } from '@mui/material/styles';
import useMediaQuery from '@mui/material/useMediaQuery';

import PropTypes from "prop-types";

import FormattedText from "../components/FormattedText";
import QuestionnaireStyle from "./QuestionnaireStyle";

/**
 * Component that enables the navigation between different pages of a Form. Used in FormPagination when the paginationVariant is "navigable".
 */
function FormPageNavigation (props) {
  const { pages, activePage, saveButton, backButton, isPageCompleted, navigateTo } = props;

  const [pageSelectorAnchorEl, setPageSelectorAnchorEl] = useState();

  const theme = useTheme();
  const condensedPageList = useMediaQuery(theme.breakpoints.down('lg'));

  // -------------------------------------------------------------------------------
  // Rendering helpers
  //

  // Page icon
  // Previous pages that are enabled are marked with a checkmark if completed, and with a warning sign if incomplete
  let pageIcon = (page, index) => (
    index < activePage && page.canBeVisible && isPageCompleted
    ? (isPageCompleted(page.keys) ? <CheckIcon sx={{fontSize: "medium"}} /> : <WarningIcon sx={{fontSize: "medium"}} />)
    : (index + 1)
  )

  // Format the title
  let pageTitle = (page) => (page ? <FormattedText variant="caption">{page.title}</FormattedText> : page)

  // Individual page buttons
  let pageButton = (page, index) => (
    <Fab
      size="small"
      type="submit"
      sx={{ width: 30, height: 30, minHeight: 30, boxShadow: "none", fontWeight: "normal" }}
      color={index == activePage ? "primary" : ''}
      onClick={() => navigateTo(index)}
      disabled={!page.canBeVisible}
    >
      { pageIcon(page, index) }
    </Fab>
  )

  // List of page buttons
  let pageList = () => (
    <Grid container spacing={2} justifyContent="space-evenly">
    { pages.map((p, index) => (
      <Grid item key={index}>
      { p.canBeVisible ?
          <Tooltip title={pageTitle(p)}>
          { pageButton(p, index) }
          </Tooltip>
        :
          pageButton(p, index)
      }
      </Grid>
    ))}
    </Grid>
  );

  // Condensed view: one button that unravels a page "menu"
  let pageSelector = () => (
    <div>
      <Fab
        sx={{ boxShadow: "none", fontWeight: "normal" }}
        onClick={event => setPageSelectorAnchorEl(event.currentTarget)}
      >
        { (activePage + 1) + "/" + pages.length }
      </Fab>
      <Menu
        disablePortal
        anchorEl={pageSelectorAnchorEl}
        open={!!pageSelectorAnchorEl}
        onClose={() => setPageSelectorAnchorEl(null)}
      >
        { pages.map((p, index) => (
          <MenuItem
            component="button"
            type="submit"
            key={index}
            sx={{width: "100%", textAlign: "inherit"}}
            disabled={!p.canBeVisible}
            selected={index == activePage}
            onClick={() => {setPageSelectorAnchorEl(null); navigateTo(index) }}
          >
            <ListItemIcon>{pageIcon(p, index)}</ListItemIcon>
            <ListItemText>{pageTitle(p)}</ListItemText>
          </MenuItem>
        ))}
      </Menu>
    </div>
  )

  // Render the expanded or condensed view depending on screen width
  return (
    <Grid container direction="row" spacing={4} justifyContent="space-between" alignItems="center" flexWrap="nowrap">
      {backButton && <Grid item>{backButton}</Grid>}
      <Grid item>
        { condensedPageList ? pageSelector() : pageList() }
      </Grid>
      <Grid item>{saveButton}</Grid>
    </Grid>
  );
};

FormPageNavigation.propTypes = {
  pages: PropTypes.array.isRequired,
  activePage: PropTypes.number.isRequired,
  saveButton: PropTypes.object.isRequired,
  backButton: PropTypes.object,
  isPageCompleted: PropTypes.func,
  navigateTo: PropTypes.func.isRequired,
};

FormPageNavigation.defaultProps = {
  activePage: 0,
};

export default FormPageNavigation;
