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

package ca.sickkids.ccm.lfs.vocabularies;

/**
 * 
 * Container class to hold VocabularyIndexerServlet parameters.
 * @version $Id$
 *
 */

public class VocabularyParams 
{
    public String identifier;
    public String source;
    public String name;
    public String version;
    public String website;
    public String citation;
	
    public VocabularyParams (String identifier, String source, String name, String version, String website, String citation) 
	{
		this.identifier = identifier;
		this.source = source;
		this.name = name;
		this.version = version;
		this.website = website;
		this.citation = citation;

		if (this.identifier == null) {
			this.identifier = "";
		}

		if (this.source == null) {
			this.source = "";
		}

		if (this.name == null) {
			this.name = "";
		}

		if (this.version == null) {
			this.version = "";
		}

		if (this.website == null) {
			this.website = "";
		}
		
		if (this.citation == null) {
			this.citation = "";
		}
	}
	/*
	public void setDefaultVersion () {
		if (version == null || version == "") {
			this.version = 
		}
	}
	
	public void */
	public boolean hasIdentifier () 
    {
        return this.identifier == "" || this.identifier == null ? true : false;
    }

	public boolean hasSource() 
	{
        return this.source == "" || this.source == null ? true : false;
    }

	public boolean hasName () 
    {
        return this.name == "" || this.name == null ? true : false;
    }

	public boolean hasVersion () 
    {
        return this.version == "" || this.version == null ? true : false;
    }

	public boolean hasWebsite () 
    {
        return this.website.contentEquals("") || this.website.contentEquals(null) ? true : false;
    }

	public boolean hasCitation () 
    {
        return this.citation.contentEquals("")|| this.citation.contentEquals(null) ? true : false;
    }
}
