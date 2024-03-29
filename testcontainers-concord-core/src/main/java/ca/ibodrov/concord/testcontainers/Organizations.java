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
import com.walmartlabs.concord.client2.CreateOrganizationResponse;
import com.walmartlabs.concord.client2.GenericOperationResult;
import com.walmartlabs.concord.client2.OrganizationEntry;
import com.walmartlabs.concord.client2.OrganizationsApi;

public class Organizations {

    private final OrganizationsApi organizationsApi;

    public Organizations(ApiClient apiClient) {
        this.organizationsApi = new OrganizationsApi(apiClient);
    }

    public CreateOrganizationResponse create(String orgName) throws ApiException {
        OrganizationEntry orgEntry = new OrganizationEntry();
        orgEntry.setName(orgName);
        return organizationsApi.createOrUpdateOrg(orgEntry);
    }

    public GenericOperationResult delete(String orgName) throws ApiException {
        return organizationsApi.deleteOrg(orgName, "yes");
    }
}
