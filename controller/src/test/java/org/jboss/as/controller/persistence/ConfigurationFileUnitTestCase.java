/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.persistence;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 *
 * @author <a href="padamec@redhat.com">Petr Adamec</a>
 */
public class ConfigurationFileUnitTestCase {
    private static final String BASE_CONFIGURATION_NAME = "standalone.xml";
    private static final String HISTORY_CONFIGURATION_NAME = "20180101-000000000standalone.xml";
    private static final String CONFIGURATION_DIR = "WFCORE-3513";
    private static final String HISTORY_DIRECTORY = "standalone_xml_history";
    private static final String SNAPSHOT_DIRECTORY = "snapshot";

    File configurationDir;
    File historyDirectory;
    File snapshotsDirectory;

    @Before
    public void createDirectoryAndFile(){
        File targetDir = new File("target");
        if (!targetDir.exists() ) {
            Assert.fail("The target directory does not exist.");
        }
        configurationDir = createDirectory(targetDir, CONFIGURATION_DIR);
        historyDirectory = createDirectory(configurationDir, HISTORY_DIRECTORY);
        snapshotsDirectory = createDirectory(historyDirectory, SNAPSHOT_DIRECTORY);
    }

    /**
     * Create directory in parent one.</br>
     * If parent directory does not exist set fail
     * @param parent parent directory
     * @param name name of new directory
     * @return new File of directory
     */
    private File createDirectory(File parent, String name) {
        checkDir(parent);
        File newDir = new File(parent, name);
        newDir.mkdir();
        if(!newDir.exists()){
            Assert.fail("Could not create directory "+name);
        }
        return newDir;
    }


    /**
     * Create file with content
     * @param dir parent directory
     * @param name name of file
     * @param contents content of file
     * @return new File with name and contents at the parent directory
     */
    private File createFile(File dir, String name, String contents){
        checkDir(dir);
        File newFile = new File(dir, name);
        if (contents != null) {
            try{
                Files.write(newFile.toPath(), contents.getBytes(StandardCharsets.UTF_8));
            }catch (IOException e){
                Assert.fail("Could not create new file "+name);
            }
        }
        return newFile;
    }

    private void checkDir(File dir){
        if(!dir.exists() || !dir.isDirectory()){
            Assert.fail("Directory for new file doesn't exist or isn't directory");
        }
    }

    /**
     * Test crating instance of ConfigurationFile. </br>
     * A configuration file that does not match the snapshot naming pattern is present in the snapshot directory.
     * Constructor should not throw an exception.</br>
     * For more information visit <a href="issues.jboss.org/browse/WFCORE-3513">https://issues.jboss.org/browse/WFCORE-3513</a>
     */
    @Test
    public void testConstructorWithCorrectFileAtSnapshot() {
        try{
            createFile(configurationDir, BASE_CONFIGURATION_NAME, "");
            createFile(snapshotsDirectory, BASE_CONFIGURATION_NAME, "");
            createFile(snapshotsDirectory, HISTORY_CONFIGURATION_NAME, "");
            new ConfigurationFile(configurationDir, BASE_CONFIGURATION_NAME, BASE_CONFIGURATION_NAME, true);
        } catch (StringIndexOutOfBoundsException e){
            Assert.fail("Could not create an instance of ConfigurationFile with standalone.xml in the snapshot directory.");
        }
    }

    /**
     * Delete file or directory recursively
     * @param f file or directory which should be deleted
     */
    private void  delete(File f){
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        if (!f.delete()) {
            Assert.fail("Could not delete file "+f.getName());
        }
    }



    @After
    public void cleanUp(){
        if (configurationDir != null) {
            delete(configurationDir);
        }
    }
}
