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

import {
  Backdrop,
  CircularProgress,
  Typography
} from "@mui/material";
import { alpha } from '@mui/material/styles';
import PropTypes from "prop-types";

import { checkPropTypes } from "../propTypes";

// A full-screen loading overlay: a dimmed backdrop with a centered spinner, shown while a page
// or a long-running action is working and the UI should not be interacted with. The backdrop is
// offset past the left navigation drawer on md+ screens so the navigation stays reachable.
//
// Props:
// open: Boolean controlling whether the overlay is shown
// message: Optional string shown under the spinner (e.g. "Reordering entries")
// progress: Optional number 0-100. When given, the spinner is determinate and shows this value;
//   otherwise it spins indeterminately.
//
// Sample usage:
// <LoadingOverlay open={saving} message="Saving changes" progress={percent} />

const LoadingOverlay = (props) => {
  checkPropTypes(LoadingOverlay, props);
  const { open, message, progress } = props;
  const isDeterminate = typeof progress === "number";

  return (
    <Backdrop
      open={open}
      sx={(theme) => ({
        flexDirection: "column",
        rowGap: 2,
        color: theme.palette.text.primary,
        backgroundColor: alpha(theme.palette.background.paper, .7),
        marginLeft: { md: "260px" },
        zIndex: theme.zIndex.drawer + 1
      })}
    >
      { message && <Typography variant="h6">{message}</Typography> }
      <CircularProgress
        variant={isDeterminate ? "determinate" : "indeterminate"}
        value={progress}
      />
    </Backdrop>
  );
};

LoadingOverlay.propTypes = {
  open: PropTypes.bool.isRequired,
  message: PropTypes.string,
  progress: PropTypes.number,
};

export default LoadingOverlay;
