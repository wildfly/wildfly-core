/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.test.integration.domain.management.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Test that a host capability registry is used to resolve interfaces
 *
 * @author jdenise@redhat.com
 */
public class CliCapabilityCompletionTestCase {

    private static CommandContext ctx;
    private static List<String> interfaces;
    private static List<String> profiles;
    private static DomainTestSupport testSupport;

    @BeforeClass
    public static void setup() throws Exception {
        testSupport = CLITestSuite.createSupport(
                CliCapabilityCompletionTestCase.class.getSimpleName());
        ctx = CLITestUtil.getCommandContext(testSupport,
                System.in, System.out);
        ctx.connectController();
        interfaces = retrieveChilds("interface");
        profiles = retrieveChilds("profile");
    }

    private static List<String> retrieveChilds(String childType) throws Exception {
        ModelNode req = new ModelNode();
        req.get(Util.OPERATION).set("read-children-names");
        req.get("child-type").set(childType);
        ModelNode response = ctx.getModelControllerClient().execute(req);
        if (!response.get(Util.OUTCOME).asString().equals(Util.SUCCESS)) {
            throw new Exception("Can't retrieve interfaces");
        }
        if (!response.get(Util.RESULT).isDefined()) {
            throw new Exception("Can't retrieve cilds");
        }

        List<ModelNode> itfs = response.get(Util.RESULT).asList();
        if (itfs.isEmpty()) {
            throw new Exception("No child found");
        }
        List<String> names = new ArrayList<>();
        for (ModelNode mn : itfs) {
            names.add(mn.asString());
        }
        Collections.sort(names);
        return names;
    }

    @AfterClass
    public static void cleanUp() throws CommandLineException {
        ctx.terminateSession();
        CLITestSuite.stopSupport();
    }

    /**
     * Activate completion for simple op argument and write-attribute.
     *
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

    /**
     * Activate completion for value-type.
     *
     * @throws Exception
     */
    @Test
    public void testProfiles() throws Exception {
        String cmd = "/profile=toto:add(includes=[";
        List<String> candidates = new ArrayList<>();
        ctx.getDefaultCommandCompleter().complete(ctx, cmd,
                cmd.length(), candidates);
        assertTrue(candidates.toString(), candidates.equals(profiles));
    }
}
