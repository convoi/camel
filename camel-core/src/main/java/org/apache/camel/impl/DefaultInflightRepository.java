/**
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
package org.apache.camel.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.support.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implement which just uses a counter
 *
 * @version 
 */
public class DefaultInflightRepository extends ServiceSupport implements InflightRepository  {

    private static final transient Logger LOG = LoggerFactory.getLogger(DefaultInflightRepository.class);
    private final AtomicInteger totalCount = new AtomicInteger();
    // use endpoint key as key so endpoints with lenient properties is registered using the same key (eg dynamic http endpoints)
    private final Map<String, AtomicInteger> endpointCount = new HashMap<String, AtomicInteger>();

    public void add(Exchange exchange) {
        int count = totalCount.incrementAndGet();
        LOG.trace("Total {} inflight exchanges. Last added: {}", count, exchange.getExchangeId());

        if (exchange.getFromEndpoint() == null) {
            return;
        }

        String key = exchange.getFromEndpoint().getEndpointKey();
        // need to be synchronized as we can concurrently add/remove
        synchronized (endpointCount) {
            AtomicInteger existing = endpointCount.get(key);
            if (existing != null) {
                existing.incrementAndGet();
            } else {
                endpointCount.put(key, new AtomicInteger(1));
            }
        }
    }

    public void remove(Exchange exchange) {
        int count = totalCount.decrementAndGet();
        LOG.trace("Total {} inflight exchanges. Last removed: {}", count, exchange.getExchangeId());

        if (exchange.getFromEndpoint() == null) {
            return;
        }

        String key = exchange.getFromEndpoint().getEndpointKey();
        // need to be synchronized as we can concurrently add/remove
        synchronized (endpointCount) {
            AtomicInteger existing = endpointCount.get(key);
            if (existing != null) {
                if (existing.decrementAndGet() <= 0) {
                    endpointCount.remove(key);
                }
            }
        }
    }

    /**
     * Internal only - Used for testing purpose.
     */
    int endpointSize() {
        return endpointCount.size();
    }

    public int size() {
        return totalCount.get();
    }

    public int size(Endpoint endpoint) {
        AtomicInteger answer = endpointCount.get(endpoint.getEndpointKey());
        return answer != null ? answer.get() : 0;
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
        int count = size();
        if (count > 0) {
            LOG.warn("Shutting down while there are still " + count + " in flight exchanges.");
        } else {
            LOG.info("Shutting down with no inflight exchanges.");
        }
        synchronized (endpointCount) {
            endpointCount.clear();
        }
    }
}
