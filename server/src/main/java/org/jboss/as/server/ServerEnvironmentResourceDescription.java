/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.server.controller.descriptions.ServerDescriptionConstants.SERVER_ENVIRONMENT;

import java.io.File;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.persistence.ConfigurationFile;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * A resource description that describes the server environment.
 * <p/>
 * Date: 17.11.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ServerEnvironmentResourceDescription extends SimpleResourceDefinition {
    public static final PathElement RESOURCE_PATH = PathElement.pathElement(ModelDescriptionConstants.CORE_SERVICE, SERVER_ENVIRONMENT);

    static final AttributeDefinition BASE_DIR = SimpleAttributeDefinitionBuilder.create("base-dir", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    static final AttributeDefinition CONFIG_DIR = SimpleAttributeDefinitionBuilder.create("config-dir", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    static final AttributeDefinition CONFIG_FILE = SimpleAttributeDefinitionBuilder.create("config-file", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    static final AttributeDefinition CONTENT_DIR = SimpleAttributeDefinitionBuilder.create("content-dir", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    static final AttributeDefinition DATA_DIR = SimpleAttributeDefinitionBuilder.create("data-dir", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    static final AttributeDefinition DEPLOY_DIR = SimpleAttributeDefinitionBuilder.create("deploy-dir", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    static final AttributeDefinition EXT_DIRS = SimpleAttributeDefinitionBuilder.create("ext-dirs", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    public static final AttributeDefinition HOME_DIR = SimpleAttributeDefinitionBuilder.create("home-dir", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    public static final AttributeDefinition HOST_NAME = SimpleAttributeDefinitionBuilder.create("host-name", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    static final AttributeDefinition INITIAL_RUNNING_MODE = SimpleAttributeDefinitionBuilder.create("initial-running-mode", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    public static final AttributeDefinition LAUNCH_TYPE = SimpleAttributeDefinitionBuilder.create("launch-type", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    static final AttributeDefinition LOG_DIR = SimpleAttributeDefinitionBuilder.create("log-dir", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    static final AttributeDefinition NODE_NAME = SimpleAttributeDefinitionBuilder.create("node-name", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    static final AttributeDefinition QUALIFIED_HOST_NAME = SimpleAttributeDefinitionBuilder.create("qualified-host-name", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    public static final AttributeDefinition SERVER_NAME = SimpleAttributeDefinitionBuilder.create("server-name", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    static final AttributeDefinition TEMP_DIR = SimpleAttributeDefinitionBuilder.create("temp-dir", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    public static final AttributeDefinition START_SUSPENDED = SimpleAttributeDefinitionBuilder.create("start-suspended", ModelType.BOOLEAN).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    public static final AttributeDefinition GRACEFUL_STARTUP = SimpleAttributeDefinitionBuilder.create("start-gracefully", ModelType.BOOLEAN).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();
    static final AttributeDefinition QUALITY = SimpleAttributeDefinitionBuilder.create("quality", ModelType.STRING).setFlags(AttributeAccess.Flag.STORAGE_RUNTIME).build();

    private static final AttributeDefinition[] SERVER_ENV_ATTRIBUTES = { BASE_DIR, CONFIG_DIR, CONFIG_FILE, CONTENT_DIR, DATA_DIR,
            DEPLOY_DIR, EXT_DIRS, HOME_DIR, HOST_NAME, INITIAL_RUNNING_MODE, LAUNCH_TYPE, LOG_DIR, NODE_NAME,
            QUALIFIED_HOST_NAME, SERVER_NAME, TEMP_DIR, START_SUSPENDED, GRACEFUL_STARTUP, QUALITY };

    private final ServerEnvironmentReadHandler osh;

    /**
     * Creates a new description provider to describe the server environment.
     *
     * @param environment the environment the resource is based on.
     */
    private ServerEnvironmentResourceDescription(final ServerEnvironment environment) {
        super(new Parameters(RESOURCE_PATH, ServerDescriptions.getResourceDescriptionResolver("server.env"))
                .setRuntime());
        osh = new ServerEnvironmentReadHandler(environment);
    }

    /**
     * A factory method for creating a new server environment resource description.
     *
     * @param environment the environment the resource is based on.
     *
     * @return a new server environment resource description.
     */
    public static ServerEnvironmentResourceDescription of(final ServerEnvironment environment) {
        return new ServerEnvironmentResourceDescription(environment);
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition attribute : SERVER_ENV_ATTRIBUTES) {
            resourceRegistration.registerReadOnlyAttribute(attribute, osh);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        PathInfoHandler.registerOperation(resourceRegistration,
                PathInfoHandler.Builder.of(null)
                        .addAttribute(CONTENT_DIR, null)
                        .addAttribute(DATA_DIR, null)
                        .addAttribute(TEMP_DIR, null)
                        .addAttribute(LOG_DIR, null)
                        .build());
    }


    /**
     * Date: 17.11.2011
     *
     * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
     */
    private static class ServerEnvironmentReadHandler implements OperationStepHandler {
        private final ServerEnvironment environment;

        ServerEnvironmentReadHandler(final ServerEnvironment environment) {
            this.environment = environment;
        }

        @Override
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            final ModelNode result = context.getResult();
            final String name = operation.require(NAME).asString();
            if (equals(name, BASE_DIR)) {
                set(result, environment.getServerBaseDir());
            }
            if (equals(name, CONFIG_DIR)) {
                set(result, environment.getServerConfigurationDir());
            }
            if (equals(name, CONFIG_FILE)) {
                set(result, environment.getServerConfigurationFile());
            }
            if (equals(name, DATA_DIR)) {
                set(result, environment.getServerDataDir());
            }
            if (equals(name, CONTENT_DIR)) {
                set(result, environment.getServerContentDir());
            }
            if (equals(name, DEPLOY_DIR)) {
                set(result, environment.getServerContentDir());
            }
            if (equals(name, EXT_DIRS)) {
                set(result, environment.getJavaExtDirs());
            }
            if (equals(name, HOME_DIR)) {
                set(result, environment.getHomeDir());
            }
            if (equals(name, HOST_NAME)) {
                set(result, environment.getHostName());
            }
            if (equals(name, LAUNCH_TYPE)) {
                set(result, environment.getLaunchType().name());
            }
            if (equals(name, INITIAL_RUNNING_MODE)) {
                set(result, environment.getInitialRunningMode().name());
            }
            if (equals(name, LOG_DIR)) {
                set(result, environment.getServerLogDir());
            }
            if (equals(name, NODE_NAME)) {
                set(result, environment.getNodeName());
            }
            if (equals(name, QUALIFIED_HOST_NAME)) {
                set(result, environment.getQualifiedHostName());
            }
            if (equals(name, SERVER_NAME)) {
                set(result, environment.getServerName());
            }
            if (equals(name, TEMP_DIR)) {
                set(result, environment.getServerTempDir());
            }
            if (equals(name, START_SUSPENDED)) {
                result.set(environment.isStartSuspended());
            }
            if (equals(name, GRACEFUL_STARTUP)) {
                result.set(environment.isStartGracefully());
            } else if (equals(name, QUALITY)) {
                result.set(environment.getQuality().toString());
            }
        }

        private void set(final ModelNode node, final String value) {
            if (value != null) {
                node.set(value);
            }
        }

        private void set(final ModelNode node, final File value) {
            if (value != null) {
                node.set(value.getAbsolutePath());
            }
        }

        private void set(final ModelNode node, final File[] value) {
            if (value != null) {
                for (File file : value) {
                    node.add(file.getAbsolutePath());
                }
            }
        }

        private void set(final ModelNode node, final ConfigurationFile value) {
            if (value != null) {
                set(node, value.getBootFile());
            }
        }

        private boolean equals(final String name, final AttributeDefinition attribute) {
            return name.equals(attribute.getName());
        }
    }
}
