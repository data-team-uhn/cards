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
package io.uhndata.cards.forms.internal;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;

import io.uhndata.cards.serialize.CSVString;

/**
 * Servlet that outputs all the Form data for a Questionnaire to a CSV.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = { "cards/Questionnaire" }, extensions = { "csv", "tsv" },
    methods = { "GET" })
public class QuestionnaireCSVServlet extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = -677311295300436475L;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException
    {
        final Resource questionnaire = request.getResource();
        final String csvPath = questionnaire.getPath() + ".data"
            + questionnaire.getResourceMetadata().getResolutionPathInfo();
        final CSVString csv = questionnaire.getResourceResolver().resolve(csvPath).adaptTo(CSVString.class);
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HHmm");
        response.addHeader("Content-disposition", "attachment; filename=" + questionnaire.getName()
            + "_" + dateFormat.format(new Date()) + ".csv");
        response.setCharacterEncoding("windows-1255");
        response.getWriter().write(csv.toString());
    }
}
