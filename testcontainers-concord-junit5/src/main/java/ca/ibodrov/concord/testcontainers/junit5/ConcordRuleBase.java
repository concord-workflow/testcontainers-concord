package ca.ibodrov.concord.testcontainers.junit5;

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

import ca.ibodrov.concord.testcontainers.Concord;
import ca.ibodrov.concord.testcontainers.ConcordEnvironment;
import ca.ibodrov.concord.testcontainers.ProcessLogStreamers;
import org.junit.jupiter.api.extension.*;

public class ConcordRuleBase<T extends Concord<T>>
        extends Concord<T> implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, AfterEachCallback {

    private ConcordEnvironment env;
    private boolean isNonStatic = false;

    @Override
    public void beforeAll(ExtensionContext context) {
        startEnvIfRequired();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (env == null) {
            isNonStatic = true;
            startEnvIfRequired();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        stopEnvIfRunning();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (isNonStatic) {
            stopEnvIfRunning();
        }
    }

    private void stopEnvIfRunning() {
        if (env != null) {
            env.close();
            env = null;

            ProcessLogStreamers.stop();
        }
    }

    private void startEnvIfRequired() {
        if (env == null) {
            env = initEnvironment();
            env.start();
        }
    }
}
