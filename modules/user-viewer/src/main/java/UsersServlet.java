import java.io.IOException;
//import java.io.PrintWriter;
import java.io.Writer;

import javax.json.JsonException;
import javax.json.stream.JsonGenerator;
import javax.json.Json;

import javax.servlet.*;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;

import java.security.Principal;
import org.apache.jackrabbit.api.security.principal.ItemBasedPrincipal;
import org.apache.jackrabbit.api.security.principal.PrincipalIterator;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalManagerImpl;
import org.apache.jackrabbit.oak.spi.security.principal.PrincipalProvider;
import org.apache.jackrabbit.oak.spi.security.principal.EmptyPrincipalProvider;

import org.osgi.service.component.annotations.Component;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;

// This property type annotation is recommended, and is permissible because we have the maven-bundle-plugin version 4.0.0 or newer
@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = "/home/users", methods = "GET")

public class UsersServlet extends SlingSafeMethodsServlet {

	private long getLongValueOrDefault(String stringValue, long defaultValue) {
		long value = defaultValue;
		if (!stringValue.isEmpty()) {
			try {
				value = Long.parseLong(stringValue);
			} catch (NumberFormatException exception) {
				value = defaultValue;
			}
		}
		return value;
	}

	@Override
	public void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
			throws ServletException, IOException {
		
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		Writer out = response.getWriter();

		try {
			String filter = request.getParameter("filter");

			long limit = getLongValueOrDefault(request.getParameter("limit"), 10);
			long offset = getLongValueOrDefault(request.getParameter("offset"), 0);

			PrincipalProvider provider = EmptyPrincipalProvider.INSTANCE;
			PrincipalManagerImpl userManager = new PrincipalManagerImpl(provider);
			
			PrincipalIterator userIterator = userManager.findPrincipals(filter, true, 3, offset, limit);
			// for the int parameter, use: 1 for non-group principals only, 2 for group principals only, 3 for all

			// Test Json Generation
			/*
			final JsonGenerator w = Json.createGenerator(out);
			w.writeStartObject();
			
			w.write("Hello", "Hello");
			w.writeEnd();
			w.flush();
			*/
			
			// Method 1: obtain members using methods
			
			final JsonGenerator w = Json.createGenerator(out);
			w.writeStartObject();


			while (userIterator.hasNext()) {
                Principal currentPrincipal = (Principal) userIterator.next();
                if (currentPrincipal.getClass() == ItemBasedPrincipal.class) {

				}
			}
			
			// Method 2: generate json using json renderer

		} finally {
			//w.close();
			out.close();
		}
	}
}