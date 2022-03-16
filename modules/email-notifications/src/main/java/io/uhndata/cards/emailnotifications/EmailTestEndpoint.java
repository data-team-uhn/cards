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
package io.uhndata.cards.emailnotifications;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.messaging.mail.MailService;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "cards/Homepage" },
    selectors = { "emailtest" })
public final class EmailTestEndpoint extends SlingSafeMethodsServlet
{
    @Reference
    private MailService mailService;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        final Writer out = response.getWriter();
        final String subject = "CARDS-UHN Test Message";
        final String text = "Here is a test message from CARDS at the University Health Network";

        //Ensure that this can only be run when logged in as admin
        final String remoteUser = request.getRemoteUser();
        if (remoteUser == null || !"admin".equals(remoteUser)) {
            //admin login required
            response.setStatus(403);
            out.write("Only admin can perform this operation.");
            return;
        }

        final String fromEmail = request.getParameter("fromEmail");
        final String fromName = request.getParameter("fromName");
        final String toEmail = request.getParameter("toEmail");
        final String toName = request.getParameter("toName");
        final boolean isHtml = "true".equals(request.getParameter("isHtml"));
        if (fromEmail == null || fromName == null || toEmail == null || toName == null) {
            //Missing parameters
            response.setStatus(400);
            out.write("Missing required URL parameters");
            return;
        }

        try {
            MimeMessage message;
            if (isHtml) {
                message = this.mailService.getMessageBuilder()
                    .from(fromEmail, fromName)
                    .to(toEmail, toName)
                    .replyTo(fromEmail)
                    .subject(subject)
                    .text(text)
                    .html("<html><head><title>Rich Text</title></head><body><p>" + text + "</p></body></html>")
                    .build();
            } else {
                message = this.mailService.getMessageBuilder()
                    .from(fromEmail, fromName)
                    .to(toEmail, toName)
                    .replyTo(fromEmail)
                    .subject(subject)
                    .text(text)
                    .build();
            }

            this.mailService.sendMessage(message);
            response.setStatus(200);
            out.write("Email prepared for sending");
        } catch (MessagingException e) {
            response.setStatus(500);
            out.write("Server error");
        }
    }
}
