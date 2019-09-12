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
package ca.sickkids.ccm.lfs.vocabularies.spi;

/**
 * Interface which defines the basic methods that wrapper classes around ontology repository jsons should have for
 * useful information to be extracted from them.
 *
 * @version $Id$
 */
public interface SourceHandler
{
    /**
     * Returns the name of the ontology repository the wrapper class uses.
     *
     * @return String name of the ontology repository used
     */
    String getRepositoryName();

    /**
     * Returns the name of the identifier of the ontology the json represents.
     *
     * @return String identifier code of the ontology in the repository
     */
    String getIdentifier();

    /**
     * Returns the long-form name of the ontology the json represents.
     *
     * @return String long-form name of the ontology
     */
    String getName();

    /**
     * Returns the url of the ontology file.
     *
     * @return String url location of the ontology file to get
     */
    String getSourceLocation();
}
