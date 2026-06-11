/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import org.jboss.as.domain.management.security.adduser.AddUser.FileMode;
import org.jboss.msc.service.StartException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import static java.lang.System.getProperty;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * Test the property file finder.
 *
 * @author <a href="mailto:flemming.harms@gmail.com">Flemming Harms</a>
 */
public class PropertyFileFinderTestCase extends PropertyTestHelper {


    @Before
    public void setup() throws IOException {
        values.setFileMode(FileMode.MANAGEMENT);
        values.getOptions().setJBossHome(getProperty("java.io.tmpdir"));
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

        File domainDir = new File(getProperty("java.io.tmpdir")+File.separator+mode);
        domainDir.mkdir();
        domainDir.deleteOnExit();
        File propertyUserFile = new File(domainDir, filename);
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

    private void cleanFiles(String mode) {
        File dir = new File(getProperty("java.io.tmpdir")+File.separator+mode);
        cleanFiles(dir);
    }

    @Test
    public void overridePropertyfileLocationRead() throws IOException {
        File domainMgmtUserFile = createPropertyFile("mgmt-users.properties", "domain");
        File standaloneMgmtUserFile = createPropertyFile("mgmt-users.properties", "standalone");
        File domainMgmtGroupFile = createPropertyFile("mgmt-groups.properties", "domain");
        File standaloneMgmtGroupFile = createPropertyFile("mgmt-groups.properties", "standalone");

        System.setProperty("jboss.server.config.user.dir", standaloneMgmtUserFile.getParent());
        System.setProperty("jboss.domain.config.user.dir", domainMgmtUserFile.getParent());
        System.setProperty("jboss.server.config.group.dir", standaloneMgmtGroupFile.getParent());
        System.setProperty("jboss.domain.config.group.dir", domainMgmtGroupFile.getParent());
        State propertyFileFinder = new PropertyFileFinder(consoleMock, values);
        State nextState = propertyFileFinder.execute();
        assertTrue(nextState instanceof PromptRealmState);
        assertTrue("Expected to find the "+USER_NAME+" in the list of known enabled users",values.getEnabledKnownUsers().contains(USER_NAME));
        assertTrue("Expected the values.getPropertiesFiles() contained the "+standaloneMgmtUserFile,values.getUserFiles().contains(standaloneMgmtUserFile));
        assertTrue("Expected the values.getPropertiesFiles() contained the "+domainMgmtUserFile,values.getUserFiles().contains(domainMgmtUserFile));
    }

    @Test
    public void overridePropertyfileLocationWrite() throws IOException, StartException {
        File domainUserFile = createPropertyFile("application-users.properties", "domain");
        File standaloneUserFile = createPropertyFile("application-users.properties", "standalone");
        File domainRolesFile = createPropertyFile("application-roles.properties", "domain");
        File standaloneRolesFile = createPropertyFile("application-roles.properties", "standalone");

        String newUserName = "Hugh.Jackman";
        values.setGroups(null);
        values.setUserName(newUserName);
        values.setFileMode(FileMode.APPLICATION);
        System.setProperty("jboss.server.config.user.dir", domainUserFile.getParent());
        System.setProperty("jboss.domain.config.user.dir", standaloneUserFile.getParent());
        System.setProperty("jboss.server.config.role.dir", domainRolesFile.getParent());
        System.setProperty("jboss.domain.config.role.dir", standaloneRolesFile.getParent());
        State propertyFileFinder = new PropertyFileFinder(consoleMock, values);
        State nextState = propertyFileFinder.execute();
        assertTrue(nextState instanceof PromptRealmState);

        File locatedDomainPropertyFile = values.getUserFiles().get(values.getUserFiles().indexOf(domainUserFile));
        File locatedStandalonePropertyFile = values.getUserFiles().get(values.getUserFiles().indexOf(standaloneUserFile));
        UpdateUser updateUserState = new UpdateUser(consoleMock, values);

        AssertConsoleBuilder consoleBuilder = new AssertConsoleBuilder().
                expectedDisplayText(updateUserState.consoleUserMessage(locatedDomainPropertyFile.getCanonicalPath())).
                expectedDisplayText(AddUser.NEW_LINE).
                expectedDisplayText(updateUserState.consoleUserMessage(locatedStandalonePropertyFile.getCanonicalPath())).
                expectedDisplayText(AddUser.NEW_LINE);
        consoleMock.setResponses(consoleBuilder);
        updateUserState.update(values);

        assertUserPropertyFile(newUserName);
        consoleBuilder.validate();
    }

    @Test
    public void noDomainDir() throws IOException {
        File standaloneMgmtUserFile = createPropertyFile("mgmt-users.properties", "standalone");
        File standaloneMgmtGroupFile = createPropertyFile("mgmt-groups.properties", "standalone");
        File domainDir = standaloneMgmtGroupFile.getParentFile().getParentFile().toPath().resolve("domain").toFile();
        assertFalse(domainDir.exists());

        System.setProperty("jboss.server.config.dir", standaloneMgmtGroupFile.getParent());
        System.setProperty("jboss.domain.config.dir", domainDir.getAbsolutePath());
        State propertyFileFinder = new PropertyFileFinder(consoleMock, values);
        State nextState = propertyFileFinder.execute();
        assertTrue(nextState.toString(), nextState instanceof PromptRealmState);
        assertTrue("Expected to find the "+USER_NAME+" in the list of known enabled users",values.getEnabledKnownUsers().contains(USER_NAME));
        assertTrue("Expected the values.getPropertiesFiles() contained the "+standaloneMgmtUserFile,values.getUserFiles().contains(standaloneMgmtUserFile));
    }

    @Test
    public void noStandaloneDir() throws IOException {
        File domainMgmtUserFile = createPropertyFile("mgmt-users.properties", "domain");
        File domainMgmtGroupFile = createPropertyFile("mgmt-groups.properties", "domain");
        File standaloneDir = domainMgmtGroupFile.getParentFile().getParentFile().toPath().resolve("standalone").toFile();
        assertFalse(standaloneDir.exists());

        System.setProperty("jboss.server.config.dir", standaloneDir.getAbsolutePath());
        System.setProperty("jboss.domain.config.dir", domainMgmtGroupFile.getParent());
        State propertyFileFinder = new PropertyFileFinder(consoleMock, values);
        State nextState = propertyFileFinder.execute();
        assertTrue(nextState.toString(), nextState instanceof PromptRealmState);
        assertTrue("Expected to find the "+USER_NAME+" in the list of known enabled users",values.getEnabledKnownUsers().contains(USER_NAME));
        assertTrue("Expected the values.getPropertiesFiles() contained the "+domainMgmtUserFile,values.getUserFiles().contains(domainMgmtUserFile));
    }

    /**
     * Tests the creation of parent directories and property files when
     * the -cf or --create-files flag is used in Standalone mode configuration.
     *
     * @throws IOException if an error occurs during directory or file creation.
     */
    @Test
    public void createFilesServerConfigDir() throws IOException {
        RuntimeOptions options = new RuntimeOptions();
        options.setCreateFiles(true);

        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        // Using a unique suffix to avoid conflicts during parallel builds
        File customDir = new File(tempDir, "user-secrets-test-" + java.util.UUID.randomUUID());
        File customFileMgmtUsers = new File(customDir, "mgmt-users.properties");
        File customFileMgmtGroups = new File(customDir, "mgmt-groups.properties");

        StateValues stateValues = new StateValues(options);
        stateValues.setFileMode(AddUser.FileMode.MANAGEMENT);
        stateValues.getOptions().setServerConfigDir(customDir.getAbsolutePath());

        PropertyFileFinder finder = new PropertyFileFinder(consoleMock, stateValues);
        try {
            State nextState = finder.execute();

            assertTrue("Should transition to PromptRealmState", nextState instanceof PromptRealmState);
            assertTrue("Directory should have been created", customDir.exists());
            assertTrue("Path should be a directory", customDir.isDirectory());
            assertTrue("User properties file should exist", customFileMgmtUsers.exists());
            assertTrue("Group properties file should exist", customFileMgmtGroups.exists());
        } finally {
            if (customDir.exists()) {
                cleanFiles(customDir);
            }
        }
    }

    /**
     * Tests the creation of parent directories and property files when
     * the -cf or --create-files flag is used in Domain mode configuration.
     *
     * @throws IOException if an error occurs during directory or file creation.
     */
    @Test
    public void createFilesDomainConfigDir() throws IOException {
        RuntimeOptions options = new RuntimeOptions();
        options.setCreateFiles(true);

        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        // Using a unique suffix to avoid conflicts during parallel builds
        File customDir = new File(tempDir, "user-secrets-test-" + java.util.UUID.randomUUID());
        File customFileMgmtUsers = new File(customDir, "mgmt-users.properties");
        File customFileMgmtGroups = new File(customDir, "mgmt-groups.properties");

        StateValues stateValues = new StateValues(options);
        stateValues.setFileMode(AddUser.FileMode.MANAGEMENT);
        stateValues.getOptions().setDomainConfigDir(customDir.getAbsolutePath());

        PropertyFileFinder finder = new PropertyFileFinder(consoleMock, stateValues);
        try {
            State nextState = finder.execute();

            assertTrue("Should transition to PromptRealmState", nextState instanceof PromptRealmState);
            assertTrue("Directory should have been created", customDir.exists());
            assertTrue("Path should be a directory", customDir.isDirectory());
            assertTrue("User properties file should exist", customFileMgmtUsers.exists());
            assertTrue("Group properties file should exist", customFileMgmtGroups.exists());
        } finally {
            if (customDir.exists()) {
                cleanFiles(customDir);
            }
        }
    }

    /**
     * Tests the error handling when the -cf or --create-files flag is used
     * and the application lacks the necessary permissions to create the path.
     *
     * @throws IOException if an unexpected error occurs.
     */
    @Test
    public void permissionError() throws IOException {
        File readOnlyDir = Files.createTempDirectory("read_only_dir").toFile();

        try {
            readOnlyDir.setWritable(false);

            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                assumeFalse("Skipping: Windows ignored setWritable(false) on directories", readOnlyDir.canWrite());
            }

            RuntimeOptions options = new RuntimeOptions();
            options.setCreateFiles(true);

            StateValues stateValues = new StateValues(options);
            stateValues.setFileMode(AddUser.FileMode.MANAGEMENT);
            stateValues.getOptions().setServerConfigDir(readOnlyDir.getAbsolutePath());

            PropertyFileFinder finder = new PropertyFileFinder(consoleMock, stateValues);
            State nextState = finder.execute();

            assertTrue("Should return ErrorState due to lack of write permissions", nextState instanceof ErrorState);
        } finally {
            readOnlyDir.setWritable(true);
            cleanFiles(readOnlyDir);
        }
    }
}
