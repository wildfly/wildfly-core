/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
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

package org.wildfly.scripts.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.jboss.as.test.shared.TimeoutUtil;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Environment {

    static final Path JBOSS_HOME;
    static final Path LOG_DIR;
    static final int TIMEOUT;
    private static final boolean IS_WINDOWS;

    static {
        String jbossHome = System.getProperty("jboss.home");
        if (isNullOrEmpty(jbossHome)) {
            jbossHome = System.getenv("JBOSS_HOME");
        }

        if (isNullOrEmpty(jbossHome)) {
            throw new RuntimeException("Failed to configure environment. No jboss.home system property or JBOSS_HOME " +
                    "environment variable set.");
        }
        JBOSS_HOME = Paths.get(jbossHome).toAbsolutePath();
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        IS_WINDOWS = os.contains("win");

        LOG_DIR = Paths.get(System.getProperty("jboss.test.log.dir"));
        try {
            Files.createDirectories(LOG_DIR);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create the log directory", e);
        }
        final String timeoutString = System.getProperty("jboss.test.start.timeout", "20");
        TIMEOUT = TimeoutUtil.adjust(Integer.parseInt(timeoutString));
    }

    static boolean isWindows() {
        return IS_WINDOWS;
    }

    static Path getStandaloneConfig(final String configFile) {
        return JBOSS_HOME.resolve("standalone").resolve("configuration").resolve(configFile);
    }

    static Path getDomainConfig(final String configFile) {
        return JBOSS_HOME.resolve("domain").resolve("configuration").resolve(configFile);
    }

    private static boolean isNullOrEmpty(final String value) {
        return value == null || value.trim().isEmpty();
    }
}
