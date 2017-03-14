/*
 * Copyright (C) 2016 Red Hat, inc., and individual contributors
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
package org.wildfly.test.jmx.staticmodule;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerSetupTask;
import org.wildfly.test.jmx.ControlledStateNotificationListener;
import org.wildfly.test.jmx.ServiceActivatorDeploymentUtil;

/**
 *
 * @author <a href="mailto:jmartisk@redhat.com">Jan Martiska</a> (c) 2016 Red Hat, inc.
 */
public class JMXFacadeListenerInStaticModuleSetupTask implements ServerSetupTask {

    public static final String TARGET_OBJECT_NAME = "jboss.root:type=state";
    private static final String ARCHIVE = "test-jmx-notifications.jar";
    public static final PathAddress EXTENSION_DMR_ADDRESS = PathAddress
            .pathAddress("extension", JMXControlledStateNotificationListenerExtension.EXTENSION_NAME);
    private File file;
    private TestModule jmxListenerModule;

    @Override
    public void setup(ManagementClient managementClient) throws Exception {
        final File dir = new File("target/archives");
        if(dir.exists()) {
            cleanFile(dir);
        }
        dir.mkdirs();
        file = new File(dir, ARCHIVE);
        ServiceActivatorDeploymentUtil.createServiceActivatorListenerArchiveForModule(file, TARGET_OBJECT_NAME, ControlledStateNotificationListener.class);

        // create the module named jmx-notification-listener
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("jmx-listener-module.xml")) {
            Path moduleXml = Files.createTempFile("jmx-module", ".xml");
            Files.copy(is, moduleXml, StandardCopyOption.REPLACE_EXISTING);

            jmxListenerModule = new TestModule("jmx-notification-listener", moduleXml.toFile());
            jmxListenerModule.addJavaArchive(file);
            jmxListenerModule.create(true);

            Files.delete(moduleXml);
        }

        // add management extension
        final ModelNode op = Operations.createOperation("add", EXTENSION_DMR_ADDRESS.toModelNode());
        op.get("module").set("jmx-notification-listener");
        managementClient.executeForResult(op);
    }

    private static void cleanFile(File toClean) {
        if (toClean.isDirectory()) {
            for (File child : toClean.listFiles()) {
                cleanFile(child);
            }
        }
        toClean.delete();
    }

    @Override
    public void tearDown(ManagementClient managementClient) throws Exception {
        // remove management extension
        managementClient.executeForResult(Operations.createOperation("remove", EXTENSION_DMR_ADDRESS.toModelNode()));

        // remove module
        if (jmxListenerModule != null) {
            jmxListenerModule.remove();
        }

    }

}
