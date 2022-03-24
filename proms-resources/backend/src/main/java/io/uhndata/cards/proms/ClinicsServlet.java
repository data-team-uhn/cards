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
package io.uhndata.cards.proms;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = { "cards/ClinicMappingFolder" }, extensions = {
    "addNew" }, methods = { "POST" })
public class ClinicsServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = -5555906093850253193L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ClinicsServlet.class);

    private static final String IMPORT_FACTORY_PID = "io.uhndata.cards.proms.internal.importer.ImportConfig";

    @Reference
    private ConfigurationAdmin configAdmin;

    @Override
    public void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException
    {
        // Determine what new clinic we're adding
        String[] newClinics = request.getParameterValues(":newClinic");
        if (newClinics == null || newClinics.length <= 0) {
            // Nothing to do
            return;
        }

        // Grab the configuration to edit
        try {
            Configuration[] configs = this.configAdmin.listConfigurations(
                "(service.factoryPid=" + IMPORT_FACTORY_PID + ")");

            if (configs != null) {
                for (Configuration config : configs) {
                    this.insertNewClinic(config, newClinics);
                }
            }
        } catch (InvalidSyntaxException e) {
            // This can happen when the filter given to the listConfigurations call above is wrong
            // This shouldn't happen unless a typo was made in the value of IMPORT_FACTORY_PID
            this.returnError(response, "Invalid syntax in config search.");
        } catch (IOException e) {
            // This can happen while updating the properties of a configuration
            // Unknown how to handle this
            this.returnError(response, "Updating clinic configurations failed.");
        }
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
            response.setStatus(SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR);
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
     * Update the clinic.names field of the given configuration with a new clinic name.
     *
     * @param config An OSGI config object for an instance of a proms ImportConfig
     * @param newClinicNames An array of new clinic names to add
     */
    public void insertNewClinic(Configuration config, String[] newClinicNames) throws IOException
    {
        String[] clinicNames = (String[]) config.getProperties().get("clinic.names");
        String[] updatedClinicNames = Arrays.copyOf(clinicNames, clinicNames.length + newClinicNames.length);
        System.arraycopy(newClinicNames, 0, updatedClinicNames, clinicNames.length, newClinicNames.length);

        // Create a dictionary to contain the update request
        Dictionary<String, Object> updateDictionary = new Hashtable<String, Object>();
        updateDictionary.put("clinic.names", updatedClinicNames);
        config.update(updateDictionary);
    }
}
