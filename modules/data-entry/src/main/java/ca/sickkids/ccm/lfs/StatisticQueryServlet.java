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
package ca.sickkids.ccm.lfs;

import java.io.IOException;
import java.io.Writer;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "lfs/Statistic" },
    selectors = { "query" })
public class StatisticQueryServlet extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = 2558430802619674046L;

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticQueryServlet.class);

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        Node statistic = request.getResource().adaptTo(Node.class);

        try {
            // Steps to returning the calculated statistic:
            // Grab the question that has data for the given x-axis (xVar)
            Node question = statistic.getProperty("xVar").getNode();

            // Grab all answers that have this question filled out
            Iterator<Resource> answers = getAnswersToQuestion(question, request.getResourceResolver());

            // Filter those answers based on whether or not their form's subject is of the correct SubjectType (yVar)
            answers = filterAnswersToSubjectType(answers, statistic.getProperty("yVar").getNode());

            // Aggregate our counts (TODO: Is this still necessary? Check with frontend)
            Map<String, Integer> counts = aggregateCounts(answers);

            // Return to user
            // Convert our HashMap into a JsonObject
            JsonObjectBuilder builder = Json.createObjectBuilder();
            Iterator<String> keysMap = counts.keySet().iterator();
            while (keysMap.hasNext()) {
                String key = keysMap.next();
                builder.add(key, counts.get(key));
            }

            // Write the output
            final Writer out = response.getWriter();
            out.write(builder.build().toString());

        } catch (RepositoryException e) {
            LOGGER.error("Failed to obtain statistic: {}", e.getMessage(), e);
        }
    }

    /**
     * Get all answers that have a given question filled out.
     *
     * @param question The question node that the answers is to
     * @param resolver A reference to a ResourceResolver
     * @return An iterator of Resources that are Nodes of answers to the given question
     */
    private Iterator<Resource> getAnswersToQuestion(Node question, ResourceResolver resolver) throws RepositoryException
    {
        final StringBuilder query =
            // We select all answers that answer our question
            new StringBuilder("select n from [lfs:Answer] as n where n.'question'='"
                + question.getIdentifier() + "'");
        return resolver.findResources(query.toString(), Query.JCR_SQL2);
    }

    /**
     * Filter the given iterator of resources to only include resources whose parent is a Form, whose
     * Subject's type is equal to the given subjectType.
     *
     * @param answers The iterator of answers to filter
     * @param subjectType A subjectType to filter for
     * @return An iterator of Resources that are Nodes of answers for the given subjectType
     */
    private Iterator<Resource> filterAnswersToSubjectType(Iterator<Resource> answers, Node subjectType)
        throws RepositoryException
    {
        Deque<Resource> newAnswers = new LinkedList<>();
        String correctType = subjectType.getIdentifier();
        while (answers.hasNext()) {
            Resource answer = answers.next();
            final Node answerSubjectType = getParentSubject(answer.adaptTo(Node.class));
            if (answerSubjectType.getIdentifier().equals(correctType)) {
                newAnswers.push(answer);
            }
        }
        return newAnswers.iterator();
    }

    /**
     * Aggregate the given Iterator of answers to a map of counts for each unique value.
     *
     * @param answers An iterator of lfs:Answer objects
     * @return A map of values -> counts
     */
    private Map<String, Integer> aggregateCounts(Iterator<Resource> answers) throws RepositoryException
    {
        Map<String, Integer> counts = new HashMap<>();
        while (answers.hasNext()) {
            Node answer = answers.next().adaptTo(Node.class);
            String value = answer.getProperty("value").getString();

            // If this already exists in our counts dict, we add 1 to its value
            // Otherwise, set it to 1 count
            if (counts.containsKey(value)) {
                counts.put(value, counts.get(value) + 1);
            } else {
                counts.put(value, 1);
            }
        }
        return counts;
    }

    /**
     * Get the parent subject type of a given lfs:Answer node.
     *
     * @param node A node corresponding to an lfs:Answer
     * @return A node corresponding to the parent subject type
     */
    public Node getParentSubject(Node node) throws RepositoryException
    {
        // Recursively go through our parents until we find a lfs:Form node
        // If we somehow reach the top level, return an error
        Node answerParent = node.getParent();
        while (!"lfs:Form".equals(answerParent.getPrimaryNodeType().getName()) && answerParent.getDepth() != 0) {
            answerParent = answerParent.getParent();
        }

        // If we never find a form by going upwards, this lfs:Answer is malformed
        if (answerParent.getDepth() == 0) {
            String error = String.format("Tried to obtain the parent lfs:Form for node {} but failed to find one",
                node.getPath());
            throw new RepositoryException(error);
        }

        Node answerSubject = answerParent.getProperty("subject").getNode();
        return answerSubject.getProperty("type").getNode();
    }
}
