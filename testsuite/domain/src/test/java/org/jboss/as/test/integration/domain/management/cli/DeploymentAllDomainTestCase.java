/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.test.integration.domain.management.cli;

import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.Before;

/**
 *
 * @author jdenise@redhat.com
 */
public class DeploymentAllDomainTestCase extends DeployAllDomainTestCase {

    @Before
    public void beforeTest() throws Exception {
        ctx = CLITestUtil.getCommandContext(testSupport);
        ctx.connectController();

        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestApp1War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgOne + ' ' + cliTestAnotherWar.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ' ' + cliTestApp2War.getAbsolutePath());
        ctx.handle("deployment deploy-file --server-groups=" + sgTwo + ',' + sgOne + ' ' + cliTestAppEar.getAbsolutePath());

        // Disable them all.
        ctx.handle("deployment disable-all --all-relevant-server-groups");
    }
}
