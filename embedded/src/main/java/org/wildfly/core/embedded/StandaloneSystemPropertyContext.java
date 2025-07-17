/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded;

import java.nio.file.Path;

/**
 * A system property context for an embedded server standalone environment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class StandaloneSystemPropertyContext extends SystemPropertyContext {
    private static final String SERVER_BASE_DIR = "jboss.server.base.dir";
    private static final String SERVER_CONFIG_DIR = "jboss.server.config.dir";
    private static final String SERVER_CONTENT_DIR = "jboss.server.content.dir";
    private static final String SERVER_DATA_DIR = "jboss.server.data.dir";
    private static final String SERVER_DEPLOY_DIR = "jboss.server.deploy.dir";
    private static final String SERVER_LOG_DIR = "jboss.server.log.dir";
    private static final String SERVER_TEMP_DIR = "jboss.server.temp.dir";

    /**
     * Creates a new system property context for a standalone environment.
     *
     * @param jbossHomeDir the JBoss home directory
     */
    StandaloneSystemPropertyContext(final Path jbossHomeDir) {
        super(jbossHomeDir);
    }

    @SuppressWarnings("deprecation")
    @Override
    void configureProperties() {
        final Path baseDir = resolveBaseDir(SERVER_BASE_DIR, "standalone");
        addPropertyIfAbsent(SERVER_BASE_DIR, baseDir);
        addPropertyIfAbsent(SERVER_CONFIG_DIR, resolvePath(baseDir, "configuration"));
        Path dataDir = resolveDir(SERVER_DATA_DIR, baseDir.resolve("data"));
        addPropertyIfAbsent(SERVER_DATA_DIR, dataDir);
        addPropertyIfAbsent(SERVER_CONTENT_DIR, resolvePath(dataDir, "content"));
        addPropertyIfAbsent(SERVER_DEPLOY_DIR, resolvePath(dataDir, "content"));
        addPropertyIfAbsent(SERVER_LOG_DIR, resolvePath(baseDir, "log"));
        addPropertyIfAbsent(SERVER_TEMP_DIR, resolvePath(baseDir, "tmp/embedded-server"));
    }
}
