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

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;

/**
 * Interface for locking and unlocking forms and subjects.
 *
 * @version $Id$
 * @since 0.9.30
 */
public interface LockManager
{
    /** The relative path that contains the lock information node if present. */
    String LOCK_NODE_PATH = "lock";
    /** The primary node type for a lock information node. */
    String LOCK_NODE_TYPE = "cards:Lock";
    /** The name of the property containing a reference to a lock node. */
    String LOCK_PROPERTY = "cards:lock";
    /** The name of the group of users who can lock nodes. */
    String LOCK_USERS = "LockUsers";
    /** The name of the group of users who can unlock nodes. */
    String UNLOCK_USERS = "UnlockUsers";
    /** The node type for the parent cards:lock nodes where locks are stored. */
    String LOCKS_NT_NAME = "rep:Unstructured";

    /**
     * Check if a form or subject is locked.
     *
     * @param node the form or subject node to check
     * @return {@code true} if the node is a locked form or subject
     * @throws LockException if the locked status of the node could not be determined
     */
    boolean isLocked(Node node) throws LockException;

    /**
     * Check if a subject satisfies all conditions to be locked.
     *
     * @param node the subject node to check
     * @return {@code true} if the node can be locked
     * @throws LockWarning if the node cannot be locked with {@code tryLock}
     * @throws LockException if it can not be determined if the node can be locked
     */
    boolean canLock(Node node) throws LockWarning, LockException;

    /**
     * Try to lock a subject node.
     *
     * @param node the subject node to try to lock
     * @throws LockWarning if the node cannot be locked with {@code tryLock} but may be lockable with {@code forceLock}
     * @throws LockError if the node cannot be locked
     * @throws LockException if an internal error occurs
     * @throws AccessDeniedException if the user does not have permission to lock this node
     */
    void tryLock(Node node) throws LockWarning, LockError, LockException, AccessDeniedException;

    /**
     * Try to lock a subject node, ignoring all warning-only precondition.
     *
     * @param node the subject node to try to lock
     * @throws LockError if the node can not be locked
     * @throws LockExcpetion if an internal error occurs
     * @throws AccessDeniedException if the user does not have permission to lock this node
     */
    void forceLock(Node node) throws LockError, LockException, AccessDeniedException;

    /**
     * Check if a subject node can be unlocked.
     * Cannot be unlocked if:
     * - The node is already unlocked
     * - The node has a locked parent
     *
     * @param node the subject node to check
     * @return {@code true} if the node can be unlocked
     * @throws LockWarning if the node cannot be unlocked but attempting to do so will silently no-op
     * @throws LockException if it can not be determined if the node can be unlocked
     */
    boolean canUnlock(Node node) throws LockWarning, LockException;


    /**
     * Try to unlock a subject node.
     * Will fail if:
     * - The node is already unlocked
     * - The node has a locked parent
     *
     * @param node the subject node to unlock
     * @throws LockError if the node cannot be unlocked
     * @throws LockExcepttion if an internal error occurs
     * @throws AccessDeniedException if the user does not have permission to unlock this node
     */
    void unlock(Node node) throws LockError, LockException, AccessDeniedException;
}
