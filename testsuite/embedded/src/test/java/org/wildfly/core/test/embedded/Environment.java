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

package org.wildfly.core.test.embedded;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.test.shared.TimeoutUtil;
import org.wildfly.core.embedded.Configuration;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Environment {

    public static final Path JBOSS_HOME;
    public static final Path MODULE_PATH;
    public static final Path LOG_DIR;
    public static final int TIMEOUT;

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

        String modulePath = System.getProperty("module.path");
        if (isNullOrEmpty(modulePath)) {
            MODULE_PATH = JBOSS_HOME.resolve("modules");
        } else {
            MODULE_PATH = Paths.get(modulePath).toAbsolutePath();
        }

        LOG_DIR = Paths.get(System.getProperty("jboss.test.log.dir"));
        final String timeoutString = System.getProperty("jboss.test.start.timeout", "20");
        TIMEOUT = TimeoutUtil.adjust(Integer.parseInt(timeoutString));
    }

    static Configuration.Builder createConfigBuilder() {
        return Configuration.Builder.of(JBOSS_HOME)
                .setModulePath(MODULE_PATH.toString());
    }

    private static boolean isNullOrEmpty(final String value) {
        return value == null || value.trim().isEmpty();
    }
}
