package main.java.ca.sickkids.ccm.lfs.commons.internal;

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

import java.io.IOException;
import java.io.Writer;

import javax.json.JsonObject;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Component;

/**
 * Testing adapter.
 *
 * @version $Id$
 */

@Component(service = { Servlet.class })
@SlingServletPaths(value = { "/testadapter" })

public class Testservlet extends SlingSafeMethodsServlet
{
    /**
     *
     */
    private static final long serialVersionUID = 3966835717257681977L;

    @Override
    public void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException
    {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Writer out = response.getWriter();

        try {
            Resource r = request.getResourceResolver()
                .getResource("/db/dummy2");
            ResourceToJsonAdapter adapter = new ResourceToJsonAdapter();
            JsonObject resp = adapter.getAdapter(r, JsonObject.class);

            out.write(resp.toString());
        } finally {
            out.close();
        }
    }
}
