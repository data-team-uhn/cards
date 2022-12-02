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
package io.uhndata.cards.utils.internal;

import java.util.List;
import java.util.Locale;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostProcessor;
import org.osgi.service.component.annotations.Component;

/**
 * For security purposes, deny uploading HTML or JavaScript files for anybody other than the admin.
 *
 * @version $Id$
 */
@Component
public class DenyScriptsSlingPostProcessor implements SlingPostProcessor
{
    @Override
    public void process(SlingHttpServletRequest request, List<Modification> changes) throws Exception
    {
        if ("admin".equalsIgnoreCase(request.getRemoteUser())) {
            return;
        }

        ResourceResolver rr = request.getResourceResolver();
        for (Modification m : changes) {
            final Resource r = rr.getResource(m.getSource());
            if (r == null || r.getResourceMetadata() == null) {
                continue;
            }
            final String contentType = r.getResourceMetadata().getContentType();
            if (contentType == null) {
                continue;
            }
            if (contentType.toLowerCase(Locale.ROOT).contains("script")) {
                throw new Exception("Script files are not allowed");
            }
            if (contentType.toLowerCase(Locale.ROOT).contains("html")) {
                throw new Exception("HTML files are not allowed");
            }
        }
    }
}
