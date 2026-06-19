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

// The reorder domain model: pure functions shared by the two reorder UIs (ReorderForm and
// ReorderDraft) and by the tree context's reorderNode action. They answer the three questions
// every reorder needs, against the flat node map produced by the tree model (see jcrToNode):
//   - is a move legal?            getMoveValidity
//   - where does the item land?   resolveTargetIndex
//   - is the move a no-op?        isNoOpMove
//   - apply it to a node map      applyMove
// Index math is done against a parent's FULL children array (conditionals included), the same
// space Sling's :order uses; entry-type filtering is a display concern handled by the callers.
// Nothing here touches React.

import { isDescendant } from "./treeQueries";

/**
 * Reason codes returned by getMoveValidity. Callers decide how to present each one (disable an
 * option, draw an invalid placeholder, throw on submit), so the codes live in one place.
 * @enum {string}
 */
export const MOVE_INVALID = {
  NO_SOURCE: 'NO_SOURCE',
  NO_PARENT: 'NO_PARENT',
  SELF: 'SELF',
  DESCENDANT: 'DESCENDANT',
  NAME_COLLISION: 'NAME_COLLISION',
};

const MOVE_INVALID_MESSAGES = {
  [MOVE_INVALID.NO_SOURCE]: 'Source node does not exist.',
  [MOVE_INVALID.NO_PARENT]: 'New parent node does not exist.',
  [MOVE_INVALID.SELF]: 'Cannot move an item into itself.',
  [MOVE_INVALID.DESCENDANT]: 'Cannot move an item into one of its own descendants.',
  [MOVE_INVALID.NAME_COLLISION]: 'New parent node already has a child with the same name.',
};

/**
 * Checks whether moving `sourceId` under `targetParentId` is structurally legal, independent of
 * the exact slot it lands in. This is the single home for the self / descendant / name-collision
 * rules that the reorder UIs and reorderNode all need. Position validity (index in range) and
 * no-op detection are separate concerns — see isValidIndex and isNoOpMove.
 *
 * @param {Object} nodes - The flat node map from the tree.
 * @param {string} sourceId - The ID of the node being moved.
 * @param {string} targetParentId - The ID of the parent the node would move under.
 * @returns {{valid: boolean, code: (string|null), message: (string|null)}} - `valid` is true when
 *   the move is allowed; otherwise `code` is a MOVE_INVALID code and `message` a human-readable string.
 */
export function getMoveValidity(nodes, sourceId, targetParentId) {
  const invalid = (code) => ({ valid: false, code, message: MOVE_INVALID_MESSAGES[code] });

  if (!nodes[sourceId]) return invalid(MOVE_INVALID.NO_SOURCE);
  if (!nodes[targetParentId]) return invalid(MOVE_INVALID.NO_PARENT);
  // Moving a node under itself, or under any of its own descendants, would detach the subtree.
  // isDescendant walks down from the source and returns true for the source itself too, so the
  // explicit self check below is only there to give the clearer "into itself" message.
  if (targetParentId === sourceId) return invalid(MOVE_INVALID.SELF);
  if (isDescendant(nodes, sourceId, targetParentId)) return invalid(MOVE_INVALID.DESCENDANT);

  // Two siblings cannot share a name in JCR. Only relevant when changing parent: staying put
  // keeps the existing (already unique) name.
  const isChangingParent = nodes[sourceId].parent !== targetParentId;
  if (isChangingParent) {
    const sourceName = nodes[sourceId].name;
    const collides = nodes[targetParentId].children.some(id => nodes[id]?.name === sourceName);
    if (collides) return invalid(MOVE_INVALID.NAME_COLLISION);
  }

  return { valid: true, code: null, message: null };
}

/**
 * Resolves the 0-based index (or the literal 'last') at which `sourceId` should land in
 * `targetParentId`. The index is expressed against the target parent's FULL children array AFTER
 * the source has been removed, so it can be handed straight to a splice (applyMove) or to Sling's
 * :order. 'last' is returned verbatim for an append, matching Sling's keyword and avoiding any
 * reliance on an out-of-range index resolving to last.
 *
 * @param {Object} nodes - The flat node map from the tree.
 * @param {string} sourceId - The ID of the node being moved.
 * @param {string} targetParentId - The ID of the parent the node moves under.
 * @param {{type: ('first'|'last'|'before'|'after'), refId?: string}} slot - Where to place it:
 *   'first'/'last' need no reference; 'before'/'after' are relative to the sibling `refId`.
 * @returns {(number|'last')} - The post-removal insertion index, or 'last' to append.
 */
