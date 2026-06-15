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

import { useEffect, useMemo, useState } from "react";

// Custom hook to track which item is currently in view (nearest the top of the viewport)
export function useInViewTracker(items) {
  const [activeItem, setActiveItem] = useState(null);

  useEffect(() => {
    // Ids of items whose top has scrolled up to/past the header line and that are still on
    // screen. We observe the strip from the top of the viewport down to the header, so an
    // item joins this set exactly when its top crosses the header line.
    const passed = new Set();

    // Measured fresh each update so the collapsing sticky header is accounted for.
    const headerLine = () => {
      const header = document.getElementById('cards-resource-header');
      return header ? header.getBoundingClientRect().bottom : 0;
    };

    const update = () => {
      const lineY = headerLine();
      // The current item is the deepest one whose top has passed the line — the largest
      // top still at/above the line. That is the innermost question (its top sits below
      // its section's), and it persists through the gap between items until the next
      // item's top reaches the line, so the breadcrumb transitions smoothly with no gaps.
      let activeId = null;
      let bestTop = -Infinity;
      passed.forEach((id) => {
        const element = document.querySelector(`[in-view-data-id='${id}']`);
        if (!element) return;
        const top = element.getBoundingClientRect().top;
        if (top <= lineY + 1 && top > bestTop) {
          activeId = id;
          bestTop = top;
        }
      });
      if (activeId) setActiveItem(activeId);
    };

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          const id = entry.target.getAttribute('in-view-data-id');
          if (!id) return;
          if (entry.isIntersecting) {
            passed.add(id);
          } else {
            passed.delete(id);
          }
        });
        update();
      },
      // Observe the strip from the top of the viewport down to the header line.
      { rootMargin: `0px 0px -${Math.max(0, window.innerHeight - headerLine())}px 0px`, threshold: 0 }
    );

    items.forEach(item => {
      const element = document.querySelector(`[in-view-data-id='${item.value}']`);
      if (element) {
        observer.observe(element);
      }
    });

    return () => observer.disconnect();
  }, [items]);

  const scrollToItem = (id) => {
    const target = document.querySelector(`[in-view-data-id='${id}']`);
    if (!target) return;
    const targetPosition = target.getBoundingClientRect().top + window.scrollY;
    window.scrollTo({
      top: targetPosition - 100,
      behavior: 'smooth'
    });
  };

  const [highlightedItems, setHighlightedItems] = useState(() => new Map());
  const highlighter = useMemo(() => ({
    highlightedItems,
    highlight: (id) => {
      setHighlightedItems((prev) => {
        const next = new Map(prev);
        next.set(id, true);
        return next;
      });
    },
    unhighlight: (id) => {
      setHighlightedItems((prev) => {
        const next = new Map(prev);
        next.delete(id);
        return next;
      });
    },
    unhighlightAll: () => setHighlightedItems(new Map()),
    isHighlighted: (id) => highlightedItems.has(id),
  }), [highlightedItems]);

  return { activeItem, scrollToItem, highlighter };
}
