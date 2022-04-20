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
 * A system property context for an embedded server domain environment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
final class HostControllerSystemPropertyContext extends SystemPropertyContext {
    private static final String DOMAIN_BASE_DIR = "jboss.domain.base.dir";
    private static final String DOMAIN_CONFIG_DIR = "jboss.domain.config.dir";
    private static final String DOMAIN_CONTENT_DIR = "jboss.domain.content.dir";
    private static final String DOMAIN_DATA_DIR = "jboss.domain.data.dir";
    private static final String DOMAIN_DEPLOYMENT_DIR = "jboss.domain.deployment.dir";
    private static final String DOMAIN_LOG_DIR = "jboss.domain.log.dir";
    private static final String DOMAIN_TEMP_DIR = "jboss.domain.temp.dir";
    private static final String JBOSS_DOMAIN_PRIMARY_ADDRESS = "jboss.domain.primary.address";
    private static final String DOMAIN_SERVERS_DIR = "jboss.domain.servers.dir";

    /**
     * Creates a new system property context for a domain environment.
     *
     * @param jbossHomeDir the JBoss home directory
     */
    HostControllerSystemPropertyContext(final Path jbossHomeDir) {
        super(jbossHomeDir);
    }

    @SuppressWarnings("deprecation")
    @Override
    void configureProperties() {
        final Path baseDir = resolveBaseDir(DOMAIN_BASE_DIR, "domain");
        addPropertyIfAbsent(DOMAIN_BASE_DIR, baseDir.toString());
        addPropertyIfAbsent(DOMAIN_CONFIG_DIR, resolvePath(baseDir, "configuration"));
        Path dataDir = resolveDir(DOMAIN_DATA_DIR, baseDir.resolve("data"));
        addPropertyIfAbsent(DOMAIN_DATA_DIR, dataDir);
        addPropertyIfAbsent(DOMAIN_CONTENT_DIR, resolvePath(dataDir, "content"));
        addPropertyIfAbsent(DOMAIN_DEPLOYMENT_DIR, resolvePath(dataDir, "content"));
        addPropertyIfAbsent(DOMAIN_LOG_DIR, resolvePath(baseDir, "log"));
        addPropertyIfAbsent(DOMAIN_TEMP_DIR, resolvePath(baseDir, "tmp"));
        checkProperty(JBOSS_DOMAIN_PRIMARY_ADDRESS);
        checkProperty(DOMAIN_SERVERS_DIR);
    }
}
