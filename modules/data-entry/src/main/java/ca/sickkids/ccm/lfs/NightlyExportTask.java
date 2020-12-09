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

package ca.sickkids.ccm.lfs;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.jcr.Node;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class NightlyExportTask implements Runnable
{
    /** Default log. */
    protected static final Logger LOGGER = LoggerFactory.getLogger(NightlyExport.class);

    /** The Resource Resolver for the current request. */
    private final ThreadLocal<ResourceResolver> resolver = new ThreadLocal<>();

    private void output(String input, String filename, String dateString)
    {
        String path = String.format("exports/%s/", dateString);
        try {
            File directory = new File(path);
            directory.mkdirs();
            FileWriter file = new FileWriter(path + filename);
            file.write(input);
            file.close();
        } catch (IOException e) {
            LOGGER.error("Failed to perform the nightly export", e.getMessage(), e);
        }
    }

    private Set<String> getChangedSubjects()
    {
        Set<String> subjects = new HashSet<String>();

        final Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        String yesterday = new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());

        String query = String.format("select n from [lfs:Question] as n where n.[jcr:created] >=%s", yesterday);

        Iterator<Resource> results = this.resolver.get().findResources(query, "JCR-SQL2");
        while (results.hasNext()) {
            try {
                String subjectId = results.next().adaptTo(Node.class).getProperty("subject").getString();
                subjects.add(subjectId);
                // TODO: Remove logging
                LOGGER.error(subjectId);
            } catch (Exception e) {
                // TODO: Handle
                // Do nothing for now
            }
        }

        return subjects;
    }

    public void run()
    {
        LOGGER.info("Executing NightlyExport");
        Date date = new Date();
        String dateString = new SimpleDateFormat("yyyyMMdd").format(date);

        Set<String> changedSubjects = this.getChangedSubjects();

        for (String subjectId : changedSubjects) {
            String subjectJson = "json contents";

            // TODO: Query endpoint from LFS-757 to set subject json.
            // Type String is a placeholder

            String filename = String.format("%s_formData_%s.json", subjectId, dateString);
            this.output(subjectJson, filename, dateString);

        }
    }
}
