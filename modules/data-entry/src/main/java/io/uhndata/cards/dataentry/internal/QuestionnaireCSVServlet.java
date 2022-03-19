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
package io.uhndata.cards.dataentry.internal;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.CSVString;

/**
 * Base class for adapting a Questionnaire and it's associated Form resources to a CSV-based format.
 *
 * @version $Id$
 */

@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = { "cards/Questionnaire" }, extensions = { "csv" }, methods = { "GET"})
public class QuestionnaireCSVServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = -677311295300436475L;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException
    {
        Resource questionnaire = request.getResource();
        final String csvPath = questionnaire.getPath() + ".data"
            + questionnaire.getResourceMetadata().getResolutionPathInfo();
        CSVString csv = questionnaire.getResourceResolver().resolve(csvPath).adaptTo(CSVString.class);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        response.addHeader("Content-disposition", "attachment; filename=" + questionnaire.getName()
            + "_" + dateFormat.format(new Date()) + ".csv");
        response.getWriter().write(csv.getData());
    }
}
