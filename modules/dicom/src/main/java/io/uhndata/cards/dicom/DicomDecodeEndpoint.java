/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.dicom;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.TagUtils;
import org.osgi.service.component.annotations.Component;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/Homepage" },
    selectors = { "dicomtest" },
    methods = { "POST" })
public final class DicomDecodeEndpoint extends SlingAllMethodsServlet
{
    @Override
    public void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException
    {
        Writer out = response.getWriter();

        //Ensure that this can only be run when logged in as admin
        final String remoteUser = request.getRemoteUser();
        if (remoteUser == null || !"admin".equals(remoteUser)) {
            //admin login required
            response.setStatus(403);
            out.write("Only admin can perform this operation.");
            return;
        }

        RequestParameter uploadedDicom = request.getRequestParameter("dicom");
        InputStream dicomStream = uploadedDicom.getInputStream();
        DicomInputStream dis = new DicomInputStream(dicomStream);
        Attributes attributes = dis.readDataset();
        int[] tags = attributes.tags();
        response.setStatus(200);
        response.setContentType("application/json");
        JsonGenerator jsonGen = Json.createGenerator(out);
        jsonGen.writeStartArray();
        for (int tag : tags) {
            String tagAddress = TagUtils.toString(tag);
            String tagValue = attributes.getString(tag);
            jsonGen.writeStartObject();
            jsonGen.write("Tag Address", tagAddress);
            jsonGen.write("Tag Value", tagValue);
            jsonGen.writeEnd();
        }
        jsonGen.writeEnd();
        jsonGen.close();
        dis.close();
        dicomStream.close();
        out.close();
    }
}
