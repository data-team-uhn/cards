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
package io.uhndata.cards.resolverProvider;

import org.apache.sling.api.resource.ResourceResolver;

/**
 * Wrapper around {@code ResourceResolverFactory.getThreadResourceResolver()} that allows explicitly setting a new
 * {@code ResourceResolver} for the current thread. This is needed because
 * {@code ResourceResolverFactory.getThreadResourceResolver()} does not support service user sessions, only actual user
 * login session, which means that code that requires a thread session will fail to run within non-login threads. For
 * code that needs a resource resolver, this is a direct replacement for
 * {@code ResourceResolverFactory.getThreadResourceResolver()}, simply change the service reference from
 * {@code ResourceResolverFactory} to {@code ThreadResourceResolverProvider}. For code that creates a new session,
 * {@code rrp.push(resolver)} needs to be called to register the new resource resolver in the thread, and
 * {@code rrp.pop()} needs to be called when the resource resolver is discarded. The thread resolver is inherited into
 * new threads.
 *
 * @version $Id$
 */
public interface ThreadResourceResolverProvider
{
    /**
     * Get the latest resource resolver registred for this thread, with fallback to the default thread resource resolver
     * provided by {@code ResourceResolverFactory} if no explicit resolver has been set up. This may return null if
     * there is no thread resource resolver either.
     *
     * @return a resource resolver or {@code null}
     */
    ResourceResolver getThreadResourceResolver();

    /**
     * Push a new resource resolver to be returned. When the resource resolver is to be destroyed, {@link #pop()} must
     * be called to remove the resource resolver from the thread.
     *
     * @param resolver a new resource resolver to be used in this thread until
     */
    void push(ResourceResolver resolver);

    /**
     * Remove the latest resource resolver set for this thread.
     */
    void pop();
}
