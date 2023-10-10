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

import com.walmartlabs.concord.client2.ProcessEntry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class LocalTest {

    private static Concord<?> concord;

    @BeforeAll
    public static void setUp() {
        Concord<?> c = new Concord<>()
                .mode(Concord.Mode.LOCAL);

        c.start();

        concord = c;
    }

    @AfterAll
    public static void tearDown() {
        if (concord != null) {
            concord.close();
            concord = null;
        }
    }

    @Test
    public void testSimpleFlow() throws Exception {
        String nameValue = "name_" + System.currentTimeMillis();

        String yml = "" +
                "flows: \n" +
                "  default:\n" +
                "    - log: Hello, ${name}!";

        ConcordProcess p = concord.processes()
                .start(new Payload()
                        .concordYml(yml)
                        .parameter("arguments.name", nameValue));

        p.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        p.assertLog(".*Hello, " + nameValue + ".*");
    }
}
