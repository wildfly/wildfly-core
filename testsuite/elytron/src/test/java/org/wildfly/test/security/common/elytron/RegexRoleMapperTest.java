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

import static org.hamcrest.core.StringContains.containsString;

@RunWith(WildFlyRunner.class)
public class RegexRoleMapperTest {
    CLIWrapper cli;

    @Before
    public void setup() throws Exception {
        cli = new CLIWrapper(true);
    }

    @After
    public void cleanup() throws Exception {
        cli.sendLine("/subsystem=elytron/regex-role-mapper=rrm:remove", true);
        cli.sendLine("/subsystem=elytron/regex-role-mapper=rrm2:remove", true);
        cli.sendLine("reload", true);
        cli.close();
    }

    @Test
    public void testInvalidParametersPassedToRegexRoleMapper() {
        boolean success =
                cli.sendLine("/subsystem=elytron/regex-role-mapper=rrm2:add(pattern=\"\", replacement=\"any\", keep-non-mapped=\"false\", replace-all=\"true\")", true);
        Assert.assertFalse(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("'' is an invalid value for parameter pattern. Values must have a minimum length of 1 characters\""));

        success = cli.sendLine("/subsystem=elytron/regex-role-mapper=rrm2:add(pattern=\"any\", replacement=\"\", keep-non-mapped=\"false\", replace-all=\"true\")", true);
        Assert.assertFalse(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("'' is an invalid value for parameter replacement. Values must have a minimum length of 1 characters\""));
    }

    @Test
    public void testAddUpdateRemoveRegexRoleMapper() {
        boolean success =
                cli.sendLine("/subsystem=elytron/regex-role-mapper=rrm:add(pattern=\"user\", replacement=\"admin\", keep-non-mapped=\"false\", replace-all=\"false\")", true);
        Assert.assertTrue(success);

        success = cli.sendLine("/subsystem=elytron/regex-role-mapper=rrm:write-attribute(name=pattern,value=\"updatedPattern\")", true);
        Assert.assertTrue(success);
        cli.sendLine("reload", true);

        success = cli.sendLine("/subsystem=elytron/regex-role-mapper=rrm:read-attribute(name=pattern)", true);
        Assert.assertTrue(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("\"result\" => \"updatedPattern\""));

        success = cli.sendLine("/subsystem=elytron/regex-role-mapper=rrm:write-attribute(name=replacement,value=\"updatedReplacement\")", true);
        Assert.assertTrue(success);
        cli.sendLine("reload", true);

        success = cli.sendLine("/subsystem=elytron/regex-role-mapper=rrm:read-attribute(name=replacement)", true);
        Assert.assertTrue(success);
        MatcherAssert.assertThat(cli.readOutput(), containsString("\"result\" => \"updatedReplacement\""));

        success = cli.sendLine("/subsystem=elytron/regex-role-mapper=rrm:remove", true);
        Assert.assertTrue(success);
    }
}
