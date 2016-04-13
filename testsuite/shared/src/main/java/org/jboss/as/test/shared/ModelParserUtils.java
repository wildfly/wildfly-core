/*
 * Copyright (C) 2014 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.test.shared;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.test.integration.management.util.CLIOpResult;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.Assert;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class ModelParserUtils {

    /**
     * Tests the ability to boot an admin-only server using the given config, persist the config,
     * reload it, and confirm that the configuration model from the original boot matches the model
     * from the reload.
     *
     * @param originalConfig the config file to use
     * @param jbossHome directory to use as $JBOSS_HOME
     * @return the configuration model read after the reload
     * @throws Exception
     */
    public static ModelNode standaloneXmlTest(File originalConfig, File jbossHome) throws Exception {
        File serverDir =  new File(jbossHome, "standalone");

        File configCopy = new File(serverDir, "configuration" + File.separatorChar + originalConfig.getName());
        FileUtils.copyFile(originalConfig, configCopy);

        CLIWrapper cli = new CLIWrapper(false);
        try {

            String line = "embed-server --admin-only=true --server-config=" + originalConfig.getName() + " --std-out=echo --jboss-home=" + jbossHome.getCanonicalPath();
            cli.sendLine(line);
            assertProcessState(cli, ControlledProcessState.State.RUNNING.toString(), TimeoutUtil.adjust(30000), false);
            ModelNode firstResult = readResourceTree(cli);
            cli.sendLine("/system-property=model-parser-util:add(value=true)");
            cli.sendLine("/system-property=model-parser-util:remove");
            cli.sendLine("reload --admin-only=true");
            assertProcessState(cli, ControlledProcessState.State.RUNNING.toString(), TimeoutUtil.adjust(30000), false);
            ModelNode secondResult = readResourceTree(cli);
            compare(firstResult, secondResult);
            return secondResult;

        } finally {
            try {
                cli.quit();
            } finally {
                System.clearProperty(ServerEnvironment.SERVER_BASE_DIR);
            }
        }
    }

    /**
     * Tests the ability to boot an admin-only Host Controller using the given host config, persist the config,
     * reload it, and confirm that the configuration model from the original boot matches the model
     * from the reload.
     *
     * @param originalConfig the config file to use for the host model
     * @param jbossHome directory to use as $JBOSS_HOME
     * @return the host subtree from the configuration model read after the reload
     * @throws Exception
     */
    public static ModelNode hostXmlTest(final File originalConfig, File jbossHome) throws Exception {
        return hostControllerTest(originalConfig, jbossHome, true);
    }

    private static ModelNode hostControllerTest(final File originalConfig, final File target, boolean hostXml) throws Exception {
        File domainDir =  new File(target, "domain");

        File configCopy = new File(domainDir, "configuration" + File.separatorChar + originalConfig.getName());
        FileUtils.copyFile(originalConfig, configCopy);

        CLIWrapper cli = new CLIWrapper(false);
        try {
            String configType = hostXml ? "--host-config=" : "--domain-config=";
            String line = "embed-host-controller " + configType + originalConfig.getName() + " --std-out=echo --jboss-home=" + target.getCanonicalPath();
            cli.sendLine(line);
            assertProcessState(cli, ControlledProcessState.State.RUNNING.toString(), TimeoutUtil.adjust(30000), true);
            ModelNode firstResult = readResourceTree(cli);
            String hostName = firstResult.get(HOST).asProperty().getName();
            cli.sendLine("/system-property=model-parser-util:add(value=true)");
            cli.sendLine("/system-property=model-parser-util:remove");
            cli.sendLine("reload --host=" + hostName + " --admin-only=true");
            assertProcessState(cli, ControlledProcessState.State.RUNNING.toString(), TimeoutUtil.adjust(30000), true);
            ModelNode secondResult = readResourceTree(cli);
            compare(pruneDomainModel(firstResult, hostXml), pruneDomainModel(secondResult, hostXml));
            return secondResult;

        } finally {
            try {
                cli.quit();
            } finally {
                System.clearProperty(ServerEnvironment.SERVER_BASE_DIR);
            }
        }
    }

    private static ModelNode pruneDomainModel(ModelNode model, boolean forHost) {
        ModelNode result = new ModelNode();
        if (forHost) {
            result.get(HOST).set(model.get(HOST));
        } else {
            for (Property prop : model.asPropertyList()) {
                if (!HOST.equals(prop.getName())) {
                    result.get(prop.getName()).set(prop.getValue());
                }
            }
        }
        return result;
    }


    /**
     * Tests the ability to boot an admin-only Host Controller using the given domain config, persist the config,
     * reload it, and confirm that the configuration model from the original boot matches the model
     * from the reload.
     *
     * @param originalConfig the config file to use for the domain model
     * @param jbossHome directory to use as $JBOSS_HOME
     * @return the configuration model read after the reload, excluding the host subtree
     * @throws Exception
     */
    public static ModelNode domainXmlTest(File originalConfig, File jbossHome) throws Exception {
        return hostControllerTest(originalConfig, jbossHome, false);
    }

    private static void assertProcessState(CLIWrapper cli, String expected, int timeout, boolean forHost) throws IOException, InterruptedException {
        long done = timeout < 1 ? 0 : System.currentTimeMillis() + timeout;
        String history = "";
        String state = null;
        do {
            try {
                state = forHost ? getHostState(cli) : getServerState(cli);
                history += state+"\n";
            } catch (Exception ignored) {
                //
                history += ignored.toString()+ "--" + cli.readOutput() + "\n";
            }
            if (expected.equals(state)) {
                return;
            } else {
                Thread.sleep(20);
            }
        } while (timeout > 0 && System.currentTimeMillis() < done);
        assertEquals(history, expected, state);
    }

    private static String getServerState(CLIWrapper cli) throws IOException {
        cli.sendLine(":read-attribute(name=server-state)", true);
        CLIOpResult result = cli.readAllAsOpResult();
        ModelNode resp = result.getResponseNode();
        ModelNode stateNode = result.isIsOutcomeSuccess() ? resp.get(RESULT) : resp.get(FAILURE_DESCRIPTION);
        return stateNode.asString();
    }

    private static String getHostState(CLIWrapper cli) throws IOException {
        cli.sendLine("/host=*:read-attribute(name=host-state)", true);
        CLIOpResult result = cli.readAllAsOpResult();
        ModelNode resp = result.getResponseNode();
        ModelNode stateNode;
        if (result.isIsOutcomeSuccess()) {
            stateNode = resp.get(RESULT).get(0).get(RESULT);
        } else {
            stateNode = resp.get(FAILURE_DESCRIPTION);
        }
        return stateNode.asString();
    }

    private static ModelNode readResourceTree(CLIWrapper cli) {
        cli.sendLine("/:read-resource(recursive=true)");
        ModelNode response = ModelNode.fromString(cli.readOutput());
        assertTrue(response.toString(), SUCCESS.equals(response.get(OUTCOME).asString()));
        ModelNode firstResult = response.get(RESULT);
        assertTrue(response.toString(), firstResult.isDefined());
        return firstResult;
    }

    private static void compare(ModelNode node1, ModelNode node2) {
        Assert.assertEquals(node1.getType(), node2.getType());
        if (node1.getType() == ModelType.OBJECT) {
            final Set<String> keys1 = node1.keys();
            final Set<String> keys2 = node2.keys();
            Assert.assertEquals(node1 + "\n" + node2, keys1.size(), keys2.size());

            for (String key : keys1) {
                final ModelNode child1 = node1.get(key);
                Assert.assertTrue("Missing: " + key + "\n" + node1 + "\n" + node2, node2.has(key));
                final ModelNode child2 = node2.get(key);
                if (child1.isDefined()) {
                    Assert.assertTrue(child1.toString(), child2.isDefined());
                    compare(child1, child2);
                } else {
                    Assert.assertFalse(child2.asString(), child2.isDefined());
                }
            }
        } else if (node1.getType() == ModelType.LIST) {
            List<ModelNode> list1 = node1.asList();
            List<ModelNode> list2 = node2.asList();
            Assert.assertEquals(list1 + "\n" + list2, list1.size(), list2.size());

            for (int i = 0; i < list1.size(); i++) {
                compare(list1.get(i), list2.get(i));
            }

        } else if (node1.getType() == ModelType.PROPERTY) {
            Property prop1 = node1.asProperty();
            Property prop2 = node2.asProperty();
            Assert.assertEquals(prop1 + "\n" + prop2, prop1.getName(), prop2.getName());
            compare(prop1.getValue(), prop2.getValue());

        } else {
            Assert.assertEquals("\n\"" + node1.asString() + "\"\n\"" + node2.asString() + "\"\n-----", node1.asString().trim(), node2.asString().trim());
        }
    }
}
