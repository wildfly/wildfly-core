/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
package org.wildfly.core.test.standalone.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILD_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAMES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *  Tests using server-config parameter in the :reload operation
 *
 * @author Kabir Khan
 */
@RunWith(WildFlyRunner.class)
public class ReloadWithConfigTestCase {
    private static final String RELOAD_TEST_CASE_ONE = "reload-test-case-one";
    private static final String RELOAD_TEST_CASE_TWO = "reload-test-case-two";

    @Inject
    private static ServerController container;

    private File snapshotDir;

    @Test
    public void reloadFromSnapshotTestCase() throws Exception {
        boolean wasReloaded = false;
        cleanSnapshotDirectory();
        addSystemProperty(RELOAD_TEST_CASE_ONE, "1");
        try {

            executeForResult(Util.createEmptyOperation("take-snapshot", PathAddress.EMPTY_ADDRESS));
            addSystemProperty(RELOAD_TEST_CASE_TWO, "2");

            List<File> snapshots = listSnapshots();
            Assert.assertEquals(1, snapshots.size());
            container.reload(snapshots.get(0).getName());
            wasReloaded = true;

            final ModelNode readChildrenNames = Util.createEmptyOperation(READ_CHILDREN_NAMES_OPERATION, PathAddress.EMPTY_ADDRESS);
            readChildrenNames.get(CHILD_TYPE).set(SYSTEM_PROPERTY);
            List<ModelNode> list = executeForResult(readChildrenNames).asList();
            Assert.assertEquals(1, list.size());
            Assert.assertEquals(RELOAD_TEST_CASE_ONE, list.get(0).asString());
        } finally {
            removeSystemProperty(RELOAD_TEST_CASE_TWO, true);
            removeSystemProperty(RELOAD_TEST_CASE_ONE, false);
            cleanSnapshotDirectory();
            if (wasReloaded) {
                //Reset the config file
                container.reload();
            }
        }
    }

    private void addSystemProperty(String name, String value) throws Exception {
        ModelNode op = Util.createAddOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, name));
        op.get(VALUE).set(value);
        executeForResult(op);
    }

    private List<File> listSnapshots() throws Exception {
        final ModelNode op = Util.createEmptyOperation("list-snapshots", PathAddress.EMPTY_ADDRESS);
        final ModelNode result = executeForResult(op);
        String dir = result.get(DIRECTORY).asString();
        ModelNode names = result.get(NAMES);
        List<File> snapshotFiles = new ArrayList<>();
        for (ModelNode nameNode : names.asList()) {
            snapshotFiles.add(new File(dir, nameNode.asString()));
        }
        return snapshotFiles;
    }

    private void cleanSnapshotDirectory() throws Exception {
        for (File file : getSnapshotDir().listFiles()) {
            Assert.assertTrue(file.delete());
        }
    }

    private ModelNode executeForResult(ModelNode op)throws Exception{
        return container.getClient().executeForResult(op);
    }

    private File getSnapshotDir() throws Exception {
        if (snapshotDir == null) {
            final ModelNode op = Util.createEmptyOperation("list-snapshots", PathAddress.EMPTY_ADDRESS);
            final ModelNode result = executeForResult(op);
            final String dir = result.get(DIRECTORY).asString();
            snapshotDir = new File(dir);
        }
        return snapshotDir;
    }

    private void removeSystemProperty(String name, boolean safe) throws Exception {
        try {
            executeForResult(Util.createRemoveOperation(PathAddress.pathAddress(SYSTEM_PROPERTY, name)));
        } catch (Exception e) {
            if (safe) {
                e.printStackTrace();
            } else {
                throw e;
            }
        }
    }

}
