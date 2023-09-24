/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.elytron;

import org.hamcrest.MatcherAssert;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

import static org.hamcrest.CoreMatchers.containsString;

@RunWith(WildFlyRunner.class)
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
