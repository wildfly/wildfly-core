/*
 * Copyright 2020 JBoss by Red Hat.
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
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
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
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Tests for authentication through the http-interface secured by an Elytron http-authentication-factory
 * with the use of a source-address-role-decoder where the IP address of the client matches the
 * address configured on the decoder.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class  AuthenticationWithSourceAddressRoleDecoderMatchTestCase {

    private static final String PREDEFINED_HTTP_SERVER_MECHANISM_FACTORY = "global";
    private static final String MANAGEMENT_FILESYSTEM_NAME = "mgmt-filesystem-name";
    private static final String ROLE_DECODER_1_NAME = "decoder1";
    private static final String ROLE_DECODER_2_NAME = "decoder2";
    private static final String AGGREGATE_ROLE_DECODER_NAME = "aggregateRoleDecoder";
    private static final String IP_PERMISSION_MAPPER_NAME = "ipPermissionMapper";

    private static File tempFolder;

    private static final String USER_ALICE = "alice";
    private static final String PASSWORD_ALICE = "alice123+";
    private static final String USER_BOB = "bob";
    private static final String PASSWORD_BOB = "bob123+";
    private static final String USER_CHARLIE = "charlie";
    private static final String PASSWORD_CHARLIE = "charlie123+";

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

            cli.sendLine("/core-service=management/management-interface=http-interface:read-attribute(name=http-authentication-factory)");
            ModelNode res = cli.readAllAsOpResult().getResponseNode().get("result");
            if (res.isDefined()) {
                existingHttpManagementFactory = res.asString();
            }

            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:add(path=\"%s\", %s)", MANAGEMENT_FILESYSTEM_NAME, escapePath(fsRealmPath), levelStr));
            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:add-identity(identity=%s)", MANAGEMENT_FILESYSTEM_NAME, USER_ALICE));
            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:set-password(identity=%s, clear={password=\"%s\"})",
                    MANAGEMENT_FILESYSTEM_NAME, USER_ALICE, PASSWORD_ALICE));
            cli.sendLine(String.format(
                    "/subsystem=elytron/filesystem-realm=%s:add-identity-attribute(identity=%s, name=Roles, value=[%s])", MANAGEMENT_FILESYSTEM_NAME,
                    USER_ALICE, "Employee"));
            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:add-identity(identity=%s)", MANAGEMENT_FILESYSTEM_NAME, USER_BOB));
            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:set-password(identity=%s, clear={password=\"%s\"})",
                    MANAGEMENT_FILESYSTEM_NAME, USER_BOB, PASSWORD_BOB));
            cli.sendLine(String.format(
                    "/subsystem=elytron/filesystem-realm=%s:add-identity-attribute(identity=%s, name=Roles, value=[%s])", MANAGEMENT_FILESYSTEM_NAME,
                    USER_BOB, "Admin"));
            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:add-identity(identity=%s)", MANAGEMENT_FILESYSTEM_NAME, USER_CHARLIE));
            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:set-password(identity=%s, clear={password=\"%s\"})",
                    MANAGEMENT_FILESYSTEM_NAME, USER_CHARLIE, PASSWORD_CHARLIE));
            cli.sendLine(String.format(
                    "/subsystem=elytron/filesystem-realm=%s:add-identity-attribute(identity=%s, name=Roles, value=[%s])", MANAGEMENT_FILESYSTEM_NAME,
                    USER_CHARLIE, "Employee"));

            cli.sendLine(String.format(
                    "/subsystem=elytron/source-address-role-decoder=%s:add(source-address=%s,roles=[Admin])", ROLE_DECODER_1_NAME, getIPAddress()));
            cli.sendLine(String.format(
                    "/subsystem=elytron/source-address-role-decoder=%s:add(source-address=%s,roles=[Employee])", ROLE_DECODER_2_NAME, "99.99.99.99"));
            cli.sendLine(String.format(
                    "/subsystem=elytron/aggregate-role-decoder=%s:add(role-decoders=[%s,%s])", AGGREGATE_ROLE_DECODER_NAME, ROLE_DECODER_1_NAME, ROLE_DECODER_2_NAME));
            cli.sendLine(String.format(
                    "/subsystem=elytron/simple-permission-mapper=%s:add(mapping-mode=and,permission-mappings=[{roles=[Admin],permission-sets=[{permission-set=login-permission},{permission-set=default-permissions}]},{principals=[%s]}])",
                    IP_PERMISSION_MAPPER_NAME, USER_CHARLIE));

            cli.sendLine(String.format(
                    "/subsystem=elytron/security-domain=%1$s:add(realms=[{realm=%1$s},{realm=local,role-mapper=super-user-mapper}],default-realm=%1$s,permission-mapper=%2$s,role-decoder=%3$s)",
                    MANAGEMENT_FILESYSTEM_NAME, IP_PERMISSION_MAPPER_NAME, AGGREGATE_ROLE_DECODER_NAME));
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
            cli.sendLine(String.format("/subsystem=elytron/aggregate-role-decoder=%s:remove()", AGGREGATE_ROLE_DECODER_NAME));
            cli.sendLine(String.format("/subsystem=elytron/source-address-role-decoder=%s:remove()", ROLE_DECODER_1_NAME));
            cli.sendLine(String.format("/subsystem=elytron/source-address-role-decoder=%s:remove()", ROLE_DECODER_2_NAME));
            cli.sendLine(String.format("/subsystem=elytron/simple-permission-mapper=%s:remove()", IP_PERMISSION_MAPPER_NAME));
        }
        FileUtils.deleteDirectory(tempFolder);
    }

    private static String getIPAddress() throws IllegalStateException {
        try {
            return InetAddress.getByName(TestSuiteEnvironment.getServerAddress()).getHostAddress();
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
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

    /* The security domain being used in this test is configured with:
       1) a source-address-role-decoder that assigns the "Admin" role if the IP address of the client is TestSuiteEnvironment.getServerAddress()
       2) a permission-mapper that assigns the "LoginPermission" if the identity has the "Admin" role unless the principal
       is "charlie"
    */

    @Test
    public void testAuthenticationIPAddressAndPermissionMapperMatch() throws Exception {
        CoreUtils.makeCallWithBasicAuthn(createSimpleManagementOperationUrl(), USER_ALICE, PASSWORD_ALICE, SC_OK);
        CoreUtils.makeCallWithBasicAuthn(createSimpleManagementOperationUrl(), USER_BOB, PASSWORD_BOB, SC_OK);
    }

    @Test
    public void testAuthenticationIPAddressMatchAndPermissionMapperMismatch() throws Exception {
        CoreUtils.makeCallWithBasicAuthn(createSimpleManagementOperationUrl(), USER_CHARLIE, PASSWORD_CHARLIE, SC_UNAUTHORIZED);
    }

    private URL createSimpleManagementOperationUrl() throws IOException {
        return new URL("http://" + host + ":9990/management?operation=attribute&name=server-state");
    }

    private static String escapePath(String path) {
        // fix windows path escaping.
        return path.replace("\\", "\\\\");
    }
}
