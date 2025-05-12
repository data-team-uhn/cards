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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.jcr.AccessDeniedException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
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
import io.uhndata.cards.locking.api.LockException;
import io.uhndata.cards.locking.api.LockManager;
import io.uhndata.cards.locking.api.LockWarning;
import io.uhndata.cards.locking.spi.LockPrecondition;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

@SuppressWarnings("checkstyle:ClassFanOutComplexity")
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
    private final ThreadLocal<Set<String>> nodesToCheckin = ThreadLocal.withInitial(TreeSet::new);

    private ThreadLocal<ResourceResolver> serviceResolver = new ThreadLocal<>();

    @Override
    public boolean isLocked(Node node) throws LockException
    {
        boolean mustCloseResolver = initializeServiceResolver();
        try {
            return isServiceNodeLocked(getServiceNode(node));
        } catch (RepositoryException e) {
            LOGGER.error("Cannot determine if node is locked", e);
            throw new LockException("Could not determine lock status");
        } finally {
            closeResolverIfNeeded(mustCloseResolver);
        }
    }

    private boolean isServiceNodeLocked(Node serviceNode) throws RepositoryException
    {
        return serviceNode.hasProperty(LOCK_PROPERTY) && serviceNode.getProperty(LOCK_PROPERTY).getLength() > 0;
    }

    @Override
    public boolean canLock(Node node) throws LockWarning, LockException
    {
        boolean mustCloseResolver = initializeServiceResolver();
        try {
            String reason = canLockWithReason(node);
            return reason == null;
        } finally {
            closeResolverIfNeeded(mustCloseResolver);
        }
    }

    @Override
    public void tryLock(Node node) throws LockWarning, LockError, LockException, AccessDeniedException
    {
        boolean mustCloseResolver = initializeServiceResolver();
        try {
            String reason = canLockWithReason(node);
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
    public void forceLock(Node node) throws LockError, LockException, AccessDeniedException
    {
        boolean mustCloseResolver = initializeServiceResolver();
        try {
            String reason = null;
            try {
                reason = canLockWithReason(node);
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
    public boolean canUnlock(Node node) throws LockWarning, LockException
    {
        boolean mustCloseResolver = initializeServiceResolver();
        try {
            String reason = canUnlockWithReason(node);
            return reason == null;
        } finally {
            closeResolverIfNeeded(mustCloseResolver);
        }
    }

    @Override
    public void unlock(Node node) throws LockError, LockException, AccessDeniedException
    {
        boolean mustCloseResolver = initializeServiceResolver();
        try {
            String reason = canUnlockWithReason(node);
            if (reason != null) {
                throw new LockError(reason);
            }
            unlockNode(node);
        } catch (LockWarning e) {
            LOGGER.warn("Warning encountered trying to unlock a node: " + e.getMessage());
            // Exit silently without doing anything
        } finally {
            closeResolverIfNeeded(mustCloseResolver);
        }
    }

    private boolean initializeServiceResolver()
        throws LockException
    {
        if (this.serviceResolver.get() == null) {
            if (this.resolverFactory == null) {
                LOGGER.error("Resolver factory is null");
                throw new LockException("Unable to initialize lock manager");
            }
            try {
                this.serviceResolver.set(this.resolverFactory.getServiceResourceResolver(
                    Map.of(ResourceResolverFactory.SUBSERVICE, "locking")));
            } catch (LoginException e) {
                LOGGER.error("Could not log in to locking service user", e);
                throw new LockException("Unable to sign into locking service user");
            }
            return true;
        } else {
            return false;
        }
    }

    private void closeResolverIfNeeded(boolean mustCloseResolver)
    {
        if (mustCloseResolver) {
            this.serviceResolver.get().close();
            this.serviceResolver.set(null);
        }
    }

    private String canLockWithReason(Node node) throws LockWarning, LockException
    {
        try {
            String permissions = checkUserPermissions(node, LOCK_USERS);
            if (permissions != null) {
                return permissions;
            }

            Node serviceNode = getServiceNode(node);
            // Cannot lock nodes that are already locked
            if (isServiceNodeLocked(serviceNode)) {
                // Node is already locked
                Node existingLock = serviceNode.getProperty(LOCK_PROPERTY).getNode();
                String author = existingLock.getProperty("author").getString();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                String time = format.format(existingLock.getProperty("time").getDate().getTime());
                return String.format("Node has already been locked by %s on %s", author, time);
            }

            // Can only lock subjects
            if (!serviceNode.isNodeType(SUBJECT_NODE_TYPE)) {
                return "Only subjects can be locked";
            }

            LockWarning warning = null;
            for (LockPrecondition precondition : this.lockPreconditions) {
                try {
                    if (!precondition.canLock(serviceNode)) {
                        // If there's a hard failure that will prevent all locking, exit immediately
                        return precondition.getName() + " precondition failed";
                    }
                } catch (LockWarning w) {
                    // Temporarily catch any warnings to avoid any hard failures being hidden
                    warning = w;
                }
            }

            // No hard failures. If there were any warnings, allow them to be sent onwards
            if (warning != null) {
                throw warning;
            }

            // No hard failures, no warnings
            return null;
        } catch (RepositoryException e) {
            LOGGER.error("Cannot determine if node can be locked", e);
            throw new LockException();
        }
    }

    private String canUnlockWithReason(Node node) throws LockWarning, LockException
    {
        try {
            String permissions = checkUserPermissions(node, UNLOCK_USERS);
            if (permissions != null) {
                return permissions;
            }

            Node serviceNode = getServiceNode(node);
            // Cannot unlock nodes that are not locked
            if (!isServiceNodeLocked(serviceNode)) {
                throw new LockWarning("The node is not locked");
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
            LOGGER.error("Cannot determine if node can be unlocked", e);
            throw new LockException();
        }
    }

    private String checkUserPermissions(final Node node, final String group)
        throws RepositoryException
    {
        String result = null;

        String userID = node.getSession().getUserID();
        if (null == userID || "anonymous".equals(userID)) {
            result = "Unkown user";
        } else if (!memberOf(userID, group)) {
            result = "Permission denied";
        }

        return result;
    }

    private boolean memberOf(final String userID, final String groupName)
        throws RepositoryException
    {
        JackrabbitSession serviceSession = (JackrabbitSession) this.serviceResolver.get().adaptTo(Session.class);
        UserManager userManager = serviceSession.getUserManager();
        Group group = (Group) userManager.getAuthorizable(groupName);
        return group.isMember(userManager.getAuthorizable(userID));
    }

    private void lockNode(Node node)
        throws LockError, LockException, AccessDeniedException
    {
        try {
            Session serviceSession = this.serviceResolver.get().adaptTo(Session.class);
            VersionManager versionManager = serviceSession.getWorkspace().getVersionManager();
            Node serviceNode = getServiceNode(node, serviceSession);
            Node lockNode = createLockNode(serviceSession);
            applyLockNode(serviceNode, lockNode, versionManager);
        } catch (RepositoryException e) {
            LOGGER.error("Unable to retrieve service user", e);
            throw new LockException("Unable to retrieve service user");
        }
    }

    private Node createLockNode(Session servicSession)
        throws LockException, AccessDeniedException
    {
        try {

            String userID = getSession(this.rrp).getUserID();
            Node parent = servicSession.getNode("/jcr:system/cards:locks");
            String name = UUID.randomUUID().toString();
            for (int i = 0; i <= 4; i += 2) {
                parent = getOrCreateNode(parent, name.substring(i, i + 2), LOCKS_NT_NAME);
            }
            Node lockNode = parent.addNode(name, LOCK_NODE_TYPE);
            lockNode.setProperty("time", Calendar.getInstance());
            lockNode.setProperty("author", userID);
            servicSession.save();
            return lockNode;
        } catch (RepositoryException e) {
            throw new LockException("Unable to create lock");
        }
    }

    private Node getOrCreateNode(Node parent, String name, String nodeType)
        throws RepositoryException
    {
        return parent.hasNode(name) ? parent.getNode(name) : parent.addNode(name, nodeType);
    }

    private void applyLockNode(Node serviceNode, Node lockNode, VersionManager versionManager)
        throws LockError, LockException
    {
        applyLockNode(serviceNode, versionManager, lockNode, "root");
    }

    private void applyLockNode(Node serviceNode, VersionManager versionManager, Node lockNode, String root)
        throws LockError, LockException
    {

        try {
            checkoutIfNeeded(serviceNode, versionManager);
            if (serviceNode.hasProperty(LOCK_PROPERTY)
                && serviceNode.getProperty(LOCK_PROPERTY).getString().length() > 0) {
                // This node is already locked: Error out if needed, otherwise exit quietly
                if (root.length() > 0) {
                    // This node is already locked: skip
                    throw new LockError("Node is already locked");
                } else {
                    return;
                }
            }
            serviceNode.setProperty(LOCK_PROPERTY, new WeakReferenceValue(lockNode));

            handleChildNodes(this::applyLockNode, versionManager, serviceNode, lockNode, "");
            if (root.length() > 0) {
                serviceNode.getSession().save();
                checkinIfNeeded(versionManager);
            }
        } catch (RepositoryException e) {
            LOGGER.error("Unable to apply lock", e);
            throw new LockException("Unable to apply lock");
        }
    }

    private void unlockNode(Node node)
        throws LockError, LockException, AccessDeniedException
    {
        try {
            Session serviceSession = this.serviceResolver.get().adaptTo(Session.class);
            VersionManager versionManager = serviceSession.getWorkspace().getVersionManager();
            Node serviceNode = getServiceNode(node, serviceSession);

            deleteLockNode(serviceNode);
            removeLockNode(serviceNode, versionManager);
        } catch (RepositoryException e) {
            LOGGER.error("Unable to retrieve service user", e);
            throw new LockException("Unable to retrieve service user");
        }
    }

    private void deleteLockNode(Node serviceNode)
        throws LockException, AccessDeniedException
    {
        try {
            // If the node is locked, unlock it.
            // Otherwise, silently do nothing.
            if (serviceNode.hasProperty(LOCK_PROPERTY)) {
                Node lockNode = serviceNode.getProperty(LOCK_PROPERTY).getNode();
                lockNode.remove();
                serviceNode.getSession().save();
            }
        } catch (RepositoryException e) {
            throw new LockException("Unable to delete lock");
        }
    }

    private void removeLockNode(Node serviceNode, VersionManager versionManager)
        throws LockError, LockException
    {
        removeLockNode(serviceNode, versionManager, null, null);
    }

    private void removeLockNode(Node serviceNode, VersionManager versionManager, Node unused, String parentLockString)
        throws LockError, LockException
    {
        try {
            checkoutIfNeeded(serviceNode, versionManager);
            String lockString = serviceNode.getProperty(LockManager.LOCK_PROPERTY).getString();
            if (parentLockString == null || parentLockString.equals(lockString)) {
                serviceNode.getProperty(LOCK_PROPERTY).remove();

                String passString = parentLockString == null ? lockString : parentLockString;
                handleChildNodes(this::removeLockNode, versionManager, serviceNode, null, passString);
            }

            if (parentLockString == null) {
                serviceNode.getSession().save();
                checkinIfNeeded(versionManager);
            }
        } catch (RepositoryException e) {
            LOGGER.error("Unable to remove lock", e);
            throw new LockException("Unable to remove lock");
        }
    }

    private void handleChildNodes(NodeLockAction handler,
        VersionManager versionManager,
        Node serviceNode,
        Node lockNode,
        String str)

        throws RepositoryException, LockError, LockException
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
                handler.perform(childNode, versionManager, lockNode, str);
            }
        }

        // Handle forms
        final PropertyIterator references = serviceNode.getReferences();
        while (references.hasNext()) {
            Node referenceNode = references.nextProperty().getParent();
            if (referenceNode.isNodeType("cards:Form")) {
                handler.perform(referenceNode, versionManager, lockNode, str);
            }
        }
    }

    private boolean isParentLocked(Node serviceNode)
        throws RepositoryException
    {
        Node parent = serviceNode.getParent();
        return parent.isNodeType(SUBJECT_NODE_TYPE) && isServiceNodeLocked(parent);
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
        return getServiceNode(node, this.serviceResolver.get().adaptTo(Session.class));
    }

    private Node getServiceNode(Node node, Session serviceSession)
        throws RepositoryException
    {
        return serviceSession.getNode(node.getPath());
    }

    private interface NodeLockAction
    {
        void perform(Node node, VersionManager versionManager, Node secondNode, String str)
            throws LockError, LockException;
    }
}
