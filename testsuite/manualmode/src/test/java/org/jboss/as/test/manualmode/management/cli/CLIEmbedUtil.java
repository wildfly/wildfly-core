/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.management.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.codehaus.plexus.util.FileUtils;
import org.jboss.as.test.integration.management.util.CLIWrapper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Shared utilities for testing embedded standalone / host-controller
 *
 * @author Ken Wills (c) 2016 Red Hat Inc.
 */

public class CLIEmbedUtil {

    static void copyConfig(final File root, String baseDirName, String base, String newName, boolean requiresExists) throws IOException {
        File configDir = new File(root, baseDirName + File.separatorChar + "configuration");
        File baseFile = new File(configDir, base);
        assertTrue(!requiresExists || baseFile.exists());
        File newFile = new File(configDir, newName);
        Files.copy(baseFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
    }

    static void delConfigFile(final File root, String baseDirName, String base) throws IOException {
        File configDir = new File(root, baseDirName + File.separatorChar + "configuration");
        File baseFile = new File(configDir, base);
        baseFile.delete();
    }

    static void copyServerBaseDir(final File root, final String baseDirName, final String newbaseDirName, boolean force) throws IOException {
        // copy the base server directory (standalone etc to a new name to test changing jboss.server.base.dir etc)
        final File baseDir = new File(root + File.separator + baseDirName);
        assertTrue(baseDir.exists());
        final File newBaseDir = new File(root + File.separator + newbaseDirName);
        assertFalse(!force && newBaseDir.exists());
        FileUtils.copyDirectoryStructure(baseDir, newBaseDir);
        assertTrue(newBaseDir.exists());

        // remove anything we'll auto-create on startup
        final String[] cleanDirs = {"content", "data", "deployments", "log", "tmp"};
        for (final String dir : cleanDirs) {
            FileUtils.deleteDirectory(root + File.separator + newbaseDirName + File.separator + dir);
        }
    }

    static List<String> getOutputLines(String raw) throws IOException {
        if (raw == null) {
            return Collections.emptyList();
        }
        BufferedReader br = new BufferedReader(new StringReader(raw));
        List<String> result = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            result.add(line);
        }
        return result;
    }

    /**
     * Sends commands to set up an elytron subsystem with resources relevant to securing process management.
     * This method assumes/requires that the caller is configuring a CLI batch, and thus should be invoked after
     * the caller has sent a "batch" command that the caller will later execute with a "run-batch".
     *
     * @param cli CLIWrapper to use for sending commands
     * @param hostName name of the host controller on which the subystem is being configured,
     *                or null if this is for a server
     */
    static void configureElytronManagement(CLIWrapper cli, String hostName) {

        String prefix = hostName == null ? "" : "/host=" + hostName;
        String pathType = hostName == null ? "server" : "domain";

        cli.sendLine(prefix + "/subsystem=elytron:add(final-providers=combined-providers,disallowed-providers=[OracleUcrypto])");
        cli.sendLine(prefix + "/subsystem=elytron/aggregate-providers=combined-providers:add(providers=[elytron,openssl])");
        cli.sendLine(prefix + "/subsystem=elytron/provider-loader=elytron:add(module=org.wildfly.security.elytron)");
        cli.sendLine(prefix + "/subsystem=elytron/provider-loader=openssl:add(module=org.wildfly.openssl)");
        cli.sendLine(prefix + "/subsystem=elytron/file-audit-log=local-audit:add(path=audit.log,relative-to=jboss."+ pathType + ".log.dir,format=JSON)");
        cli.sendLine(prefix + "/subsystem=elytron/security-domain=ManagementDomain:add(permission-mapper=default-permission-mapper,default-realm=ManagementRealm,realms=[{realm=ManagementRealm,role-decoder=groups-to-roles},{realm=local,role-mapper=super-user-mapper}])");
        cli.sendLine(prefix + "/subsystem=elytron/identity-realm=local:add(identity=\"$local\")");
        cli.sendLine(prefix + "/subsystem=elytron/properties-realm=ManagementRealm:add(users-properties={path=mgmt-users.properties,relative-to=jboss." + pathType + ".config.dir,digest-realm-name=ManagementRealm},groups-properties={path=mgmt-groups.properties,relative-to=jboss." + pathType + ".config.dir})");
        cli.sendLine(prefix + "/subsystem=elytron/simple-permission-mapper=default-permission-mapper:add(mapping-mode=first,permission-mappings=[{principals=[anonymous],permission-sets=[{permission-set=default-permissions}]},{match-all=true,permission-sets=[{permission-set=login-permission},{permission-set=default-permissions}]}])");
        cli.sendLine(prefix + "/subsystem=elytron/constant-realm-mapper=local:add(realm-name=local)");
        cli.sendLine(prefix + "/subsystem=elytron/simple-role-decoder=groups-to-roles:add(attribute=groups)");
        cli.sendLine(prefix + "/subsystem=elytron/constant-role-mapper=super-user-mapper:add(roles=[SuperUser])");
        cli.sendLine(prefix + "/subsystem=elytron/permission-set=login-permission:add(permissions=[{class-name=org.wildfly.security.auth.permission.LoginPermission}])");
        cli.sendLine(prefix + "/subsystem=elytron/permission-set=default-permissions:add()");
        cli.sendLine(prefix + "/subsystem=elytron/http-authentication-factory=management-http-authentication:add(security-domain=ManagementDomain,http-server-mechanism-factory=global,mechanism-configurations=[{mechanism-name=DIGEST,mechanism-realm-configurations=[{realm-name=ManagementRealm}]}])");
        cli.sendLine(prefix + "/subsystem=elytron/provider-http-server-mechanism-factory=global:add()");
        cli.sendLine(prefix + "/subsystem=elytron/sasl-authentication-factory=management-sasl-authentication:add(security-domain=ManagementDomain,sasl-server-factory=configured,mechanism-configurations=[{mechanism-name=JBOSS-LOCAL-USER,realm-mapper=local},{mechanism-name=DIGEST-MD5,mechanism-realm-configurations=[{realm-name=ManagementRealm}]}])");
        cli.sendLine(prefix + "/subsystem=elytron/configurable-sasl-server-factory=configured:add(sasl-server-factory=elytron,properties={wildfly.sasl.local-user.default-user=\"$local\",wildfly.sasl.local-user.challenge-path=\"${jboss." + pathType + ".temp.dir}/auth\"})");
        cli.sendLine(prefix + "/subsystem=elytron/mechanism-provider-filtering-sasl-server-factory=elytron:add(sasl-server-factory=global,filters=[{provider-name=WildFlyElytron}])");
        cli.sendLine(prefix + "/subsystem=elytron/provider-sasl-server-factory=global:add()");

    }
}
