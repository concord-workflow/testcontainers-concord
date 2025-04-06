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
import ca.ibodrov.concord.testcontainers.ContainerListener;
import ca.ibodrov.concord.testcontainers.ContainerType;
import ca.ibodrov.concord.testcontainers.Payload;
import com.walmartlabs.concord.client2.ProcessEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;

import static ca.ibodrov.concord.testcontainers.Utils.randomString;

public class ConcordRuleTest {

    private static final Logger log = LoggerFactory.getLogger(ConcordRuleTest.class);

    @RegisterExtension
    public static ConcordRule concord = new ConcordRule()
            .mode(Concord.Mode.DOCKER)
            .containerListener(new ContainerListener() {
                @Override
                public void afterStart(ContainerType type, Container<?> container) {
                    if (type == ContainerType.SERVER) {
                        log.info("Concord IT server login: {}/#/login?useApiKey=true", concord.apiBaseUrl());
                        log.info("Concord IT admin token: {}", concord.environment().apiToken());
                    }
                }
            })
            .useLocalMavenRepository(true);

    @Test
    void testSimpleFlow() throws Exception {
        String nameValue = "name_" + System.currentTimeMillis();

        String orgName = "org_" + randomString();
        concord.organizations().create(orgName);

        String projectName = "project_" + randomString();
        concord.projects().create(orgName, projectName);

        String yml = """
                flows:
                  default:
                    - log: Hello, ${name}!
                """;

        ConcordProcess p = concord.processes()
                .start(new Payload()
                        .concordYml(yml)
                        .org(orgName)
                        .project(projectName)
                        .arg("name", nameValue));

        p.waitForStatus(ProcessEntry.StatusEnum.FINISHED);
        p.assertLog(".*Hello, " + nameValue + ".*");
    }
}
