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

package ca.sickkids.ccm.lfs.cardiacrehab.internal.serialize;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.json.JsonObject;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class NightlyExportTask implements Runnable
{
    /** Default log. */
    protected static final Logger LOGGER = LoggerFactory.getLogger(NightlyExport.class);

    /** The Resource Resolver for the current request. */
    private final ResourceResolverFactory resolverFactory;

    NightlyExportTask(final ResourceResolverFactory resolverFactory)
    {
        this.resolverFactory = resolverFactory;
    }

    @Override
    public void run()
    {
        LOGGER.info("Executing NightlyExport");
        Date date = new Date();
        String fileDateString = new SimpleDateFormat("yyyyMMdd").format(date);

        final Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        String requestDateString = new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());

        Set<String> changedSubjects = this.getChangedSubjects(requestDateString);

        for (String subjectId : changedSubjects) {
            SubjectContents subjectContents = getSubjectContents(subjectId, requestDateString);
            if (subjectContents != null) {
                String filename = String.format("%s_formData_%s.json", subjectId, fileDateString);
                this.output(subjectContents, filename, fileDateString);
            }
        }
    }

    private final class SubjectContents
    {
        private String data;
        private String url;

        SubjectContents(String data, String url)
        {
            this.data = data;
            this.url = url;
        }

        public String getData()
        {
            return this.data;
        }

        public String getUrl()
        {
            return this.url;
        }
    }

    private Set<String> getChangedSubjects(String requestDateString)
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Set<String> subjects = new HashSet<>();
            String query = String.format(
                "SELECT subject.* FROM [lfs:Form] AS form INNER JOIN [lfs:Subject] AS subject"
                    + " ON form.'subject'=subject.[jcr:uuid] WHERE form.[jcr:created] >= '%s'",
                requestDateString
            );

            Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
            while (results.hasNext()) {
                try {
                    String subjectId = results.next().adaptTo(Node.class).getName();
                    subjectId = subjectId.substring(subjectId.lastIndexOf("/") + 1);
                    subjects.add(subjectId);
                } catch (RepositoryException e) {
                    LOGGER.warn("Failed to retrieve name of updated subject: {}", e.getMessage(), e);
                }
            }
            return subjects;
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        }
        return Collections.emptySet();
    }

    private SubjectContents getSubjectContents(String subjectId, String requestDateString)
    {
        String subjectDataUrl = String.format(
            "/Subjects/%s.data.deep.bare.-identify.relativeDates.dataFilter:createdAfter=%s",
            subjectId,
            requestDateString
        );
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Resource subjectData = resolver.resolve(subjectDataUrl);
            return new SubjectContents(subjectData.adaptTo(JsonObject.class).toString(), subjectDataUrl);
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
            return null;
        }
    }

    private void output(SubjectContents input, String filename, String dateString)
    {
        String path = String.format("%s/cards-exports/%s/", System.getProperty("user.home"), dateString);
        File directory = new File(path);
        directory.mkdirs();
        try (FileWriter file = new FileWriter(path + filename)) {
            file.write(input.getData());
            LOGGER.info("Exported {} to {}, size {}B", input.getUrl(), path, directory.length());
        } catch (IOException e) {
            LOGGER.error("Failed to perform the nightly export", e.getMessage(), e);
        }
    }
}
