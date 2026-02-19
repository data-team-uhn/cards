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
package io.uhndata.cards.healthcheck.internal;

import java.util.HashSet;
import java.util.Set;

import org.apache.felix.hc.api.FormattingResultLog;
import org.apache.felix.hc.api.HealthCheck;
import org.apache.felix.hc.api.Result;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 * Check that there are no duplicate jars.
 *
 * @version $Id$
 * @since 0.9.37
 */
@Component(service = HealthCheck.class,
    property = { HealthCheck.TAGS + "=cards", HealthCheck.NAME + "=CARDS duplicate jars" }, immediate = true)
public class DuplicateJarsHealthCheck implements HealthCheck
{
    private BundleContext context;

    @Activate
    protected void activate(BundleContext bundleContext)
    {
        this.context = bundleContext;
    }

    @Override
    public Result execute()
    {
        final FormattingResultLog result = new FormattingResultLog();
        final Set<String> seen = new HashSet<>();
        int count = 0;
        final Bundle[] allBundles = this.context.getBundles();
        if (allBundles == null || allBundles.length == 0) {
            return new Result(Result.Status.HEALTH_CHECK_ERROR, "No bundles found!");
        }
        for (Bundle bundle : allBundles) {
            count++;
            if (!seen.add(bundle.getSymbolicName())) {
                result.warn("Possible duplicate jar: {}", bundle.getSymbolicName());
            }
        }
        result.info("Checked {} bundles", count);
        return new Result(result);
    }
}
