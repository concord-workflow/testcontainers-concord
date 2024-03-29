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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiException;

public class ProcessBuilder {

    private final ApiClient client;
    private final Processes processes;

    private Payload payload;
    private boolean streamLogs;

    public ProcessBuilder(ApiClient client, Processes processes) {
        this.client = client;
        this.processes = processes;
    }

    /**
     * Payload to use.
     */
    public ProcessBuilder payload(Payload payload) {
        this.payload = payload;
        return this;
    }

    /**
     * If {@code true} the process' log will be streamed into stdout.
     */
    public ProcessBuilder streamLogs(boolean streamLogs) {
        this.streamLogs = streamLogs;
        return this;
    }

    public ConcordProcess start() throws ApiException {
        if (this.payload == null) {
            throw new IllegalStateException("'payload' is required");
        }

        ConcordProcess p = processes.start(payload);

        if (this.streamLogs) {
            ProcessLogStreamers.start(client, p.instanceId());
        }

        return p;
    }
}
