/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import mockit.Mock;
import mockit.MockUp;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.ControllerInitializer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

class TestEnvironment extends AdditionalInitialization {

    static final int LDAPS1_PORT = 11391;
    static final int LDAPS2_PORT = 11392;

    @Override
    protected ControllerInitializer createControllerInitializer() {
        ControllerInitializer initializer = new ControllerInitializer();

        try {
            URL fsr = getClass().getResource("filesystem-realm-empty");
            if (fsr != null) emptyDirectory(new File(fsr.getFile()).toPath());
        } catch (Exception e) {
            throw new RuntimeException("Could ensure empty testing filesystem directory", e);
        }

        try {
            initializer.addPath("jboss.server.config.dir", getClass().getResource(".").getFile(), null);
            initializer.addPath("jboss.server.data.dir", "target", null);
        } catch (Exception e) {
            throw new RuntimeException("Could not create test config directory", e);
        }

        return initializer;
    }

    public static void startLdapService() {
        try {
            LdapService.builder()
                    .setWorkingDir(new File("./target/apache-ds/working1"))
                    .createDirectoryService("Test Service")
                    .addPartition("Elytron", "dc=elytron,dc=wildfly,dc=org", 5, "uid")
                    .importLdif(TestEnvironment.class.getResourceAsStream("ldap-schemas.ldif"))
                    .importLdif(TestEnvironment.class.getResourceAsStream("ldap-data.ldif"))
                    .addTcpServer("Default TCP", "localhost", LDAPS1_PORT, "localhost.keystore", "Elytron")
                    .start();
            LdapService.builder()
                    .setWorkingDir(new File("./target/apache-ds/working2"))
                    .createDirectoryService("Test Service")
                    .addPartition("Elytron", "dc=elytron,dc=wildfly,dc=org", 5, "uid")
                    .importLdif(TestEnvironment.class.getResourceAsStream("ldap-schemas.ldif"))
                    .importLdif(TestEnvironment.class.getResourceAsStream("ldap-referred.ldif"))
                    .addTcpServer("Default TCP", "localhost", LDAPS2_PORT, "localhost.keystore", "Elytron")
                    .start();
        } catch (Exception e) {
            throw new RuntimeException("Could not start LDAP embedded server.", e);
        }
    }

    private void emptyDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // classloader obtaining mock to load classes from testsuite
    private static class ClassLoadingAttributeDefinitionsMock extends MockUp<ClassLoadingAttributeDefinitions> {
        @Mock
        static ClassLoader resolveClassLoader(String module) {
            return SaslTestCase.class.getClassLoader();
        }
    }

    static void mockCallerModuleClassloader() {
        new ClassLoadingAttributeDefinitionsMock();
    }

    // base add handler mock to prevent services starting for parsing testcase
    private static class BaseAddHandlerMock extends MockUp<BaseAddHandler> {
        @Mock
        protected boolean requiresRuntime(OperationContext context) {
            return false;
        }
    }

    static void forceRequireRuntimeFalse() throws Exception {
        new BaseAddHandlerMock();

        Class<?> innerClass = Class.forName(SecurityPropertyResourceDefinition.class.getName() + "$PropertyRemoveHandler");
        new MockUp<Object>(innerClass) {
            @Mock
            protected boolean requiresRuntime(OperationContext context) {
                return false;
            }
        };
    }
}