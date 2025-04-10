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
package io.uhndata.cards.locking.spi;

import javax.jcr.Node;

import io.uhndata.cards.locking.api.LockException;
import io.uhndata.cards.locking.api.LockWarning;

/**
 * A restriction that may prevent a node from being locked based on the current node state.
 * Can allow locking, prevent all locking or prevent non-forced locking.
 *
 * @version $Id$
 * @since 0.9.30
 */
public interface LockPrecondition
{
    /**
     * Check if the node can be locked in it's current state.
     *
     * @param node the node to be locked
     * @return {@code true} if this precondition does not prevent locking.
     *         {@code false} if this condition prevents all locking
     * @throws LockWarning if this precondition should prevent non-forced locking
     * @throws LockException if this precondition could not be evaluated
     */
    boolean canLock(Node node) throws LockWarning, LockException;

    /**
     * The name of this precondition.
     *
     * @return the name of this precondition
     */
    String getName();
}
