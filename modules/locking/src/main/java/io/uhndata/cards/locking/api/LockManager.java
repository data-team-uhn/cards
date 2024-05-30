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
package io.uhndata.cards.locking.api;

import javax.jcr.Node;

/**
 * Interface for locking and unlocking forms and subjects.
 *
 * @version $Id$
 */
public interface LockManager
{
    /** The status flag that indicates that a node is locked. */
    String LOCKED_FLAG = "LOCKED";
    /** The relative path that contains the lock information node if present. */
    String LOCK_NODE_PATH = "lock";
    /** The primary node type for a lock information node. */
    String LOCK_NODE_TYPE = "cards:Lock";
    /** The relative path that contains a node's status flags. */
    String STATUS_PROPERTY = "statusFlags";

    /**
     * Check if a form or subject is locked.
     *
     * @param node the form or subject node to check
     * @return {@code true} if the node is a locked form or subject
     * @throws LockError if the locked status of the node could not be determined
     */
    boolean isLocked(Node node) throws LockError;

    /**
     * Check if a subject satisfies all conditions to be locked.
     * - Not already locked
     * - Does not have any other conditions meaning that it cannot be locked
     *
     * @param node the subject node to check
     * @return {@code true} if the node can be locked
     * @throws LockWarning if the node cannot be locked with {@code tryLock}
     * @throws LockError if it can not be determined if the node can be locked
     */
    boolean canLock(Node node) throws LockWarning, LockError;

    /**
     * Try to lock a subject node.
     * Will fail if:
     * - The node is already locked
     * - There are any other conditions preventing it from being locked
     *
     * @param node the subject node to try to lock
     * @throws LockWarning if the node cannot be locked due to a precondition
     * @throws LockError if the node cannot be locked for another reason
     */
    void tryLock(Node node) throws LockWarning, LockError;

    /**
     * Try to lock a subject node.
     * Will fail if the node is already locked
     *
     * @param node the subject node to try to lock
     * @throws LockError if the node could not be locked
     */
    void forceLock(Node node) throws LockError;

    /**
     * Check if a subject node can be unlocked.
     * Cannot be unlocked if:
     * - The node is already unlocked
     * - The node has a locked parent
     *
     * @param node the subject node to check
     * @return {@code true} if the node can be unlocked
     * @throws LockError if it can not be determined if the node can be unlocked
     */
    boolean canUnlock(Node node) throws LockError;


    /**
     * Try to unlock a subject node.
     * Will fail if:
     * - The node is already unlocked
     * - The node has a locked parent
     *
     * @param node the subject node to unlock
     * @throws LockError if the node cannot be unlocked
     */
    void unlock(Node node) throws LockError;
}
