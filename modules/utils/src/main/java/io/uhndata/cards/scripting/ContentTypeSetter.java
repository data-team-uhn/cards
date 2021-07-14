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
package io.uhndata.cards.scripting;

import javax.script.Bindings;

import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.scripting.sightly.pojo.Use;

/**
 * A HTL Use-API that can set the content type of the response. This is needed because HTL cannot call methods that
 * require parameters, only simple getter-like methods. To use this API, simply place the following code in a HTL file:
 * <p>
 * <tt>
 * &lt;sly data-sly-use.contentType="io.uhndata.cards.scripting.ContentTypeSetter"&gt;${contentType.html}&lt;/sly&gt;
 * </tt>
 * </p>
 *
 * @version $Id$
 */
public class ContentTypeSetter implements Use
{
    private SlingHttpServletResponse response;

    @Override
    public void init(Bindings bindings)
    {
        this.response = (SlingHttpServletResponse) bindings.get("response");
    }

    /** Set the content type to text/html. */
    public void html()
    {
        this.response.setContentType("text/html;charset=UTF-8");
    }

    /** Set the content type to application/javascript. */
    public void javascript()
    {
        this.response.setContentType("application/javascript;charset=UTF-8");
    }

    /** Set the content type to application/json. */
    public void json()
    {
        this.response.setContentType("application/json;charset=UTF-8");
    }

    /** Set the content type to text/csv. */
    public void csv()
    {
        this.response.setContentType("text/csv;charset=UTF-8");
    }
}
