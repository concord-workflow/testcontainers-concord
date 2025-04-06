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

import com.walmartlabs.concord.client2.*;

public class Projects {

    private final ProjectsApi projectApi;

    public Projects(ApiClient apiClient) {
        this.projectApi = new ProjectsApi(apiClient);
    }

    public ProjectOperationResponse create(String orgName, String projectName) throws ApiException {
        return create(orgName, projectName, true); // true to maintain backward compatibility
    }

    public ProjectOperationResponse create(String orgName, String projectName, boolean allowPayloads) throws ApiException {
        ProjectEntry projectEntry = new ProjectEntry()
                .name(projectName)
                .orgName(orgName)
                .acceptsRawPayload(Boolean.TRUE)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.TEAM_MEMBERS)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC);

        var project = projectApi.createOrUpdateProject(orgName, projectEntry);

        if (allowPayloads) {
            projectApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                    .orgName(orgName)
                    .teamName("default")
                    .level(ResourceAccessEntry.LevelEnum.OWNER));
        }

        return project;
    }
}
