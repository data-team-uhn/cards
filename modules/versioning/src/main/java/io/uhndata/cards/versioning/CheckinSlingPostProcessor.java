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
package io.uhndata.cards.versioning;

import java.util.List;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostProcessor;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SlingPostProcessor} to conditionally check-in Forms upon save.
 *
 * @version $Id$
 */
@Component
public class CheckinSlingPostProcessor implements SlingPostProcessor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CheckinSlingPostProcessor.class);

    @Override
    public void process(SlingHttpServletRequest request, List<Modification> changes) throws RepositoryException
    {
        RequestParameter doCheckin = request.getRequestParameter(":checkin");
        if (doCheckin != null) {
            LOGGER.debug("Running CheckinSlingPostProcessor::process");
            final Node n = request.getResource().adaptTo(Node.class);
            if (n.isNodeType("mix:lastModified")) {
                // At this point, all the changes are done in the session, not yet persisted, and trying to checkin the
                // node will fail. We must save first, although it is not recommended to do so in a PostProcessor.
                n.getSession().save();
                n.getSession().getWorkspace().getVersionManager().checkin(n.getPath());
                changes.add(Modification.onCheckin(n.getPath()));
            }
        }
    }
}
