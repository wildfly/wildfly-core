/*
 * Copyright 2016 JBoss by Red Hat.
 *
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
 */
package org.wildfly.test.jmx.events;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class InstallJmxNotifierSetupTask implements ServerSetupTask {

    @Override
    public void setup(ManagementClient managementClient) throws Exception {
        PathAddress address = PathAddress.pathAddress("subsystem", "core-management")
                .append("process-state-listener", "my-listener");
        ModelNode addListener = Util.createAddOperation(address);
        addListener.get("class").set(ProcessStateJmx.class.getName());
        addListener.get("module").set("org.wildfly.test.jmx.events");
        managementClient.executeForResult(addListener);
    }

    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        ModelControllerClient client = managementClient.getControllerClient();
        PathAddress address = PathAddress.pathAddress("subsystem", "core-management")
                .append("process-state-listener", "my-listener");
        client.execute(Util.createRemoveOperation(address));
    }
}
