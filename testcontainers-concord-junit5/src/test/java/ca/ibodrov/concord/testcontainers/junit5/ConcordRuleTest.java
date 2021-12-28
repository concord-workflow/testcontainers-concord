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
import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import com.walmartlabs.concord.client.ProcessEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class ConcordRuleTest {

    @RegisterExtension
    public static ConcordRule concord = new ConcordRule()
            .mode(Concord.Mode.DOCKER)
            .useLocalMavenRepository(true);

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
                        .arg("name", nameValue));

        p.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        p.assertLog(".*Hello, " + nameValue + ".*");
    }
}
