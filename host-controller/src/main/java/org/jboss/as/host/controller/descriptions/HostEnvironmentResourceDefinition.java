/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.descriptions;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_ENVIRONMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

import java.io.File;
import java.net.InetAddress;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.persistence.ConfigurationFile;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * A resource description that describes the host environment.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author Kabir Khan
 */
public class HostEnvironmentResourceDefinition extends SimpleResourceDefinition {
    private static final PathElement RESOURCE_PATH = PathElement.pathElement(CORE_SERVICE, HOST_ENVIRONMENT);

    private static final AttributeDefinition PROCESS_CONTROLLER_ADDRESS =
            createAttributeDefinition("process-controller-address", ModelType.STRING, SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG);
    private static final AttributeDefinition PROCESS_CONTROLLER_PORT =
            createAttributeDefinition("process-controller-port", ModelType.INT, SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG);
    private static final AttributeDefinition HOST_CONTROLLER_ADDRESS =
            createAttributeDefinition("host-controller-address", ModelType.STRING, SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG);
    private static final AttributeDefinition HOST_CONTROLLER_PORT =
            createAttributeDefinition("host-controller-port", ModelType.INT, SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG);
    private static final AttributeDefinition HOME_DIR = createAttributeDefinition("home-dir");
    private static final AttributeDefinition MODULES_DIR = createAttributeDefinition("modules-dir");
    private static final AttributeDefinition DOMAIN_BASE_DIR = createAttributeDefinition("domain-base-dir");
    private static final AttributeDefinition DOMAIN_CONFIG_DIR = createAttributeDefinition("domain-config-dir");
    private static final AttributeDefinition HOST_CONFIG_FILE = createAttributeDefinition("host-config-file");
    private static final AttributeDefinition DOMAIN_CONFIG_FILE = createAttributeDefinition("domain-config-file");
    private static final AttributeDefinition DOMAIN_CONTENT_DIR = createAttributeDefinition("domain-content-dir");
    private static final AttributeDefinition DOMAIN_DATA_DIR = createAttributeDefinition("domain-data-dir");
    private static final AttributeDefinition DOMAIN_LOG_DIR = createAttributeDefinition("domain-log-dir");
    private static final AttributeDefinition DOMAIN_SERVERS_DIR = createAttributeDefinition("domain-servers-dir");
    private static final AttributeDefinition DOMAIN_TEMP_DIR = createAttributeDefinition("domain-temp-dir");
    private static final AttributeDefinition DEFAULT_JVM = createAttributeDefinition("default-jvm");
    private static final AttributeDefinition IS_RESTART = createAttributeDefinition("is-restart", ModelType.BOOLEAN);
    private static final AttributeDefinition BACKUP_DOMAIN_FILES = createAttributeDefinition("backup-domain-files", ModelType.BOOLEAN);
    private static final AttributeDefinition USE_CACHED_DC = createAttributeDefinition("use-cached-dc", ModelType.BOOLEAN);
    private static final AttributeDefinition INITIAL_RUNNING_MODE = createAttributeDefinition("initial-running-mode");
    private static final AttributeDefinition QUALIFIED_HOST_NAME = createAttributeDefinition("qualified-host-name");
    private static final AttributeDefinition HOST_NAME = createAttributeDefinition("host-name");
    private static final AttributeDefinition STABILITY = createAttributeDefinition("stability");
    private static final AttributeDefinition PERMISSIBLE_STABILITY_LEVELS = new SimpleListAttributeDefinition.Builder("permissible-stability-levels", STABILITY)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    private static final AttributeDefinition[] HOST_ENV_ATTRIBUTES = {
        PROCESS_CONTROLLER_ADDRESS,
        PROCESS_CONTROLLER_PORT,
        HOST_CONTROLLER_ADDRESS,
        HOST_CONTROLLER_PORT,
        HOME_DIR,
        MODULES_DIR,
        DOMAIN_BASE_DIR,
        DOMAIN_CONFIG_DIR,
        HOST_CONFIG_FILE,
        DOMAIN_CONFIG_FILE,
        DOMAIN_CONTENT_DIR,
        DOMAIN_DATA_DIR,
        DOMAIN_LOG_DIR,
        DOMAIN_SERVERS_DIR,
        DOMAIN_TEMP_DIR,
        DEFAULT_JVM,
        IS_RESTART,
        BACKUP_DOMAIN_FILES,
        USE_CACHED_DC,
        INITIAL_RUNNING_MODE,
        QUALIFIED_HOST_NAME,
        HOST_NAME,
        STABILITY,
        PERMISSIBLE_STABILITY_LEVELS
    };

    private final HostEnvironmentReadHandler osh;

    /**
     * Creates a new description provider to describe the server environment.
     *
     * @param environment the environment the resource is based on.
     */

    private HostEnvironmentResourceDefinition(final HostControllerEnvironment environment) {
        super(new Parameters(RESOURCE_PATH, HostResolver.getResolver("host.env")).setRuntime());
        osh = new HostEnvironmentReadHandler(environment);
    }

    /**
     * A factory method for creating a new server environment resource description.
     *
     * @param environment the environment the resource is based on.
     *
     * @return a new server environment resource description.
     */
    public static HostEnvironmentResourceDefinition of(final HostControllerEnvironment environment) {
        return new HostEnvironmentResourceDefinition(environment);
    }

