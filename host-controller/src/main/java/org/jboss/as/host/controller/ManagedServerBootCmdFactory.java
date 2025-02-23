/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BOOT_TIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOOPBACK;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SSL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultCapabilityServiceSupport;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ExpressionResolverImpl;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.capability.registry.ImmutableCapabilityRegistry;
import org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition;
import org.jboss.as.host.controller.jvm.JvmType;
import org.jboss.as.host.controller.model.host.HostResourceDefinition;
import org.jboss.as.host.controller.model.jvm.JvmElement;
import org.jboss.as.host.controller.model.jvm.JvmOptionsBuilderFactory;
import org.jboss.as.host.controller.resources.SslLoopbackResourceDefinition;
import org.jboss.as.process.CommandLineConstants;
import org.jboss.as.process.ProcessControllerClient;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.controller.resources.SystemPropertyResourceDefinition;
import org.jboss.as.server.mgmt.domain.HostControllerConnectionService.SSLContextSupplier;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Combines the relevant parts of the domain-level and host-level models to
 * determine the jvm launch command needed to start an application server instance.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ManagedServerBootCmdFactory implements ManagedServerBootConfiguration {

    private static final String HOST_CONTROLLER_PROCESS_NAME_PROP = "[" + ProcessControllerClient.HOST_CONTROLLER_PROCESS_NAME + "]";
    private static final Set<String> SERVER_CONFIGURATION_PROPERTIES = new HashSet<>(Arrays.asList(
            "jboss.server.log.dir",
            "jboss.server.config.dir",
            "jboss.server.base.dir",
            "jboss.server.config.user.dir",
            "jboss.server.config.group.dir",
            "jboss.server.temp.dir",
            "jboss.server.content.dir",
            "jboss.server.data.dir",
            "jboss.server.deploy.dir",
            "jboss.server.controller.base.dir",
            "jboss.server.default.config",
            "jboss.server.management.uuid",
            "jboss.server.persist.config"));

    private static final Random random = new Random();
    private static final ModelNode EMPTY = new ModelNode();
    static {
        EMPTY.setEmptyList();
        EMPTY.protect();
    }
    private static final String EXCLUDED_PROPERTIES_PROP = "jboss.host.server-excluded-properties";
    private static Set<String> getExcludedHostProperties() {
        String excluded = System.getProperty(EXCLUDED_PROPERTIES_PROP);
        if (excluded == null || excluded.length() == 0) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.asList(excluded.split(",")));
    }

    private final String serverName;
    private final int processId = Math.abs(random.nextInt());
    private final ModelNode domainModel;
    private final ModelNode hostModel;
    private final ModelNode serverModel;
    private final ModelNode serverGroup;
    private final JvmElement jvmElement;
    private final HostControllerEnvironment environment;
    private final boolean managementSubsystemEndpoint;
    private final ModelNode endpointConfig = new ModelNode();
    private final ManagedServerExprResolver expressionResolver;
    private final DirectoryGrouping directoryGrouping;
    private final Supplier<SSLContext> sslContextSupplier;
    private final boolean suspend;
    private final boolean gracefulStartup;
    private JvmType jvmType;
    private final String logDir;
    private final String tmpDir;
    private final String dataDir;

    public ManagedServerBootCmdFactory(final String serverName, final ModelNode domainModel, final ModelNode hostModel,
                                       final HostControllerEnvironment environment, final ExpressionResolver expressionResolver,
                                       final ImmutableCapabilityRegistry capabilityRegistry, final boolean suspend) {
        this.serverName = serverName;
        this.domainModel = domainModel;
        this.hostModel = hostModel;
        this.environment = environment;
        this.expressionResolver = new ManagedServerExprResolver(expressionResolver, this.serverName,
                new DefaultCapabilityServiceSupport(capabilityRegistry));
        this.suspend = suspend;
        this.serverModel = resolveExpressions(hostModel.require(SERVER_CONFIG).require(serverName), this.expressionResolver, true);
        this.directoryGrouping = resolveDirectoryGrouping(hostModel, this.expressionResolver);
        final String serverGroupName = serverModel.require(GROUP).asString();
        this.serverGroup = resolveExpressions(domainModel.require(SERVER_GROUP).require(serverGroupName), this.expressionResolver, true);

        String serverVMName = null;
        ModelNode serverVM = null;
        if(serverModel.hasDefined(JVM)) {
            for (final String jvm : serverModel.get(JVM).keys()) {
                serverVMName = jvm;
                serverVM = serverModel.get(JVM, jvm);
                break;
            }
        }
        String groupVMName = null;
        ModelNode groupVM = null;
        if(serverGroup.hasDefined(JVM)) {
            for(final String jvm : serverGroup.get(JVM).keys()) {
                groupVMName = jvm;
                groupVM = serverGroup.get(JVM, jvm);
                break;
            }
        }
        // Use the subsystem endpoint
        // TODO by default use the subsystem endpoint
        this.managementSubsystemEndpoint = serverGroup.get(ServerGroupResourceDefinition.MANAGEMENT_SUBSYSTEM_ENDPOINT.getName()).asBoolean(false);
        // Get the endpoint configuration
        if(managementSubsystemEndpoint) {
            final String profileName = serverGroup.get(PROFILE).asString();
            final ModelNode profile = domainModel.get(PROFILE, profileName);
            if(profile.hasDefined(SUBSYSTEM) && profile.hasDefined("remoting")) {
                endpointConfig.set(profile.get(SUBSYSTEM, "remoting"));
            }
        }

        Map<String, String> bootTimeProperties = getAllSystemProperties(true);
        // Use the directory grouping type to set props controlling the server data/log/tmp dirs
        String serverDirProp = bootTimeProperties.get(ServerEnvironment.SERVER_BASE_DIR);
        File serverDir = serverDirProp == null ? new File(environment.getDomainServersDir(), serverName) : new File(serverDirProp);
        this.logDir = addPathProperty("log", ServerEnvironment.SERVER_LOG_DIR, bootTimeProperties,
                directoryGrouping, environment.getDomainLogDir(), serverDir);
        this.tmpDir = addPathProperty("tmp", ServerEnvironment.SERVER_TEMP_DIR, bootTimeProperties,
                directoryGrouping, environment.getDomainTempDir(), serverDir);
        this.dataDir = addPathProperty("data", ServerEnvironment.SERVER_DATA_DIR, bootTimeProperties,
                directoryGrouping, environment.getDomainDataDir(), serverDir);

        new File(this.logDir).mkdirs();

        this.expressionResolver.addResolvableValue(ServerEnvironment.SERVER_BASE_DIR, serverDir.getAbsolutePath());
        this.expressionResolver.addResolvableValue(ServerEnvironment.SERVER_LOG_DIR, logDir);
        this.expressionResolver.addResolvableValue(ServerEnvironment.SERVER_TEMP_DIR, tmpDir);
        this.expressionResolver.addResolvableValue(ServerEnvironment.SERVER_DATA_DIR, dataDir);
        this.expressionResolver.addResolvableValue(ServerEnvironment.SERVER_NAME, this.serverName);

        final String jvmName = serverVMName != null ? serverVMName : groupVMName;
        final ModelNode hostVM = jvmName != null ? hostModel.get(JVM, jvmName) : null;

        this.jvmElement = new JvmElement(jvmName,
                resolveNilableExpressions(hostVM, this.expressionResolver, false),
                resolveNilableExpressions(groupVM, this.expressionResolver, false),
                resolveNilableExpressions(serverVM, this.expressionResolver, false));

        this.sslContextSupplier = createSSLContextSupplier(serverModel, this.expressionResolver);

        try {
            this.gracefulStartup = ServerGroupResourceDefinition.GRACEFUL_STARTUP.resolveModelAttribute(this.expressionResolver, serverGroup).asBoolean();
        } catch (OperationFailedException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static ModelNode resolveNilableExpressions(final ModelNode unresolved, final ExpressionResolver expressionResolver, boolean excludePostBootSystemProps) {
        return unresolved == null ? null : resolveExpressions(unresolved, expressionResolver, excludePostBootSystemProps);
    }

    /**
     * Resolve expressions in the given model (if there are any)
     *
     * @param unresolved node with possibly unresolved expressions. Cannot be {@code null}
     * @param expressionResolver resolver to use. Cannot be {@code null}
     * @param excludePostBootSystemProps {@code true} if child system-property nodes should be checked
     *                                               for the 'boot-time' attribute, with resolution being
     *                                               skipped if that is set to 'false'. WFCORE-450
     *
     * @return a clone of {@code unresolved} with all expression resolved
     */
    static ModelNode resolveExpressions(final ModelNode unresolved, final ExpressionResolver expressionResolver, boolean excludePostBootSystemProps) {

        ModelNode toResolve = unresolved.clone();
        ModelNode sysProps = null;
        if (excludePostBootSystemProps && toResolve.hasDefined(SYSTEM_PROPERTY)) {
            sysProps = toResolve.remove(SYSTEM_PROPERTY);
        }
        try {
            ModelNode result = expressionResolver.resolveExpressions(toResolve);
            if (sysProps != null) {
                ModelNode resolvedSysProps = new ModelNode().setEmptyObject();
                for (Property property : sysProps.asPropertyList()) {
                    ModelNode val = property.getValue();
                    boolean bootTime = SystemPropertyResourceDefinition.BOOT_TIME.resolveModelAttribute(expressionResolver, val).asBoolean();
                    if (bootTime) {
                        val.get(VALUE).set(SystemPropertyResourceDefinition.VALUE.resolveModelAttribute(expressionResolver, val));
                    }
                    // store the resolved boot-time to save re-resolving later
                    val.get(BOOT_TIME).set(bootTime);
                    resolvedSysProps.get(property.getName()).set(val);
                }
                result.get(SYSTEM_PROPERTY).set(resolvedSysProps);
            }
            return result;
        } catch (OperationFailedException e) {
            // Fail
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * Returns the value of found in the model.
     *
     * @param model the model that contains the key and value.
     * @param expressionResolver the expression resolver to use to resolve expressions
     *
     * @return the directory grouping found in the model.
     *
     * @throws IllegalArgumentException if the {@link org.jboss.as.controller.descriptions.ModelDescriptionConstants#DIRECTORY_GROUPING directory grouping}
     *                                  was not found in the model.
     */
    private static DirectoryGrouping resolveDirectoryGrouping(final ModelNode model, final ExpressionResolver expressionResolver) {
        try {
            return DirectoryGrouping.forName(HostResourceDefinition.DIRECTORY_GROUPING.resolveModelAttribute(expressionResolver, model).asString());
        } catch (OperationFailedException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Create and verify the configuration before trying to start the process.
     *
     * @return the process boot configuration
     */
    public ManagedServerBootConfiguration createConfiguration() {
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public HostControllerEnvironment getHostControllerEnvironment() {
        return environment;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getServerLaunchCommand() {
        return getServerLaunchCommand(true, true);
    }

    /** {@inheritDoc} */
    @Override
    public boolean compareServerLaunchCommand(ManagedServerBootConfiguration other) {
        boolean comparable = other instanceof ManagedServerBootCmdFactory;
        if (comparable) {
            ManagedServerBootCmdFactory otherImpl = (ManagedServerBootCmdFactory) other;
            comparable = getJvmType(false).equals(otherImpl.getJvmType(false))
                    && getServerLaunchCommand(false, false).equals(otherImpl.getServerLaunchCommand(false, false));
        }
        return comparable;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> getServerLaunchProperties() {
        List<String> launchCommand = getServerLaunchCommand(true, false);
        return parseLaunchProperties(launchCommand);
    }

    private List<String> getServerLaunchCommand(boolean includeProcessId, boolean forLaunch) {
        final List<String> command = new ArrayList<String>();

        if (jvmElement.getLaunchCommand() != null) {
            List<String> commandPrefix = getLaunchPrefixCommands();
            if(commandPrefix != null)
                command.addAll(commandPrefix);
        }

        final String jbossModulesJar = getAbsolutePath(environment.getHomeDir(), "jboss-modules.jar");

        JvmType localJvmType = getJvmType(forLaunch);

        command.add(localJvmType.getJavaExecutable());

        command.add("-D[" + ManagedServer.getServerProcessName(serverName) + "]");

        if (includeProcessId) {
            command.add("-D[" + ManagedServer.getServerProcessId(processId) + "]");
        }

        // If the module options include a Java agent we'll add jboss-modules.jar by default
        if (jvmElement.getModuleOptions().contains("\\-javaagent:.+")) {
            command.add("-javaagent:" + jbossModulesJar);
        }

        JvmOptionsBuilderFactory.getInstance(localJvmType).addOptions(jvmElement, command);

        Map<String, String> bootTimeProperties = getAllSystemProperties(true);
        // Add in properties passed in to the ProcessController command line
        Set<String> excludedHostProperties = getExcludedHostProperties();
        for (Map.Entry<String, String> hostProp : environment.getHostSystemProperties().entrySet()) {
            String propName = hostProp.getKey();
            if (!bootTimeProperties.containsKey(propName)
                    && ! SERVER_CONFIGURATION_PROPERTIES.contains(propName)
                    && !excludedHostProperties.contains(propName)) {
                bootTimeProperties.put(propName, hostProp.getValue());
            }
        }
        for (Entry<String, String> entry : bootTimeProperties.entrySet()) {
            String property = entry.getKey();
            String value = entry.getValue();
            if (!"org.jboss.boot.log.file".equals(property) && !"logging.configuration".equals(property)
                    && !HOST_CONTROLLER_PROCESS_NAME_PROP.equals(property)) {
                final StringBuilder sb = new StringBuilder("-D");
                sb.append(property);
                if (value != null) {
                    sb.append('=');
                    sb.append(value);
                }
                command.add(sb.toString());
            }
        }

        command.addAll(localJvmType.getDefaultArguments());
        command.addAll(localJvmType.getOptionalDefaultArguments());

        command.add(String.format("-D%s=%s", ServerEnvironment.SERVER_LOG_DIR, this.logDir));
        command.add(String.format("-D%s=%s", ServerEnvironment.SERVER_TEMP_DIR, this.tmpDir));
        command.add(String.format("-D%s=%s", ServerEnvironment.SERVER_DATA_DIR, this.dataDir));

        final File loggingConfig = new File(dataDir, "logging.properties");
        final File path;
        if (loggingConfig.exists()) {
            path = loggingConfig;
        } else {
            // Sets the initial log file to use
            command.add("-Dorg.jboss.boot.log.file=" + getAbsolutePath(new File(logDir), "server.log"));

            // Used as a fall back in case the default-server-logging.properties has been deleted
            final File domainConfigFile = getAbsoluteFile(environment.getDomainConfigurationDir(), "logging.properties");
            // The default configuration file to use if nothing is set
            final File defaultConfigFile = getAbsoluteFile(environment.getDomainConfigurationDir(), "default-server-logging.properties");

            // If the default file exists, it should but we should be safe in case it was deleted by a user
            if (defaultConfigFile.exists()) {
                path = defaultConfigFile;
            } else {
                // Default to the domain/configuration/logging.properties if the default wasn't found to get initial
                // boot logging
                path = domainConfigFile;
            }
        }

        if (path.exists()) {
            try {
                command.add(String.format("-Dlogging.configuration=%s", path.toURI().toURL().toExternalForm()));
            } catch (MalformedURLException e) {
                ROOT_LOGGER.failedToSetLoggingConfiguration(e, serverName, path);
            }
        } else {
            ROOT_LOGGER.serverLoggingConfigurationFileNotFound(serverName);
        }

        command.add("-jar");
        command.add(jbossModulesJar);
        command.add("-mp");
        command.add(environment.getModulePath());
        // Enable the security manager if required
        if (environment.isSecurityManagerEnabled()){
            command.add("-secmgr");
        }
        command.addAll(jvmElement.getModuleOptions().getOptions());
        command.add("org.jboss.as.server");

        if (!isGracefulStartup()) {
            command.add(CommandLineConstants.GRACEFUL_STARTUP + "=false");
        }

        if(suspend) {
            command.add(CommandLineConstants.START_MODE + "=" + CommandLineConstants.SUSPEND_MODE);
        }
        return command;
    }

    @Override
    public boolean isManagementSubsystemEndpoint() {
        return managementSubsystemEndpoint;
    }

    @Override
    public ModelNode getSubsystemEndpointConfiguration() {
        return endpointConfig;
    }

    private Supplier<SSLContext> createSSLContextSupplier(final ModelNode serverModel, final ExpressionResolver resolver) {
        if (serverModel.hasDefined(SSL, LOOPBACK) == false) {
            return null;
        }

        ModelNode ssl = serverModel.get(SSL, LOOPBACK);

        try {
            String sslProtocol = SslLoopbackResourceDefinition.SSL_PROTOCOCOL.resolveModelAttribute(resolver, ssl).asString();
            String trustManagerAlgorithm = asStringIfDefined(ssl, SslLoopbackResourceDefinition.TRUST_MANAGER_ALGORITHM, resolver);
            String trustStoreType = asStringIfDefined(ssl, SslLoopbackResourceDefinition.TRUSTSTORE_TYPE, resolver);
            String trustStorePath = asStringIfDefined(ssl, SslLoopbackResourceDefinition.TRUSTSTORE_PATH, resolver);
            ModelNode trustStorePasswordModel = SslLoopbackResourceDefinition.TRUSTSTORE_PASSWORD.resolveModelAttribute(resolver, ssl);
            char[] trustStorePassword = trustStorePasswordModel.isDefined() ? trustStorePasswordModel.asString().toCharArray() : null;

            return new SSLContextSupplier(sslProtocol, trustManagerAlgorithm, trustStoreType, trustStorePath, trustStorePassword);
        } catch (OperationFailedException e) {
            throw new IllegalStateException(e);
        }
    }

    private String asStringIfDefined(ModelNode model, AttributeDefinition attribute, ExpressionResolver resolver) throws OperationFailedException {
        ModelNode value = attribute.resolveModelAttribute(resolver, model);
        if (value.isDefined()) {
            return value.asString();
        }

        return null;
    }

    @Override
    public Supplier<SSLContext> getSSLContextSupplier() {
        return sslContextSupplier;
    }

    @Override
    public boolean isSuspended() {
        return suspend;
    }

    @Override
    public boolean isGracefulStartup() {
        return gracefulStartup;
    }

    @Override
    public int getServerProcessId() {
        return processId;
    }

    private synchronized JvmType getJvmType(boolean forLaunch) {
        JvmType result = this.jvmType;
        if (result == null) {
            String javaHome = jvmElement.getJavaHome();
            if (javaHome == null) {
                if (environment.getDefaultJVM() != null) {
                    String javaExecutable = environment.getDefaultJVM().getAbsolutePath();
                    result = JvmType.createFromJavaExecutable(javaExecutable, forLaunch);
                } else {
                    result = JvmType.createFromSystemProperty(forLaunch);
                }
            } else {
                result = JvmType.createFromJavaHome(javaHome, forLaunch);
            }
            if (forLaunch) {
                this.jvmType = result;
            } // else don't cache it as we don't know if it's valid and may not add correct default java opts
        }
        return result;
    }

    private ArrayList<String> getLaunchPrefixCommands(){
        String launchCommand = jvmElement.getLaunchCommand();
        ArrayList<String> commands = null;

        if(launchCommand.length()>0){
            commands = new ArrayList<String>(Arrays.asList(launchCommand.split("\\s* \\s*")));
        }
        ROOT_LOGGER.serverLaunchCommandPrefix(this.serverName, launchCommand);
        return commands;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> getServerLaunchEnvironment() {
        final Map<String, String> env = new HashMap<String, String>();
        for(final Entry<String, String> property : jvmElement.getEnvironmentVariables().entrySet()) {
            env.put(property.getKey(), property.getValue());
        }
        return env;
    }

    private Map<String, String> getAllSystemProperties(boolean boottimeOnly){
        Map<String, String> props = new TreeMap<String, String>();

        addSystemProperties(domainModel, props, boottimeOnly);
        addSystemProperties(serverGroup, props, boottimeOnly);
        addSystemProperties(hostModel, props, boottimeOnly);
        addSystemProperties(serverModel, props, boottimeOnly);

        return props;
    }

    private void addSystemProperties(final ModelNode source, final Map<String, String> props, boolean boottimeOnly) {
        if (source.hasDefined(SYSTEM_PROPERTY)) {
            for (Property prop : source.get(SYSTEM_PROPERTY).asPropertyList()) {
                ModelNode propResource = prop.getValue();
                try {
                    if (boottimeOnly && !SystemPropertyResourceDefinition.BOOT_TIME.resolveModelAttribute(expressionResolver, propResource).asBoolean()) {
                        continue;
                    }
                } catch (OperationFailedException e) {
                    throw new IllegalStateException(e);
                }
                String val = propResource.hasDefined(VALUE) ? propResource.get(VALUE).asString() : null;
                props.put(prop.getName(), val);
            }
        }
    }

    /**
     * Adds the absolute path to command.
     *
     * @param typeName          the type of directory.
     * @param propertyName      the name of the property.
     * @param properties        the properties where the path may already be defined.
     * @param directoryGrouping the directory group type.
     * @param typeDir           the domain level directory for the given directory type; to be used for by-type grouping
     * @param serverDir         the root directory for the server, to be used for 'by-server' grouping
     * @return the absolute path that was added.
     */
    private String addPathProperty(final String typeName, final String propertyName, final Map<String, String> properties, final DirectoryGrouping directoryGrouping,
                                   final File typeDir, File serverDir) {
        final String result;
        final String value = properties.get(propertyName);
        if (value == null) {
            switch (directoryGrouping) {
                case BY_TYPE:
                    result = getAbsolutePath(typeDir, "servers", serverName);
                    break;
                case BY_SERVER:
                default:
                    result = getAbsolutePath(serverDir, typeName);
                    break;
            }
            properties.put(propertyName, result);
        } else {
            final File dir = new File(value);
            switch (directoryGrouping) {
                case BY_TYPE:
                    result = getAbsolutePath(dir, "servers", serverName);
                    break;
                case BY_SERVER:
                default:
                    result = getAbsolutePath(dir, serverName);
                    break;
            }
        }
        return result;
    }

    static String getAbsolutePath(final File root, final String... paths) {
        return getAbsoluteFile(root, paths).getAbsolutePath();
    }

    static File getAbsoluteFile(final File root, final String... paths) {
        File path = root;
        for(String segment : paths) {
            path = new File(path, segment);
        }
        return path.getAbsoluteFile();
    }

    private static Map<String, String> parseLaunchProperties(final List<String> commands) {
        final Map<String, String> result = new LinkedHashMap<String, String>();
        for (String cmd : commands) {
            if (cmd.startsWith("-D")) {
                final String[] parts = cmd.substring(2).split("=");
                if (parts.length == 2) {
                    result.put(parts[0], parts[1]);
                } else if (parts.length == 1) {
                    result.put(parts[0], "true");
                }
            }
        }
        return result;
    }

    static class ManagedServerExprResolver extends ExpressionResolverImpl {
        final ExpressionResolver delegate;
        final String serverName;
        final CapabilityServiceSupport capabilityServiceSupport;
        final Map<String, String> resolvableData;

        public ManagedServerExprResolver(ExpressionResolver delegate, String serverName, CapabilityServiceSupport capabilityServiceSupport) {
            super(true);
            this.delegate = delegate;
            this.serverName = serverName;
            this.capabilityServiceSupport = capabilityServiceSupport;
            this.resolvableData = new HashMap<>() {{
                put(ServerEnvironment.SERVER_BASE_DIR, null);
                put(ServerEnvironment.SERVER_DATA_DIR, null);
                put(ServerEnvironment.SERVER_LOG_DIR, null);
                put(ServerEnvironment.SERVER_TEMP_DIR, null);
                put(ServerEnvironment.SERVER_NAME, null);
            }};
        }

        @Override
        public ModelNode resolveExpressions(final ModelNode node) throws OperationFailedException {
            return resolveExpressions(node, capabilityServiceSupport);
        }

        @Override
        protected void resolvePluggableExpression(ModelNode node, CapabilityServiceSupport capabilityServiceSupport) {
            String expression = node.asString();
            if (expression.length() > 3) {
                String expressionValue = expression.substring(2, expression.length() - 1);
                if (resolvableData.containsKey(expressionValue)) {
                    String resolved = resolvableData.get(expressionValue);
                    if (resolved != null) {
                        node.set(resolved);
                    }
                } else {
                    node.set(delegate.resolveExpressions(node, capabilityServiceSupport));
                }
            }
        }

        public void addResolvableValue(String property, String value) {
            if (! resolvableData.containsKey(property)) {
                throw new IllegalStateException();
            }
            resolvableData.put(property, value);
        }
    }
}
