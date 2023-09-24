/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
