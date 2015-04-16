/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.management.security.adduser;

import org.jboss.as.domain.management.security.adduser.AddUser.FileMode;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
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

    // As we're reusing StateValues across invocation of PropertyFileFinder execute()
    // we need to reset permissions state prior to executing newxt test.
    private void resetStateValuePermissionsFlags() {
        values.setValidFilePermissions(true);
        values.setFilePermissionsProblemPath(null);
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
            // Don't forget to reset permissions state
            resetStateValuePermissionsFlags();
        }

        // Test parent dir without execute
        if(standaloneDir.setExecutable(false)) {
            State nextState = propertyFileFinder.execute();
            assertTrue(nextState instanceof ErrorState);
            standaloneDir.setExecutable(true);
            // Don't forget to reset permissions state
            resetStateValuePermissionsFlags();
        }

        // Test file without read
        if(standaloneMgmtUserFile.setReadable(false)) {
            State nextState = propertyFileFinder.execute();
            assertTrue(nextState instanceof ErrorState);
            standaloneMgmtUserFile.setReadable(true);
            // Don't forget to reset permissions state
            resetStateValuePermissionsFlags();
        }

        // Test file without write
        if(standaloneMgmtUserFile.setWritable(false)) {
            State nextState = propertyFileFinder.execute();
            assertTrue(nextState instanceof ErrorState);
            standaloneMgmtUserFile.setWritable(true);
            // Don't forget to reset permissions state
            resetStateValuePermissionsFlags();
        }

    }

}
