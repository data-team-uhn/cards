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

import { useEffect, useRef, useState } from "react";

import { loadExtensions } from "./uiextension/extensionManager";

export default function PageStart(props) {

  const [ componentHeights, setComponentHeights ] = useState([]);
  const [ componentPositions, setComponentPositions ] = useState([]);
  const [ extensionData, setExtensionData ] = useState(null);
  const [ isInitialized, setIsInitialized ] = useState(false);
  const [ pageStartHeight, setPageStartHeight ] = useState(0);

  // Tracks the DOM node and its ResizeObserver for each extension, keyed by index, so heights stay
  // correct when a banner appears asynchronously or reflows when the window is resized.
  const observed = useRef({});

  const extensionsName = props.extensionsName || "PageStart";

  useEffect(() => {
    props.setTotalHeight?.(pageStartHeight);
  }, [props.setTotalHeight, pageStartHeight]);

  // Stop observing every extension when this component goes away.
  useEffect(() => {
    return () => {
      Object.values(observed.current).forEach(entry => entry.observer.disconnect());
      observed.current = {};
    };
  }, []);

  // Records the measured height of the extension at the given index, ignoring no-op updates.
  const setHeight = (index, height) => {
    setComponentHeights(prev => {
      if (prev[index] === height) {
        return prev;
      }
      const next = prev.slice();
      next[index] = height;
      return next;
    });
  };

  // Called by each extension with its rendered DOM node (or null when it renders nothing). Measures
  // the node now and keeps a ResizeObserver on it so later size changes update the stored height.
  const measure = (index, node) => {
    const previous = observed.current[index];
    if (previous?.node === node) {
      // Same node as last time: re-measure only (e.g. the extension re-reported on a state change).
      node && setHeight(index, node.getBoundingClientRect().height);
      return;
    }
    // The node changed (mounted, replaced or unmounted): stop watching the old one.
    previous?.observer.disconnect();
    if (node == null) {
      delete observed.current[index];
      setHeight(index, 0);
      return;
    }
    const observer = new ResizeObserver(() => setHeight(index, node.getBoundingClientRect().height));
    observer.observe(node);
    observed.current[index] = { node, observer };
    setHeight(index, node.getBoundingClientRect().height);
  };

  const arrayEquals = (a, b) => {
    return (
      Array.isArray(a) && Array.isArray(b) &&
      (a.length === b.length) &&
      a.every((val, i) => val === b[i])
    );
  };

  useEffect(() => {
    if (!isInitialized) {
      loadExtensions(extensionsName)
        .then((resp) => {
          if (resp.length > 0) {
            setExtensionData(resp);
            let zeros = [];
            for (let i = 0; i < resp.length; i++) {
              zeros.push(0);
            }
            setComponentHeights(zeros.slice());
            setComponentPositions(zeros.slice());
          }
          setIsInitialized(true);
        });
    }
  }, [isInitialized]);

  if (!isInitialized) {
    return null;
  }

  if (extensionData == null) {
    return null;
  }

  let visualComponents = [];
  for (let i = 0; i < extensionData.length; i++) {
    visualComponents.push(extensionData[i]["cards:extensionRender"]);
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

  if (pageStartHeight != totalHeight) {
    setPageStartHeight(totalHeight);
  }

  return (
    <>
      {
        visualComponents.map((ThisComp, index) => {
          return (
            <ThisComp
              {...props}
              key={index}
              style={{ top: (componentPositions[index]) + 'px' }}
              onRender={(node) => measure(index, node)}
            />
          );
        })
      }
    </>
  );
}
