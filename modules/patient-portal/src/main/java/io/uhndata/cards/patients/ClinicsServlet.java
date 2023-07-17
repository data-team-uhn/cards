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
package io.uhndata.cards.patients;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.Privilege;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.permissions.spi.PermissionsManager;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = { "cards/ClinicMappingFolder" }, methods = { "POST" })
public class ClinicsServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = -5555906093850253193L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ClinicsServlet.class);

    // FIXME Delete this
    private static final String IMPORT_FACTORY_PID = "io.uhndata.cards.proms.internal.importer.ImportConfig";

    private static final String DESCRIPTION_FIELD = "description";

    private static final String PRIMARY_TYPE_FIELD = "jcr:primaryType";

    private final ThreadLocal<String> clinicName = new ThreadLocal<>();

    private final ThreadLocal<String> displayName = new ThreadLocal<>();

    private final ThreadLocal<String> sidebarLabel = new ThreadLocal<>();

    private final ThreadLocal<String> surveyID = new ThreadLocal<>();

    private final ThreadLocal<String> emergencyContact = new ThreadLocal<>();

    private final ThreadLocal<Double> tokenLifetime = new ThreadLocal<>();

    private final ThreadLocal<String> description = new ThreadLocal<>();

    private final ThreadLocal<String> idHash = new ThreadLocal<>();

    @Reference
    private ConfigurationAdmin configAdmin;

    @Reference
    private PermissionsManager permissionsManager;

    @Override
    public void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException
    {
        try {
            // Create all of the necessary nodes
            final ResourceResolver resolver = request.getResourceResolver();
            this.parseArguments(request);
            try {
                final Session session = resolver.adaptTo(Session.class);
                this.displayName.set(getUniqueDisplayName(resolver, this.displayName.get()));
                this.createClinicMapping(resolver);
                final Group clinicGroup = this.createGroup(resolver);
                this.createSidebar(resolver, clinicGroup);
                this.createDashboardExtension(resolver);
                session.save();
            } catch (final RepositoryException e) {
                this.returnError(response, e.getMessage());
            } catch (final NullPointerException e) {
                this.returnError(response, e.getMessage());
            }

            // Grab the configuration to edit
            final Configuration[] configs = this.configAdmin.listConfigurations(
                "(service.factoryPid=" + IMPORT_FACTORY_PID + ")");

            if (configs != null) {
                for (final Configuration config : configs) {
                    this.insertNewClinic(config, this.clinicName.get());
                }
            }
        } catch (final InvalidSyntaxException e) {
            // This can happen when the filter given to the listConfigurations call above is wrong
            // This shouldn't happen unless a typo was made in the value of IMPORT_FACTORY_PID
            this.returnError(response, "Invalid syntax in config search.");
        } catch (final IOException e) {
            // This can happen while updating the properties of a configuration
            // Unknown how to handle this
            this.returnError(response, "Updating clinic configurations failed.");
        } finally {
            cleanup();
        }
    }

    /**
     * Parse out the arguments from the request into threadlocal variables for later usage.
     *
     * @param request servlet request whose arguments we need to parse
     */
    private boolean parseArguments(final SlingHttpServletRequest request)
    {
        this.clinicName.set(request.getParameter("clinicName"));
        this.displayName.set(request.getParameter("displayName"));
        this.sidebarLabel.set(request.getParameter("sidebarLabel"));
        this.surveyID.set(request.getParameter("survey"));
        this.emergencyContact.set(request.getParameter("emergencyContact"));
        String tokenLifetimeParam = StringUtils.defaultString(request.getParameter("tokenLifetime"), "");
        if (StringUtils.isNotBlank(tokenLifetimeParam)) {
            this.tokenLifetime.set(Double.valueOf(tokenLifetimeParam));
        }
        this.description.set(request.getParameter("description"));
        this.idHash.set(Integer.toString(this.clinicName.get().hashCode()));
        return true;
    }

    /**
     * Get a unique display name for a cards:ClinicMapping node, appending numbers to the end if one already exists.
     *
     * @param resolver Resource resolver to use
     * @param displayName Given display name to check for duplicates for
     * @return Unique display name
     */
    public String getUniqueDisplayName(final ResourceResolver resolver, String displayName)
        throws RepositoryException
    {
        // Pre-sanitize the name
        String sanitizedName = displayName.replaceAll("'", "''");

        // Query for similar names
        String query = "SELECT * FROM [cards:ClinicMapping] as c WHERE c.'displayName' LIKE '"
            + sanitizedName + "%'";
        Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");

        // Determine what names currently exist
        Set<Integer> foundNames = new HashSet<>();
        boolean noNumberValid = true;
        Pattern numberRegex = Pattern.compile(displayName + " ([\\d]+)");
        while (results.hasNext()) {
            String name = results.next().adaptTo(Node.class).getProperty("displayName").getString();

            Matcher match = numberRegex.matcher(name);
            if (match.find()) {
                foundNames.add(Integer.parseInt(match.group(1)));
            } else if (displayName.equals(name)) {
                noNumberValid = false;
            }
        }

        // Determine if we can use the display name as-is
        if (noNumberValid) {
            return displayName;
        }

        // Find the first number i that works if we stick it after the display name
        for (int i = 1; i < foundNames.size(); i++) {
            if (!foundNames.contains(i)) {
                return displayName + " " + String.valueOf(i);
            }
        }
        return displayName + " " + String.valueOf(foundNames.size() + 1);
    }

    /**
     * Return an error code to the POST request, letting the user know that something is wrong.
     *
     * @param response object to send response through
     * @param reason reason to give to user
     */
    private void returnError(final SlingHttpServletResponse response, String reason)
    {
        LOGGER.error(reason);
        try {
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            Writer out = response.getWriter();
            JsonGenerator generator = Json.createGenerator(out);
            generator.writeStartObject();
            generator.write("error", reason);
            generator.writeEnd();
            generator.flush();
        } catch (IOException e) {
            LOGGER.error("Furthermore, IOException occurred while returning response to user: {}", e.getMessage(), e);
        }
    }

    /**
     * Create a cards:ClinicMapping node.
     *
     * @param resolver Resource resolver to use
     */
    private void createClinicMapping(final ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        final Resource parentResource = resolver.getResource("/Survey/ClinicMapping");

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("clinicName", this.clinicName.get());
        params.put("displayName", this.displayName.get());
        params.put("sidebarLabel", this.sidebarLabel.get());
        params.put("survey", this.surveyID.get());
        params.put("emergencyContact", this.emergencyContact.get());
        params.put(ClinicsServlet.DESCRIPTION_FIELD, this.description.get());
        params.put(ClinicsServlet.PRIMARY_TYPE_FIELD, "cards:ClinicMapping");
        if (this.tokenLifetime.get() != null) {
            params.put("tokenLifetime", this.tokenLifetime.get());
        }
        resolver.create(parentResource, this.idHash.get(), params);
    }

    /**
     * Create a Group with access to the clinic.
     *
     * @param resolver Resource resolver to use
     * @return the created Group
     * @throws RepositoryException if accessing the repository fails
     */
    private Group createGroup(final ResourceResolver resolver) throws RepositoryException
    {
        final Session session = resolver.adaptTo(Session.class);
        if (!(session instanceof JackrabbitSession)) {
            return null;
        }
        final JackrabbitSession jsession = (JackrabbitSession) session;
        final UserManager um = jsession.getUserManager();
        return um.createGroup(this.clinicName.get());
    }

    /**
     * Create a cards:Extension node for the sidebar.
     *
     * @param resolver Resource resolver to use
     * @param clinicGroup the Group corresponding to this clinic
     */
    private void createSidebar(final ResourceResolver resolver, final Group clinicGroup)
        throws RepositoryException, PersistenceException
    {
        final Resource parentResource = resolver.getResource("/Extensions/Sidebar");
        final Resource sidebarEntry = resolver.create(parentResource, this.idHash.get(), Map.of(
            "cards:extensionPointId", "cards/coreUI/sidebar/entry",
            "cards:extensionName", this.sidebarLabel.get(),
            "cards:targetURL", "/content.html/Dashboard/" + this.idHash.get(),
            "cards:icon", "asset:patient-portal.clinicIcon.js",
            "cards:defaultOrder", 10,
            ClinicsServlet.PRIMARY_TYPE_FIELD, "cards:Extension"));
        if (clinicGroup != null) {
            Session session = resolver.adaptTo(Session.class);
            this.permissionsManager.addAccessControlEntry(sidebarEntry.getPath(), false,
                EveryonePrincipal.getInstance(), new String[] { Privilege.JCR_ALL }, null, session);
            this.permissionsManager.addAccessControlEntry(sidebarEntry.getPath(), true, clinicGroup.getPrincipal(),
                new String[] { Privilege.JCR_READ }, null, session);
        }
    }

    /**
     * Create a dashboard extension for the new clinic.
     *
     * @param resolver Resource resolver to use
     */
    private void createDashboardExtension(final ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        final Resource parentResource = resolver.getResource("/apps/cards/ExtensionPoints");
        resolver.create(parentResource, "DashboardViews" + this.idHash.get(), Map.of(
            ClinicsServlet.PRIMARY_TYPE_FIELD, "cards:ExtensionPoint",
            "cards:extensionPointId", "clinics/dashboard/" + this.idHash.get(),
            "cards:extensionPointName", this.displayName.get() + " questionnaires dashboard"));
    }

    /**
     * Update the clinic.names field of the given configuration with a new clinic name.
     *
     * @param config An OSGI config object for an instance of a proms ImportConfig
     * @param newClinicName An new clinic's name to add
     */
    public void insertNewClinic(Configuration config, String newClinicName) throws IOException
    {
        String[] clinicNames = (String[]) config.getProperties().get("clinic.names");
        String[] updatedClinicNames = Arrays.copyOf(clinicNames, clinicNames.length + 1);
        updatedClinicNames[clinicNames.length] = newClinicName;

        // Create a dictionary to contain the update request
        Dictionary<String, Object> updateDictionary = new Hashtable<>();
        updateDictionary.put("clinic.names", updatedClinicNames);
        config.update(updateDictionary);
    }

    /**
     * Remove any data from the ThreadLocal state.
     */
    private void cleanup()
    {
        this.clinicName.remove();
        this.description.remove();
        this.displayName.remove();
        this.emergencyContact.remove();
        this.tokenLifetime.remove();
        this.idHash.remove();
        this.sidebarLabel.remove();
        this.surveyID.remove();
    }
}
