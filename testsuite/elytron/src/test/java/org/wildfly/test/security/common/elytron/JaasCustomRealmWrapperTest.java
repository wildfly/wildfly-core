/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.test.security.common.elytron;

import org.jboss.as.cli.Util;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import java.io.File;
import java.nio.file.Files;

/**
 * Tests testing JaasSecurityRealm via custom realm resource
 */
@RunWith(WildflyTestRunner.class)
public class JaasCustomRealmWrapperTest {

    @ClassRule
    public static TemporaryFolder tmpDir = new TemporaryFolder();

    @BeforeClass
    public static void setup() throws Exception {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, "testJaasCustomRealm.jar")
                .addAsResource(new StringAsset("Dependencies: org.wildfly.security"), "META-INF/MANIFEST.MF")
                .addClasses(TestLoginModule.class, TestCallbackHandler.class);
        File jarFile = new File(tmpDir.getRoot(), "testJaasCustomRealm.jar");
        jar.as(ZipExporter.class).exportTo(jarFile, true);
        CLIWrapper cli = new CLIWrapper(true);
        try {
            if (!isTsBootable()) {
                cli.sendLine("module add --name=jaasLoginModule --resources=" + jarFile.getAbsolutePath() +
                        " --dependencies=org.wildfly.security.elytron,org.wildfly.extension.elytron");
            } else {
                cli.sendLine("module add --module-root-dir=" + getBootableJarModulesPath() + " --name=jaasLoginModule --resources=" + jarFile.getAbsolutePath() +
                        " --dependencies=org.wildfly.security.elytron,org.wildfly.extension.elytron");
            }
        } finally {
            Files.deleteIfExists(new File(tmpDir.getRoot(), "testJaasCustomRealm.jar").toPath());
        }
    }

    @Test
    public void testAddJaasRealmAsCustomRealm() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        cli.sendLine("/subsystem=elytron/custom-realm=customJaasWrapperRealm:add(module=org.wildfly.extension.elytron.jaas-realm, class-name=org.wildfly.extension.elytron.JaasCustomSecurityRealmWrapper," +
                "configuration={entry=Entry1,module=jaasLoginModule, callback-handler=org.wildfly.test.integration.elytron.realm.TestCallbackHandler, path=" +
                JaasCustomRealmWrapperTest.class.getResource("jaas-login.config").getFile() + "})");
        Assert.assertTrue(cli.readAllAsOpResult().isIsOutcomeSuccess());
        cli.sendLine("/subsystem=elytron/security-domain=jaasTestDomain:add(realms=[{realm=customJaasWrapperRealm}], default-realm=customJaasWrapperRealm, permission-mapper=default-permission-mapper)");
        Assert.assertTrue(cli.readAllAsOpResult().isIsOutcomeSuccess());
        cli.sendLine("/subsystem=elytron/security-domain=jaasTestDomain:remove");
        cli.sendLine("/subsystem=elytron/custom-realm=customJaasWrapperRealm:remove");
    }

    @Test
    public void testJaasRealmHasToContainEntry() {
        CLIWrapper cli = null;
        try {
            cli = new CLIWrapper(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        try {
            cli.sendLine("/subsystem=elytron/custom-realm=customJaasWrapperRealm:add(module=org.wildfly.extension.elytron.jaas-realm," +
                    "class-name=org.wildfly.extension.elytron.JaasCustomSecurityRealmWrapper," +
                    "configuration={module=jaasLoginModule,callback-handler=org.wildfly.test.integration.elytron.realm.TestCallbackHandler, path=" +
                    JaasCustomRealmWrapperTest.class.getResource("jaas-login.config").getFile() + "})");
            Assert.fail();
        } catch (AssertionError e) {
            if (!e.getMessage().contains(" Entry is not defined.")) {
                Assert.fail();
            }
        }
    }

    @AfterClass
    public static void cleanUp() throws Exception {
        CLIWrapper cli = new CLIWrapper(true);
        try {
            if (!isTsBootable()) {
                cli.sendLine("module remove --name=jaasLoginModule");
            } else {
                cli.sendLine("module remove --module-root-dir=" + getBootableJarModulesPath() + " --name=jaasLoginModule");
            }
        } catch (AssertionError e) {
            // ignore failure on Windows, cannot remove module on running server due to file locks
            if (!Util.isWindows())
                throw e;
        }
        cli.sendLine("reload");
    }

    private static boolean isTsBootable() {
        String bootableJar = System.getProperty("wildfly.bootable.jar");
        return bootableJar != null && bootableJar.equals("true");
    }

    private static String getBootableJarModulesPath() {
        return TestSuiteEnvironment.getSystemProperty("wildfly.bootable.jar.install.dir") + "/modules";
    }
}