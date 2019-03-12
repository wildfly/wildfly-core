/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package org.wildfly.core.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JvmTest {

    @Test
    public void testReleaseFile() throws Exception {
        testReleaseFile("", false);
        testReleaseFile("1.8.0", false);
        testReleaseFile("1.8.0_191", false);
        testReleaseFile("9", true);
        testReleaseFile("9.0", true);
        testReleaseFile("9.0.1", true);
        testReleaseFile("10", true);
        testReleaseFile("10.0", true);
        testReleaseFile("10.0.2", true);
        testReleaseFile("11", true);
        testReleaseFile("11.0.1", true);
    }

    private static void testReleaseFile(final String version, final boolean expectedValue) throws IOException {
        final Path javaHome = createFakeJavaHome(version);
        try {
            Assert.assertEquals(String.format("Expected version %s to %s a modular JVM", version, (expectedValue ? "be" : "not be")),
                    expectedValue, Jvm.of(javaHome).isModular());
        } finally {
            Files.walkFileTree(javaHome, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static Path createFakeJavaHome(final String version) throws IOException {
        final Path javaHome = Files.createTempDirectory("fake-java-home");
        Files.createFile(Files.createDirectory(javaHome.resolve("bin")).resolve(Environment.isWindows() ? "java.exe" : "java"));
        final Path releaseFile = javaHome.resolve("release");
        Files.write(releaseFile, Collections.singleton(String.format("JAVA_VERSION=\"%s\"%n", version)), StandardCharsets.UTF_8);
        return javaHome;
    }
}
