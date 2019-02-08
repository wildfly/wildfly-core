/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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
package org.wildfly.core.test.standalone.mgmt.api;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADMIN_ONLY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REPLY_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUEST_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESTART;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESUME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SHUTDOWN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.START_MODE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TIMEOUT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USE_CURRENT_SERVER_CONFIG;

import javax.inject.Inject;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Simple test to validate the server's model availability and reading it as XML or from the root.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(WildflyTestRunner.class)
public class CoreServerTestCase {

    @Inject
    private ManagementClient managementClient;

    /**
     * Validates that the model can be read in xml form.
     *
     * @throws Exception
     */
    @Test
    public void testReadConfigAsXml() throws Exception {
        ModelNode request = new ModelNode();
        request.get("operation").set("read-config-as-xml");
        request.get("address").setEmptyList();
        ModelNode r = managementClient.getControllerClient().execute(request);

        Assert.assertEquals(SUCCESS, r.require(OUTCOME).asString());
    }

    /**
     * Validates that all resource and operation descriptions can be generated.
     *
     * @throws Exception
     */
    @Test
    public void testReadResourceDescription() throws Exception {
        ModelNode request = new ModelNode();
        request.get("operation").set("read-resource");
        request.get("address").setEmptyList();
        request.get("recursive").set(true);
        ModelNode r = managementClient.getControllerClient().execute(request);

        Assert.assertEquals("response with failure details:" + r.toString(), SUCCESS, r.require(OUTCOME).asString());

        request = new ModelNode();
        request.get("operation").set("read-resource-description");
        request.get("address").setEmptyList();
        request.get("recursive").set(true);
        request.get("operations").set(true);
        request.get("inherited").set(false);
        r = managementClient.getControllerClient().execute(request);

        Assert.assertEquals("response with failure details:" + r.toString(), SUCCESS, r.require(OUTCOME).asString());
        validateOps(r);

        // Make sure the inherited op descriptions work as well

        request = new ModelNode();
        request.get("operation").set("read-resource-description");
        request.get("address").setEmptyList();
        request.get("recursive").set(false); // NOT recursive; we just need them once
        request.get("operations").set(true);
        request.get("inherited").set(true);
        r = managementClient.getControllerClient().execute(request);

        Assert.assertEquals("response with failure details:" + r.toString(), SUCCESS, r.require(OUTCOME).asString());
    }

    private void validateOps(ModelNode response){
        ModelNode operations = response.get(RESULT, OPERATIONS);
        validateOperation(operations, RELOAD, null, ADMIN_ONLY, USE_CURRENT_SERVER_CONFIG, SERVER_CONFIG, START_MODE);
        validateOperation(operations, RESUME, null);
        validateOperation(operations, SHUTDOWN, null, RESTART, TIMEOUT, SUSPEND_TIMEOUT);
        validateOperation(operations, SUSPEND, null, TIMEOUT, SUSPEND_TIMEOUT);
    }

    private static void validateOperation(ModelNode operations, String name, ModelType replyType, String... params) {
        Assert.assertTrue(operations.toString(), operations.hasDefined(name));
        ModelNode op = operations.get(name);
        ModelNode props = op.get(REQUEST_PROPERTIES);
        for (String param : params) {
            Assert.assertTrue(op.toString(), props.hasDefined(param));
        }
        ModelNode reply = op.get(REPLY_PROPERTIES);
        if (replyType != null) {
            Assert.assertTrue(op.toString(), reply.hasDefined(TYPE));
            Assert.assertEquals(op.toString(), replyType, reply.get(TYPE).asType());
        } else {
            Assert.assertFalse(op.toString(), reply.hasDefined(TYPE));
        }
    }
}
