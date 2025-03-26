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
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.version.VersionManager;

import org.apache.jackrabbit.value.WeakReferenceValue;
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
            return isServiceNodeLocked(getServiceNode(node));
        } catch (RepositoryException e) {
            LOGGER.error("Unexpected error checking if node is locked", e);
            throw new LockError("Could not determine lock status");
        } finally {
            closeResolverIfNeeded(mustCloseResolver);
        }
    }

    private boolean isServiceNodeLocked(Node serviceNode) throws LockError
    {
        try {
            return serviceNode.hasProperty(LOCK_PROPERTY) && serviceNode.getProperty(LOCK_PROPERTY).getLength() > 0;
        } catch (RepositoryException e) {
            LOGGER.error("Unexpected error checking if node is locked", e);
            throw new LockError("Could not determine lock status");
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
                lockNode(node);
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
                lockNode(node);
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
            unlockNode(node);
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
            if (isServiceNodeLocked(serviceNode)) {
                return "The node is already locked";
            }

            // Can only lock subjects
            if (!serviceNode.isNodeType(SUBJECT_NODE_TYPE)) {
                return "Only subjects can be locked";
            }

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
            if (!isServiceNodeLocked(serviceNode)) {
                return "The node is not locked";
            }

            // Can only unlock subjects
            if (!serviceNode.isNodeType(SUBJECT_NODE_TYPE)) {
                return "Only subjects can be unlocked";
            }

            // Can't unlock nodes if their parent is locked
            if (isParentLocked(serviceNode)) {
                return "Parent is locked";
            }

            return null;
        } catch (RepositoryException e) {
            LOGGER.error("Unexpected error determining if node can be unlocked", e);
            throw new LockError();
        }
    }

    private void lockNode(Node node)
        throws LockError
    {
        try {
            VersionManager versionManager
                = this.serviceResolver.adaptTo(Session.class).getWorkspace().getVersionManager();
            Node serviceNode = getServiceNode(node);
            Node lockNode = createLockNode();
            applyLockNode(serviceNode, lockNode, versionManager);
        } catch (RepositoryException e) {
            LOGGER.error("Unable to retrieve service user", e);
            throw new LockError("Unable to retrieve service user");
        }
    }

    private Node createLockNode()
        throws LockError
    {
        try {
            Session session = getSession(this.rrp);
            String userID = session.getUserID();
            Node lockHomePage = session.getNode("/Locks");
            Node lockNode = lockHomePage.addNode(UUID.randomUUID().toString(), LOCK_NODE_TYPE);
            lockNode.setProperty("time", Calendar.getInstance());
            lockNode.setProperty("author", userID);
            session.save();
            return lockNode;
        } catch (RepositoryException e) {
            throw new LockError("Unable to create lock");
        }
    }

    private void applyLockNode(Node serviceNode, Node lockNode, VersionManager versionManager)
        throws LockError
    {
        applyLockNode(serviceNode, versionManager, lockNode, "root");
    }

    private void applyLockNode(Node serviceNode, VersionManager versionManager, Node lockNode, String root)
        throws LockError
    {

        try {
            checkoutIfNeeded(serviceNode, versionManager);
            if (serviceNode.hasProperty(LOCK_PROPERTY)
                && serviceNode.getProperty(LOCK_PROPERTY).getString().length() > 0) {
                // This node is already locked: Error out if needed, otherwise exit quietly
                if (root.length() > 0) {
                    throw new LockError("Node is already locked");
                } else {
                    return;
                }
            }
            serviceNode.setProperty(LOCK_PROPERTY, new WeakReferenceValue(lockNode));

            final Set<String> statusFlags = getStatusFlags(serviceNode);
            statusFlags.add(LOCKED_FLAG);
            serviceNode.setProperty(STATUS_PROPERTY, statusFlags.toArray(new String[0]));

            handleChildNodes(this::applyLockNode, versionManager, serviceNode, lockNode, "");
            if (root.length() > 0) {
                serviceNode.getSession().save();
                checkinIfNeeded(versionManager);
            }
        } catch (RepositoryException e) {
            LOGGER.error("Unable to apply lock", e);
            throw new LockError("Unable to apply lock");
        } finally {
            try {
                checkinIfNeeded(versionManager);
            } catch (RepositoryException e) {
                // Should not happen
                LOGGER.error("Cannot checkin", e);

            }
        }
    }

    private void unlockNode(Node node)
        throws LockError
    {
        try {
            VersionManager versionManager
                = this.serviceResolver.adaptTo(Session.class).getWorkspace().getVersionManager();
            Node serviceNode = getServiceNode(node);

            deleteLockNode(node);
            removeLockNode(serviceNode, versionManager);
        } catch (RepositoryException e) {
            LOGGER.error("Unable to retrieve service user", e);
            throw new LockError("Unable to retrieve service user");
        }
    }

    private void deleteLockNode(Node node)
        throws LockError
    {
        try {
            if (!node.hasProperty(LOCK_PROPERTY)) {
                throw new LockError("Node is not locked");
            }
            Node lockNode = node.getProperty(LOCK_PROPERTY).getNode();
            lockNode.remove();
            node.getSession().save();
        } catch (RepositoryException e) {
            throw new LockError("Unable to delete lock");
        }
    }

    private void removeLockNode(Node serviceNode, VersionManager versionManager)
        throws LockError
    {
        removeLockNode(serviceNode, versionManager, null, null);
    }
    private void removeLockNode(Node serviceNode, VersionManager versionManager, Node unused, String parentLockString)
        throws LockError
    {
        try {
            checkoutIfNeeded(serviceNode, versionManager);
            String lockString = serviceNode.getProperty(LOCK_PROPERTY).getString();
            if (parentLockString == null || parentLockString.equals(lockString)) {
                LOGGER.error("parentLock: {}, lockString: {}", parentLockString, lockString);
                serviceNode.getProperty(LOCK_PROPERTY).remove();

                final Set<String> statusFlags = getStatusFlags(serviceNode);
                statusFlags.remove(LOCKED_FLAG);
                serviceNode.setProperty(STATUS_PROPERTY, statusFlags.toArray(new String[0]));

                String passString = parentLockString == null ? lockString : parentLockString;
                handleChildNodes(this::removeLockNode, versionManager, serviceNode, null, passString);
            }

            if (parentLockString == null) {
                serviceNode.getSession().save();
                checkinIfNeeded(versionManager);
            }
        } catch (RepositoryException e) {
            LOGGER.error("Unable to remove lock", e);
            throw new LockError("Unable to remove lock");
        } finally {
            try {
                checkinIfNeeded(versionManager);
            } catch (RepositoryException e) {
                // Should not happen
                LOGGER.error("Cannot checkin");
            }
        }
    }

    private void handleChildNodes(NodeLockHandler handler,
        VersionManager versionManager,
        Node serviceNode,
        Node lockNode,
        String str)

        throws RepositoryException, LockError
    {
        if (!serviceNode.isNodeType(SUBJECT_NODE_TYPE)) {
            // Only check for subject children
            return;
        }

        // Handle child subjects
        final NodeIterator childNodes = serviceNode.getNodes();
        while (childNodes.hasNext()) {
            Node childNode = childNodes.nextNode();
            if (childNode.isNodeType(SUBJECT_NODE_TYPE)) {
                handler.handle(childNode, versionManager, lockNode, str);
            }
        }

        // Handle forms
        final PropertyIterator references = serviceNode.getReferences();
        while (references.hasNext()) {
            Node referenceNode = references.nextProperty().getParent();
            if (referenceNode.isNodeType("cards:Form")) {
                handler.handle(referenceNode, versionManager, lockNode, str);
            }
        }
    }

    private boolean isParentLocked(Node serviceNode)
        throws RepositoryException, LockError
    {
        Node parent = serviceNode.getParent();
        return parent.isNodeType(SUBJECT_NODE_TYPE) && isServiceNodeLocked(parent);
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

    private void checkinIfNeeded(VersionManager versionManager)
        throws RepositoryException
    {
        for (String path : this.nodesToCheckin.get()) {
            versionManager.checkin(path);
        }
        this.nodesToCheckin.get().clear();
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
        void handle(Node node, VersionManager versionManager, Node secondNode, String str) throws LockError;
    }
}
