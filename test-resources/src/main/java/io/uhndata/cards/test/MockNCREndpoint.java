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
package io.uhndata.cards.test;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = { Servlet.class })
@SlingServletPaths(value = { "/ncr/annotate" })
public class MockNCREndpoint extends SlingSafeMethodsServlet
{
    private static final String MOCK_NCR_INPUT_1 = "The patient has heart disease and diabetes";
    private static final String MOCK_NCR_INPUT_2 = "The patient has renal cancer and myopia";
    private static final String MOCK_NCR_INPUT_3 = "The patient has experienced chest pain and shortness of breath";

    private static final String MOCK_NCR_OUTPUT_1 = "{\"matches\":"
        + " [{\"end\": 29, \"hp_id\": \"/Vocabularies/HP/HP0001627\","
        + " \"names\": [\"Abnormal heart morphology\", \"Abnormality of cardiac morphology\","
        + " \"Abnormality of the heart\", \"Abnormally shaped heart\", \"Cardiac abnormality\", \"Cardiac anomalies\","
        + " \"Congenital heart defect\", \"Congenital heart defects\"], \"score\": \"0.69478846\", \"start\": 16},"
        + " {\"end\": 42, \"hp_id\": \"/Vocabularies/HP/HP0000819\", \"names\": [\"Diabetes mellitus\"],"
        + " \"score\": \"0.91309816\", \"start\": 34}]}";

    private static final String MOCK_NCR_OUTPUT_2 = "{\"matches\":"
        + " [{\"end\": 28, \"hp_id\": \"/Vocabularies/HP/HP0009726\","
        + " \"names\": [\"Renal neoplasm\", \"Kidney cancer\", \"Neoplasia of the kidneys\", \"Renal neoplasia\","
        + " \"Renal tumors\"], \"score\": \"0.9697626\", \"start\": 16}, {\"end\": 39, \"hp_id\":"
        + " \"/Vocabularies/HP/HP0000545\", \"names\": [\"Myopia\", \"Close sighted\", \"Near sighted\","
        + " \"Near sightedness\", \"Nearsightedness\"], \"score\": \"0.9827367\","
        + " \"start\": 33}]}";

    private static final String MOCK_NCR_OUTPUT_3 = "{\"matches\":"
        + " [{\"end\": 62, \"hp_id\": \"/Vocabularies/HP/HP0002098\","
        + " \"names\": [\"Respiratory distress\", \"Breathing difficulties\", \"Difficulty breathing\","
        + " \"Respiratory difficulties\", \"Short of breath\", \"Shortness of breath\"], \"score\": \"0.9901055\","
        + " \"start\": 43}, {\"end\": 38, \"hp_id\": \"/Vocabularies/HP/HP0100749\", \"names\": [\"Chest pain\","
        + " \"Chest pain\", \"Thoracic pain\"], \"score\": \"0.9980915\", \"start\": 28}]}";

    @Reference
    private ResourceResolverFactory resolverFactory;

    private String getNCRAnnotation(String text)
    {
        if (text == null) {
            return "{\"matches\": []}";
        } else if (MOCK_NCR_INPUT_1.equals(text)) {
            return MOCK_NCR_OUTPUT_1;
        } else if (MOCK_NCR_INPUT_2.equals(text)) {
            return MOCK_NCR_OUTPUT_2;
        } else if (MOCK_NCR_INPUT_3.equals(text)) {
            return MOCK_NCR_OUTPUT_3;
        }
        return "{\"matches\": []}";
    }

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        final Writer out = response.getWriter();
        final String annotateText = request.getParameter("text");
        out.write(getNCRAnnotation(annotateText));
    }
}
