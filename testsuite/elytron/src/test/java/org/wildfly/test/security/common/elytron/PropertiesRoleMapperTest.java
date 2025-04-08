/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.elytron;

import static org.hamcrest.core.StringContains.containsString;

import org.hamcrest.MatcherAssert;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author <a href="mailto:pesilva@redhat.com">Pedro Hos</a>
 *
 */
@RunWith(WildFlyRunner.class)
public class PropertiesRoleMapperTest {

    CLIWrapper cli;

    @Before
    public void setup() throws Exception {
        cli = new CLIWrapper(true);
    }

    @After
    public void cleanup() throws Exception {
        cli.sendLine("/subsystem=elytron/properties-role-mapper=prm:remove", true);
        cli.sendLine("reload", true);
        cli.close();
    }

    @Test
    public void newPropertiesRoleMapperSuccess() {
        boolean success = cli.sendLine("/subsystem=elytron/properties-role-mapper=prm:add(path="+ PropertiesRoleMapperTest.class.getResource("rolesMapping-roles.properties").getFile()  + ")", true);
        Assert.assertTrue(success);
    }

    @Test
    public void newPropertiesRoleMapperFileNotFound() {
        boolean success = cli.sendLine("/subsystem=elytron/properties-role-mapper=prm:add(path=wrong-rolesMapping-roles.properties)", true);
        Assert.assertFalse(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("Role Mapper Mapped configuration file does not exist."));
    }

    @Test
    public void newPropertiesRoleMapperPathDir() {
        String path = "/";
        boolean success = cli.sendLine("/subsystem=elytron/properties-role-mapper=prm:add(path="+ path  + ")", true);
        Assert.assertFalse(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("is a directory and cannot be used as a Role Mapper file."));
    }

}
