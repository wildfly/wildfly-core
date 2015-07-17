/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.controller.persistence;

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xnio.IoUtils;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class PersistanceResourceTestCase {

    File configsDir;
    File standardDir;
    File externalDir;
    File historyDir;
    File currentHistoryDir;
    File standardFile;
    File externalFile;
    File lastFile;
    File initialFile;
    File bootFile;

    @Before
    public void createDirectoriesAndFiles() throws Exception {
        File tgt = new File("target");
        if (!tgt.exists()) {
            Assert.fail("target/ does not exist");
        }
        configsDir = createDir(tgt, "persistence-test-configs");
        standardDir = createDir(configsDir, "standard");
        externalDir = createDir(configsDir, "external");
        standardFile = createFile(standardDir, "standard.xml", "std");
        externalFile = createFile(externalDir, "standard.xml", "ext");

        historyDir = new File(standardDir, "standard_xml_history");
        lastFile = new File(historyDir, "standard.last.xml");
        bootFile = new File(historyDir, "standard.boot.xml");
        initialFile = new File(historyDir, "standard.initial.xml");

        currentHistoryDir = new File(historyDir, "current");
    }

    @After
    public void deleteDirectoriesAndFiles() throws Exception {
        delete(configsDir);
    }

    @Test
    public void testFileResource() throws Exception {
        assertFileContents(standardFile, "std");
        TestFileResourcePersister persister = new TestFileResourcePersister(standardFile);
        store(persister, "One");
        assertFileContents(standardFile, "One");
        store(persister, "Two");
        assertFileContents(standardFile, "Two");

        Assert.assertFalse(historyDir.exists());
    }

    @Test
    public void testDefaultPersistentConfigurationFile() throws Exception {
        assertFileContents(standardFile, "std");
        Assert.assertFalse(historyDir.exists());
        ConfigurationFile configurationFile = new ConfigurationFile(standardDir, "standard.xml", null, true);
        Assert.assertEquals(standardFile.getCanonicalPath(), configurationFile.getBootFile().getCanonicalPath());
        TestConfigurationFilePersister persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "std", "std", "std");

        store(persister, "One");
        checkFiles(null, "One", "std", "std", "One", "std");

        store(persister, "Two");
        checkFiles(null, "Two", "std", "std", "Two", "std", "One");

        store(persister, "Three");
        checkFiles(null, "Three", "std", "std", "Three", "std", "One", "Two");

        // "Reboot"
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", null, true);
        Assert.assertEquals(standardFile.getCanonicalPath(), configurationFile.getBootFile().getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "Three", "std", "Three", "Three");

        store(persister, "Four");
        checkFiles(null, "Four", "std", "Three", "Four", "Three");
    }

    @Test
    public void testOtherPersistentConfigurationFile() throws Exception {
        assertFileContents(standardFile, "std");
        createFile(standardDir, "test.xml", "non-std");

        ConfigurationFile configurationFile = new ConfigurationFile(standardDir, "standard.xml", "test.xml", true);
        File bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(new File(standardDir, "test.xml").getCanonicalPath(), bootingFile.getCanonicalPath());
        TestConfigurationFilePersister persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles("test", "non-std", "non-std", "non-std", "non-std");

        store(persister, "One");
        checkFiles("test", "One", "non-std", "non-std", "One", "non-std");

        store(persister, "Two");
        checkFiles("test", "Two", "non-std", "non-std", "Two", "non-std", "One");

        store(persister, "Three");
        checkFiles("test", "Three", "non-std", "non-std", "Three", "non-std", "One", "Two");

        // "Reboot"
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "test.xml", true);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(new File(standardFile.getCanonicalFile().getParentFile(), "test.xml").getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles("test", "Three", "non-std", "Three", "Three");

        store(persister, "Four");
        checkFiles("test", "Four", "non-std", "Three", "Four", "Three");
    }

    @Test
    public void testPersistentConfigurationFileFromBackupUsingThePrefix() throws Exception {
        //Boot with boot
        bootFile.getParentFile().mkdirs();
        createFile(historyDir, bootFile.getName(), "boot");

        ConfigurationFile configurationFile = new ConfigurationFile(standardDir, "standard.xml", "boot", true);
        File bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(bootFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        TestConfigurationFilePersister persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "boot", "boot", "boot", "boot");

        store(persister, "One");
        checkFiles(null, "One", "boot", "boot", "One", "boot");

        store(persister, "Two");
        checkFiles(null, "Two", "boot", "boot", "Two", "boot", "One");

        store(persister, "Three");
        checkFiles(null, "Three", "boot", "boot", "Three", "boot", "One", "Two");

        // "Reboot" with last
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "last", true);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(lastFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "Three", "boot", "Three", "Three");

        store(persister, "Four");
        checkFiles(null, "Four", "boot", "Three", "Four", "Three");

        // "Reboot" with initial
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "initial", true);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(initialFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "boot", "boot", "boot", "boot");

        store(persister, "Four");
        checkFiles(null, "Four", "boot", "boot", "Four", "boot");

        store(persister, "Five");
        checkFiles(null, "Five", "boot", "boot", "Five", "boot", "Four");

        store(persister, "Six");
        checkFiles(null, "Six", "boot", "boot", "Six", "boot", "Four", "Five");

        // "Reboot" with v2
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "v2", true);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(new File(currentHistoryDir, "standard.v2.xml").getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "Four", "boot", "Four", "Four");
    }

    @Test
    public void testPersistentConfigurationFileFromBackupUsingTheRelativePath() throws Exception {
        //Boot with boot
        bootFile.getParentFile().mkdirs();
        createFile(historyDir, bootFile.getName(), "boot");

        ConfigurationFile configurationFile = new ConfigurationFile(standardDir, "standard.xml", "standard_xml_history/standard.boot.xml", true);
        File bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(bootFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        TestConfigurationFilePersister persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "boot", "boot", "boot", "boot");

        store(persister, "One");
        checkFiles(null, "One", "boot", "boot", "One", "boot");

        store(persister, "Two");
        checkFiles(null, "Two", "boot", "boot", "Two", "boot", "One");

        store(persister, "Three");
        checkFiles(null, "Three", "boot", "boot", "Three", "boot", "One", "Two");

        // "Reboot" with last
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "standard_xml_history/standard.last.xml", true);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(lastFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "Three", "boot", "Three", "Three");

        store(persister, "Four");
        checkFiles(null, "Four", "boot", "Three", "Four", "Three");

        // "Reboot" with initial
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "standard_xml_history/standard.initial.xml", true);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(initialFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "boot", "boot", "boot", "boot");

        store(persister, "Four");
        checkFiles(null, "Four", "boot", "boot", "Four", "boot");

        store(persister, "Five");
        checkFiles(null, "Five", "boot", "boot", "Five", "boot", "Four");

        store(persister, "Six");
        checkFiles(null, "Six", "boot", "boot", "Six", "boot", "Four", "Five");

        // "Reboot" with v2
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "standard_xml_history/current/standard.v2.xml", true);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(new File(currentHistoryDir, "standard.v2.xml").getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "Four", "boot", "Four", "Four");
    }

    @Test(expected = IllegalStateException.class)
    public void testPersistentBadRawName() {
        new ConfigurationFile(standardDir, "standard.xml", "crap.xml", true);
    }

    @Test(expected = IllegalStateException.class)
    public void testPersistentNameFromOutsideConfigDirectory() throws Exception {
        File file = createFile(externalDir, "standard.xml", "test");
        ConfigurationFile configurationFile = new ConfigurationFile(standardDir, "standard.xml", file.getAbsolutePath(), true);
    }

    @Test
    public void testDefaultNonPersistentConfigurationFile() throws Exception {
        assertFileContents(standardFile, "std");
        Assert.assertFalse(historyDir.exists());
        ConfigurationFile configurationFile = new ConfigurationFile(standardDir, "standard.xml", null, false);
        Assert.assertEquals(standardFile.getCanonicalPath(), configurationFile.getBootFile().getCanonicalPath());
        TestConfigurationFilePersister persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "std", "std", "std");

        store(persister, "One");
        checkFiles(null, "std", "std", "std", "One", "std");

        store(persister, "Two");
        checkFiles(null, "std", "std", "std", "Two", "std", "One");

        store(persister, "Three");
        checkFiles(null, "std", "std", "std", "Three", "std", "One", "Two");

        // "Reboot"
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", null, false);
        Assert.assertEquals(standardFile.getCanonicalPath(), configurationFile.getBootFile().getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "std", "std", "std");

        store(persister, "Four");
        checkFiles(null, "std", "std", "std", "Four", "std");
    }

    @Test
    public void testOtherNonPersistentConfigurationFile() throws Exception {
        assertFileContents(standardFile, "std");
        createFile(standardDir, "test.xml", "non-std");

        ConfigurationFile configurationFile = new ConfigurationFile(standardDir, "standard.xml", "test.xml", false);
        File bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(new File(standardDir, "test.xml").getCanonicalPath(), bootingFile.getCanonicalPath());
        TestConfigurationFilePersister persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles("test", "non-std", "non-std", "non-std", "non-std");

        store(persister, "One");
        checkFiles("test", "non-std", "non-std", "non-std", "One", "non-std");

        store(persister, "Two");
        checkFiles("test", "non-std", "non-std", "non-std", "Two", "non-std", "One");

        store(persister, "Three");
        checkFiles("test", "non-std", "non-std", "non-std", "Three", "non-std", "One", "Two");

        // "Reboot"
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "test.xml", false);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(new File(standardFile.getCanonicalFile().getParentFile(), "test.xml").getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles("test", "non-std", "non-std", "non-std", "non-std");

        store(persister, "Four");
        checkFiles("test", "non-std", "non-std", "non-std", "Four", "non-std");
    }

    @Test
    public void testNonPersistentConfigurationFileFromBackupUsingThePrefix() throws Exception {
        //Boot with boot
        bootFile.getParentFile().mkdirs();
        createFile(historyDir, bootFile.getName(), "boot");

        ConfigurationFile configurationFile = new ConfigurationFile(standardDir, "standard.xml", "boot", false);
        File bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(bootFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        TestConfigurationFilePersister persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "boot", "boot", "boot");

        store(persister, "One");
        checkFiles(null, "std", "boot", "boot", "One", "boot");

        store(persister, "Two");
        checkFiles(null, "std", "boot", "boot", "Two", "boot", "One");

        store(persister, "Three");
        checkFiles(null, "std", "boot", "boot", "Three", "boot", "One", "Two");

        // "Reboot" with last
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "last", false);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(lastFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "boot", "Three", "Three");

        store(persister, "Four");
        checkFiles(null, "std", "boot", "Three", "Four", "Three");

        // "Reboot" with initial
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "initial", false);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(initialFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "boot", "boot", "boot");

        store(persister, "Four");
        checkFiles(null, "std", "boot", "boot", "Four", "boot");

        store(persister, "Five");
        checkFiles(null, "std", "boot", "boot", "Five", "boot", "Four");

        store(persister, "Six");
        checkFiles(null, "std", "boot", "boot", "Six", "boot", "Four", "Five");

        // "Reboot" with v2
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "v2", false);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(new File(currentHistoryDir, "standard.v2.xml").getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "boot", "Four", "Four");
    }

    @Test
    public void testNonPersistentConfigurationFileFromBackupUsingTheRelativePath() throws Exception {
        //Boot with boot
        bootFile.getParentFile().mkdirs();
        createFile(historyDir, bootFile.getName(), "boot");

        ConfigurationFile configurationFile = new ConfigurationFile(standardDir, "standard.xml", "standard_xml_history/standard.boot.xml", false);
        File bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(bootFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        TestConfigurationFilePersister persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "boot", "boot", "boot");

        store(persister, "One");
        checkFiles(null, "std", "boot", "boot", "One", "boot");

        store(persister, "Two");
        checkFiles(null, "std", "boot", "boot", "Two", "boot", "One");

        store(persister, "Three");
        checkFiles(null, "std", "boot", "boot", "Three", "boot", "One", "Two");

        // "Reboot" with last
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "standard_xml_history/standard.last.xml", false);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(lastFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "boot", "Three", "Three");

        store(persister, "Four");
        checkFiles(null, "std", "boot", "Three", "Four", "Three");

        // "Reboot" with initial
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "standard_xml_history/standard.initial.xml", false);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(initialFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "boot", "boot", "boot");

        store(persister, "Four");
        checkFiles(null, "std", "boot", "boot", "Four", "boot");

        store(persister, "Five");
        checkFiles(null, "std", "boot", "boot", "Five", "boot", "Four");

        store(persister, "Six");
        checkFiles(null, "std", "boot", "boot", "Six", "boot", "Four", "Five");

        // "Reboot" with v2
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", "standard_xml_history/current/standard.v2.xml", false);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(new File(currentHistoryDir, "standard.v2.xml").getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "boot", "Four", "Four");
    }

    @Test(expected = IllegalStateException.class)
    public void testNonPersistentBadRawName() {
        new ConfigurationFile(standardDir, "standard.xml", "crap.xml", false);
    }

    @Test
    public void testNonPersistentNameFromOutsideConfigDirectory() throws Exception {
        assertFileContents(externalFile, "ext");

        ConfigurationFile configurationFile = new ConfigurationFile(standardDir, "standard.xml", externalFile.getCanonicalPath(), false);
        File bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(externalFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        TestConfigurationFilePersister persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "ext", "ext", "ext");
        assertFileContents(externalFile, "ext");

        store(persister, "One");
        checkFiles(null, "std", "ext", "ext", "One", "ext");
        assertFileContents(externalFile, "ext");

        store(persister, "Two");
        checkFiles(null, "std", "ext", "ext", "Two", "ext", "One");
        assertFileContents(externalFile, "ext");

        store(persister, "Three");
        checkFiles(null, "std", "ext", "ext", "Three", "ext", "One", "Two");
        assertFileContents(externalFile, "ext");

        externalFile.delete();
        createFile(externalDir, externalFile.getName(), "ext2");

        // "Reboot"
        configurationFile = new ConfigurationFile(standardDir, "standard.xml", externalFile.getCanonicalPath(), false);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(externalFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "ext", "ext2", "ext2");
        assertFileContents(externalFile, "ext2");

        store(persister, "Four");
        checkFiles(null, "std", "ext", "ext2", "Four", "ext2");
        assertFileContents(externalFile, "ext2");
    }

    @Test
    public void testPosixFilePermissions() throws Exception {
        assertFileContents(standardFile, "std");
        if (!Files.getFileStore(standardDir.toPath()).supportsFileAttributeView(PosixFileAttributeView.class)) {
            return;
        }
        PosixFileAttributeView posixStandardDirView = Files.getFileAttributeView(standardDir.toPath(), PosixFileAttributeView.class);
        assert posixStandardDirView != null;
        Set<PosixFilePermission> allPermissions = new HashSet<>(Arrays.asList(PosixFilePermission.values()));
        //Setting 777 on the parent dir so created files should be with the specified permissions
        posixStandardDirView.setPermissions(allPermissions);
        PosixFileAttributeView posixStandardFileView = Files.getFileAttributeView(standardFile.toPath(), PosixFileAttributeView.class);
        assert posixStandardFileView != null;
        Set<PosixFilePermission> readWritePermissions = new HashSet<>(Arrays.asList(OWNER_READ, OWNER_WRITE, GROUP_READ));
        //Setting 660 on the file
        posixStandardFileView.setPermissions(readWritePermissions);
        ConfigurationFile configurationFile = new ConfigurationFile(standardDir, "standard.xml", standardFile.getCanonicalPath(), false);
        File bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(standardFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        Path testPermissions = Files.createFile(standardDir.toPath().resolve("TestPermissions.tmp"));
        PosixFileAttributeView posixDefaultCreatedFilePermissionView = Files.getFileAttributeView(testPermissions, PosixFileAttributeView.class);
        assert posixDefaultCreatedFilePermissionView != null;
        Set<PosixFilePermission> defaultPermissions = posixDefaultCreatedFilePermissionView.readAttributes().permissions();
        assertThat(defaultPermissions, hasItem(OWNER_READ));
        assertThat(defaultPermissions, hasItem(OWNER_WRITE));
        assertThat(defaultPermissions, hasItem(GROUP_READ));
        assertThat(defaultPermissions, hasItem(GROUP_WRITE));
        assertThat(defaultPermissions, hasItem(OTHERS_READ));
        assertThat(defaultPermissions, not(hasItem(OWNER_EXECUTE)));
        assertThat(defaultPermissions, not(hasItem(GROUP_EXECUTE)));
        assertThat(defaultPermissions, not(hasItem(OTHERS_WRITE)));
        assertThat(defaultPermissions, not(hasItem(OTHERS_EXECUTE)));
        Files.delete(testPermissions);
        configurationFile.successfulBoot();
        Set<PosixFilePermission> configurationFilePemissions = posixStandardFileView.readAttributes().permissions();
        assertThat(configurationFilePemissions, hasItem(OWNER_READ));
        assertThat(configurationFilePemissions, hasItem(OWNER_WRITE));
        assertThat(configurationFilePemissions, hasItem(GROUP_READ));
        assertThat(configurationFilePemissions, not(hasItem(OWNER_EXECUTE)));
        assertThat(configurationFilePemissions, not(hasItem(GROUP_WRITE)));
        assertThat(configurationFilePemissions, not(hasItem(GROUP_EXECUTE)));
        assertThat(configurationFilePemissions, not(hasItem(OTHERS_READ)));
        assertThat(configurationFilePemissions, not(hasItem(OTHERS_WRITE)));
        assertThat(configurationFilePemissions, not(hasItem(OTHERS_EXECUTE)));
    }

    @Test
    public void testAclFilePermissions() throws Exception {
        assertFileContents(standardFile, "std");
        if (!Files.getFileStore(standardDir.toPath()).supportsFileAttributeView(AclFileAttributeView.class)) {
            return;
        }
        AclFileAttributeView aclStandardDirView = Files.getFileAttributeView(standardDir.toPath(), AclFileAttributeView.class);
        assert aclStandardDirView != null;
        //Setting 777 on the parent dir so created files should be with the specified permissions
        List<AclEntry> aclEntries = aclStandardDirView.getAcl();
        List<AclEntry> allPermissions = new ArrayList<>(aclEntries.size());
        for(AclEntry entry : aclEntries) {
            if(entry.principal().equals(aclStandardDirView.getOwner())) {
                allPermissions.add(createAllAccessACLEntry(entry.principal()));
            } else {
                allPermissions.add(entry);
            }
        }
        aclStandardDirView.setAcl(allPermissions);
        AclFileAttributeView aclStandardFileView = Files.getFileAttributeView(standardFile.toPath(), AclFileAttributeView.class);
        assert aclStandardFileView != null;
        aclEntries = aclStandardFileView.getAcl();
        List<AclEntry> readWritePermissions = new ArrayList<>(aclEntries.size());
        //Setting 660 on the file
        for(AclEntry entry : aclEntries) {
            if(entry.principal().equals(aclStandardDirView.getOwner())) {
                readWritePermissions.add(createConfigurationAccessACLEntry(entry.principal()));
            } else {
                readWritePermissions.add(entry);
            }
        }
        aclStandardFileView.setAcl(readWritePermissions);
        ConfigurationFile configurationFile = new ConfigurationFile(standardDir, "standard.xml", standardFile.getCanonicalPath(), false);
        File bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(standardFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        Path testPermissions = Files.createFile(standardDir.toPath().resolve("TestPermissions.tmp"));
        AclFileAttributeView aclDefaultCreatedFilePermissionView = Files.getFileAttributeView(testPermissions, AclFileAttributeView.class);
        assert aclDefaultCreatedFilePermissionView != null;
        List<AclEntry> defaultPermissions = aclDefaultCreatedFilePermissionView.getAcl();
        for(AclEntry entry : defaultPermissions) {
            if(entry.principal().equals(aclDefaultCreatedFilePermissionView.getOwner())) {
                assertThat(entry.toString(), entry.permissions(), hasItem(AclEntryPermission.EXECUTE));
            }
        }
        Files.delete(testPermissions);
        configurationFile.successfulBoot();
        List<AclEntry> configurationFilePemissions = aclStandardFileView.getAcl();
         for(AclEntry entry : configurationFilePemissions) {
            if(entry.principal().equals(aclStandardFileView.getOwner())) {
                 assertThat(entry.toString(),entry.permissions(), not(hasItem(AclEntryPermission.EXECUTE)));
            }
        }
    }

    private AclEntry createAllAccessACLEntry(UserPrincipal user) {
        AclEntry entry = AclEntry
                .newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(user)
                .setPermissions(AclEntryPermission.values())
                .setFlags(AclEntryFlag.FILE_INHERIT,
                        AclEntryFlag.DIRECTORY_INHERIT)
                .build();
        return entry;
    }

    private AclEntry createConfigurationAccessACLEntry(UserPrincipal user) {
        AclEntry entry = AclEntry
                .newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(user)
                .setPermissions(
                        AclEntryPermission.WRITE_NAMED_ATTRS,
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.WRITE_ATTRIBUTES,
                        AclEntryPermission.READ_ATTRIBUTES,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.READ_DATA,
                        AclEntryPermission.READ_NAMED_ATTRS,
                        AclEntryPermission.READ_ACL,
                        AclEntryPermission.SYNCHRONIZE,
                        AclEntryPermission.DELETE)
                .setFlags(AclEntryFlag.FILE_INHERIT)
                .build();
        return entry;
    }

    @Test
    public void testNonPersistentReload() throws Exception {
        assertFileContents(standardFile, "std");

        ConfigurationFile configurationFile = new ConfigurationFile(standardDir, "standard.xml", standardFile.getCanonicalPath(), false);
        File bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(standardFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        TestConfigurationFilePersister persister = new TestConfigurationFilePersister(configurationFile);

        configurationFile.successfulBoot();
        checkDirectoryExists(historyDir);
        checkDirectoryExists(currentHistoryDir);

        checkFiles(null, "std", "std", "std", "std");

        store(persister, "One");
        checkFiles(null, "std", "std", "std", "One", "std");

        configurationFile.resetBootFile(false);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(standardFile.getCanonicalPath(), bootingFile.getCanonicalPath());
        persister = new TestConfigurationFilePersister(configurationFile);
        configurationFile.successfulBoot();
        checkFiles(null, "std", "std", "std", "One", "std");

        configurationFile.resetBootFile(true);
        bootingFile = configurationFile.getBootFile();
        Assert.assertEquals(addSuffix(standardFile, "last"), bootingFile.getCanonicalPath());

    }

    private String addSuffix(File file, String suffix) throws IOException {
        StringBuilder builder = new StringBuilder(file.getParentFile().getCanonicalPath());
        System.out.println(builder);
        builder.append(File.separatorChar);
        builder.append(file.getName().replace('.', '_') + "_history" + File.separatorChar);
        builder.append(file.getName().substring(0, file.getName().lastIndexOf(".xml")));
        builder.append(".");
        builder.append(suffix);
        builder.append(".xml");

        System.out.println(builder.toString());

        return builder.toString();
    }

    private void checkFiles(String mainFileName, String main, String initial, String boot, String last, String... versions) throws Exception {
        File mainFile = this.standardFile;
        File bootFile = this.bootFile;
        File lastFile = this.lastFile;
        File initialFile = this.initialFile;
        if (mainFileName != null) {
            mainFile = new File(mainFile.getAbsoluteFile().getParent(), mainFileName + ".xml");
            bootFile = new File(bootFile.getAbsoluteFile().getParent(), mainFileName + ".boot.xml");
            lastFile = new File(bootFile.getAbsoluteFile().getParent(), mainFileName + ".last.xml");
            initialFile = new File(bootFile.getAbsoluteFile().getParent(), mainFileName + ".initial.xml");
        }

        assertFileContents(mainFile, main);
        assertFileContents(bootFile, boot);
        assertFileContents(lastFile, last);
        assertFileContents(initialFile, initial);
        checkVersionedHistory(mainFileName == null ? "standard" : mainFileName, versions);
    }

    private void store(TestConfigurationPersister persister, String s) throws Exception {
        persister.store(new ModelNode(s), Collections.<PathAddress>emptySet()).commit();
    }

    private File createDir(File dir, String name) {
        checkDirectoryExists(dir);
        File created = new File(dir, name);
        created.mkdir();
        if (!created.exists()) {
            Assert.fail("Could not create " + created);
        }
        return created;
    }

    private File createFile(File dir, String name, String contents) throws IOException {
        checkDirectoryExists(dir);
        File file = new File(dir, name);
        if (contents != null) {
            Writer out = new BufferedWriter(new FileWriter(file));
            try {
                out.write(contents);
            } finally {
                IoUtils.safeClose(out);
            }
        }
        return file;
    }

    private void checkDirectoryExists(File dir) {
        Assert.assertTrue(dir + " does not exist", dir.exists());
        Assert.assertTrue(dir + " is not a directory", dir.isDirectory());
    }

    private void delete(File file) {
        if (file.isDirectory()) {
            for (String name : file.list()) {
                delete(new File(file, name));
            }
        }
        if (!file.delete() && file.exists()) {
            Assert.fail("Could not delete " + file);
        }
    }

    private void assertFileContents(File file, String expectedContents) throws Exception {
        Assert.assertTrue(file + " does not exist", file.exists());
        StringBuilder sb = new StringBuilder();
        BufferedReader in = new BufferedReader(new FileReader(file));
        try {
            String s = in.readLine();
            while (s != null) {
                sb.append(s);
                s = in.readLine();
            }
        } finally {
            in.close();
        }
        Assert.assertEquals(expectedContents, sb.toString());
    }

    private void checkVersionedHistory(String name, String... versions) throws Exception {
        for (int i = 0; i <= versions.length; i++) {
            File file = new File(currentHistoryDir, name + ".v" + (i + 1) + ".xml");
            if (i == versions.length) {
                Assert.assertFalse(file.exists());
            } else {
                assertFileContents(file, versions[i]);
            }
        }
    }

    private class TestFileResourcePersister extends TestConfigurationPersister {

        private final File fileName;

        public TestFileResourcePersister(File fileName) {
            this.fileName = fileName;
        }

        @Override
        public PersistenceResource create(ModelNode model) throws ConfigurationPersistenceException {
            return new FilePersistenceResource(model, fileName, this);
        }
    }

    private class TestConfigurationFilePersister extends TestConfigurationPersister {

        private final ConfigurationFile configurationFile;

        public TestConfigurationFilePersister(ConfigurationFile configurationFile) {
            this.configurationFile = configurationFile;
        }

        @Override
        PersistenceResource create(ModelNode model) throws ConfigurationPersistenceException {
            return new ConfigurationFilePersistenceResource(model, configurationFile, this);
        }
    }
}
