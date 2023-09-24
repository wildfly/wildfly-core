/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import org.jboss.as.domain.management.security.adduser.AddUser.FileMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static java.lang.System.getProperty;
import static org.junit.Assert.assertTrue;

/**
 * Test the property file finder permissions tests.
 *
 * @author <a href="mailto:bgaisford@punagroup.com">Brandon Gaisford</a>
 */
public class PropertyFileFinderPermissionsTestCase extends PropertyTestHelper {


    @Before
    public void setup() throws IOException {
        values.setFileMode(FileMode.MANAGEMENT);
        values.getOptions().setJBossHome(getProperty("java.io.tmpdir")+File.separator+"permmissions");
        cleanFiles("domain");
        cleanFiles("standalone");
        cleanProperties();
    }

    @After
    public void teardown() {
        cleanFiles("domain");
        cleanFiles("standalone");
        cleanProperties();
    }

    private File createPropertyFile(String filename, String mode) throws IOException {

        File permissionsDir = new File(getProperty("java.io.tmpdir")+File.separator+"permmissions");
        permissionsDir.mkdir();
        permissionsDir.deleteOnExit();
        File parentDir = new File(permissionsDir.getPath()+File.separator+mode);
        parentDir.mkdir();
        parentDir.deleteOnExit();
        File propertyUserFile = new File(parentDir, filename);
        propertyUserFile.createNewFile();
        propertyUserFile.deleteOnExit();
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(propertyUserFile), StandardCharsets.UTF_8));
        try {
          Properties domainPropeties = new Properties();
          domainPropeties.setProperty(USER_NAME,"mypassword");
          domainPropeties.store(bw,"");
        } finally {
           bw.close();
        }
        return propertyUserFile;
    }

    /**
     * Restore permissions for a dir structure (so we can clean it out) and then clean it.
     * Allows clean rerunning of the test in an env where java.io.tmpdir doesn't change between runs.
     */
    private void cleanFiles(String mode) {
        File dir = new File(getProperty("java.io.tmpdir")+File.separator+"permmissions"+File.separator+mode);
        dir.setExecutable(true);
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                child.setReadable(true);
                child.setWritable(true);
            }
        }
        cleanFiles(dir);
    }

    @Test
    public void testPropertyFileFinderFilePermissions() throws IOException {

        File domainMgmtUserFile = createPropertyFile("mgmt-users.properties", "domain");
        File standaloneMgmtUserFile = createPropertyFile("mgmt-users.properties", "standalone");
        File domainMgmtGroupFile = createPropertyFile("mgmt-groups.properties", "domain");
        File standaloneMgmtGroupFile = createPropertyFile("mgmt-groups.properties", "standalone");

        System.setProperty("jboss.server.config.user.dir", standaloneMgmtUserFile.getParent());
        System.setProperty("jboss.domain.config.user.dir", domainMgmtUserFile.getParent());
        System.setProperty("jboss.server.config.group.dir", standaloneMgmtGroupFile.getParent());
        System.setProperty("jboss.domain.config.group.dir", domainMgmtGroupFile.getParent());

        File standaloneDir = standaloneMgmtUserFile.getParentFile();
        State propertyFileFinder = new PropertyFileFinder(consoleMock, values);

        // Test parent dir without read
        if(standaloneDir.setReadable(false)) {
            State nextState = propertyFileFinder.execute();
            assertTrue(nextState instanceof ErrorState);
            standaloneDir.setReadable(true);
        }

        propertyFileFinder = new PropertyFileFinder(consoleMock, values);

        // Test parent dir without execute
        if(standaloneDir.setExecutable(false)) {
            State nextState = propertyFileFinder.execute();
            assertTrue(nextState.toString(), nextState instanceof ErrorState);
            standaloneDir.setExecutable(true);
        }

        propertyFileFinder = new PropertyFileFinder(consoleMock, values);

        // Test file without read
        if(standaloneMgmtUserFile.setReadable(false)) {
            State nextState = propertyFileFinder.execute();
            assertTrue(nextState.toString(), nextState instanceof ErrorState);
            standaloneMgmtUserFile.setReadable(true);
        }

        propertyFileFinder = new PropertyFileFinder(consoleMock, values);

        // Test file without write
        if(standaloneMgmtUserFile.setWritable(false)) {
            State nextState = propertyFileFinder.execute();
            assertTrue(nextState.toString(), nextState instanceof ErrorState);
            standaloneMgmtUserFile.setWritable(true);
        }

    }

}
