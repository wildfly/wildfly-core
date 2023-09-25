/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.elytron;

import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
public class SecurityDomainLockReleaseTest {
    CLIWrapper cli;

    @Before
    public void setup() throws Exception {
        cli = new CLIWrapper(true);

        cli.sendLine("/subsystem=elytron/key-store=exampleKeystore:add(path=keystore, relative-to=jboss.server.config.dir, type=JKS, credential-reference={clear-text=secret})", true);
        cli.sendLine("/subsystem=elytron/key-store=exampleKeystore:generate-key-pair(alias=localhost,algorithm=RSA,key-size=1024,validity=365,distinguished-name=\"CN=localhost\")", true);
        cli.sendLine("/subsystem=elytron/key-store=exampleKeystore:store()", true);
        cli.sendLine("/subsystem=elytron/filesystem-realm=exampleSecurityRealm:add(path=fs-realm-users,relative-to=jboss.server.config.dir, key-store=exampleKeystore, key-store-alias=localhost)", true);
        cli.sendLine("/subsystem=elytron/filesystem-realm=exampleSecurityRealm:add-identity(identity=user1)", true);
        cli.sendLine("/subsystem=elytron/filesystem-realm=exampleSecurityRealm:set-password(identity=user1, clear={password=\"passwordUser1\"})", true);
        cli.sendLine("/subsystem=elytron/filesystem-realm=exampleSecurityRealm:add-identity-attribute(identity=user1, name=Roles, value=[\"Admin\",\"Guest\"])", true);
        cli.sendLine("/subsystem=elytron/security-domain=exampleSecurityDomain:add(default-realm=exampleSecurityRealm,permission-mapper=default-permission-mapper,realms=[{realm=exampleSecurityRealm}])", true);
        cli.sendLine("reload");
    }

    @After
    public void cleanup() throws Exception {
        removeTestResources();
        cli.close();
    }

    @Test
    public void testReadIdentityReleasingLock() throws Exception {
        boolean success = cli.sendLine("/subsystem=elytron/security-domain=exampleSecurityDomain:read-identity(name=user1)", true);
        Assert.assertTrue(success);

        success = cli.sendLine("/subsystem=elytron/filesystem-realm=exampleSecurityRealm:update-key-pair()", true);
        Assert.assertTrue(success);

        success = cli.sendLine("/subsystem=elytron/security-domain=exampleSecurityDomain:read-identity(name=user1)", true);
        Assert.assertTrue(success);
    }

    private void removeTestResources() {
        cli.sendLine("/subsystem=elytron/security-domain=exampleSecurityDomain:remove");
        cli.sendLine("/subsystem=elytron/filesystem-realm=exampleSecurityRealm:remove-identity(identity=user1)");
        cli.sendLine("/subsystem=elytron/filesystem-realm=exampleSecurityRealm:remove");
        cli.sendLine("/subsystem=elytron/key-store=exampleKeystore:remove-alias(alias=localhost)");
        cli.sendLine("/subsystem=elytron/key-store=exampleKeystore:remove");
        cli.sendLine("reload");
    }
}
