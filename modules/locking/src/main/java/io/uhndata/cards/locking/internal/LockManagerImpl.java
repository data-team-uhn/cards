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
package io.uhndata.cards.locking.internal;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.version.VersionManager;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.locking.api.LockError;
import io.uhndata.cards.locking.api.LockManager;
import io.uhndata.cards.locking.api.LockWarning;
import io.uhndata.cards.locking.spi.LockPrecondition;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

@Component(service = LockManager.class)
public class LockManagerImpl implements LockManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LockManagerImpl.class);
    private static final String SUBJECT_NODE_TYPE = "cards:Subject";

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference(fieldOption = FieldOption.REPLACE, cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY)
    private ResourceResolverFactory resolverFactory;

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, fieldOption = FieldOption.REPLACE,
        policy = ReferencePolicy.DYNAMIC)
    private volatile List<LockPrecondition> lockPreconditions;

    /** The nodes that should be checked in. */
    private final ThreadLocal<Set<String>> nodesToCheckin = ThreadLocal.withInitial(() -> new TreeSet<>());

    private ResourceResolver serviceResolver;

    @Override
    public boolean isLocked(Node node) throws LockError
    {
        boolean mustCloseResolver = initializeServiceResolver();
        try {
            return getStatusFlags(getServiceNode(node)).contains(LOCKED_FLAG);
        } catch (RepositoryException e) {
            LOGGER.error("Unexpected error checking if node is locked", e);
            throw new LockError("Could not determine lock status");
        } finally {
            closeResolverIfNeeded(mustCloseResolver);
        }
    }


    @Override
    public boolean canLock(Node node) throws LockWarning, LockError
    {
        boolean mustCloseResolver = initializeServiceResolver();
        try {
            String reason = canLockWithReason(node, true);
            return reason != null;
        } finally {
            closeResolverIfNeeded(mustCloseResolver);
        }
    }

    @Override
    public void tryLock(Node node) throws LockWarning, LockError
    {
        boolean mustCloseResolver = initializeServiceResolver();
        try {
            String reason = canLockWithReason(node, true);
            if (reason != null) {
                throw new LockError(reason);
            } else {
                handleNode(node, this::lockNode);
            }
        } finally {
            closeResolverIfNeeded(mustCloseResolver);
        }
    }

    @Override
    public void forceLock(Node node) throws LockError
    {
        boolean mustCloseResolver = initializeServiceResolver();
        try {
            String reason = null;
            try {
                reason = canLockWithReason(node, false);
            } catch (LockWarning e) {
                // Warning thrown: ignore and force lock anyways
            }
            if (reason != null) {
                throw new LockError(reason);
            } else {
                handleNode(node, this::lockNode);
            }
        } finally {
            closeResolverIfNeeded(mustCloseResolver);
        }
    }


    @Override
    public boolean canUnlock(Node node) throws LockError
    {
        boolean mustCloseResolver = initializeServiceResolver();
        try {
            String reason = canUnlockWithReason(node);
            return reason != null;
        } finally {
            closeResolverIfNeeded(mustCloseResolver);
        }
    }


    @Override
    public void unlock(Node node) throws LockError
    {
        boolean mustCloseResolver = initializeServiceResolver();
        try {
            String reason = canUnlockWithReason(node);
            if (reason != null) {
                throw new LockError(reason);
            }
            handleNode(node, this::unlockNode);
        } finally {
            closeResolverIfNeeded(mustCloseResolver);
        }
    }

    private boolean initializeServiceResolver()
        throws LockError
    {
        if (this.serviceResolver == null) {
            if (this.resolverFactory == null) {
                LOGGER.error("Resolver factory is null");
                throw new LockError("Unable to initialize lock manager");
            }
            try {
                this.serviceResolver = this.resolverFactory.getServiceResourceResolver(
                    Map.of(ResourceResolverFactory.SUBSERVICE, "locking"));
            } catch (LoginException e) {
                LOGGER.error("Could not get service resolver", e);
                throw new LockError("Unable to initialize lock manager");
            }
            return true;
        } else {
            return false;
        }
    }

    private void closeResolverIfNeeded(boolean mustCloseResolver)
    {
        if (mustCloseResolver) {
            this.serviceResolver.close();
            this.serviceResolver = null;
        }
    }

    private String canLockWithReason(Node node, boolean checkPreconditions) throws LockWarning, LockError
    {
        try {
            Node serviceNode = getServiceNode(node);
            // Cannot lock nodes that are already locked
            if (isLocked(serviceNode)) {
                return "The node is already locked";
            }

            // Can only lock subjects
            if (!serviceNode.isNodeType(SUBJECT_NODE_TYPE)) {
                return "Only subjects can be locked";
            }

            // TODO: check if user has rights to lock this node
            if (checkPreconditions) {
                for (LockPrecondition precondition : this.lockPreconditions) {
                    if (!precondition.canLock(serviceNode)) {
                        return "Precondition failed";
                    }
                }
            }

            return null;
        } catch (RepositoryException e) {
            LOGGER.error("Unexpected error determining if node can be locked", e);
            throw new LockError();
        }
    }

    private String canUnlockWithReason(Node node) throws LockError
    {
        try {
            Node serviceNode = getServiceNode(node);
            // Cannot unlock nodes that are not locked
            if (!isLocked(serviceNode)) {
                return "The node is not locked";
            }

            // Can only unlock subjects
            if (!serviceNode.isNodeType(SUBJECT_NODE_TYPE)) {
                return "Only subjects can be unlocked";
            }

            // Can't unlock nodes if their parent is locked
            if (isParentLocked(node)) {
                return "Parent is locked";
            }

            // TODO: check if user has rights to unlock this node

            return null;
        } catch (RepositoryException e) {
            LOGGER.error("Unexpected error determining if node can be unlocked", e);
            throw new LockError();
        }
    }

    private void handleNode(Node node, NodeLockHandler handler)
        throws LockError
    {
        final Session session;
        final VersionManager versionManager;
        final Node serviceNode;
        try {
            session = this.serviceResolver.adaptTo(Session.class);
            versionManager = session.getWorkspace().getVersionManager();
            serviceNode = getServiceNode(node);
        } catch (RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
            throw new LockError("Unexpected error");
        }

        handler.handle(versionManager, serviceNode, true);

        try {
            session.save();
            for (String path : this.nodesToCheckin.get()) {
                versionManager.checkin(path);
            }
        } catch (RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
            throw new LockError("Unable to save changed nodes");
        }
    }

    private void lockNode(VersionManager versionManager, Node node, boolean isRoot)
        throws LockError
    {
        try {
            checkoutIfNeeded(node, versionManager);

            if (isRoot) {
                // Create the lock node
                String userID = getSession(this.rrp).getUserID();
                Node lockNode = node.addNode(LOCK_NODE_PATH, LOCK_NODE_TYPE);
                lockNode.setProperty("time", Calendar.getInstance());
                lockNode.setProperty("author", userID);
            }

            final Set<String> statusFlags = getStatusFlags(node);
            statusFlags.add(LOCKED_FLAG);
            node.setProperty(STATUS_PROPERTY, statusFlags.toArray(new String[0]));

            handleChildNodes(this::lockNode, versionManager, node);
        } catch (RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
            throw new LockError("Error adding lock to nodes");
        }
    }

    private void unlockNode(VersionManager versionManager, Node node, boolean isRoot)
        throws LockError
    {
        try {
            if (node.hasNode(LOCK_NODE_PATH)) {
                if (isRoot) {
                    // Remove the lock node as this node is being directly unlocked
                    node.getNode(LOCK_NODE_PATH).remove();
                } else {
                    // Do not indirectly unlock nodes that have lock nodes.
                    // Halt traversal here but do not prevent other parent/sibling nodes from being processed.
                    return;
                }
            }

            checkoutIfNeeded(node, versionManager);
            final Set<String> statusFlags = getStatusFlags(node);
            statusFlags.remove(LOCKED_FLAG);
            node.setProperty(STATUS_PROPERTY, statusFlags.toArray(new String[0]));

            handleChildNodes(this::unlockNode, versionManager, node);
        } catch (RepositoryException e) {
            LOGGER.error(e.getMessage(), e);
            throw new LockError("Error removing lock from nodes");
        }
    }

    private void handleChildNodes(NodeLockHandler handler, VersionManager versionManager,
        Node node)
        throws LockError
    {
        try {
            if (!node.isNodeType(SUBJECT_NODE_TYPE)) {
                // Only check for subject children
                return;
            }

            // Handle child subjects
            final NodeIterator childNodes = node.getNodes();
            while (childNodes.hasNext()) {
                Node childNode = childNodes.nextNode();
                if (childNode.isNodeType(SUBJECT_NODE_TYPE)) {
                    handler.handle(versionManager, childNode, false);
                }
            }

            // Handle forms
            final PropertyIterator references = node.getReferences();
            while (references.hasNext()) {
                Node referenceNode = references.nextProperty().getParent();
                if (referenceNode.isNodeType("cards:Form")) {
                    handler.handle(versionManager, referenceNode, false);
                }
            }
        } catch (RepositoryException e) {
            LOGGER.error("Unexpected error determining node types", e);
            throw new LockError("Could not determine node types");
        }
    }

    private static boolean isParentLocked(Node node)
        throws RepositoryException
    {
        Node parent = node.getParent();
        return parent.isNodeType(SUBJECT_NODE_TYPE) && getStatusFlags(parent).contains(LOCKED_FLAG);
    }

    private static Set<String> getStatusFlags(Node node)
        throws RepositoryException
    {
        final Set<String> statusFlags = new TreeSet<>();
        if (node.hasProperty(STATUS_PROPERTY)) {
            for (Value value : node.getProperty(STATUS_PROPERTY).getValues()) {
                statusFlags.add(value.getString());
            }
        }
        return statusFlags;
    }

    private void checkoutIfNeeded(Node node, VersionManager versionManager)
        throws RepositoryException
    {
        final String path = node.getPath();
        if (!versionManager.isCheckedOut(path)) {
            this.nodesToCheckin.get().add(path);
            versionManager.checkout(path);
        }
    }

    /**
     * Obtain the current session from the resource resolver factory.
     *
     * @param rrp the resource resolver factory service, may be {@code null}
     * @return the current session, or {@code null} if a session may not be obtained
     */
    private Session getSession(final ThreadResourceResolverProvider rrp)
    {
        if (rrp == null) {
            return null;
        }

        final ResourceResolver rr = rrp.getThreadResourceResolver();
        if (rr == null) {
            return null;
        }
        return rr.adaptTo(Session.class);
    }

    private Node getServiceNode(Node node)
        throws RepositoryException
    {
        return this.serviceResolver.adaptTo(Session.class).getNode(node.getPath());
    }

    private interface NodeLockHandler
    {
        void handle(VersionManager versionManager, Node node, boolean isRoot)
            throws LockError;
    }
}
