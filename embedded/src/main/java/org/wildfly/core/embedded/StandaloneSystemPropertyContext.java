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
        addPropertyIfAbsent(SERVER_BASE_DIR, baseDir.toString());
        addPropertyIfAbsent(SERVER_CONFIG_DIR, resolvePath(baseDir, "configuration"));
        addPropertyIfAbsent(SERVER_DATA_DIR, resolvePath(baseDir, "data"));
        addPropertyIfAbsent(SERVER_CONTENT_DIR, resolvePath(baseDir, "data", "content"));
        addPropertyIfAbsent(SERVER_DEPLOY_DIR, resolvePath(baseDir, "data", "content"));
        addPropertyIfAbsent(SERVER_LOG_DIR, resolvePath(baseDir, "log"));
        addPropertyIfAbsent(SERVER_TEMP_DIR, resolvePath(baseDir, "tmp"));
    }
}
