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
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.scripting.sightly.pojo.Use;

/**
 * A HTL Use-API that can set the response status code. This is needed because HTL cannot call methods that require
 * parameters, only simple getter-like methods. To use this API, simply place the following code in a HTL file,
 * replacing with the needed status code method:
 * <p>
 * <tt>
 * &lt;sly data-sly-use.statusCode="io.uhndata.cards.scripting.StatusCodeSetter"&gt;${statusCode.created}&lt;/sly&gt;
 * </tt>
 * </p>
 *
 * @version $Id$
 */
public class StatusCodeSetter implements Use
{
    private SlingHttpServletResponse response;

    @Override
    public void init(Bindings bindings)
    {
        this.response = (SlingHttpServletResponse) bindings.get("response");
    }

    /** Set the response status code to 200 OK. */
    public void ok()
    {
        this.response.setStatus(HttpServletResponse.SC_OK);
    }

    /** Set the response status code to 201 Created. */
    public void created()
    {
        this.response.setStatus(HttpServletResponse.SC_CREATED);
    }

    /** Set the response status code to 202 Accepted. */
    public void accepted()
    {
        this.response.setStatus(HttpServletResponse.SC_ACCEPTED);
    }

    /** Set the response status code to 204 No Content. */
    public void noContent()
    {
        this.response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    /** Set the response status code to 400 Bad Request. */
    public void badRequest()
    {
        this.response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }

    /** Set the response status code to 401 Unauthorized. */
    public void unauthorized()
    {
        this.response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    /** Set the response status code to 403 Forbidden. */
    public void forbidden()
    {
        this.response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    /** Set the response status code to 404 Not Found. */
    public void notFound()
    {
        this.response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    /** Set the response status code to 405 Method Not Allowed. */
    public void methodNotAllowed()
    {
        this.response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    /** Set the response status code to 406 Not Acceptable. */
    public void notAcceptable()
    {
        this.response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
    }

    /** Set the response status code to 409 Conflict. */
    public void conflict()
    {
        this.response.setStatus(HttpServletResponse.SC_CONFLICT);
    }

    /** Set the response status code to 423 Locked. */
    public void locked()
    {
        this.response.setStatus(423);
    }

    /** Set the response status code to 500 Internal Server Error. */
    public void internalServerError()
    {
        this.response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    /** Set the response status code to 501 Not Implemented. */
    public void notImplemented()
    {
        this.response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
    }
}
