/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.core.test.standalone.mgmt.api;

import static org.jboss.as.test.integration.management.util.ModelUtil.createOpNode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.as.test.integration.management.util.ModelUtil;
import org.jboss.dmr.ModelNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.test.standalone.base.ContainerResourceMgmtTestBase;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Tests both automated and manual configuration model persistence snapshot generation.
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
@RunWith(WildflyTestRunner.class)
public class ModelPersistenceTestCase extends ContainerResourceMgmtTestBase {

    private class CfgFileDescription {

        public CfgFileDescription(int version, File file, long hash) {
            this.version = version;
            this.file = file;
            this.hash = hash;
        }

        public int version;
        public File file;
        public long hash;

    }

    private static final String SERVER_CONFIG_DIR = System.getProperty("jboss.home") + "/standalone/configuration";
    private static final String HISTORY_DIR = "standalone_xml_history";
    private static final String CURRENT_DIR = "current";

    private static Path configDir;
    private static Path currentCfgDir;
    private static Path lastCfgFile;


    @Before
    public void before() throws IOException, MgmtOperationException {

        if (configDir == null) {
            configDir = Paths.get(SERVER_CONFIG_DIR);
            assertTrue("Server config dir " + SERVER_CONFIG_DIR + " does not exists.", Files.exists(configDir));
            assertTrue(Files.isDirectory(configDir));
            currentCfgDir = configDir.resolve(HISTORY_DIR).resolve(CURRENT_DIR);

            // get server configuration name
            ModelNode op = createOpNode("core-service=server-environment", "read-attribute");
            op.get("name").set("config-file");
            ModelNode result = executeOperation(op);
            String configFile = result.asString();
            String configFileName = Paths.get(configFile).getFileName().toString();
            assertTrue(configFileName.endsWith(".xml"));
            configFileName = configFileName.substring(0, configFileName.length() - 4);

            lastCfgFile = configDir.resolve(HISTORY_DIR).resolve(configFileName + ".last.xml");
        }

    }

    @Test
    public void testSimpleOperation() throws Exception {

        CfgFileDescription lastBackupDesc = getLatestBackup(currentCfgDir);

        long lastFileHash = Files.exists(lastCfgFile) ? FileUtils.checksumCRC32(lastCfgFile.toFile()) : -1;
        ModelNode op;
        CfgFileDescription newBackupDesc;

        try {
            // execute operation so the model gets updated
            op = createOpNode("system-property=test", "add");
            op.get("value").set("test");
            executeOperation(op);

            // check that the automated snapshat has been generated
            newBackupDesc = getLatestBackup(currentCfgDir);
            assertNotNull("Model snapshot not found.", newBackupDesc);
            // check that the version is incremented by one
            assertTrue(lastBackupDesc.version == newBackupDesc.version - 1);

            // check that the last cfg file has changed
            assertTrue(lastFileHash != FileUtils.checksumCRC32(lastCfgFile.toFile()));
        } finally {
            // remove testing attribute
            op = createOpNode("system-property=test", "remove");
            executeOperation(op);
        }


        // check that the snapshot has been updated again
        lastBackupDesc = newBackupDesc;
        newBackupDesc = getLatestBackup(currentCfgDir);
        assertNotNull("Model snapshot not found.", newBackupDesc);
        // check that the version is incremented by one
        assertEquals("Version was not properly incremented", lastBackupDesc.version, newBackupDesc.version - 1);
    }

    @Test
    public void testCompositeOperation() throws Exception {

        CfgFileDescription lastBackupDesc = null;
        CfgFileDescription newBackupDesc = null;
        try {
            lastBackupDesc = getLatestBackup(currentCfgDir);

            // execute composite operation
            ModelNode[] steps = new ModelNode[2];
            steps[0] = createOpNode("system-property=test", "add");
            steps[0].get("value").set("test");
            steps[1] = createOpNode("system-property=test", "write-attribute");
            steps[1].get("name").set("value");
            steps[1].get("value").set("test2");
            executeOperation(ModelUtil.createCompositeNode(steps));

            // check that the automated snapshat has been generated
            newBackupDesc = getLatestBackup(currentCfgDir);
            // check that the version is incremented by one
            assertTrue(lastBackupDesc.version == newBackupDesc.version - 1);
        } finally {
            // remove testing attribute
            ModelNode op = createOpNode("system-property=test", "remove");
            executeOperation(op);
        }

        // check that the snapshot has been updated again
        lastBackupDesc = newBackupDesc;

        newBackupDesc = getLatestBackup(currentCfgDir);
        assertNotNull("Model snapshot not found.", newBackupDesc);
        // check that the version is incremented by one
        assertEquals("Version was not properly incremented", lastBackupDesc.version, newBackupDesc.version - 1);
    }

