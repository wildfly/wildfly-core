/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.aesh.readline.terminal.formatting.Color;
import org.jboss.as.cli.CliConfig;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.ColorConfig;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.ControllerAddress;
import org.jboss.as.cli.SSLConfig;
import org.jboss.as.cli.util.CLIExpressionResolver;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.logging.Logger;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLMapper;
import org.wildfly.common.xml.XMLInputFactoryUtil;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Represents the JBoss CLI configuration.
 *
 * @author Alexey Loubyansky
 */
class CliConfigImpl implements CliConfig {

    /*
     * Legacy vault expression - no longer supported.
     */
    private static final Pattern VAULT_PATTERN = Pattern.compile("VAULT::.*::.*::.*");

    private static final String JBOSS_XML_CONFIG = "jboss.cli.config";
    private static final String CURRENT_WORKING_DIRECTORY = "user.dir";
    private static final String JBOSS_CLI_FILE = "jboss-cli.xml";

    private static final String ACCESS_CONTROL = "access-control";
    private static final String CONTROLLER = "controller";
    private static final String CONTROLLERS = "controllers";
    private static final String DEFAULT_CONTROLLER = "default-controller";
    private static final String DEFAULT_PROTOCOL = "default-protocol";
    private static final String ENABLED = "enabled";
    private static final String FILE_DIR = "file-dir";
    private static final String FILE_NAME = "file-name";
    private static final String JBOSS_CLI = "jboss-cli";
    private static final String HISTORY = "history";
    private static final String HOST = "host";
    private static final String MAX_SIZE = "max-size";
    private static final String NAME = "name";
    private static final String PORT = "port";
    private static final String PROTOCOL = "protocol";
    private static final String CONNECTION_TIMEOUT = "connection-timeout";
    private static final String RESOLVE_PARAMETER_VALUES = "resolve-parameter-values";
    private static final String SILENT = "silent";
    private static final String USE_LEGACY_OVERRIDE = "use-legacy-override";
    private static final String VALIDATE_OPERATION_REQUESTS = "validate-operation-requests";
    private static final String ECHO_COMMAND = "echo-command";
    private static final String COMMAND_TIMEOUT = "command-timeout";
    private static final String OUTPUT_JSON = "output-json";
    private static final String COLOR_OUTPUT = "color-output";
    private static final String OUTPUT_PAGING = "output-paging";

    private static final Logger log = Logger.getLogger(CliConfig.class);

    static CliConfig newBootConfig(boolean echoCommand) throws CliInitializationException {
        CliConfigImpl config = new CliConfigImpl();
        config.validateOperationRequests = false;
        config.echoCommand = echoCommand;
        return config;
    }

    static CliConfig load(final CommandContext ctx) throws CliInitializationException {
        return load(ctx, null);
    }

    static CliConfig load(final CommandContext ctx, CommandContextConfiguration configuration) throws CliInitializationException {
        File jbossCliFile = findCLIFileFromSystemProperty();

        if (jbossCliFile == null) {
            jbossCliFile = findCLIFileInCurrentDirectory();
        }

        if (jbossCliFile == null) {
            jbossCliFile = findCLIFileInJBossHome();
        }

        CliConfigImpl config = null;

        if (jbossCliFile == null) {
            System.err.println("WARN: can't find " + JBOSS_CLI_FILE + ". Using default configuration values.");
            config = new CliConfigImpl();
        } else {
            config = parse(ctx, jbossCliFile);
        }

        if( configuration != null ){
            config = overrideConfigWithArguments(config, configuration);
        }

        return config;
    }

    private static File findCLIFileFromSystemProperty() {
        final String jbossCliConfig = WildFlySecurityManager.getPropertyPrivileged(JBOSS_XML_CONFIG, null);
        if (jbossCliConfig == null) return null;

        return new File(jbossCliConfig);
    }

    private static File findCLIFileInCurrentDirectory() {
        final String currentDir = WildFlySecurityManager.getPropertyPrivileged(CURRENT_WORKING_DIRECTORY, null);
        if (currentDir == null) return null;

        File jbossCliFile = new File(currentDir, JBOSS_CLI_FILE);

        if (!jbossCliFile.exists()) return null;

        return jbossCliFile;
    }

    private static File findCLIFileInJBossHome() {
        final String jbossHome = WildFlySecurityManager.getEnvPropertyPrivileged("JBOSS_HOME", null);
        if (jbossHome == null) return null;

        File jbossCliFile = new File(jbossHome + File.separatorChar + "bin", JBOSS_CLI_FILE);

        if (!jbossCliFile.exists()) return null;

        return jbossCliFile;
    }

    private static CliConfigImpl parse(final CommandContext ctx, File f) throws CliInitializationException {
        if(f == null) {
            throw new CliInitializationException("The file argument is null.");
        }
        if(!f.exists()) {
            //throw new CliInitializationException(f.getAbsolutePath() + " doesn't exist.");
            return new CliConfigImpl();
        }

        CliConfigImpl config = new CliConfigImpl();

        BufferedInputStream input = null;
        try {
            final XMLMapper mapper = XMLMapper.Factory.create();
            final XMLElementReader<CliConfigImpl> reader = new CliConfigReader();
            for (Namespace current : Namespace.cliValues()) {
                mapper.registerRootElement(new QName(current.getUriString(), JBOSS_CLI), reader);
            }
            FileInputStream is = new FileInputStream(f);
            input = new BufferedInputStream(is);
            XMLStreamReader streamReader = XMLInputFactoryUtil.create().createXMLStreamReader(input);
            mapper.parseDocument(config, streamReader);
            streamReader.close();
        } catch(Throwable t) {
            throw new CliInitializationException("Failed to parse " + f.getAbsolutePath(), t);
        } finally {
            StreamUtils.safeClose(input);
        }
        return config;
    }