export function resolveTargetIndex(nodes, sourceId, targetParentId, slot) {
  if (slot.type === 'first') return 0;
  if (slot.type === 'last') return 'last';

  const children = nodes[targetParentId].children;
  const refIndex = children.indexOf(slot.refId);
  // 'before' takes the reference's slot; 'after' takes the one past it.
  let index = slot.type === 'after' ? refIndex + 1 : refIndex;

  // Moving within the same parent, removing the source first shifts every later sibling down by
  // one, so a destination past the source's old slot must compensate.
  if (nodes[sourceId].parent === targetParentId) {
    const sourceIndex = children.indexOf(sourceId);
    if (sourceIndex < index) index -= 1;
  }
  return index;
}

/**
 * Checks whether the resolved destination is where the source already sits — i.e. the move would
 * change nothing. Only possible when staying within the same parent. Unifies the "already first",
 * "already last" and "dropped onto its own next-sibling slot" cases the UIs each guard against.
 *
 * @param {Object} nodes - The flat node map from the tree.
 * @param {string} sourceId - The ID of the node being moved.
 * @param {string} targetParentId - The ID of the parent the node would move under.
 * @param {(number|'last')} resolvedIndex - The destination from resolveTargetIndex.
 * @returns {boolean} - True when the source would end up in its current position.
 */
export function isNoOpMove(nodes, sourceId, targetParentId, resolvedIndex) {
  if (nodes[sourceId].parent !== targetParentId) return false;
  const children = nodes[targetParentId].children;
  const currentIndex = children.indexOf(sourceId);
  // Removing the source then re-inserting it at its own index reconstructs the original order.
  const finalIndex = resolvedIndex === 'last' ? children.length - 1 : resolvedIndex;
  return finalIndex === currentIndex;
}

/**
 * Checks that an index is a placeable position in `targetParentId`: either the 'last' keyword, or
 * an integer from 0 up to and including the current child count (the slot just past the end).
 *
 * @param {Object} nodes - The flat node map from the tree.
 * @param {string} targetParentId - The ID of the parent the node moves under.
 * @param {(number|'last')} index - The index to validate.
 * @returns {boolean} - True when the index is valid.
 */
export function isValidIndex(nodes, targetParentId, index) {
  if (index === 'last') return true;
  if (!Number.isInteger(index)) return false;
  const childrenCount = nodes[targetParentId].children.length;
  return index >= 0 && index <= childrenCount;
}

/**
 * Immutably applies a move to a flat node map: detaches the source from its current parent and
 * inserts it into the target parent at `index` (or appends it for 'last'), updating the source's
 * parent pointer. Only the touched nodes are cloned; untouched nodes are shared. Mirrors what the
 * server does, so the reorder UIs can reflect a move without a reload.
 *
 * @param {Object} nodes - The flat node map from the tree.
 * @param {{sourceId: string, targetParentId: string, index: (number|'last')}} move - The move.
 * @returns {Object} - A new node map with the move applied.
 */
export function applyMove(nodes, { sourceId, targetParentId, index }) {
  const sourceParentId = nodes[sourceId].parent;
  const next = { ...nodes };

  // Detach from the current parent first. When source and target share a parent, this updated
  // (source-free) children array is the one we splice into below — so `index`, computed against
  // the post-removal array by resolveTargetIndex, lines up.
  next[sourceParentId] = {
    ...next[sourceParentId],
    children: next[sourceParentId].children.filter(id => id !== sourceId),
  };

  const targetChildren = [...next[targetParentId].children];
  const at = index === 'last' ? targetChildren.length : index;
  targetChildren.splice(at, 0, sourceId);
  next[targetParentId] = { ...next[targetParentId], children: targetChildren };
  next[sourceId] = { ...next[sourceId], parent: targetParentId };

  return next;
}
