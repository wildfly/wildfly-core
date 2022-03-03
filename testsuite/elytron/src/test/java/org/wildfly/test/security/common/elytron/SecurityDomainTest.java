/*
 * Copyright 2020 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.test.security.common.elytron;

import org.hamcrest.MatcherAssert;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import static org.hamcrest.CoreMatchers.containsString;

@RunWith(WildflyTestRunner.class)
public class SecurityDomainTest {
    CLIWrapper cli;

    @Before
    public void setup() throws Exception {
        cli = new CLIWrapper(true);
        // add security-domain
        cli.sendLine("/subsystem=elytron/filesystem-realm=myFsRealm:add(path=my-fs-realm-users,relative-to=jboss.server.config.dir)");
        cli.sendLine("/subsystem=elytron/filesystem-realm=myFsRealm:add-identity(identity=myIdentity)");
        cli.sendLine("/subsystem=elytron/security-domain=mySD:add(realms=[{realm=myFsRealm}],default-realm=myFsRealm,permission-mapper=default-permission-mapper)");
        cli.sendLine("reload");
    }

    @After
    public void cleanup() throws Exception {
        removeTestResources();
        cli.close();
    }

    @Test
    public void testReadIdentityFromSecurityDomain() {
        boolean success = cli.sendLine("/subsystem=elytron/security-domain=mySD:read-identity(name=myIdentity)", true);
        Assert.assertTrue(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("\"name\" => \"myIdentity\""));
    }

    private void removeTestResources() {
        cli.sendLine("/subsystem=elytron/security-domain=mySD:remove");
        cli.sendLine("/subsystem=elytron/filesystem-realm=myFsRealm:remove");
        cli.sendLine("reload");
    }
}
