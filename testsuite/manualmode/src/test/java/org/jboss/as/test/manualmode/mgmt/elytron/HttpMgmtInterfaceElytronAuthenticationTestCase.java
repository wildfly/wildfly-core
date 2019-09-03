/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.manualmode.mgmt.elytron;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.security.common.CoreUtils;

import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Test for authentication through http-interface secured by Elytron http-authentication-factory.
 *
 * @author olukas
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class HttpMgmtInterfaceElytronAuthenticationTestCase {

    private static final String PREDEFINED_HTTP_SERVER_MECHANISM_FACTORY = "global";
    private static final String MANAGEMENT_FILESYSTEM_NAME = "mgmt-filesystem-name";

    private static File tempFolder;

    private static final String USER = "user";
    private static final String CORRECT_PASSWORD = "password";

    private static String host;

    private static String existingHttpManagementFactory;

    @Inject
    private static ServerController CONTROLLER;

    public static void prepareServerConfiguration() throws Exception {
        tempFolder = File.createTempFile("ely-" + HttpMgmtInterfaceElytronAuthenticationTestCase.class.getSimpleName(), "", null);
        if (tempFolder.exists()) {
            tempFolder.delete();
            tempFolder.mkdir();
        }
        String fsRealmPath = tempFolder.getAbsolutePath() + File.separator + "fs-realm-users";

        try (CLIWrapper cli = new CLIWrapper(true)) {
            final String levelStr = "";
            final List<String> roles = new ArrayList<>();

            cli.sendLine("/core-service=management/management-interface=http-interface:read-attribute(name=http-authentication-factory)");
            ModelNode res = cli.readAllAsOpResult().getResponseNode().get("result");
            if (res.isDefined()) {
                existingHttpManagementFactory = res.asString();
            }

            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:add(path=\"%s\", %s)", MANAGEMENT_FILESYSTEM_NAME, escapePath(fsRealmPath), levelStr));
            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:add-identity(identity=%s)", MANAGEMENT_FILESYSTEM_NAME, USER));
            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:set-password(identity=%s, clear={password=\"%s\"})",
                    MANAGEMENT_FILESYSTEM_NAME, USER, CORRECT_PASSWORD));
            if (!roles.isEmpty()) {
                cli.sendLine(String.format(
                        "/subsystem=elytron/filesystem-realm=%s:add-identity-attribute=%s:add-attribute(name=groups, value=[%s])", MANAGEMENT_FILESYSTEM_NAME,
                       USER, String.join(",", roles)));
            }

            cli.sendLine(String.format(
                    "/subsystem=elytron/security-domain=%1$s:add(realms=[{realm=%1$s,role-decoder=groups-to-roles},{realm=local,role-mapper=super-user-mapper}],default-realm=%1$s,permission-mapper=default-permission-mapper)",
                    MANAGEMENT_FILESYSTEM_NAME));
            cli.sendLine(String.format(
                    "/subsystem=elytron/http-authentication-factory=%1$s:add(http-server-mechanism-factory=%2$s,security-domain=%1$s,"
                    + "mechanism-configurations=[{mechanism-name=BASIC,mechanism-realm-configurations=[{realm-name=\"%1$s\"}]}])",
                    MANAGEMENT_FILESYSTEM_NAME, PREDEFINED_HTTP_SERVER_MECHANISM_FACTORY));
            cli.sendLine(String.format(
                    "/core-service=management/management-interface=http-interface:write-attribute(name=http-authentication-factory,value=%s)",
                    MANAGEMENT_FILESYSTEM_NAME));
            cli.sendLine("reload");
        }
    }

    public static void resetServerConfiguration() throws Exception {
        try (CLIWrapper cli = new CLIWrapper(true)) {
            FileUtils.deleteQuietly(tempFolder);
            String restoreMgmtAuth = existingHttpManagementFactory == null
                    ? "/core-service=management/management-interface=http-interface:undefine-attribute(name=http-authentication-factory)"
                    : String.format(
                    "/core-service=management/management-interface=http-interface:write-attribute(name=http-authentication-factory,value=%s)",
                    existingHttpManagementFactory);
            cli.sendLine(String.format(
                    "/core-service=management/management-interface=http-interface:undefine-attribute(name=http-authentication-factory)",
                    MANAGEMENT_FILESYSTEM_NAME));
            cli.sendLine(String.format(
                    "/subsystem=elytron/http-authentication-factory=%s:remove()",
                    MANAGEMENT_FILESYSTEM_NAME, PREDEFINED_HTTP_SERVER_MECHANISM_FACTORY));
            cli.sendLine(String.format("/subsystem=elytron/security-domain=%s:remove()", MANAGEMENT_FILESYSTEM_NAME));
            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:remove()", MANAGEMENT_FILESYSTEM_NAME));
        }
        FileUtils.deleteDirectory(tempFolder);
    }

    @BeforeClass
    public static void setupServer() throws Exception {
        CONTROLLER.start();
        host = TestSuiteEnvironment.getServerAddress();
        prepareServerConfiguration();
    }

    @AfterClass
    public static void resetServer() throws Exception {
        try {
            resetServerConfiguration();
        } finally {
            CONTROLLER.stop();
        }
    }

    /**
     * Test whether existing user with correct password has granted access through http-interface secured by Elytron
     * http-authentication-factory.
     */
    @Test
    public void testCorrectUser() throws Exception {
        CoreUtils.makeCallWithBasicAuthn(createSimpleManagementOperationUrl(), USER, CORRECT_PASSWORD, SC_OK);
    }

    /**
     * Test whether existing user with wrong password has denied access through http-interface secured by Elytron
     * http-authentication-factory.
     */
    @Test
    public void testWrongPassword() throws Exception {
        CoreUtils.makeCallWithBasicAuthn(createSimpleManagementOperationUrl(), USER, "wrongPassword", SC_UNAUTHORIZED);
    }

    /**
     * Test whether existing user with empty password has denied access through http-interface secured by Elytron
     * http-authentication-factory.
     */
    @Test
    public void testEmptyPassword() throws Exception {
        CoreUtils.makeCallWithBasicAuthn(createSimpleManagementOperationUrl(), USER, "", SC_UNAUTHORIZED);
    }

    /**
     * Test whether non-existing user has denied access through http-interface secured by Elytron http-authentication-factory.
     */
    @Test
    public void testWrongUser() throws Exception {
        CoreUtils.makeCallWithBasicAuthn(createSimpleManagementOperationUrl(), "wrongUser", CORRECT_PASSWORD, SC_UNAUTHORIZED);
    }

    /**
     * Test whether user with empty username has denied access through http-interface secured by Elytron
     * http-authentication-factory.
     */
    @Test
    public void testEmptyUser() throws Exception {
        CoreUtils.makeCallWithBasicAuthn(createSimpleManagementOperationUrl(), "", CORRECT_PASSWORD, SC_UNAUTHORIZED);
    }

    private URL createSimpleManagementOperationUrl() throws URISyntaxException, IOException {
        return new URL("http://" + host + ":9990/management?operation=attribute&name=server-state");
    }

    private static String escapePath(String path) {
        // fix windows path escaping.
        return path.replace("\\", "\\\\");
    }
}