    @Test
    public void testCompositeOperationRollback() throws Exception {

        CfgFileDescription lastBackupDesc = getLatestBackup(currentCfgDir);

        // execute operation so the model gets updated
        ModelNode op = createOpNode("system-property=test", "add");
        op.get("value").set("test");
        executeAndRollbackOperation(op);

        // check that the model has not been updated
        CfgFileDescription newBackupDesc = getLatestBackup(currentCfgDir);
        assertNotNull("Model snapshot not found.", newBackupDesc);
        assertNotNull("Last backup snapshot not found.", lastBackupDesc);

        // check that the config did not change
        assertEquals("Version should be same", lastBackupDesc.version, newBackupDesc.version);
        assertEquals("hash should match", lastBackupDesc.hash, newBackupDesc.hash);
    }

    @Test
    public void testTakeAndDeleteSnapshot() throws Exception {

        // take snapshot

        ModelNode op = createOpNode(null, "take-snapshot");
        ModelNode result = executeOperation(op);

        // check that the snapshot file exists
        String snapshotFileName = result.asString();
        File snapshotFile = new File(snapshotFileName);
        assertTrue(snapshotFile.exists());

        // compare with current cfg
        long snapshotHash = FileUtils.checksumCRC32(snapshotFile);
        long lastHash = FileUtils.checksumCRC32(lastCfgFile.toFile());
        assertEquals(snapshotHash,lastHash);

        // delete snapshot
        op = createOpNode(null, "delete-snapshot");
        op.get("name").set(snapshotFile.getName());
        executeOperation(op);
        // check that the file is deleted
        assertFalse("Snapshot file still exists.", snapshotFile.exists());

    }

    @Test
    public void testListSnapshots() throws Exception {

        // take snapshot
        ModelNode op = createOpNode(null, "take-snapshot");
        ModelNode result = executeOperation(op);

        // check that the snapshot file exists
        String snapshotFileName = result.asString();
        File snapshotFile = new File(snapshotFileName);
        assertTrue(snapshotFile.exists());

        // get the snapshot listing
        op = createOpNode(null, "list-snapshots");
        result = executeOperation(op);
        File snapshotDir = new File(result.get("directory").asString());
        assertTrue(snapshotDir.isDirectory());

        List<String> snapshotNames = ModelUtil.modelNodeAsStingList(result.get("names"));
        assertTrue(snapshotNames.contains(snapshotFile.getName()));

    }

    private CfgFileDescription getLatestBackup(Path dir) throws IOException {

        final int[] lastVersion = {0};
        final File[] lastFile = {null};


        if (Files.isDirectory(dir)) {
            try(Stream<Path> files = Files.list(dir)) {
                files.filter(file -> {
                    String fileName = file.getFileName().toString();
                    String[] nameParts = fileName.split("\\.");
                    return !(!nameParts[0].contains("standalone") && !nameParts[2].equals("xml"));
                }).forEach(path -> {
                    String fileName = path.getFileName().toString();
                    String[] nameParts = fileName.split("\\.");
                    if (!nameParts[0].contains("standalone")) { return; }
                    if (!nameParts[2].equals("xml")) { return; }
                    int version = Integer.valueOf(nameParts[1].substring(1));
                    if (version > lastVersion[0]) {
                        lastVersion[0] = version;
                        lastFile[0] = path.toFile();
                    }
                });
            }
        }
        return new CfgFileDescription(lastVersion[0], lastFile[0], (lastFile[0] != null) ? FileUtils.checksumCRC32(lastFile[0]) : 0);
    }
}