    private static AttributeDefinition createAttributeDefinition(String name) {
        return createAttributeDefinition(name, ModelType.STRING);
    }

    private static AttributeDefinition createAttributeDefinition(String name, ModelType type) {
        return SimpleAttributeDefinitionBuilder.create(name, type)
                .setStorageRuntime()
                .setRuntimeServiceNotRequired()
                .build();
    }

    private static AttributeDefinition createAttributeDefinition(String name, ModelType type, AccessConstraintDefinition... accessConstraints) {
        SimpleAttributeDefinitionBuilder builder = SimpleAttributeDefinitionBuilder.create(name, type)
                .setStorageRuntime()
                .setRuntimeServiceNotRequired();
        if (accessConstraints != null) {
            for (AccessConstraintDefinition acd : accessConstraints) {
                builder = builder.addAccessConstraint(acd);
            }
        }
        return builder.build();
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition attribute : HOST_ENV_ATTRIBUTES) {
            resourceRegistration.registerReadOnlyAttribute(attribute, osh);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        PathInfoHandler.registerOperation(resourceRegistration,
                PathInfoHandler.Builder.of(null)
                        .addAttribute(HOME_DIR, null)
                        .addAttribute(DOMAIN_BASE_DIR, null)
                        .addAttribute(DOMAIN_CONFIG_DIR, null)
                        .addAttribute(DOMAIN_CONTENT_DIR, null)
                        .addAttribute(DOMAIN_DATA_DIR, null)
                        .addAttribute(DOMAIN_LOG_DIR, null)
                        .addAttribute(DOMAIN_SERVERS_DIR, null)
                        .addAttribute(DOMAIN_TEMP_DIR, null)
                        .build());
    }



    private static class HostEnvironmentReadHandler implements OperationStepHandler {
        private final HostControllerEnvironment environment;

        HostEnvironmentReadHandler(final HostControllerEnvironment environment) {
            this.environment = environment;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            final ModelNode result = context.getResult();
            final String name = operation.require(NAME).asString();
            if (equals(name, PROCESS_CONTROLLER_ADDRESS)) {
                set(result, environment.getProcessControllerAddress());
            } else if (equals(name, PROCESS_CONTROLLER_PORT)) {
                set(result, environment.getProcessControllerPort());
            } else if (equals(name, HOST_CONTROLLER_ADDRESS)) {
                set(result, environment.getHostControllerAddress());
            } else if (equals(name, HOST_CONTROLLER_PORT)) {
                set(result, environment.getHostControllerPort());
            } else if (equals(name, HOME_DIR)) {
                set(result, environment.getHomeDir());
            } else if (equals(name, MODULES_DIR)) {
                set (result, environment.getModulesDir());
            } else if (equals(name, DOMAIN_BASE_DIR)) {
                set(result, environment.getDomainBaseDir());
            } else if (equals(name, DOMAIN_CONFIG_DIR)) {
                set(result, environment.getDomainConfigurationDir());
            } else if (equals(name, HOST_CONFIG_FILE)) {
                set(result, environment.getHostConfigurationFile());
            } else if (equals(name, DOMAIN_CONFIG_FILE)) {
                set(result, environment.getDomainConfigurationFile());
            } else if (equals(name, DOMAIN_CONTENT_DIR)) {
                set(result, environment.getDomainContentDir());
            } else if (equals(name, DOMAIN_DATA_DIR)) {
                set(result, environment.getDomainDataDir());
            } else if (equals(name, DOMAIN_LOG_DIR)) {
                set(result, environment.getDomainLogDir());
            } else if (equals(name, DOMAIN_SERVERS_DIR)) {
                set(result, environment.getDomainServersDir());
            } else if (equals(name, DOMAIN_TEMP_DIR)) {
                set(result, environment.getDomainTempDir());
            } else if (equals(name, DEFAULT_JVM)) {
                set(result, environment.getDefaultJVM());
            } else if (equals(name, IS_RESTART)) {
                set(result, environment.isRestart());
            } else if (equals(name, BACKUP_DOMAIN_FILES)) {
                set(result, environment.isBackupDomainFiles());
            } else if (equals(name, USE_CACHED_DC)) {
                set(result, environment.isUseCachedDc());
            } else if (equals(name, INITIAL_RUNNING_MODE)) {
                set(result, environment.getInitialRunningMode().name());
            } else if (equals(name, QUALIFIED_HOST_NAME)) {
                set(result, environment.getQualifiedHostName());
            } else if (equals(name, HOST_NAME)) {
                set(result, environment.getHostName());
            } else if (equals(name, STABILITY)) {
                result.set(environment.getStability().toString());
            } else if (equals(name, PERMISSIBLE_STABILITY_LEVELS)) {
                for (Stability s : environment.getStabilities()) {
                    result.add(s.toString());
                }
            }
        }

        private void set(final ModelNode node, final int value) {
            node.set(value);
        }

        private void set(final ModelNode node, final boolean value) {
            node.set(value);
        }


        private void set(final ModelNode node, final String value) {
            if (value != null) {
                node.set(value);
            }
        }

        private void set(final ModelNode node, final InetAddress value) {
            if (value != null) {
                node.set(value.toString());
            }
        }

        private void set(final ModelNode node, final File value) {
            if (value != null) {
                node.set(value.getAbsolutePath());
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