    private static CliConfigImpl overrideConfigWithArguments(CliConfigImpl cliConfig, CommandContextConfiguration configuration){
        // The configuration options from the command line should only override if they are not defaults.
        // This is to prevent a default Configuration option from overriding an Option defined in the config file.
        cliConfig.connectionTimeout         = configuration.getConnectionTimeout() != -1          ? configuration.getConnectionTimeout()        : cliConfig.getConnectionTimeout();
        cliConfig.silent                    = configuration.isSilent()                            ? configuration.isSilent()                    : cliConfig.silent;
        cliConfig.errorOnInteract           = configuration.isErrorOnInteract() != null           ? configuration.isErrorOnInteract()           : cliConfig.errorOnInteract;
        cliConfig.echoCommand               = configuration.isEchoCommand()                       ? configuration.isEchoCommand()               : cliConfig.echoCommand;
        cliConfig.commandTimeout            = configuration.getCommandTimeout() != null           ? configuration.getCommandTimeout()           : cliConfig.commandTimeout;
        cliConfig.validateOperationRequests = configuration.isValidateOperationRequests() != null ? configuration.isValidateOperationRequests() : cliConfig.validateOperationRequests;
        cliConfig.outputJSON                = configuration.isOutputJSON()                        ? configuration.isOutputJSON()                : cliConfig.isOutputJSON();
        cliConfig.outputPaging              = !configuration.isOutputPaging()                     ? configuration.isOutputPaging()              : cliConfig.isOutputPaging();
        cliConfig.resolveParameterValues    = configuration.isResolveParameterValues()            ? configuration.isResolveParameterValues()    : cliConfig.resolveParameterValues;
        if (!configuration.isColorOutput()) {
            cliConfig.colorOutput = false;
        } else if (configuration.isColorOutput() && cliConfig.colorConfig == null) {
            cliConfig.colorOutput = true;
        } else if (configuration.isColorOutput() && cliConfig.colorConfig != null) {
            cliConfig.colorOutput = cliConfig.isColorOutput();
        }

        return cliConfig;
    }

    private static String resolveString(String str) throws XMLStreamException {
        if(str == null) {
            return null;
        }
        if(str.startsWith("${") && str.endsWith("}")) {
            str = str.substring(2, str.length() - 1);
            final String resolved = WildFlySecurityManager.getPropertyPrivileged(str, null);
            if(resolved == null) {
                throw new XMLStreamException("Failed to resolve '" + str + "' to a non-null value.");
            }
            str = resolved;
        }
        return str;
    }

    private static boolean resolveBoolean(String str) throws XMLStreamException {
        return Boolean.parseBoolean(resolveString(str));
    }

    private static int resolveInteger(String str) throws XMLStreamException {
        return Integer.parseInt(resolveString(str));
    }

    private CliConfigImpl() {
        defaultControllerProtocol = "remote+http";

        historyEnabled = true;
        historyFileName = ".jboss-cli-history";
        historyFileDir = WildFlySecurityManager.getPropertyPrivileged("user.home", null);
        historyMaxSize = 500;

        connectionTimeout = 5000;
    }

    private String defaultControllerProtocol;
    private boolean useLegacyOverride = true;
    private ControllerAddress defaultController = new ControllerAddress(null, "localhost", -1);
    private Map<String, ControllerAddress> controllerAliases = Collections.emptyMap();

    private boolean historyEnabled;
    private String historyFileName;
    private String historyFileDir;
    private int historyMaxSize;

    private int connectionTimeout;

    private boolean validateOperationRequests = true;
    private boolean resolveParameterValues = false;

    private SSLConfig sslConfig;

    private boolean silent;
    private boolean errorOnInteract = true;

    private boolean accessControl = true;

    private boolean echoCommand;

    private Integer commandTimeout;

    private boolean outputJSON;

    private boolean colorOutput;
    private ColorConfigImpl colorConfig;

    private boolean outputPaging = true;

    @Override
    public String getDefaultControllerProtocol() {
        return defaultControllerProtocol;
    }

    @Override
    public boolean isUseLegacyOverride() {
        return useLegacyOverride;
    }

    @Override
    @Deprecated
    public String getDefaultControllerHost() {
        return getDefaultControllerAddress().getHost();
    }

    @Override
    @Deprecated
    public int getDefaultControllerPort() {
        return getDefaultControllerAddress().getPort();
    }

    @Override
    public ControllerAddress getDefaultControllerAddress() {
        return defaultController;
    }

    @Override
    public ControllerAddress getAliasedControllerAddress(String alias) {
        return controllerAliases.get(alias);
    }

    @Override
    public boolean isHistoryEnabled() {
        return historyEnabled;
    }

    @Override
    public String getHistoryFileName() {
        return historyFileName;
    }

    @Override
    public String getHistoryFileDir() {
        return historyFileDir;
    }

    @Override
    public int getHistoryMaxSize() {
        return historyMaxSize;
    }

    @Override
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    @Override
    public boolean isValidateOperationRequests() {
        return validateOperationRequests;
    }

    @Override
    public boolean isResolveParameterValues() {
        return resolveParameterValues;
    }

    @Override
    public SSLConfig getSslConfig() {
        return sslConfig;
    }

    @Override
    public boolean isSilent() {
        return silent;
    }

    @Override
    public boolean isErrorOnInteract() {
        return errorOnInteract;
    }

    @Override
    public boolean isAccessControl() {
        return accessControl;
    }

    @Override
    public boolean isEchoCommand() {
        return echoCommand;
    }

    @Override
    public Integer getCommandTimeout() {
        return commandTimeout;
    }

    @Override
    public boolean isOutputJSON() {
        return outputJSON;
    }

    @Override
    public boolean isColorOutput() {
        return colorOutput;
    }

    @Override
    public ColorConfig getColorConfig() {
        return colorConfig;
    }

    @Override
    public boolean isOutputPaging() {
        return outputPaging;
    }

    static class SslConfig implements SSLConfig {

        private String alias = null;
        private String keyStore = null;
        private String keyStorePassword = null;
        private String keyPassword =null;
        private String trustStore = null;
        private String trustStorePassword = null;
        private boolean modifyTrustStore = true;

        public String getKeyStore() {
            return keyStore;
        }

        void setKeyStore(final String keyStore) {
            this.keyStore = keyStore;
        }

        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        void setKeyStorePassword(final String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
        }

        public String getAlias() {
            return alias;
        }

        void setAlias(final String alias) {
            this.alias = alias;
        }

        public String getKeyPassword() {
            return keyPassword;
        }

        void setKeyPassword(final String keyPassword) {
            this.keyPassword = keyPassword;
        }

        public String getTrustStore() {
            return trustStore;
        }

        void setTrustStore(final String trustStore) {
            this.trustStore = trustStore;
        }

        public String getTrustStorePassword() {
            return trustStorePassword;
        }

        void setTrustStorePassword(final String trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
        }

        public boolean isModifyTrustStore() {
            return modifyTrustStore;
        }

        void setModifyTrustStore(final boolean modifyTrustStore) {
            this.modifyTrustStore = modifyTrustStore;
        }
    }

    static class ColorConfigImpl implements ColorConfig {
        private boolean enabled = false;
        private Color errorColor;
        private Color warnColor;
        private Color successColor;
        private Color requiredColor;
        private Color batchColor;
        private Color promptColor;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public Color getErrorColor() {
            return errorColor;
        }

