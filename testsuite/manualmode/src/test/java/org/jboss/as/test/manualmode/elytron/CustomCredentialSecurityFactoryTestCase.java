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
package org.jboss.as.test.manualmode.elytron;

import static org.apache.http.HttpStatus.SC_OK;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test for authentication through http-interface secured by Elytron http-authentication-factory where we test that
 * custom-credential-security-factory is called.
 *
 * @author olukas
 * @author Hynek Švábek <hsvabek@redhat.com>
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class CustomCredentialSecurityFactoryTestCase {

    private static final String PREDEFINED_HTTP_SERVER_MECHANISM_FACTORY = "global";
    private static final String MANAGEMENT_FILESYSTEM_NAME = "mgmt-filesystem-name";
    private static final String CUSTOM_CREDENTIAL_SECURITY_FACTORY_MODULE_NAME = "org.jboss.customcredentialsecurityfactoryimpl";
    private static final String CUSTOM_CRED_SEC_FACTORY_NAME = "customCredSecFactory";

    private static Path tempFolder;

    private static final String USER = "user";
    private static final String CORRECT_PASSWORD = "password";

    private static String existingHttpManagementFactory;

    @Inject
    private static ServerController CONTROLLER;

    public static void prepareServerConfiguration() throws Exception {
        tempFolder = Files.createTempDirectory("ely-" + CustomCredentialSecurityFactoryTestCase.class.getSimpleName());

        Path fsRealmPath = tempFolder.resolve("fs-realm-users");

        try (CLIWrapper cli = new CLIWrapper(true)) {
            final String levelStr = "";

            Path moduleJar = createJar("testJar", CustomCredentialSecurityFactoryImpl.class);
            try {
                cli.sendLine("module add --name=" + CUSTOM_CREDENTIAL_SECURITY_FACTORY_MODULE_NAME
                    + " --slot=main --dependencies=org.wildfly.security.elytron,org.wildfly.extension.elytron --resources="
                    + moduleJar.toAbsolutePath());
            } finally {
                Files.deleteIfExists(moduleJar);
            }

            cli.sendLine("/core-service=management/management-interface=http-interface:read-attribute(name=http-authentication-factory)");
            ModelNode res = cli.readAllAsOpResult().getResponseNode().get("result");
            if (res.isDefined()) {
                existingHttpManagementFactory = res.asString();
            }

            cli.sendLine(String.format(
                "/subsystem=elytron/custom-credential-security-factory=%s:add(class-name=%s, module=%s)",
                CUSTOM_CRED_SEC_FACTORY_NAME, CustomCredentialSecurityFactoryImpl.class.getName(),
                CUSTOM_CREDENTIAL_SECURITY_FACTORY_MODULE_NAME));

            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:add(path=\"%s\", %s)", MANAGEMENT_FILESYSTEM_NAME, escapePath(fsRealmPath.toAbsolutePath().toString()), levelStr));
            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:add-identity(identity=%s)", MANAGEMENT_FILESYSTEM_NAME, USER));
            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:set-password(identity=%s, clear={password=\"%s\"})",
                    MANAGEMENT_FILESYSTEM_NAME, USER, CORRECT_PASSWORD));

            cli.sendLine(String.format(
                    "/subsystem=elytron/security-domain=%1$s:add(realms=[{realm=%1$s,role-decoder=groups-to-roles},{realm=local,role-mapper=super-user-mapper}],default-realm=%1$s,permission-mapper=default-permission-mapper)",
                    MANAGEMENT_FILESYSTEM_NAME));
            cli.sendLine(String.format(
                    "/subsystem=elytron/http-authentication-factory=%1$s:add(http-server-mechanism-factory=%2$s,security-domain=%1$s,"
                    + "mechanism-configurations=[{mechanism-name=BASIC,mechanism-realm-configurations=[{realm-name=\"%1$s\"}]}, credential-security-factory=%3$s])",
                MANAGEMENT_FILESYSTEM_NAME, PREDEFINED_HTTP_SERVER_MECHANISM_FACTORY, CUSTOM_CRED_SEC_FACTORY_NAME));
            cli.sendLine(String.format(
                    "/core-service=management/management-interface=http-interface:write-attribute(name=http-authentication-factory,value=%s)",
                    MANAGEMENT_FILESYSTEM_NAME));
            ServerReload.executeReloadAndWaitForCompletion(CONTROLLER.getClient().getControllerClient());
        }
    }

    public static void resetServerConfiguration() throws Exception {
        try (CLIWrapper cli = new CLIWrapper(true)) {
            String restoreMgmtAuth = existingHttpManagementFactory == null
                    ? "/core-service=management/management-interface=http-interface:undefine-attribute(name=http-authentication-factory)"
                    : String.format(
                        "/core-service=management/management-interface=http-interface:write-attribute(name=http-authentication-factory,value=%s)",
                        existingHttpManagementFactory);
            cli.sendLine(restoreMgmtAuth);
            cli.sendLine(String.format(
                    "/subsystem=elytron/http-authentication-factory=%s:remove()",
                    MANAGEMENT_FILESYSTEM_NAME, PREDEFINED_HTTP_SERVER_MECHANISM_FACTORY), true);
            cli.sendLine(String.format("/subsystem=elytron/security-domain=%s:remove()", MANAGEMENT_FILESYSTEM_NAME), true);
            cli.sendLine(String.format("/subsystem=elytron/filesystem-realm=%s:remove()", MANAGEMENT_FILESYSTEM_NAME), true);
            cli.sendLine(String.format("/subsystem=elytron/custom-credential-security-factory=%s:remove()",
                CUSTOM_CRED_SEC_FACTORY_NAME), true);
            cli.sendLine("module remove --name=" + CUSTOM_CREDENTIAL_SECURITY_FACTORY_MODULE_NAME, true);
        } finally {
            FileUtils.deleteDirectory(tempFolder.toFile());
        }
    }

    @BeforeClass
    public static void setupServer() throws Exception {
        CONTROLLER.start();
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
        try (CLIWrapper cli = new CLIWrapper(true)) {
            StringBuilder configuration = new StringBuilder("throwException").append("=").append(false);

            cli.sendLine(String.format(
                    "/subsystem=elytron/custom-credential-security-factory=%s:write-attribute(name=configuration, value={%s})",
                    CUSTOM_CRED_SEC_FACTORY_NAME, configuration));
            ServerReload.executeReloadAndWaitForCompletion(CONTROLLER.getClient().getControllerClient());
        }
        CoreUtils.makeCallWithBasicAuthn(createSimpleManagementOperationUrl(), USER, CORRECT_PASSWORD, SC_OK);
    }

    /**
     * Test where is thrown exception in custom-credential-security-factory.
     */
    @Test
    public void testCorrectUserCustomCredentialSecurityFactoryException() throws Exception {
        try (CLIWrapper cli = new CLIWrapper(true)) {
            StringBuilder configuration = new StringBuilder("throwException").append("=").append(true);

            cli.sendLine(String.format(
                    "/subsystem=elytron/custom-credential-security-factory=%s:write-attribute(name=configuration, value={%s})",
                    CUSTOM_CRED_SEC_FACTORY_NAME, configuration));
            ServerReload.executeReloadAndWaitForCompletion(CONTROLLER.getClient().getControllerClient());
        }
        CoreUtils.makeCallWithBasicAuthn(createSimpleManagementOperationUrl(), USER, CORRECT_PASSWORD,
            HttpStatus.SC_INTERNAL_SERVER_ERROR);
    }

    private URL createSimpleManagementOperationUrl() throws URISyntaxException, IOException {
        return new URL("http://" + TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort()
            + "/management?operation=attribute&name=server-state");
    }

    private static String escapePath(String path) {
        // fix windows path escaping.
        return path.replace("\\", "\\\\");
    }

    public static Path createJar(String namePrefix, Class<?>... classes) throws IOException {
        Path testJar = Files.createTempFile(namePrefix, ".jar");
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class).addClasses(classes);
        jar.as(ZipExporter.class).exportTo(testJar.toFile(), true);
        return testJar;
    }
}
