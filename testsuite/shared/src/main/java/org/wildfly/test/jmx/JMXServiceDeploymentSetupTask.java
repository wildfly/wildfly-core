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
package org.wildfly.test.jmx;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BYTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.test.integration.management.rbac.Outcome;
import org.jboss.as.test.integration.management.rbac.RbacUtil;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class JMXServiceDeploymentSetupTask implements ServerSetupTask {
    public static final String  OBJECT_NAME = "jboss.test:service=testdeployments";
    private File file;

    @Override
    public void setup(ManagementClient managementClient) throws Exception {
        final File dir = new File("target/archives");
        if(dir.exists()) {
            cleanFile(dir);
        }
        dir.mkdirs();
        file = new File(dir, "test-jmx-deployment.jar");
        ServiceActivatorDeploymentUtil.createServiceActivatorDeployment(file, OBJECT_NAME, Dynamic.class);
        deploy(managementClient.getControllerClient(), file);
    }

    public void setup(ModelControllerClient client, String serverGroupName) throws IOException {
        final File dir = new File("target/archives");
        if(dir.exists()) {
            cleanFile(dir);
        }
        dir.mkdirs();
        file = new File(dir, "test-jmx-deployment.jar");
        ServiceActivatorDeploymentUtil.createServiceActivatorDeployment(file, OBJECT_NAME, Dynamic.class);
        deploy(client, file);
        ModelNode op = createOpNode("server-group=" + serverGroupName + "/deployment=" + file.getName(), ADD);
        op.get(ENABLED).set(true);
        RbacUtil.executeOperation(client, op, Outcome.SUCCESS);
    }

    protected void deploy(ModelControllerClient client, File file) throws IOException {
        ModelNode op = createOpNode("deployment=" + file.getName(), ADD);
        op.get(ENABLED).set(true);
        ModelNode content = op.get(CONTENT).add();
        content.get(BYTES).set(getContent(file));
        RbacUtil.executeOperation(client, op, Outcome.SUCCESS);
    }

    private byte[] getContent(File file) throws IOException {
        InputStream in = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            in = new FileInputStream(file);
            StreamUtils.copyStream(in, out);
            return out.toByteArray();
        } finally {
            StreamUtils.safeClose(in);
            StreamUtils.safeClose(out);
        }
    }

    protected static void removeDeployment(ModelControllerClient client, File file) throws IOException {
        ModelNode op = createOpNode("deployment=" + file.getName(),  REMOVE);
        RbacUtil.executeOperation(client, op, Outcome.SUCCESS);
    }

    private static void cleanFile(File toClean) {
        if (toClean.isDirectory()) {
            for (File child : toClean.listFiles()) {
                cleanFile(child);
            }
        }
        toClean.delete();
    }

    public void tearDown(ModelControllerClient client, String serverGroupName) throws Exception {
        ModelNode op = createOpNode("server-group=" + serverGroupName + "/deployment=" + file.getName(), REMOVE);
        RbacUtil.executeOperation(client, op, Outcome.SUCCESS);
        removeDeployment(client, file);
    }

    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        removeDeployment(managementClient.getControllerClient(), file);
    }

}
