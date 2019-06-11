/*
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
*/

import java.io.PrintWriter;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalQueryManager;
import org.apache.jackrabbit.api.security.principal.PincipalIterator;\
import java.security.Principal;

// This property type annotation is recommended, and is permissible because we have the maven-bundle-plugin version 4.0.0 or newer
@Component (service = {Servlet.class})
@SlingServletResourceTypes(
		resourceTypes="/home/users",
		methods= "GET",
		extensions="html"
		)

public class UsersServlet extends SlingSafeMethodsServlet {
    @Override
    public doGet (SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
    	response.setContentType("text/html;charset=UTF-8");
    	PrintWriter out = response.getWriter();
    	
    	try {
    		String filter = request.getParameter("filter");
            
    		long limit = request.getParameter("limit");
            if(limit == null || limit < 1) {
               limit = 10;
            }
    	    
            long offset = request.getParameter("offset");
            if (offset == null || offset < 0 || offset > limit) {
                offset = 0;
            }

            PrincipalQueryManager users-pqm = new PrincipalQueryManager;
            PrincipalIterator users-pi = users-pqm.findPrincipals(filter, true, 3, offset, limit); 
            // for the int parameter, use: 1 for non-group principals only, 2 for group principals only, 3 for all
            /*
            out.println("<html>");
            out.println("<head>");
            out.println("<title>User principal list. </title>");
            out.println("<body>");
            */
            while(user-pi.hasNext()) {
            	Principal currentPrincipal = users-pi.filter ;
            	if (currentPrincipal.getClass() == ItemBasedPrincipal.class) {
            		
            	}
            }
            /*
            out.println("</body>");
            out.println("</html>");
            */
            
            /*
             * For JSON: 
             * 
             * 
             */
    	} finally {
    		out.close();
    	}     
    }
}
/*
 * @SlingServlet(methods=

{"GET"}
,paths=

{"/home/users"}
)
class UsersServlet extends org.apache.sling.api.servlets.SlingSafeMethodsServlet

In doGet it can use org.apache.jackrabbit.oak.spi.security.principal.PrincipalQueryManager.findPrincipals(String, boolean, int, long, long) 
to retrieve the list of principals.

It accepts as parameters:

filter, string to search for
offset, number to start from, 0-based, default 0
limit, number of results to return, default 10
From the list of Principals returned, if they are instanceof ItemBasedPrincipal, then you can get the path to that principal using 
((ItemBasedPrincipal) principal).getPath().

To get a Resource from the path, you can do request.getResourceResolver().resolve(path).
 */