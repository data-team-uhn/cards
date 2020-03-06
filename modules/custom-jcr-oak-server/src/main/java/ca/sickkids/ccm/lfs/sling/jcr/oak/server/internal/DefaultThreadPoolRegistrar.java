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
package ca.sickkids.ccm.lfs.sling.jcr.oak.server.internal;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.sling.commons.threads.ThreadPool;
import org.apache.sling.commons.threads.ThreadPoolManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(
    immediate = true)
public class DefaultThreadPoolRegistrar
{

    @Reference
    private ThreadPoolManager threadPoolManager;

    private ThreadPool threadPool;

    private ServiceRegistration<ThreadPool> serviceRegistration;

    public DefaultThreadPoolRegistrar()
    {
    }

    @Activate
    private void activate(final BundleContext bundleContext)
    {
        this.threadPool = this.threadPoolManager.get(ThreadPoolManager.DEFAULT_THREADPOOL_NAME);
        final Dictionary<String, String> properties = new Hashtable<>();
        properties.put("name", this.threadPool.getName());
        this.serviceRegistration = bundleContext.registerService(ThreadPool.class, this.threadPool, properties);
    }

    @Deactivate
    private void deactivate()
    {
        if (this.serviceRegistration != null) {
            this.serviceRegistration.unregister();
            this.serviceRegistration = null;
        }
        this.threadPoolManager.release(this.threadPool);
        this.threadPool = null;
    }

}
