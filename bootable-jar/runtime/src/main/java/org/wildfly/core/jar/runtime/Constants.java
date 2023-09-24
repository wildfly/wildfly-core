/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.jar.runtime;

/**
 *
 * @author jdenise
 */
interface Constants {

    static final String DEPLOYMENT_ARG = "--deployment";
    static final String INSTALL_DIR_ARG = "--install-dir";
    static final String DISPLAY_GALLEON_CONFIG_ARG = "--display-galleon-config";
    static final String CLI_SCRIPT_ARG = "--cli-script";

    static final String DEPLOYMENTS = "deployments";
    static final String STANDALONE_CONFIG = "standalone.xml";

    static final String LOG_MANAGER_PROP = "java.util.logging.manager";
    static final String LOG_MANAGER_CLASS = "org.jboss.logmanager.LogManager";
    static final String LOG_BOOT_FILE_PROP = "org.jboss.boot.log.file";
    static final String LOGGING_PROPERTIES = "logging.properties";

    static final String SERVER_LOG = "server.log";
    static final String SERVER_STATE = "server-state";
    static final String STOPPED = "stopped";

    static final String SHA1 = "sha1";

    String DEBUG_PROPERTY = "org.wildfly.core.jar.debug";
}
