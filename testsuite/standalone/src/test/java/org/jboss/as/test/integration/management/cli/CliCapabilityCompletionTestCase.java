/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.Operations;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildFlyRunner.class)
public class CliCapabilityCompletionTestCase {

    private static CommandContext ctx;
    private static final List<String> interfaces = new ArrayList<>();

    @BeforeClass
    public static void setup() throws Exception {
        ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        ctx.connectController();
        ModelNode req = new ModelNode();
        req.get(Util.OPERATION).set("read-children-names");
        req.get("child-type").set("interface");
        ModelNode response = ctx.getModelControllerClient().execute(req);
        if (!response.get(Util.OUTCOME).asString().equals(Util.SUCCESS)) {
            throw new Exception("Can't retrieve interfaces " + response);
        }
        if (!response.get(Util.RESULT).isDefined()) {
            throw new Exception("Can't retrieve interfaces");
        }

        List<ModelNode> itfs = response.get(Util.RESULT).asList();
        if (itfs.isEmpty()) {
            throw new Exception("No interfaces found");
        }
        for(ModelNode mn : itfs) {
            interfaces.add(mn.asString());
        }
        Collections.sort(interfaces);
    }

    @AfterClass
    public static void cleanUp() throws CommandLineException {
        ctx.terminateSession();
    }

    /**
     * Activate completion for simple op argument and write-attribute.
     * Value-type completion testing is done in domain (usage of profiles).
     * @throws Exception
     */
    @Test
    public void testInterfaces() throws Exception {
        {
            String cmd = "/socket-binding-group=standard-sockets/socket-binding=toto:add(interface=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertTrue(candidates.toString(), candidates.equals(interfaces));
        }

        {
            String cmd = "/socket-binding-group=standard-sockets/socket-binding=toto:write-attribute(name=interface,value=";
            List<String> candidates = new ArrayList<>();
            ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                    cmd.length(), candidates);
            assertTrue(candidates.toString(), candidates.equals(interfaces));
        }
    }

    @Test
    public void testDynamicRequirements() throws Exception {
        ModelNode req = new ModelNode();
        req.get(Util.OPERATION).set("read-resource-description");
        req.get(Util.ADDRESS).set(PathAddress.pathAddress(PathElement.pathElement("socket-binding-group", "standard-sockets"), PathElement.pathElement("socket-binding", "test")).toModelNode());
        ModelNode result = Operations.readResult(ctx.execute(req, ""));
        assertTrue(result.require("capabilities").asList().size() == 1);
        ModelNode capability  = result.require("capabilities").asList().get(0);
        assertEquals("We should have a socket-binding capability provided", "org.wildfly.network.socket-binding",
                capability.require("name").asString());
        assertTrue("We should have a dynamic capability provided", capability.require("dynamic").asBoolean());
        List<ModelNode> dynamicElts = capability.require("dynamic-elements").asList();
        assertEquals(1, dynamicElts.size());
        assertEquals("We should have a socket-binding capability provided", "socket-binding", dynamicElts.get(0).asString());
        assertEquals("We should have an interface capability requirement", "org.wildfly.network.interface",
                result.require("attributes").require("interface").require("capability-reference").asString());
    }
}
