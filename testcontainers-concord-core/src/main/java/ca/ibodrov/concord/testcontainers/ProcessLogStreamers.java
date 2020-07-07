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

import com.walmartlabs.concord.ApiClient;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProcessLogStreamers {

    private static final ThreadLocal<ProcessLogStreamers> instance = ThreadLocal.withInitial(ProcessLogStreamers::new);

    public static void start(ApiClient client, UUID instanceId) {
        ProcessLogStreamers s = instance.get();
        s.doStart(client, instanceId);
    }

    public static void stop() {
        ProcessLogStreamers s = instance.get();
        s.doStop();
    }

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private void doStart(ApiClient client, UUID instanceId) {
        executor.submit(new ProcessLogStreamer(client, instanceId));
    }

    private void doStop() {
        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
