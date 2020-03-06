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
package ca.sickkids.ccm.lfs.sling.jcr.oak.server.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.AccessControlException;

import javax.jcr.AccessDeniedException;
import javax.jcr.Credentials;
import javax.jcr.InvalidItemStateException;
import javax.jcr.InvalidSerializedDataException;
import javax.jcr.Item;
import javax.jcr.ItemExistsException;
import javax.jcr.ItemNotFoundException;
import javax.jcr.LoginException;
import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.ValueFactory;
import javax.jcr.Workspace;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.retention.RetentionManager;
import javax.jcr.security.AccessControlManager;
import javax.jcr.version.VersionException;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.Oak;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Custom <tt>JackrabbitSession</tt> that ensures that the correct <tt>TCCL</tt> is set in OSGi environments.
 *
 * @see #TcclWrappingJackrabbitSession(JackrabbitSession)
 * @version $Id$
 */
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class TcclWrappingJackrabbitSession implements JackrabbitSession
{

    private final JackrabbitSession wrapped;

    public TcclWrappingJackrabbitSession(JackrabbitSession wrapped)
    {
        this.wrapped = wrapped;
    }

    // calls setting the TCCL

    @Override
    public Session impersonate(Credentials credentials) throws LoginException, RepositoryException
    {

        Thread thread = Thread.currentThread();

        ClassLoader oldClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(Oak.class.getClassLoader());

        try {
            Session session = this.wrapped.impersonate(credentials);
            return new TcclWrappingJackrabbitSession((JackrabbitSession) session);
        } finally {
            thread.setContextClassLoader(oldClassLoader);
        }
    }

    // only pure delegate methods below

    @Override
    public Repository getRepository()
    {
        return this.wrapped.getRepository();
    }

    @Override
    public String getUserID()
    {
        return this.wrapped.getUserID();
    }

    @Override
    public String[] getAttributeNames()
    {
        return this.wrapped.getAttributeNames();
    }

    @Override
    public Object getAttribute(String name)
    {
        return this.wrapped.getAttribute(name);
    }

    @Override
    public boolean hasPermission(String absPath, String... actions) throws RepositoryException
    {
        return this.wrapped.hasPermission(absPath, actions);
    }

    @Override
    public Workspace getWorkspace()
    {
        return this.wrapped.getWorkspace();
    }

    @Override
    public Node getRootNode() throws RepositoryException
    {
        return this.wrapped.getRootNode();
    }

    @Override
    @SuppressWarnings("deprecation")
    public Node getNodeByUUID(String uuid) throws ItemNotFoundException, RepositoryException
    {
        return this.wrapped.getNodeByUUID(uuid);
    }

    @Override
    public Node getNodeByIdentifier(String id) throws ItemNotFoundException, RepositoryException
    {
        return this.wrapped.getNodeByIdentifier(id);
    }

    @Override
    public Item getItem(String absPath) throws PathNotFoundException, RepositoryException
    {
        return this.wrapped.getItem(absPath);
    }

    @Override
    public PrincipalManager getPrincipalManager()
        throws AccessDeniedException, UnsupportedRepositoryOperationException, RepositoryException
    {
        return this.wrapped.getPrincipalManager();
    }

    @Override
    public Node getNode(String absPath) throws PathNotFoundException, RepositoryException
    {
        return this.wrapped.getNode(absPath);
    }

    @Override
    public Property getProperty(String absPath) throws PathNotFoundException, RepositoryException
    {
        return this.wrapped.getProperty(absPath);
    }

    @Override
    public UserManager getUserManager()
        throws AccessDeniedException, UnsupportedRepositoryOperationException, RepositoryException
    {
        return this.wrapped.getUserManager();
    }

    @Override
    public boolean itemExists(String absPath) throws RepositoryException
    {
        return this.wrapped.itemExists(absPath);
    }

    @Override
    public Item getItemOrNull(String absPath) throws RepositoryException
    {
        return this.wrapped.getItemOrNull(absPath);
    }

    @Override
    public boolean nodeExists(String absPath) throws RepositoryException
    {
        return this.wrapped.nodeExists(absPath);
    }

    @Override
    public Property getPropertyOrNull(String absPath) throws RepositoryException
    {
        return this.wrapped.getPropertyOrNull(absPath);
    }

    @Override
    public boolean propertyExists(String absPath) throws RepositoryException
    {
        return this.wrapped.propertyExists(absPath);
    }

    @Override
    public Node getNodeOrNull(String absPath) throws RepositoryException
    {
        return this.wrapped.getNodeOrNull(absPath);
    }

    @Override
    public void move(String srcAbsPath, String destAbsPath) throws ItemExistsException, PathNotFoundException,
        VersionException, ConstraintViolationException, LockException, RepositoryException
    {
        this.wrapped.move(srcAbsPath, destAbsPath);
    }

    @Override
    public void removeItem(String absPath) throws VersionException, LockException, ConstraintViolationException,
        AccessDeniedException, RepositoryException
    {
        this.wrapped.removeItem(absPath);
    }

    @Override
    public void save() throws AccessDeniedException, ItemExistsException, ReferentialIntegrityException,
        ConstraintViolationException, InvalidItemStateException, VersionException, LockException,
        NoSuchNodeTypeException, RepositoryException
    {
        this.wrapped.save();
    }

    @Override
    public void refresh(boolean keepChanges) throws RepositoryException
    {
        this.wrapped.refresh(keepChanges);
    }

    @Override
    public boolean hasPendingChanges() throws RepositoryException
    {
        return this.wrapped.hasPendingChanges();
    }

    @Override
    public ValueFactory getValueFactory() throws UnsupportedRepositoryOperationException, RepositoryException
    {
        return this.wrapped.getValueFactory();
    }

    @Override
    public boolean hasPermission(String absPath, String actions) throws RepositoryException
    {
        return this.wrapped.hasPermission(absPath, actions);
    }

    @Override
    public void checkPermission(String absPath, String actions) throws AccessControlException, RepositoryException
    {
        this.wrapped.checkPermission(absPath, actions);
    }

    @Override
    public boolean hasCapability(String methodName, Object target, Object[] arguments) throws RepositoryException
    {
        return this.wrapped.hasCapability(methodName, target, arguments);
    }

    @Override
    public ContentHandler getImportContentHandler(String parentAbsPath, int uuidBehavior) throws PathNotFoundException,
        ConstraintViolationException, VersionException, LockException, RepositoryException
    {
        return this.wrapped.getImportContentHandler(parentAbsPath, uuidBehavior);
    }

    @Override
    public void importXML(String parentAbsPath, InputStream in, int uuidBehavior)
        throws IOException, PathNotFoundException, ItemExistsException, ConstraintViolationException,
        VersionException, InvalidSerializedDataException, LockException, RepositoryException
    {
        this.wrapped.importXML(parentAbsPath, in, uuidBehavior);
    }

    @Override
    public void exportSystemView(String absPath, ContentHandler contentHandler, boolean skipBinary, boolean noRecurse)
        throws PathNotFoundException, SAXException, RepositoryException
    {
        this.wrapped.exportSystemView(absPath, contentHandler, skipBinary, noRecurse);
    }

    @Override
    public void exportSystemView(String absPath, OutputStream out, boolean skipBinary, boolean noRecurse)
        throws IOException, PathNotFoundException, RepositoryException
    {
        this.wrapped.exportSystemView(absPath, out, skipBinary, noRecurse);
    }

    @Override
    public void exportDocumentView(String absPath, ContentHandler contentHandler, boolean skipBinary, boolean noRecurse)
        throws PathNotFoundException, SAXException, RepositoryException
    {
        this.wrapped.exportDocumentView(absPath, contentHandler, skipBinary, noRecurse);
    }

    @Override
    public void exportDocumentView(String absPath, OutputStream out, boolean skipBinary, boolean noRecurse)
        throws IOException, PathNotFoundException, RepositoryException
    {
        this.wrapped.exportDocumentView(absPath, out, skipBinary, noRecurse);
    }

    @Override
    public void setNamespacePrefix(String prefix, String uri) throws NamespaceException, RepositoryException
    {
        this.wrapped.setNamespacePrefix(prefix, uri);
    }

    @Override
    public String[] getNamespacePrefixes() throws RepositoryException
    {
        return this.wrapped.getNamespacePrefixes();
    }

    @Override
    public String getNamespaceURI(String prefix) throws NamespaceException, RepositoryException
    {
        return this.wrapped.getNamespaceURI(prefix);
    }

    @Override
    public String getNamespacePrefix(String uri) throws NamespaceException, RepositoryException
    {
        return this.wrapped.getNamespacePrefix(uri);
    }

    @Override
    public void logout()
    {
        this.wrapped.logout();
    }

    @Override
    public boolean isLive()
    {
        return this.wrapped.isLive();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void addLockToken(String lt)
    {
        this.wrapped.addLockToken(lt);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String[] getLockTokens()
    {
        return this.wrapped.getLockTokens();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeLockToken(String lt)
    {
        this.wrapped.removeLockToken(lt);
    }

    @Override
    public AccessControlManager getAccessControlManager()
        throws UnsupportedRepositoryOperationException, RepositoryException
    {
        return this.wrapped.getAccessControlManager();
    }

    @Override
    public RetentionManager getRetentionManager() throws UnsupportedRepositoryOperationException, RepositoryException
    {
        return this.wrapped.getRetentionManager();
    }
}