        public void setErrorColor(Color errorColor) {
            this.errorColor = errorColor;
        }

        @Override
        public Color getWarnColor() {
            return warnColor;
        }

        public void setWarnColor(Color warnColor) {
            this.warnColor = warnColor;
        }

        @Override
        public Color getSuccessColor() {
            return successColor;
        }

        public void setSuccessColor(Color successColor) {
            this.successColor = successColor;
        }

        @Override
        public Color getRequiredColor() {
            return requiredColor;
        }

        public void setBatchColor(Color batchColor) {
            this.batchColor = batchColor;
        }

        @Override
        public Color getWorkflowColor() {
            return batchColor;
        }

        @Override
        public Color getPromptColor() {
            return promptColor;
        }

        public void setPromptColor(Color promptColor) {
            this.promptColor = promptColor;
        }

        public void setRequiredColor(Color requiredColor) {
            this.requiredColor = requiredColor;
        }

        public Color convertColor(String name) throws CliInitializationException {
            switch (name.toLowerCase()) {
                case "black":
                    return Color.BLACK;
                case "red":
                    return Color.RED;
                case "green":
                    return Color.GREEN;
                case "yellow":
                    return Color.YELLOW;
                case "blue":
                    return Color.BLUE;
                case "magenta":
                    return Color.MAGENTA;
                case "cyan":
                    return Color.CYAN;
                case "white":
                    return Color.WHITE;
                case "default":
                    return Color.DEFAULT;
                default:
                    throw new CliInitializationException("Invalid color \"" + name + "\".");
            }
        }
    }

    static class CliConfigReader implements XMLElementReader<CliConfigImpl> {

        public void readElement(XMLExtendedStreamReader reader, CliConfigImpl config) throws XMLStreamException {
            String localName = reader.getLocalName();
            if (JBOSS_CLI.equals(localName) == false) {
                throw new XMLStreamException("Unexpected element: " + localName);
            }

            Namespace readerNS = Namespace.forUri(reader.getNamespaceURI());
            for (Namespace current : Namespace.cliValues()) {
                if (readerNS.equals(current)) {
                    switch (readerNS) {
                        case CLI_1_0:
                            readCLIElement_1_0(reader, readerNS, config);
                            break;
                        case CLI_1_1:
                            readCLIElement_1_1(reader, readerNS, config);
                            break;
                        case CLI_1_2:
                        case CLI_1_3:
                            readCLIElement_1_2(reader, readerNS, config);
                            break;
                        case CLI_2_0:
                            readCLIElement_2_0(reader, readerNS, config);
                            break;
                        case CLI_3_0:
                            readCLIElement_3_0(reader, readerNS, config);
                            break;
                        case CLI_3_1:
                            readCLIElement_3_1(reader, readerNS, config);
                            break;
                        case CLI_3_2:
                            readCLIElement_3_2(reader, readerNS, config);
                            break;
                        case CLI_3_3:
                            readCLIElement_3_3(reader, readerNS, config);
                            break;
                        case CLI_3_4:
                            readCLIElement_3_4(reader, readerNS, config);
                            break;
                        default:
                            readCLIElement_4_0(reader, readerNS, config);
                    }
                    return;
                }
            }
            throw new XMLStreamException("Unexpected element: " + localName);
        }

