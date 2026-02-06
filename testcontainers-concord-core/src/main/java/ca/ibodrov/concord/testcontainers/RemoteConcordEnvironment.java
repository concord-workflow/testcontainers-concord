package ca.ibodrov.concord.testcontainers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2020 Ivan Bodrov
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class RemoteConcordEnvironment implements ConcordEnvironment {

    private static final Logger log = LoggerFactory.getLogger(RemoteConcordEnvironment.class);

    private final URI baseUrl;
    private final String apiToken;

    public RemoteConcordEnvironment(Concord<?> opts) {
        if (opts.extraContainerSupplier() != null) {
            log.warn("extraContainerSupplier is only supported in DOCKER mode");
        }

        this.baseUrl = URI.create(opts.apiBaseUrl());
        this.apiToken = opts.apiToken();
    }

    @Override
    public int apiPort() {
        return baseUrl.getPort();
    }

    @Override
    public String apiToken() {
        return apiToken;
    }

    @Override
    public void start() {
        // TODO verify the remove environment
    }

    @Override
    public void stop() {
    }
}
