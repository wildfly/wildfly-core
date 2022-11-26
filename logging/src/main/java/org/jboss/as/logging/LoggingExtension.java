/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.logging;


import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition.Parameters;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.access.constraint.ApplicationTypeConfig;
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathInfoHandler;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.controller.services.path.ResolvePathHandler;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.as.logging.LoggingProfileOperations.LoggingProfileAdd;
import org.jboss.as.logging.deployments.resources.LoggingDeploymentResources;
import org.jboss.as.logging.filters.FilterResourceDefinition;
import org.jboss.as.logging.formatters.CustomFormatterResourceDefinition;
import org.jboss.as.logging.formatters.JsonFormatterResourceDefinition;
import org.jboss.as.logging.formatters.PatternFormatterResourceDefinition;
import org.jboss.as.logging.formatters.XmlFormatterResourceDefinition;
import org.jboss.as.logging.handlers.AbstractHandlerDefinition;
import org.jboss.as.logging.handlers.AsyncHandlerResourceDefinition;
import org.jboss.as.logging.handlers.ConsoleHandlerResourceDefinition;
import org.jboss.as.logging.handlers.CustomHandlerResourceDefinition;
import org.jboss.as.logging.handlers.FileHandlerResourceDefinition;
import org.jboss.as.logging.handlers.PeriodicHandlerResourceDefinition;
import org.jboss.as.logging.handlers.PeriodicSizeRotatingHandlerResourceDefinition;
import org.jboss.as.logging.handlers.SizeRotatingHandlerResourceDefinition;
import org.jboss.as.logging.handlers.SocketHandlerResourceDefinition;
import org.jboss.as.logging.handlers.SyslogHandlerResourceDefinition;
import org.jboss.as.logging.loggers.LoggerResourceDefinition;
import org.jboss.as.logging.loggers.RootLoggerResourceDefinition;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.logging.logmanager.WildFlyLogContextSelector;
import org.jboss.as.logging.stdio.LogContextStdioContextSelector;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.stdio.StdioContext;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingExtension implements Extension {

    public static final String SUBSYSTEM_NAME = "logging";
    private static final String RESOURCE_NAME = LoggingExtension.class.getPackage().getName() + ".LocalDescriptions";

    private static final String SKIP_LOG_MANAGER_PROPERTY = "org.wildfly.logging.skipLogManagerCheck";
    private static final String EMBEDDED_PROPERTY = "org.wildfly.logging.embedded";

    private static final PathElement LOGGING_PROFILE_PATH = PathElement.pathElement(CommonAttributes.LOGGING_PROFILE);

    private static final GenericSubsystemDescribeHandler DESCRIBE_HANDLER = GenericSubsystemDescribeHandler.create(LoggingChildResourceComparator.INSTANCE);

    private static final int MANAGEMENT_API_MAJOR_VERSION = 9;
    private static final int MANAGEMENT_API_MINOR_VERSION = 0;
    private static final int MANAGEMENT_API_MICRO_VERSION = 0;

    private static final ModelVersion CURRENT_VERSION = ModelVersion.create(MANAGEMENT_API_MAJOR_VERSION, MANAGEMENT_API_MINOR_VERSION, MANAGEMENT_API_MICRO_VERSION);

    private static final List<String> DELEGATE_DESC_OPTS = Arrays.asList(
            AbstractHandlerDefinition.UPDATE_OPERATION_NAME,
            RootLoggerResourceDefinition.ROOT_LOGGER_ADD_OPERATION_NAME
    );

    /**
     * Returns a resource description resolver that uses common descriptions for some attributes.
     *
     * @param keyPrefix the prefix to be appended to the {@link LoggingExtension#SUBSYSTEM_NAME}
     *
     * @return the resolver
     */
    public static ResourceDescriptionResolver getResourceDescriptionResolver(final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder(SUBSYSTEM_NAME);
        for (String kp : keyPrefix) {
            prefix.append('.').append(kp);
        }
        return new StandardResourceDescriptionResolver(prefix.toString(), RESOURCE_NAME, LoggingExtension.class.getClassLoader(), true, false) {
            @Override
            public String getOperationParameterDescription(final String operationName, final String paramName, final Locale locale, final ResourceBundle bundle) {
                if (DELEGATE_DESC_OPTS.contains(operationName)) {
                    return getResourceAttributeDescription(paramName, locale, bundle);
                }
                return super.getOperationParameterDescription(operationName, paramName, locale, bundle);
            }

            @Override
            public String getOperationParameterValueTypeDescription(final String operationName, final String paramName, final Locale locale,
                                                                    final ResourceBundle bundle, final String... suffixes) {
                if (DELEGATE_DESC_OPTS.contains(operationName)) {
                    return getResourceAttributeDescription(paramName, locale, bundle);
                }
                return super.getOperationParameterValueTypeDescription(operationName, paramName, locale, bundle, suffixes);
            }

            @Override
            public String getOperationParameterDeprecatedDescription(final String operationName, final String paramName, final Locale locale, final ResourceBundle bundle) {
                if (DELEGATE_DESC_OPTS.contains(operationName)) {
                    return getResourceAttributeDeprecatedDescription(paramName, locale, bundle);
                }
                return super.getOperationParameterDeprecatedDescription(operationName, paramName, locale, bundle);
            }
        };
    }


    @Override
    public void initialize(final ExtensionContext context) {
        final ProcessType processType = context.getProcessType();
        final boolean embedded = processType == ProcessType.EMBEDDED_HOST_CONTROLLER || processType == ProcessType.EMBEDDED_SERVER;
        final WildFlyLogContextSelector contextSelector;

        // For embedded servers we want to use a custom default LogContext
        if (embedded) {
            // Use the standard WildFlyLogContextSelector if we should wrap the current log context
            if (getBooleanProperty(EMBEDDED_PROPERTY, true)) {
                contextSelector = WildFlyLogContextSelector.Factory.createEmbedded();
            } else {
                contextSelector = WildFlyLogContextSelector.Factory.create();
            }
        } else {

            // The logging subsystem requires JBoss Log Manager to be used. Note this can be overridden and may fail late
            // instead of early. The reason to allow for the comparison to be overridden is some environments may wrap
            // the log manager. As long as the delegate log manager is JBoss Log Manager we should be okay.
            if (getBooleanProperty(SKIP_LOG_MANAGER_PROPERTY, false)) {
                LoggingLogger.ROOT_LOGGER.debugf("System property %s was set to true. Skipping the log manager check.", SKIP_LOG_MANAGER_PROPERTY);
                // Since we're overriding we will check the log manager system property and log a warning if the value is
                // not org.jboss.logmanager.LogManager.
                final String logManagerName = WildFlySecurityManager.getPropertyPrivileged("java.util.logging.manager", null);
                if (!org.jboss.logmanager.LogManager.class.getName().equals(logManagerName)) {
                    LoggingLogger.ROOT_LOGGER.unknownLogManager(logManagerName);
                }
            } else {
                // Testing the log manager must use the FQCN as the classes may be loaded via different class loaders
                if (!java.util.logging.LogManager.getLogManager().getClass().getName().equals(org.jboss.logmanager.LogManager.class.getName())) {
                    throw LoggingLogger.ROOT_LOGGER.extensionNotInitialized();
                }
            }
            contextSelector = WildFlyLogContextSelector.Factory.create();

            // Install STDIO context selector
            StdioContext.setStdioContextSelector(new LogContextStdioContextSelector(StdioContext.getStdioContext()));
        }
        LogContext.setLogContextSelector(contextSelector);

        // Load logging API modules
        try {
            final ModuleLoader moduleLoader = Module.forClass(LoggingExtension.class).getModuleLoader();
            for (LoggingModuleDependency dependency : LoggingModuleDependency.values()) {
                try {
                    contextSelector.addLogApiClassLoader(moduleLoader.loadModule(dependency.getModuleName()).getClassLoader());
                } catch (Throwable ignore) {
                    // ignore
                }
            }
        } catch (Exception ignore) {
            // ignore
        }

        final SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, CURRENT_VERSION);


        PathManager pathManager = null;
        // The path manager is only available if this is a server
        if (context.getProcessType().isServer()) {
            pathManager = context.getPathManager();
        }
        final LoggingResourceDefinition rootResource = new LoggingResourceDefinition(pathManager, contextSelector);
        final ManagementResourceRegistration registration = subsystem.registerSubsystemModel(rootResource);
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, DESCRIBE_HANDLER);
        // Register root sub-models
        registerSubModels(registration, true, pathManager);

        // Register logging profile sub-models
        ApplicationTypeConfig atc = new ApplicationTypeConfig(SUBSYSTEM_NAME, CommonAttributes.LOGGING_PROFILE);
        ResourceDefinition profile = new SimpleResourceDefinition(
                new Parameters(LOGGING_PROFILE_PATH, getResourceDescriptionResolver())
                        .setAddHandler(new LoggingProfileAdd(pathManager))
                        .setRemoveHandler(LoggingProfileOperations.REMOVE_PROFILE)
                        .setAccessConstraints(new ApplicationTypeAccessConstraintDefinition(atc)));

        registerLoggingProfileSubModels(registration.registerSubModel(profile), pathManager);

        // Register deployment resources
        if (context.isRuntimeOnlyRegistrationValid()) {
            final SimpleResourceDefinition deploymentSubsystem = new SimpleResourceDefinition(new Parameters(LoggingResourceDefinition.SUBSYSTEM_PATH, getResourceDescriptionResolver("deployment")).setFeature(false).setRuntime());
            final ManagementResourceRegistration deployments = subsystem.registerDeploymentModel(deploymentSubsystem);
            final ManagementResourceRegistration configurationResource = deployments.registerSubModel(LoggingDeploymentResources.CONFIGURATION);
            configurationResource.registerSubModel(LoggingDeploymentResources.HANDLER);
            configurationResource.registerSubModel(LoggingDeploymentResources.LOGGER);
            configurationResource.registerSubModel(LoggingDeploymentResources.FORMATTER);
            configurationResource.registerSubModel(LoggingDeploymentResources.FILTER);
            configurationResource.registerSubModel(LoggingDeploymentResources.POJO);
            configurationResource.registerSubModel(LoggingDeploymentResources.ERROR_MANAGER);
        }

        subsystem.registerXMLElementWriter(LoggingSubsystemWriter::new);
    }


    @Override
    public void initializeParsers(final ExtensionParsingContext context) {
        setParser(context, Namespace.LOGGING_1_0, new LoggingSubsystemParser_1_0());
        setParser(context, Namespace.LOGGING_1_1, new LoggingSubsystemParser_1_1());
        setParser(context, Namespace.LOGGING_1_2, new LoggingSubsystemParser_1_2());
        setParser(context, Namespace.LOGGING_1_3, new LoggingSubsystemParser_1_3());
        setParser(context, Namespace.LOGGING_1_4, new LoggingSubsystemParser_1_4());
        setParser(context, Namespace.LOGGING_1_5, new LoggingSubsystemParser_1_5());
        setParser(context, Namespace.LOGGING_2_0, new LoggingSubsystemParser_2_0());
        setParser(context, Namespace.LOGGING_3_0, new LoggingSubsystemParser_3_0());
        setParser(context, Namespace.LOGGING_4_0, new LoggingSubsystemParser_4_0());
        setParser(context, Namespace.LOGGING_5_0, new LoggingSubsystemParser_5_0());
        setParser(context, Namespace.LOGGING_6_0, new LoggingSubsystemParser_6_0());
        setParser(context, Namespace.LOGGING_7_0, new LoggingSubsystemParser_7_0());
        setParser(context, Namespace.LOGGING_8_0, new LoggingSubsystemParser_8_0());

        // Hack to ensure the Element and Attribute enums are loaded during this call which
        // is part of concurrent boot. These enums trigger a lot of classloading and static
        // initialization that we don't want deferred until the single-threaded parsing phase
        //noinspection ConstantConditions,EqualsBetweenInconvertibleTypes
        if (Element.forName("").equals(Attribute.forName(""))) { // never true
            throw new IllegalStateException();
        }
    }

    private void registerLoggingProfileSubModels(final ManagementResourceRegistration registration, final PathManager pathManager) {
        registerSubModels(registration, false, pathManager);
    }

    private void registerSubModels(final ManagementResourceRegistration registration,
                                   final boolean includeLegacyAttributes, final PathManager pathManager) {
        // Only register if the path manager is not null, e.g. is a server
        ResolvePathHandler resolvePathHandler = null;
        PathInfoHandler diskUsagePathHandler = null;
        if (pathManager != null) {
            resolvePathHandler = ResolvePathHandler.Builder.of(pathManager)
                    .setParentAttribute(CommonAttributes.FILE)
                    .build();
            diskUsagePathHandler = PathInfoHandler.Builder.of(pathManager)
                    .setParentAttribute(CommonAttributes.FILE)
                    .addAttribute(PathResourceDefinition.PATH, PathResourceDefinition.RELATIVE_TO)
                    .build();
            final LogFileResourceDefinition logFileResourceDefinition = new LogFileResourceDefinition(pathManager);
            registration.registerSubModel(logFileResourceDefinition);
        }

        final RootLoggerResourceDefinition rootLoggerResourceDefinition = new RootLoggerResourceDefinition(includeLegacyAttributes);
        registration.registerSubModel(rootLoggerResourceDefinition);

        final LoggerResourceDefinition loggerResourceDefinition = new LoggerResourceDefinition(includeLegacyAttributes);
        registration.registerSubModel(loggerResourceDefinition);

        final AsyncHandlerResourceDefinition asyncHandlerResourceDefinition = new AsyncHandlerResourceDefinition(includeLegacyAttributes);
        registration.registerSubModel(asyncHandlerResourceDefinition);

        final ConsoleHandlerResourceDefinition consoleHandlerResourceDefinition = new ConsoleHandlerResourceDefinition(includeLegacyAttributes);
        registration.registerSubModel(consoleHandlerResourceDefinition);

        final FileHandlerResourceDefinition fileHandlerResourceDefinition = new FileHandlerResourceDefinition(resolvePathHandler, diskUsagePathHandler, includeLegacyAttributes);
        registration.registerSubModel(fileHandlerResourceDefinition);

        final PeriodicHandlerResourceDefinition periodicHandlerResourceDefinition = new PeriodicHandlerResourceDefinition(resolvePathHandler, diskUsagePathHandler, includeLegacyAttributes);
        registration.registerSubModel(periodicHandlerResourceDefinition);

        final PeriodicSizeRotatingHandlerResourceDefinition periodicSizeRotatingHandlerResourceDefinition = new PeriodicSizeRotatingHandlerResourceDefinition(resolvePathHandler, diskUsagePathHandler);
        registration.registerSubModel(periodicSizeRotatingHandlerResourceDefinition);

        final SizeRotatingHandlerResourceDefinition sizeRotatingHandlerResourceDefinition = new SizeRotatingHandlerResourceDefinition(resolvePathHandler, includeLegacyAttributes);
        registration.registerSubModel(sizeRotatingHandlerResourceDefinition);

        final CustomHandlerResourceDefinition customHandlerResourceDefinition = new CustomHandlerResourceDefinition(includeLegacyAttributes);
        registration.registerSubModel(customHandlerResourceDefinition);

        registration.registerSubModel(SyslogHandlerResourceDefinition.INSTANCE);
        registration.registerSubModel(PatternFormatterResourceDefinition.INSTANCE);
        registration.registerSubModel(CustomFormatterResourceDefinition.INSTANCE);
        registration.registerSubModel(JsonFormatterResourceDefinition.INSTANCE);
        registration.registerSubModel(XmlFormatterResourceDefinition.INSTANCE);
        registration.registerSubModel(SocketHandlerResourceDefinition.INSTANCE);
        registration.registerSubModel(FilterResourceDefinition.INSTANCE);
    }

    public static final class TransformerRegistration implements ExtensionTransformerRegistration {

        @Override
        public String getSubsystemName() {
            return SUBSYSTEM_NAME;
        }

        @Override
        public void registerTransformers(SubsystemTransformerRegistration subsystemRegistration) {

            registerTransformerDefinitions(subsystemRegistration,
                    new LoggingResourceDefinition.TransformerDefinition(),
                    new RootLoggerResourceDefinition.TransformerDefinition(),
                    new LoggerResourceDefinition.TransformerDefinition(),
                    new AsyncHandlerResourceDefinition.TransformerDefinition(),
                    new ConsoleHandlerResourceDefinition.TransformerDefinition(),
                    new FileHandlerResourceDefinition.TransformerDefinition(),
                    new PeriodicHandlerResourceDefinition.TransformerDefinition(),
                    new PeriodicSizeRotatingHandlerResourceDefinition.TransformerDefinition(),
                    new SizeRotatingHandlerResourceDefinition.TransformerDefinition(),
                    new CustomHandlerResourceDefinition.TransformerDefinition(),
                    new SyslogHandlerResourceDefinition.TransformerDefinition(),
                    new PatternFormatterResourceDefinition.TransformerDefinition(),
                    new CustomFormatterResourceDefinition.TransformerDefinition(),
                    new JsonFormatterResourceDefinition.TransformerDefinition(),
                    new XmlFormatterResourceDefinition.TransformerDefinition(),
                    new SocketHandlerResourceDefinition.TransformerDefinition(),
                    new FilterResourceDefinition.TransformerDefinition());
        }

        private static void registerTransformerDefinitions(final SubsystemTransformerRegistration registration, final TransformerResourceDefinition... defs) {
            ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(CURRENT_VERSION);

            registerTransformers(chainedBuilder, CURRENT_VERSION, KnownModelVersion.VERSION_8_0_0, defs);
            registerTransformers(chainedBuilder, KnownModelVersion.VERSION_8_0_0, KnownModelVersion.VERSION_7_0_0, defs);
            registerTransformers(chainedBuilder, KnownModelVersion.VERSION_7_0_0, KnownModelVersion.VERSION_6_0_0, defs);
            registerTransformers(chainedBuilder, KnownModelVersion.VERSION_6_0_0, KnownModelVersion.VERSION_5_0_0, defs);
            registerTransformers(chainedBuilder, KnownModelVersion.VERSION_5_0_0, KnownModelVersion.VERSION_2_0_0, defs);
            // Version 1.5.0 has the periodic-size-rotating-file-handler and the suffix attribute on the size-rotating-file-handler.
            // Neither of these are in 2.0.0 (WildFly 8.x). Mapping from 3.0.0 to 1.5.0 is required
            registerTransformers(chainedBuilder, KnownModelVersion.VERSION_3_0_0, KnownModelVersion.VERSION_1_5_0, defs);

            chainedBuilder.buildAndRegister(registration, new ModelVersion[] {
                    KnownModelVersion.VERSION_2_0_0.getModelVersion(),
                    KnownModelVersion.VERSION_6_0_0.getModelVersion(),
                    KnownModelVersion.VERSION_7_0_0.getModelVersion(),
                    KnownModelVersion.VERSION_8_0_0.getModelVersion(),
            }, new ModelVersion[] {
                    KnownModelVersion.VERSION_1_5_0.getModelVersion(),
                    KnownModelVersion.VERSION_3_0_0.getModelVersion(),
                    KnownModelVersion.VERSION_4_0_0.getModelVersion(),
                    KnownModelVersion.VERSION_5_0_0.getModelVersion(),
                    KnownModelVersion.VERSION_6_0_0.getModelVersion(),
                    KnownModelVersion.VERSION_7_0_0.getModelVersion(),
                    KnownModelVersion.VERSION_8_0_0.getModelVersion(),
            });
        }

        private static void registerTransformers(final ChainedTransformationDescriptionBuilder chainedBuilder, final KnownModelVersion fromVersion, final KnownModelVersion toVersion, final TransformerResourceDefinition... defs) {
            registerTransformers(chainedBuilder, fromVersion.getModelVersion(), toVersion, defs);
        }

        private static void registerTransformers(final ChainedTransformationDescriptionBuilder chainedBuilder, final ModelVersion fromVersion, final KnownModelVersion toVersion, final TransformerResourceDefinition... defs) {
            final ResourceTransformationDescriptionBuilder subsystemBuilder = chainedBuilder.createBuilder(fromVersion, toVersion.getModelVersion());
            final ResourceTransformationDescriptionBuilder loggingProfileBuilder = subsystemBuilder.addChildResource(LOGGING_PROFILE_PATH);

            for (TransformerResourceDefinition def : defs) {
                def.registerTransformers(toVersion, subsystemBuilder, loggingProfileBuilder);
            }
        }
    }

    private static void setParser(final ExtensionParsingContext context, final Namespace namespace, final XMLElementReader<List<ModelNode>> parser) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, namespace.getUriString(), parser);
    }

    private static boolean getBooleanProperty(final String property, final boolean dft) {
        final String value = WildFlySecurityManager.getPropertyPrivileged(property, null);
        if (value == null) {
            return dft;
        }
        return value.isEmpty() || Boolean.parseBoolean(value);
    }

    public static class LoggingChildResourceComparator implements Comparator<PathElement> {
        static final LoggingChildResourceComparator INSTANCE = new LoggingChildResourceComparator();
        private static final int GREATER = 1;
        private static final int LESS = -1;

        @Override
        public int compare(final PathElement o1, final PathElement o2) {
            final String key1 = o1.getKey();
            final String key2 = o2.getKey();
            int result = key1.compareTo(key2);
            if (key1.equals(key2)) {
                // put the one already present first to preserve original order
                result = GREATER;
            } else {
                if (ModelDescriptionConstants.SUBSYSTEM.equals(key1)) {
                    result = LESS;
                } else if (ModelDescriptionConstants.SUBSYSTEM.equals(key2)) {
                    result = GREATER;
                } else if (CommonAttributes.LOGGING_PROFILE.equals(key1)) {
                    result = LESS;
                } else if (CommonAttributes.LOGGING_PROFILE.equals(key2)) {
                    result = GREATER;
                } else if (PatternFormatterResourceDefinition.NAME.equals(key1)) {
                    result = LESS;
                } else if (PatternFormatterResourceDefinition.NAME.equals(key2)) {
                    result = GREATER;
                } else if (CustomFormatterResourceDefinition.NAME.equals(key1)) {
                    result = LESS;
                } else if (CustomFormatterResourceDefinition.NAME.equals(key2)) {
                    result = GREATER;
                } else if (FilterResourceDefinition.NAME.equals(key1)) {
                    result = LESS;
                } else if (FilterResourceDefinition.NAME.equals(key2)) {
                    result = GREATER;
                } else if (RootLoggerResourceDefinition.NAME.equals(key1)) {
                    result = GREATER;
                } else if (RootLoggerResourceDefinition.NAME.equals(key2)) {
                    result = LESS;
                } else if (LoggerResourceDefinition.NAME.equals(key1)) {
                    result = GREATER;
                } else if (LoggerResourceDefinition.NAME.equals(key2)) {
                    result = LESS;
                } else if (AsyncHandlerResourceDefinition.NAME.equals(key1)) {
                    result = GREATER;
                } else if (AsyncHandlerResourceDefinition.NAME.equals(key2)) {
                    result = LESS;
                }
            }
            return result;
        }
    }

}