        public void readCLIElement_1_0(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            boolean jbossCliEnded = false;
            while (reader.hasNext() && jbossCliEnded == false) {
                int tag = reader.nextTag();
                assertExpectedNamespace(reader, expectedNs);
                if(tag == XMLStreamConstants.START_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(DEFAULT_CONTROLLER)) {
                        readDefaultController_1_0(reader, expectedNs, config);
                    } else if (localName.equals(HISTORY)) {
                        readHistory(reader, expectedNs, config);
                    } else if (localName.equals("ssl")) {
                        SslConfig sslConfig = new SslConfig();
                        readSSLElement_1_0(reader, expectedNs, sslConfig);
                        config.sslConfig = sslConfig;
                    } else {
                        throw new XMLStreamException("Unexpected element: " + localName);
                    }
                } else if(tag == XMLStreamConstants.END_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(JBOSS_CLI)) {
                        jbossCliEnded = true;
                    }
                }
            }
        }

        public void readCLIElement_1_1(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            boolean jbossCliEnded = false;
            while (reader.hasNext() && jbossCliEnded == false) {
                int tag = reader.nextTag();
                assertExpectedNamespace(reader, expectedNs);
                if(tag == XMLStreamConstants.START_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(DEFAULT_CONTROLLER)) {
                        readDefaultController_1_0(reader, expectedNs, config);
                    } else if(localName.equals(VALIDATE_OPERATION_REQUESTS)) {
                        config.validateOperationRequests = resolveBoolean(reader.getElementText());
                    } else if(localName.equals(HISTORY)) {
                        readHistory(reader, expectedNs, config);
                    } else if (localName.equals("ssl")) {
                        SslConfig sslConfig = new SslConfig();
                        readSSLElement_1_0(reader, expectedNs, sslConfig);
                        config.sslConfig = sslConfig;
                    } else {
                        throw new XMLStreamException("Unexpected element: " + localName);
                    }
                } else if(tag == XMLStreamConstants.END_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(JBOSS_CLI)) {
                        jbossCliEnded = true;
                    }
                }
            }
        }

        public void readCLIElement_1_2(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            boolean jbossCliEnded = false;
            while (reader.hasNext() && jbossCliEnded == false) {
                int tag = reader.nextTag();
                assertExpectedNamespace(reader, expectedNs);
                if(tag == XMLStreamConstants.START_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(DEFAULT_CONTROLLER)) {
                        readDefaultController_1_0(reader, expectedNs, config);
                    } else if(localName.equals(VALIDATE_OPERATION_REQUESTS)) {
                        config.validateOperationRequests = resolveBoolean(reader.getElementText());
                    } else if(localName.equals(HISTORY)) {
                        readHistory(reader, expectedNs, config);
                    } else if(localName.equals(RESOLVE_PARAMETER_VALUES)) {
                        config.resolveParameterValues = resolveBoolean(reader.getElementText());
                    } else if (CONNECTION_TIMEOUT.equals(localName)) {
                        final String text = reader.getElementText();
                        try {
                            config.connectionTimeout = Integer.parseInt(text);
                        } catch(NumberFormatException e) {
                            throw new XMLStreamException("Failed to parse " + JBOSS_CLI + " " + CONNECTION_TIMEOUT + " value '" + text + "'", e);
                        }
                    } else if (localName.equals("ssl")) {
                        SslConfig sslConfig = new SslConfig();
                        if(expectedNs == Namespace.CLI_1_2) {
                            readSSLElement_1_1(reader, expectedNs, sslConfig);
                        } else {
                            // Namespace.CLI_1_3
                            readSSLElement_3_0(reader, expectedNs, sslConfig);
                        }
                        config.sslConfig = sslConfig;
                    } else if(localName.equals(SILENT)) {
                        config.silent = resolveBoolean(reader.getElementText());
                    } else {
                        throw new XMLStreamException("Unexpected element: " + localName);
                    }
                } else if(tag == XMLStreamConstants.END_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(JBOSS_CLI)) {
                        jbossCliEnded = true;
                    }
                }
            }
        }

        public void readCLIElement_2_0(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            boolean jbossCliEnded = false;
            while (reader.hasNext() && jbossCliEnded == false) {
                int tag = reader.nextTag();
                assertExpectedNamespace(reader, expectedNs);
                if(tag == XMLStreamConstants.START_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(DEFAULT_PROTOCOL)) {
                        readDefaultProtocol_2_0(reader, expectedNs, config);
                    } else if (localName.equals(DEFAULT_CONTROLLER)) {
                        readDefaultController_2_0(reader, expectedNs, config);
                    } else if (localName.equals(CONTROLLERS)) {
                        readControllers_2_0(reader, expectedNs, config);
                    } else if (localName.equals(VALIDATE_OPERATION_REQUESTS)) {
                        config.validateOperationRequests = resolveBoolean(reader.getElementText());
                    } else if(localName.equals(HISTORY)) {
                        readHistory(reader, expectedNs, config);
                    } else if(localName.equals(RESOLVE_PARAMETER_VALUES)) {
                        config.resolveParameterValues = resolveBoolean(reader.getElementText());
                    } else if (CONNECTION_TIMEOUT.equals(localName)) {
                        final String text = reader.getElementText();
                        try {
                            config.connectionTimeout = Integer.parseInt(text);
                        } catch(NumberFormatException e) {
                            throw new XMLStreamException("Failed to parse " + JBOSS_CLI + " " + CONNECTION_TIMEOUT + " value '" + text + "'", e);
                        }
                    } else if (localName.equals("ssl")) {
                        SslConfig sslConfig = new SslConfig();
                        readSSLElement_2_0(reader, expectedNs, sslConfig);
                        config.sslConfig = sslConfig;
                    } else if(localName.equals(SILENT)) {
                        config.silent = resolveBoolean(reader.getElementText());
                    } else if(localName.equals(ACCESS_CONTROL)) {
                        config.accessControl = resolveBoolean(reader.getElementText());
                        logAccessControl(config.accessControl);
                    } else {
                        throw new XMLStreamException("Unexpected element: " + localName);
                    }
                } else if(tag == XMLStreamConstants.END_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(JBOSS_CLI)) {
                        jbossCliEnded = true;
                    }
                }
            }
        }

        public void readCLIElement_3_0(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            boolean jbossCliEnded = false;
            while (reader.hasNext() && jbossCliEnded == false) {
                int tag = reader.nextTag();
                assertExpectedNamespace(reader, expectedNs);
                if(tag == XMLStreamConstants.START_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(DEFAULT_PROTOCOL)) {
                        readDefaultProtocol_2_0(reader, expectedNs, config);
                    } else if (localName.equals(DEFAULT_CONTROLLER)) {
                        readDefaultController_2_0(reader, expectedNs, config);
                    } else if (localName.equals(CONTROLLERS)) {
                        readControllers_2_0(reader, expectedNs, config);
                    } else if (localName.equals(VALIDATE_OPERATION_REQUESTS)) {
                        config.validateOperationRequests = resolveBoolean(reader.getElementText());
                    } else if(localName.equals(HISTORY)) {
                        readHistory(reader, expectedNs, config);
                    } else if(localName.equals(RESOLVE_PARAMETER_VALUES)) {
                        config.resolveParameterValues = resolveBoolean(reader.getElementText());
                    } else if (CONNECTION_TIMEOUT.equals(localName)) {
                        final String text = reader.getElementText();
                        try {
                            config.connectionTimeout = Integer.parseInt(text);
                        } catch(NumberFormatException e) {
                            throw new XMLStreamException("Failed to parse " + JBOSS_CLI + " " + CONNECTION_TIMEOUT + " value '" + text + "'", e);
                        }
                    } else if (localName.equals("ssl")) {
                        SslConfig sslConfig = new SslConfig();
                        readSSLElement_3_0(reader, expectedNs, sslConfig);
                        config.sslConfig = sslConfig;
                    } else if(localName.equals(SILENT)) {
                        config.silent = resolveBoolean(reader.getElementText());
                    } else if(localName.equals(ACCESS_CONTROL)) {
                        config.accessControl = resolveBoolean(reader.getElementText());
                        logAccessControl(config.accessControl);
                    } else {
                        throw new XMLStreamException("Unexpected element: " + localName);
                    }
                } else if(tag == XMLStreamConstants.END_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(JBOSS_CLI)) {
                        jbossCliEnded = true;
                    }
                }
            }
        }

        // Added the echo-command element
        public void readCLIElement_3_1(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            boolean jbossCliEnded = false;
            while (reader.hasNext() && jbossCliEnded == false) {
                int tag = reader.nextTag();
                assertExpectedNamespace(reader, expectedNs);
                if(tag == XMLStreamConstants.START_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(DEFAULT_PROTOCOL)) {
                        readDefaultProtocol_2_0(reader, expectedNs, config);
                    } else if (localName.equals(DEFAULT_CONTROLLER)) {
                        readDefaultController_2_0(reader, expectedNs, config);
                    } else if (localName.equals(CONTROLLERS)) {
                        readControllers_2_0(reader, expectedNs, config);
                    } else if (localName.equals(VALIDATE_OPERATION_REQUESTS)) {
                        config.validateOperationRequests = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(ECHO_COMMAND)) {
                        config.echoCommand = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(COMMAND_TIMEOUT)) {
                        config.commandTimeout = resolveInteger(reader.getElementText());
                    } else if (localName.equals(HISTORY)) {
                        readHistory(reader, expectedNs, config);
                    } else if(localName.equals(RESOLVE_PARAMETER_VALUES)) {
                        config.resolveParameterValues = resolveBoolean(reader.getElementText());
                    } else if (CONNECTION_TIMEOUT.equals(localName)) {
                        final String text = reader.getElementText();
                        try {
                            config.connectionTimeout = Integer.parseInt(text);
                        } catch(NumberFormatException e) {
                            throw new XMLStreamException("Failed to parse " + JBOSS_CLI + " " + CONNECTION_TIMEOUT + " value '" + text + "'", e);
                        }
                    } else if (localName.equals("ssl")) {
                        SslConfig sslConfig = new SslConfig();
                        readSSLElement_3_0(reader, expectedNs, sslConfig);
                        config.sslConfig = sslConfig;
                    } else if(localName.equals(SILENT)) {
                        config.silent = resolveBoolean(reader.getElementText());
                    } else if(localName.equals(ACCESS_CONTROL)) {
                        config.accessControl = resolveBoolean(reader.getElementText());
                        logAccessControl(config.accessControl);
                    } else {
                        throw new XMLStreamException("Unexpected element: " + localName);
                    }
                } else if(tag == XMLStreamConstants.END_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(JBOSS_CLI)) {
                        jbossCliEnded = true;
                    }
                }
            }
        }

        // Added the output-json element
        public void readCLIElement_3_2(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            boolean jbossCliEnded = false;
            while (reader.hasNext() && jbossCliEnded == false) {
                int tag = reader.nextTag();
                assertExpectedNamespace(reader, expectedNs);
                if(tag == XMLStreamConstants.START_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(DEFAULT_PROTOCOL)) {
                        readDefaultProtocol_2_0(reader, expectedNs, config);
                    } else if (localName.equals(DEFAULT_CONTROLLER)) {
                        readDefaultController_2_0(reader, expectedNs, config);
                    } else if (localName.equals(CONTROLLERS)) {
                        readControllers_2_0(reader, expectedNs, config);
                    } else if (localName.equals(VALIDATE_OPERATION_REQUESTS)) {
                        config.validateOperationRequests = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(ECHO_COMMAND)) {
                        config.echoCommand = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(OUTPUT_JSON)) {
                        config.outputJSON = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(COMMAND_TIMEOUT)) {
                        config.commandTimeout = resolveInteger(reader.getElementText());
                    } else if (localName.equals(HISTORY)) {
                        readHistory(reader, expectedNs, config);
                    } else if(localName.equals(RESOLVE_PARAMETER_VALUES)) {
                        config.resolveParameterValues = resolveBoolean(reader.getElementText());
                    } else if (CONNECTION_TIMEOUT.equals(localName)) {
                        final String text = reader.getElementText();
                        try {
                            config.connectionTimeout = Integer.parseInt(text);
                        } catch(NumberFormatException e) {
                            throw new XMLStreamException("Failed to parse " + JBOSS_CLI + " " + CONNECTION_TIMEOUT + " value '" + text + "'", e);
                        }
                    } else if (localName.equals("ssl")) {
                        SslConfig sslConfig = new SslConfig();
                        readSSLElement_3_0(reader, expectedNs, sslConfig);
                        config.sslConfig = sslConfig;
                    } else if(localName.equals(SILENT)) {
                        config.silent = resolveBoolean(reader.getElementText());
                    } else if(localName.equals(ACCESS_CONTROL)) {
                        config.accessControl = resolveBoolean(reader.getElementText());
                        logAccessControl(config.accessControl);
                    } else {
                        throw new XMLStreamException("Unexpected element: " + localName);
                    }
                } else if(tag == XMLStreamConstants.END_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(JBOSS_CLI)) {
                        jbossCliEnded = true;
                    }
                }
            }
        }

        // Added the color-output element
        public void readCLIElement_3_3(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            boolean jbossCliEnded = false;
            while (reader.hasNext() && !jbossCliEnded) {
                int tag = reader.nextTag();
                assertExpectedNamespace(reader, expectedNs);
                if(tag == XMLStreamConstants.START_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(DEFAULT_PROTOCOL)) {
                        readDefaultProtocol_2_0(reader, expectedNs, config);
                    } else if (localName.equals(DEFAULT_CONTROLLER)) {
                        readDefaultController_2_0(reader, expectedNs, config);
                    } else if (localName.equals(CONTROLLERS)) {
                        readControllers_2_0(reader, expectedNs, config);
                    } else if (localName.equals(VALIDATE_OPERATION_REQUESTS)) {
                        config.validateOperationRequests = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(ECHO_COMMAND)) {
                        config.echoCommand = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(OUTPUT_JSON)) {
                        config.outputJSON = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(COLOR_OUTPUT)) {
                        ColorConfigImpl colorConfig = new ColorConfigImpl();
                        readColorElement_3_3(reader, expectedNs, colorConfig);
                        config.colorConfig = colorConfig;
                        config.colorOutput = colorConfig.isEnabled();
                    } else if (localName.equals(COMMAND_TIMEOUT)) {
                        config.commandTimeout = resolveInteger(reader.getElementText());
                    } else if (localName.equals(HISTORY)) {
                        readHistory(reader, expectedNs, config);
                    } else if(localName.equals(RESOLVE_PARAMETER_VALUES)) {
                        config.resolveParameterValues = resolveBoolean(reader.getElementText());
                    } else if (CONNECTION_TIMEOUT.equals(localName)) {
                        final String text = reader.getElementText();
                        try {
                            config.connectionTimeout = Integer.parseInt(text);
                        } catch(NumberFormatException e) {
                            throw new XMLStreamException("Failed to parse " + JBOSS_CLI + " " + CONNECTION_TIMEOUT + " value '" + text + "'", e);
                        }
                    } else if (localName.equals("ssl")) {
                        SslConfig sslConfig = new SslConfig();
                        readSSLElement_3_0(reader, expectedNs, sslConfig);
                        config.sslConfig = sslConfig;
                    } else if(localName.equals(SILENT)) {
                        config.silent = resolveBoolean(reader.getElementText());
                    } else if(localName.equals(ACCESS_CONTROL)) {
                        config.accessControl = resolveBoolean(reader.getElementText());
                        logAccessControl(config.accessControl);
                    } else {
                        throw new XMLStreamException("Unexpected element: " + localName);
                    }
                } else if(tag == XMLStreamConstants.END_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(JBOSS_CLI)) {
                        jbossCliEnded = true;
                    }
                }
            }
        }

        //Added output paging element
        public void readCLIElement_3_4(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            boolean jbossCliEnded = false;
            while (reader.hasNext() && !jbossCliEnded) {
                int tag = reader.nextTag();
                assertExpectedNamespace(reader, expectedNs);
                if(tag == XMLStreamConstants.START_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(DEFAULT_PROTOCOL)) {
                        readDefaultProtocol_2_0(reader, expectedNs, config);
                    } else if (localName.equals(DEFAULT_CONTROLLER)) {
                        readDefaultController_2_0(reader, expectedNs, config);
                    } else if (localName.equals(CONTROLLERS)) {
                        readControllers_2_0(reader, expectedNs, config);
                    } else if (localName.equals(VALIDATE_OPERATION_REQUESTS)) {
                        config.validateOperationRequests = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(ECHO_COMMAND)) {
                        config.echoCommand = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(OUTPUT_JSON)) {
                        config.outputJSON = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(COLOR_OUTPUT)) {
                        ColorConfigImpl colorConfig = new ColorConfigImpl();
                        readColorElement_3_3(reader, expectedNs, colorConfig);
                        config.colorConfig = colorConfig;
                        config.colorOutput = colorConfig.isEnabled();
                    } else if (localName.equals(COMMAND_TIMEOUT)) {
                        config.commandTimeout = resolveInteger(reader.getElementText());
                    } else if (localName.equals(HISTORY)) {
                        readHistory(reader, expectedNs, config);
                    } else if(localName.equals(RESOLVE_PARAMETER_VALUES)) {
                        config.resolveParameterValues = resolveBoolean(reader.getElementText());
                    } else if (CONNECTION_TIMEOUT.equals(localName)) {
                        final String text = reader.getElementText();
                        try {
                            config.connectionTimeout = Integer.parseInt(text);
                        } catch(NumberFormatException e) {
                            throw new XMLStreamException("Failed to parse " + JBOSS_CLI + " " + CONNECTION_TIMEOUT + " value '" + text + "'", e);
                        }
                    } else if (localName.equals("ssl")) {
                        SslConfig sslConfig = new SslConfig();
                        readSSLElement_3_0(reader, expectedNs, sslConfig);
                        config.sslConfig = sslConfig;
                    } else if(localName.equals(SILENT)) {
                        config.silent = resolveBoolean(reader.getElementText());
                    } else if(localName.equals(ACCESS_CONTROL)) {
                        config.accessControl = resolveBoolean(reader.getElementText());
                        logAccessControl(config.accessControl);
                    } else if (localName.equals(OUTPUT_PAGING)) {
                        config.outputPaging = resolveBoolean(reader.getElementText());
                    } else {
                        throw new XMLStreamException("Unexpected element: " + localName);
                    }
                } else if(tag == XMLStreamConstants.END_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(JBOSS_CLI)) {
                        jbossCliEnded = true;
                    }
                }
            }
        }

        void readCLIElement_4_0(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            boolean jbossCliEnded = false;
            while (reader.hasNext() && !jbossCliEnded) {
                int tag = reader.nextTag();
                assertExpectedNamespace(reader, expectedNs);
                if(tag == XMLStreamConstants.START_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(DEFAULT_PROTOCOL)) {
                        readDefaultProtocol_2_0(reader, expectedNs, config);
                    } else if (localName.equals(DEFAULT_CONTROLLER)) {
                        readDefaultController_2_0(reader, expectedNs, config);
                    } else if (localName.equals(CONTROLLERS)) {
                        readControllers_2_0(reader, expectedNs, config);
                    } else if (localName.equals(VALIDATE_OPERATION_REQUESTS)) {
                        config.validateOperationRequests = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(ECHO_COMMAND)) {
                        config.echoCommand = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(OUTPUT_JSON)) {
                        config.outputJSON = resolveBoolean(reader.getElementText());
                    } else if (localName.equals(COLOR_OUTPUT)) {
                        ColorConfigImpl colorConfig = new ColorConfigImpl();
                        readColorElement_3_3(reader, expectedNs, colorConfig);
                        config.colorConfig = colorConfig;
                        config.colorOutput = colorConfig.isEnabled();
                    } else if (localName.equals(COMMAND_TIMEOUT)) {
                        config.commandTimeout = resolveInteger(reader.getElementText());
                    } else if (localName.equals(HISTORY)) {
                        readHistory(reader, expectedNs, config);
                    } else if(localName.equals(RESOLVE_PARAMETER_VALUES)) {
                        config.resolveParameterValues = resolveBoolean(reader.getElementText());
                    } else if (CONNECTION_TIMEOUT.equals(localName)) {
                        final String text = reader.getElementText();
                        try {
                            config.connectionTimeout = Integer.parseInt(text);
                        } catch(NumberFormatException e) {
                            throw new XMLStreamException("Failed to parse " + JBOSS_CLI + " " + CONNECTION_TIMEOUT + " value '" + text + "'", e);
                        }
                    } else if (localName.equals("ssl")) {
                        SslConfig sslConfig = new SslConfig();
                        readSSLElement_3_0(reader, expectedNs, sslConfig);
                        config.sslConfig = sslConfig;
                    } else if(localName.equals(SILENT)) {
                        config.silent = resolveBoolean(reader.getElementText());
                    } else if(localName.equals(ACCESS_CONTROL)) {
                        config.accessControl = resolveBoolean(reader.getElementText());
                        logAccessControl(config.accessControl);
                    } else if (localName.equals(OUTPUT_PAGING)) {
                        config.outputPaging = resolveBoolean(reader.getElementText());
                    } else {
                        throw new XMLStreamException("Unexpected element: " + localName);
                    }
                } else if(tag == XMLStreamConstants.END_ELEMENT) {
                    final String localName = reader.getLocalName();
                    if (localName.equals(JBOSS_CLI)) {
                        jbossCliEnded = true;
                    }
                }
            }
        }

        private void readDefaultProtocol_2_0(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config)
                throws XMLStreamException {
            final int attributes = reader.getAttributeCount();
            for (int i = 0; i < attributes; i++) {
                String namespace = reader.getAttributeNamespace(i);
                String localName = reader.getAttributeLocalName(i);
                String value = reader.getAttributeValue(i);

                if (namespace != null && !namespace.equals("")) {
                    throw new XMLStreamException("Unexpected attribute '" + namespace + ":" + localName + "'",
                            reader.getLocation());
                } else if (localName.equals(USE_LEGACY_OVERRIDE)) {
                    config.useLegacyOverride = Boolean.parseBoolean(value);
                } else {
                    throw new XMLStreamException("Unexpected attribute '" + localName + "'", reader.getLocation());
                }
            }

            final String resolved = resolveString(reader.getElementText());
            if (resolved != null && resolved.length() > 0) {
                config.defaultControllerProtocol = resolved;
            }
        }

        private void readDefaultController_1_0(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            config.defaultController = readController(false, reader, expectedNs);
        }

        private void readDefaultController_2_0(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            config.defaultController = readController(true, reader, expectedNs);
        }

        private ControllerAddress readController(boolean allowProtocol, XMLExtendedStreamReader reader, Namespace expectedNs) throws XMLStreamException {
            String protocol = null;
            String host = null;
            int port = -1;

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                assertExpectedNamespace(reader, expectedNs);
                final String localName = reader.getLocalName();
                final String resolved = resolveString(reader.getElementText());
                if (HOST.equals(localName) && host == null) {
                    host = resolved;
                } else if (PROTOCOL.equals(localName) && protocol == null && allowProtocol) {
                    protocol = resolved;
                } else if (PORT.equals(localName) && port < 0) {
                    try {
                        port = Integer.parseInt(resolved);
                        if (port < 0) {
                            throw new XMLStreamException("Invalid negative port \"" + resolved + "\"");
                        }
                    } catch (NumberFormatException e) {
                        throw new XMLStreamException("Failed to parse " + DEFAULT_CONTROLLER + " " + PORT + " value '"
                                + resolved + "'", e);
                    }
                } else {
                    throw new XMLStreamException("Unexpected child of " + DEFAULT_CONTROLLER + ": " + localName);
                }
            }

            return new ControllerAddress(protocol, host == null ? "localhost" : host, port);
        }

        private void readControllers_2_0(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            Map<String, ControllerAddress> aliasedAddresses = new HashMap<String, ControllerAddress>();
            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                assertExpectedNamespace(reader, expectedNs);
                final String localName = reader.getLocalName();

                if (CONTROLLER.equals(localName)) {
                    String name = null;
                    final int attributes = reader.getAttributeCount();
                    for (int i = 0; i < attributes; i++) {
                        String namespace = reader.getAttributeNamespace(i);
                        String attrLocalName = reader.getAttributeLocalName(i);
                        String value = reader.getAttributeValue(i);

                        if (namespace != null && !namespace.equals("")) {
                            throw new XMLStreamException("Unexpected attribute '" + namespace + ":" + attrLocalName + "'",
                                    reader.getLocation());
                        } else if (attrLocalName.equals(NAME) && name == null) {
                            name = value;
                        } else {
                            throw new XMLStreamException("Unexpected attribute '" + attrLocalName + "'", reader.getLocation());
                        }
                    }

                    if (name == null) {
                        throw new XMLStreamException("Missing required attribute 'name'", reader.getLocation());
                    }
                    aliasedAddresses.put(name, readController(true, reader, expectedNs));
                } else {
                    throw new XMLStreamException("Unexpected child of " + CONTROLLER + ": " + localName);
                }
            }

            config.controllerAliases = Collections.unmodifiableMap(aliasedAddresses);
        }

        private void readHistory(XMLExtendedStreamReader reader, Namespace expectedNs, CliConfigImpl config) throws XMLStreamException {
            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                assertExpectedNamespace(reader, expectedNs);
                final String localName = reader.getLocalName();
                final String resolved = resolveString(reader.getElementText());
                if (ENABLED.equals(localName)) {
                    config.historyEnabled = Boolean.parseBoolean(resolved);
                } else if (FILE_NAME.equals(localName)) {
                    config.historyFileName = resolved;
                } else if (FILE_DIR.equals(localName)) {
                    config.historyFileDir = resolved;
                } else if (MAX_SIZE.equals(localName)) {
                    try {
                        config.historyMaxSize = Integer.parseInt(resolved);
                    } catch(NumberFormatException e) {
                        throw new XMLStreamException("Failed to parse " + HISTORY + " " + MAX_SIZE + " value '" + resolved + "'", e);
                    }
                } else {
                    throw new XMLStreamException("Unexpected child of " + DEFAULT_CONTROLLER + ": " + localName);
                }
            }
        }

        public void readSSLElement_1_0(XMLExtendedStreamReader reader, Namespace expectedNs, SslConfig config) throws XMLStreamException {
            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                assertExpectedNamespace(reader, expectedNs);
                final String localName = reader.getLocalName();
                if ("keyStore".equals(localName)) {
                    config.setKeyStore(reader.getElementText());
                } else if ("keyStorePassword".equals(localName)) {
                    config.setKeyStorePassword(reader.getElementText());
                } else if ("trustStore".equals(localName)) {
                    config.setTrustStore(reader.getElementText());
                } else if ("trustStorePassword".equals(localName)) {
                    config.setTrustStorePassword(reader.getElementText());
                } else if ("modifyTrustStore".equals(localName)) {
                    config.setModifyTrustStore(resolveBoolean(reader.getElementText()));
                } else {
                    throw new XMLStreamException("Unexpected child of ssl : " + localName);
                }
            }
        }

        public void readSSLElement_1_1(XMLExtendedStreamReader reader, Namespace expectedNs, SslConfig config) throws XMLStreamException {
            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                assertExpectedNamespace(reader, expectedNs);
                final String localName = reader.getLocalName();
                /*
                 * The element naming was inconsistent with the rest of the schema so from version 1.1 of the schema we have
                 * switched to the hyphenated form of element names instead of camel case, the original forms are still handled
                 * by the parser but are subject to being removed in subsequent releases.
                 */
                if ("alias".equals(localName)) {
                    config.setAlias(reader.getElementText());
                } else if ("key-store".equals(localName) || "keyStore".equals(localName)) {
                    config.setKeyStore(reader.getElementText());
                } else if ("key-store-password".equals(localName) || "keyStorePassword".equals(localName)) {
                    config.setKeyStorePassword(reader.getElementText());
                } else if ("key-password".equals(localName) || "keyPassword".equals(localName)) {
                    config.setKeyPassword(reader.getElementText());
                } else if ("trust-store".equals(localName) || "trustStore".equals(localName)) {
                    config.setTrustStore(reader.getElementText());
                } else if ("trust-store-password".equals(localName) || "trustStorePassword".equals(localName)) {
                    config.setTrustStorePassword(reader.getElementText());
                } else if ("modify-trust-store".equals(localName) || "modifyTrustStore".equals(localName)) {
                    config.setModifyTrustStore(resolveBoolean(reader.getElementText()));
                } else {
                    throw new XMLStreamException("Unexpected child of ssl : " + localName);
                }
            }
        }

        public void readSSLElement_2_0(XMLExtendedStreamReader reader, Namespace expectedNs, SslConfig config) throws XMLStreamException {

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                assertExpectedNamespace(reader, expectedNs);
                final String localName = reader.getLocalName();

                if("vault".equals(localName)) {
                    throw new XMLStreamException("Vault support has been removed, please remove the <vault /> configuration.");
                } else if ("alias".equals(localName)) {
                    config.setAlias(reader.getElementText());
                } else if ("key-store".equals(localName) || "keyStore".equals(localName)) {
                    config.setKeyStore(reader.getElementText());
                } else if ("key-store-password".equals(localName) || "keyStorePassword".equals(localName)) {
                    config.setKeyStorePassword(getPassword("key-store-password", reader.getElementText()));
                } else if ("key-password".equals(localName) || "keyPassword".equals(localName)) {
                    config.setKeyPassword(getPassword("key-password", reader.getElementText()));
                } else if ("trust-store".equals(localName) || "trustStore".equals(localName)) {
                    config.setTrustStore(reader.getElementText());
                } else if ("trust-store-password".equals(localName) || "trustStorePassword".equals(localName)) {
                    config.setTrustStorePassword(getPassword("trust-store-password", reader.getElementText()));
                } else if ("modify-trust-store".equals(localName) || "modifyTrustStore".equals(localName)) {
                    config.setModifyTrustStore(resolveBoolean(reader.getElementText()));
                } else {
                    throw new XMLStreamException("Unexpected child of ssl : " + localName);
                }
            }
        }

        public void readSSLElement_3_0(XMLExtendedStreamReader reader, Namespace expectedNs, SslConfig config) throws XMLStreamException {

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                assertExpectedNamespace(reader, expectedNs);
                final String localName = reader.getLocalName();

                if("vault".equals(localName)) {
                    throw new XMLStreamException("Vault support has been removed, please remove the <vault /> configuration.");
                } else if ("alias".equals(localName)) {
                    config.setAlias(reader.getElementText());
                } else if ("key-store".equals(localName) || "keyStore".equals(localName)) {
                    config.setKeyStore(reader.getElementText());
                } else if ("key-store-password".equals(localName) || "keyStorePassword".equals(localName)) {
                    config.setKeyStorePassword(getPassword("key-store-password", reader.getElementText()));
                } else if ("key-password".equals(localName) || "keyPassword".equals(localName)) {
                    config.setKeyPassword(getPassword("key-password", reader.getElementText()));
                } else if ("trust-store".equals(localName) || "trustStore".equals(localName)) {
                    config.setTrustStore(reader.getElementText());
                } else if ("trust-store-password".equals(localName) || "trustStorePassword".equals(localName)) {
                    config.setTrustStorePassword(getPassword("trust-store-password", reader.getElementText()));
                } else if ("modify-trust-store".equals(localName) || "modifyTrustStore".equals(localName)) {
                    config.setModifyTrustStore(resolveBoolean(reader.getElementText()));
                } else {
                    throw new XMLStreamException("Unexpected child of ssl : " + localName);
                }
            }
        }

        void readSSLElement_4_0(XMLExtendedStreamReader reader, Namespace expectedNs, SslConfig config) throws XMLStreamException {

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                assertExpectedNamespace(reader, expectedNs);
                final String localName = reader.getLocalName();

                if ("alias".equals(localName)) {
                    config.setAlias(reader.getElementText());
                } else if ("key-store".equals(localName) || "keyStore".equals(localName)) {
                    config.setKeyStore(reader.getElementText());
                } else if ("key-store-password".equals(localName) || "keyStorePassword".equals(localName)) {
                    config.setKeyStorePassword(getPassword("key-store-password", reader.getElementText()));
                } else if ("key-password".equals(localName) || "keyPassword".equals(localName)) {
                    config.setKeyPassword(getPassword("key-password", reader.getElementText()));
                } else if ("trust-store".equals(localName) || "trustStore".equals(localName)) {
                    config.setTrustStore(reader.getElementText());
                } else if ("trust-store-password".equals(localName) || "trustStorePassword".equals(localName)) {
                    config.setTrustStorePassword(getPassword("trust-store-password", reader.getElementText()));
                } else if ("modify-trust-store".equals(localName) || "modifyTrustStore".equals(localName)) {
                    config.setModifyTrustStore(resolveBoolean(reader.getElementText()));
                } else {
                    throw new XMLStreamException("Unexpected child of ssl : " + localName);
                }
            }
        }

        public void readColorElement_3_3(XMLExtendedStreamReader reader, Namespace expectedNs, ColorConfigImpl config)
                throws XMLStreamException {
            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                assertExpectedNamespace(reader, expectedNs);
                final String localName = reader.getLocalName();
                try {
                    if ("enabled".equals(localName)) {
                        config.setEnabled(resolveBoolean(reader.getElementText()));
                    } else if ("error-color".equals(localName)) {
                        config.setErrorColor(config.convertColor(reader.getElementText()));
                    } else if ("warn-color".equals(localName)) {
                        config.setWarnColor(config.convertColor(reader.getElementText()));
                    } else if ("success-color".equals(localName)) {
                        config.setSuccessColor(config.convertColor(reader.getElementText()));
                    } else if ("required-color".equals(localName)) {
                        config.setRequiredColor(config.convertColor(reader.getElementText()));
                    } else if ("workflow-color".equals(localName)) {
                        config.setBatchColor(config.convertColor(reader.getElementText()));
                    } else if ("prompt-color".equals(localName)) {
                        config.setPromptColor(config.convertColor(reader.getElementText()));
                    } else {
                        throw new XMLStreamException("Unexpected child of color-output: " + localName);
                    }
                } catch (CliInitializationException ciex) {
                    throw new XMLStreamException("Error parsing color-output", ciex);
                }
            }
        }

        private String getPassword(String element, String str) throws XMLStreamException {
            if (VAULT_PATTERN.matcher(str).matches()) {
                log.warnf("Unexpected vault expression for '%s'.", element);
            }
            return CLIExpressionResolver.resolveOrOriginal(str);
        }

        static void assertExpectedNamespace(XMLExtendedStreamReader reader, Namespace expectedNs) throws XMLStreamException {
            if (expectedNs.equals(Namespace.forUri(reader.getNamespaceURI())) == false) {
                unexpectedElement(reader);
            }
        }

        static void requireNoContent(final XMLExtendedStreamReader reader) throws XMLStreamException {
            if (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                unexpectedElement(reader);
            }
        }

        static void unexpectedElement(XMLExtendedStreamReader reader) throws XMLStreamException {
            throw new XMLStreamException("Unexpected element " + reader.getName() + " at " + reader.getLocation());
        }

        private static void logAccessControl(boolean accessControl) {
            if(log.isTraceEnabled()) {
                log.trace(ACCESS_CONTROL + " is " + accessControl);
            }
        }
    }
}
