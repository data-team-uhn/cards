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

import React, { useEffect, useState } from "react";
import { loadExtensions } from "../../uiextension/extensionManager";
import { Button } from "@material-ui/core";

export default function TopVisualElement(props) {

  const [ componentHeights, setComponentHeights ] = useState([]);
  const [ componentPositions, setComponentPositions ] = useState([]);
  const [ triggerRedraw, setTriggerRedraw ] = useState(false);
  const [ extensionData, setExtensionData ] = useState(null);
  const [ isInitialized, setIsInitialized ] = useState(false);

  const arrayEquals = (a, b) => {
    if (a.length != b.length) {
      return false;
    }
    for (let i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  };

  //Redraw the top elements if the browser window is resized
  window.onresize = () => {
    setTriggerRedraw(!triggerRedraw);
  };

  if (!isInitialized) {
    loadExtensions("TopVisualElement")
      .then((resp) => {
        if (resp.length > 0) {
          setExtensionData(resp);
          let zeros = [];
          for (let i = 0; i < resp.length; i++) {
            zeros.push(0);
          }
          setComponentHeights(zeros);
          setComponentPositions(zeros);
        }
        setIsInitialized(true);
      });
  }

  if (!isInitialized) {
    return (
      <Button variant="contained" color="primary" style={{ position: 'fixed', zIndex: 1040 }}>
        Loading Top Visual Element...
      </Button>
    );
  }

  if (extensionData == null) {
    return null;
  }

  let visualComponents = [];
  for (let i = 0; i < extensionData.length; i++) {
    visualComponents.push(extensionData[i]["lfs:extensionRender"]);
  }

  let newComponentPositions = [];
  let totalHeight = 0;
  for (let i = 0; i < componentHeights.length; i++) {
    newComponentPositions.push(totalHeight);
    totalHeight += componentHeights[i];
  }
  if (!arrayEquals(componentPositions, newComponentPositions)) {
    setComponentPositions(newComponentPositions);
  }
  props.setTotalHeight(totalHeight);

  return (
    <React.Fragment>
    {
      visualComponents.map((ThisComp, index) => {
        return (
          <ThisComp
            {...props}
            style={{ top: (componentPositions[index]) + 'px' }}
            onRender={(node) => {
                if (node != null) {
                  let n = node.getBoundingClientRect().height;
                  if (componentHeights[index] != n) {
                    let newComponentHeights = componentHeights.slice();
                    newComponentHeights[index] = n;
                    setComponentHeights(newComponentHeights);
                  }
                }
              }
            }
          />
        );
      })
    }
    </React.Fragment>
  );
}
