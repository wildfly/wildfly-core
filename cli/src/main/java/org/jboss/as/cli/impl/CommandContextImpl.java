/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.AccessDeniedException;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivilegedAction;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;
import javax.security.sasl.SaslException;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.impl.operator.OutputDelegate;
import org.aesh.command.parser.CommandLineParserException;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.extensions.grep.Grep;
import org.aesh.readline.Prompt;
import org.aesh.terminal.utils.Config;
import org.aesh.readline.util.FileAccessPermission;
import org.jboss.as.cli.Attachments;
import org.jboss.as.cli.CliConfig;
import org.jboss.as.cli.CliEvent;
import org.jboss.as.cli.CliEventListener;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandCompleter;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandHandlerProvider;
import org.jboss.as.cli.CommandHistory;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.CommandLineRedirection;
import org.jboss.as.cli.ConnectionInfo;
import org.jboss.as.cli.ControllerAddress;
import org.jboss.as.cli.ControllerAddressResolver;
import org.jboss.as.cli.OperationCommand;
import org.jboss.as.cli.OperationCommand.HandledRequest;
import org.jboss.as.cli.RequestWithAttachments;
import org.jboss.as.cli.SSLConfig;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.as.cli.batch.impl.DefaultBatchManager;
import org.jboss.as.cli.batch.impl.DefaultBatchedCommand;
import org.jboss.as.cli.embedded.EmbeddedControllerHandlerRegistrar;
import org.jboss.as.cli.embedded.EmbeddedProcessLaunch;
import org.jboss.as.cli.handlers.ArchiveHandler;
import org.jboss.as.cli.handlers.AttachmentHandler;
import org.jboss.as.cli.handlers.ClearScreenHandler;
import org.jboss.as.cli.handlers.CommandCommandHandler;
import org.jboss.as.cli.handlers.CommandTimeoutHandler;
import org.jboss.as.cli.handlers.ConnectionInfoHandler;
import org.jboss.as.cli.handlers.DeployHandler;
import org.jboss.as.cli.handlers.DeploymentInfoHandler;
import org.jboss.as.cli.handlers.DeploymentOverlayHandler;
import org.jboss.as.cli.handlers.EchoDMRHandler;
import org.jboss.as.cli.handlers.EchoVariableHandler;
import org.jboss.as.cli.handlers.GenericTypeOperationHandler;
import org.jboss.as.cli.handlers.HistoryHandler;
import org.jboss.as.cli.handlers.LsHandler;
import org.jboss.as.cli.handlers.OperationRequestHandler;
import org.jboss.as.cli.handlers.PrefixHandler;
import org.jboss.as.cli.handlers.PrintWorkingNodeHandler;
import org.jboss.as.cli.handlers.QuitHandler;
import org.jboss.as.cli.handlers.ReadAttributeHandler;
import org.jboss.as.cli.handlers.ReadOperationHandler;
import org.jboss.as.cli.handlers.ReloadHandler;
import org.jboss.as.cli.handlers.ResponseHandler;
import org.jboss.as.cli.handlers.SetVariableHandler;
import org.jboss.as.cli.handlers.ShutdownHandler;
import org.jboss.as.cli.handlers.UndeployHandler;
import org.jboss.as.cli.handlers.UnsetVariableHandler;
import org.jboss.as.cli.handlers.batch.BatchClearHandler;
import org.jboss.as.cli.handlers.batch.BatchDiscardHandler;
import org.jboss.as.cli.handlers.batch.BatchEditLineHandler;
import org.jboss.as.cli.handlers.batch.BatchHandler;
import org.jboss.as.cli.handlers.batch.BatchHoldbackHandler;
import org.jboss.as.cli.handlers.batch.BatchListHandler;
import org.jboss.as.cli.handlers.batch.BatchMoveLineHandler;
import org.jboss.as.cli.handlers.batch.BatchRemoveLineHandler;
import org.jboss.as.cli.handlers.batch.BatchRunHandler;
import org.jboss.as.cli.handlers.ifelse.ElseHandler;
import org.jboss.as.cli.handlers.ifelse.EndIfHandler;
import org.jboss.as.cli.handlers.ifelse.IfHandler;
import org.jboss.as.cli.handlers.jca.DataSourceAddCompositeHandler;
import org.jboss.as.cli.handlers.jca.JDBCDriverInfoHandler;
import org.jboss.as.cli.handlers.jca.JDBCDriverNameProvider;
import org.jboss.as.cli.handlers.jca.XADataSourceAddCompositeHandler;
import org.jboss.as.cli.handlers.loop.DoneHandler;
import org.jboss.as.cli.handlers.loop.ForHandler;
import org.jboss.as.cli.handlers.module.ASModuleHandler;
import org.jboss.as.cli.handlers.trycatch.CatchHandler;
import org.jboss.as.cli.handlers.trycatch.EndTryHandler;
import org.jboss.as.cli.handlers.trycatch.FinallyHandler;
import org.jboss.as.cli.handlers.trycatch.TryHandler;
import org.jboss.as.cli.impl.CLICommandCompleter.Completer;
import org.jboss.as.cli.impl.ReadlineConsole.Settings;
import org.jboss.as.cli.impl.ReadlineConsole.SettingsBuilder;
import org.jboss.as.cli.impl.aesh.AeshCommands;
import org.jboss.as.cli.impl.aesh.AeshCommands.CLIExecution;
import org.jboss.as.cli.impl.aesh.CLICommandRegistry;
import org.jboss.as.cli.impl.aesh.cmd.ConnectCommand;
import org.jboss.as.cli.impl.aesh.cmd.HelpCommand;
import org.jboss.as.cli.impl.aesh.cmd.VersionCommand;
import org.jboss.as.cli.impl.aesh.cmd.deployment.DeploymentCommand;
import org.jboss.as.cli.impl.aesh.cmd.operation.OperationCommandContainer;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.NodePathFormatter;
import org.jboss.as.cli.operation.OperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationCandidatesProvider;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestParser;
import org.jboss.as.cli.operation.impl.DefaultPrefixFormatter;
import org.jboss.as.cli.operation.impl.RolloutPlanCompleter;
import org.jboss.as.cli.parsing.command.CommandFormat;
import org.jboss.as.cli.parsing.operation.OperationFormat;
import org.jboss.as.cli.util.FingerprintGenerator;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.protocol.GeneralTimeoutHandler;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;
import org.jboss.stdio.StdioContext;
import org.wildfly.common.iteration.CodePointIterator;
import org.wildfly.core.cli.command.BatchCompliantCommand;
import org.wildfly.core.cli.command.DMRCommand;
import org.wildfly.security.OneTimeSecurityFactory;
import org.wildfly.security.SecurityFactory;
import org.wildfly.security.auth.callback.CallbackUtil;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.security.auth.callback.CredentialCallback;
import org.wildfly.security.auth.callback.OptionalNameCallback;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.credential.BearerTokenCredential;
import org.wildfly.security.credential.PasswordCredential;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.wildfly.security.password.interfaces.DigestPassword;
import org.xnio.http.RedirectException;

/**
 *
 * @author Alexey Loubyansky
 */
public class CommandContextImpl implements CommandContext, ModelControllerClientFactory.ConnectionCloseHandler {

    private static final Logger log = Logger.getLogger(CommandContext.class);

    private static final byte RUNNING = 0;
    private static final byte TERMINATING = 1;
    private static final byte TERMINATED = 2;

    /**
     * State Tracking
     *
     * Interact             - Interactive UI
     *
     * Silent               - Only send input. No output.
     * Error On Interact    - If non-interactive mode requests user interaction, throw an error.
     */
    private boolean INTERACT          = false;

    private boolean SILENT            = false;
    private boolean ERROR_ON_INTERACT = false;

    /** the cli configuration */
    private final CliConfig config;
    private final ControllerAddressResolver addressResolver;

    /** command registry */
    private final CLICommandRegistry cmdRegistry;
    /**
     * loads command handlers from the domain management model extensions
     */
    private ExtensionsLoader extLoader;

    private ReadlineConsole console;

    /** whether the session should be terminated */
    private byte terminate;

    /** current command line */
    private String cmdLine;
    /** parsed command arguments */
    private DefaultCallbackHandler parsedCmd = new DefaultCallbackHandler(true);

    /** domain or standalone mode */
    private boolean domainMode;
    /** the controller client */
    private ModelControllerClient client;

    /** the address of the current controller */
    private ControllerAddress currentAddress;
    /** the command line specified username */
    private final String username;
    /** the command line specified password */
    private final char[] password;
    /** flag to disable the local authentication mechanism */
    private final boolean disableLocalAuth;
    /** Flag to indicate that the SSLContext was the default context and not configured */
    private boolean defaultSslContext;
    /** The SSLContext when managed by the CLI */
    private SecurityFactory<SSLContext> sslContextFactory;
    /** The TrustManager in use by the SSLContext, a reference is kept to rejected certificates can be captured. */
    private volatile LazyDelagatingTrustManager trustManager;
    /** various key/value pairs */
    private Map<Scope, Map<String, Object>> map = new HashMap<>();
    /** operation request address prefix */
    private final OperationRequestAddress prefix = new DefaultOperationRequestAddress();
    /** the prefix formatter */
    private final NodePathFormatter prefixFormatter = DefaultPrefixFormatter.INSTANCE;
    /** provider of operation request candidates for tab-completion */
    private final OperationCandidatesProvider operationCandidatesProvider;
    /** operation request handler */
    private final OperationRequestHandler operationHandler;
    /** batches */
    private BatchManager batchManager = new DefaultBatchManager();
    /** the default command completer */
    private final CommandLineCompleter cmdCompleter;
    /** the timeout handler */
    private final GeneralTimeoutHandler timeoutHandler = new GeneralTimeoutHandler();
    /** the client bind address */
    private final String clientBindAddress;

    private List<CliEventListener> listeners = new ArrayList<CliEventListener>();

    /** the value of this variable will be used as the exit code of the vm, it is reset by every command/operation executed */
    private int exitCode;

    private File currentDir = new File("");

    /** whether to resolve system properties passed in as values of operation parameters*/
    private boolean resolveParameterValues;

    private Map<String, String> variables;

    private CliShutdownHook.Handler shutdownHook;

    /** command line handling redirection */
    private CommandLineRedirectionRegistration redirection;

    /** this object saves information to be used in ConnectionInfoHandler */
    private ConnectionInfoBean connInfoBean;

    private final CLIPrintStream cliPrintStream;

    // Store a ref to the default input stream aesh will use before we do any manipulation of stdin
    //private InputStream stdIn = new SettingsBuilder().create().getInputStream();
    private boolean uninstallIO;

    private static JaasConfigurationWrapper jaasConfigurationWrapper; // we want this wrapper to be only created once

    private final boolean echoCommand;

    private static final short DEFAULT_TIMEOUT = 0;
    private int timeout = DEFAULT_TIMEOUT;
    private int configTimeout;
    private ControllerAddress connectionAddress;

    private boolean redefinedOutput;

    private final AeshCommands aeshCommands;
    private CLICommandInvocation invocationContext;
    private final CommandCompleter legacyCmdCompleter;

    private boolean colourOutput;

    private final boolean bootInvoker;
    /**
     * Version mode - only used when --version is called from the command line.
     *
     * @throws CliInitializationException
     */
    CommandContextImpl() throws CliInitializationException {
        bootInvoker = false;
        this.console = null;
        this.operationCandidatesProvider = null;
        this.cmdCompleter = null;
        this.legacyCmdCompleter = null;
        operationHandler = new OperationRequestHandler();
        initStdIO();
        aeshCommands = new AeshCommands(this, new OperationCommandContainer(this));
        cmdRegistry = aeshCommands.getRegistry();
        try {
            initCommands();
        } catch (CommandLineException | CommandLineParserException e) {
            throw new CliInitializationException("Failed to initialize commands", e);
        }
        config = CliConfigImpl.load(this);
        addressResolver = ControllerAddressResolver.newInstance(config, null);
        resolveParameterValues = config.isResolveParameterValues();
        SILENT = config.isSilent();
        ERROR_ON_INTERACT = config.isErrorOnInteract();
        echoCommand = config.isEchoCommand();
        configTimeout = config.getCommandTimeout() == null ? DEFAULT_TIMEOUT : config.getCommandTimeout();
        setCommandTimeout(configTimeout);
        username = null;
        password = null;
        disableLocalAuth = false;
        clientBindAddress = null;
        cliPrintStream = new CLIPrintStream();
        initSSLContext();
        initJaasConfig();

        addShutdownHook();
        CliLauncher.runcom(this);
    }

    /**
     * Default constructor used for both interactive and non-interactive mode.
     *
     */
    CommandContextImpl(CommandContextConfiguration configuration) throws CliInitializationException {
        bootInvoker = false;
        config = CliConfigImpl.load(this, configuration);
        addressResolver = ControllerAddressResolver.newInstance(config, configuration.getController());

        operationHandler = new OperationRequestHandler();

        this.username = configuration.getUsername();
        this.password = configuration.getPassword();
        this.disableLocalAuth = configuration.isDisableLocalAuth();
        this.clientBindAddress = configuration.getClientBindAddress();

        SILENT = config.isSilent();
        ERROR_ON_INTERACT = config.isErrorOnInteract();
        echoCommand = config.isEchoCommand();
        configTimeout = config.getCommandTimeout() == null ? DEFAULT_TIMEOUT : config.getCommandTimeout();
        setCommandTimeout(configTimeout);
        resolveParameterValues = config.isResolveParameterValues();
        redefinedOutput = configuration.getConsoleOutput() != null;
        cliPrintStream = !redefinedOutput ? new CLIPrintStream() : new CLIPrintStream(configuration.getConsoleOutput());
        // System.out has been captured prior IO been replaced. That is required due to embed-server use case
        // that will replace output.
        initStdIO();
        initSSLContext();
        initJaasConfig();
        if (configuration.isInitConsole() || configuration.getConsoleInput() != null) {
            // we don't ask to start the console here because it will start reading the input immediately
            // this will break in case the launching line had input redirection and switch to connect to the controller,
            // e.g. jboss-cli.ch -c < some_file
            // the input will be read before the connection is established
            initBasicConsole(configuration.getConsoleInput(), false);
            aeshCommands = new AeshCommands(this, console, new OperationCommandContainer(this));
            this.cmdRegistry = aeshCommands.getRegistry();
            legacyCmdCompleter = new CommandCompleter(cmdRegistry);
            aeshCommands.setLegacyCommandCompleter(legacyCmdCompleter);
            cmdCompleter = aeshCommands.getCommandCompleter();
            this.operationCandidatesProvider = new DefaultOperationCandidatesProvider();
            if (config.isColorOutput()) {
                colourOutput = true;
                Util.configureColors(this);
            }
        } else {
            aeshCommands = new AeshCommands(this, new OperationCommandContainer(this));
            this.cmdRegistry = aeshCommands.getRegistry();
            this.cmdCompleter = null;
            this.legacyCmdCompleter = null;
            this.operationCandidatesProvider = null;
        }

        try {
            initCommands();
        } catch (CommandLineException | CommandLineParserException e) {
            throw new CliInitializationException("Failed to initialize commands", e);
        }

        addShutdownHook();
        CliLauncher.runcom(this);
    }

    /**
     * Constructor called from Boot invoker, minimal configuration.
     * public for testing purpose.
     *
     */
    public CommandContextImpl(OutputStream output) throws CliInitializationException {
        this(output, true);
    }

    /**
     * Constructor called from Boot invoker, minimal configuration. Used by test.
     * public for testing purpose.
     *
     */
    public CommandContextImpl(OutputStream output, boolean enableEchoCommand) throws CliInitializationException {
        bootInvoker = true;
        config = CliConfigImpl.newBootConfig(enableEchoCommand);
        addressResolver = ControllerAddressResolver.newInstance(config, null);

        operationHandler = new OperationRequestHandler();

        this.username = null;
        this.password = null;
        this.disableLocalAuth = false;
        this.clientBindAddress = null;

        SILENT = config.isSilent();
        ERROR_ON_INTERACT = config.isErrorOnInteract();
        echoCommand = config.isEchoCommand();
        configTimeout = config.getCommandTimeout() == null ? DEFAULT_TIMEOUT : config.getCommandTimeout();
        resolveParameterValues = config.isResolveParameterValues();
        redefinedOutput = output != null;
        cliPrintStream = !redefinedOutput ? new CLIPrintStream() : new CLIPrintStream(output);

        aeshCommands = new AeshCommands(this, new OperationCommandContainer(this));
        this.cmdRegistry = aeshCommands.getRegistry();
        this.cmdCompleter = null;
        this.legacyCmdCompleter = null;
        this.operationCandidatesProvider = null;

        try {
            initCommands(true);
        } catch (CommandLineException | CommandLineParserException e) {
            throw new CliInitializationException("Failed to initialize commands", e);
        }
    }

    protected void addShutdownHook() {
        shutdownHook = new CliShutdownHook.Handler() {
            @Override
            public void shutdown() {
                terminateSession();
            }};
        CliShutdownHook.add(shutdownHook);
    }

    protected void initBasicConsole(InputStream consoleInput) throws CliInitializationException {
        initBasicConsole(consoleInput, true);
    }

    protected void initBasicConsole(InputStream consoleInput, boolean start) throws CliInitializationException {
        // this method shouldn't be called twice during the session
        assert console == null : "the console has already been initialized";
        Settings settings = createSettings(consoleInput);
        try {
            this.console = new ReadlineConsole(settings);
        } catch (IOException ex) {
            throw new CliInitializationException(ex);
        }
        this.console.setActionCallback((line) -> {
            handleSafe(line.trim());
            if (console != null) {
                console.setPrompt(getPrompt());
            }
        });
        if (start) {
            try {
                console.start();
            } catch (IOException ex) {
                throw new CliInitializationException(ex);
            }
        }
    }

    private Settings createSettings(InputStream consoleInput) {
        SettingsBuilder settings = new SettingsBuilder();
        if (consoleInput != null) {
            settings.inputStream(consoleInput);
        }
        settings.outputStream(cliPrintStream);
        settings.outputRedefined(redefinedOutput);
        settings.disableHistory(!config.isHistoryEnabled());
        settings.outputPaging(config.isOutputPaging());
        settings.historyFile(new File(config.getHistoryFileDir(), config.getHistoryFileName()));
        settings.historySize(config.getHistoryMaxSize());

        // Modify Default History File Permissions
        FileAccessPermission permissions = new FileAccessPermission();
        permissions.setReadableOwnerOnly(true);
        permissions.setWritableOwnerOnly(true);
        settings.historyFilePermission(permissions);

        return settings.create();
    }

    private void initStdIO() {
        try {
            StdioContext.install();
            this.uninstallIO = true;
        } catch (IllegalStateException e) {
            this.uninstallIO = false;
        }
    }

    private void restoreStdIO() {
        if (uninstallIO) {
            try {
                StdioContext.uninstall();
            } catch (IllegalStateException ignored) {
                // someone else must have uninstalled
            }
        }
    }

    private void initCommands() throws CommandLineException, CommandLineParserException {
        initCommands(false);
    }

    private void initCommands(boolean bootInvoker) throws CommandLineException, CommandLineParserException {
        // aesh commands
        cmdRegistry.addCommand(new VersionCommand());
        cmdRegistry.addCommand(new HelpCommand(cmdRegistry));
        if (!bootInvoker) {
            cmdRegistry.addCommand(new ConnectCommand());
        }
        DeploymentCommand.registerDeploymentCommands(this, aeshCommands.getRegistry());
        // aesh extensions, for now add grep to make | operator
        // usable.
        cmdRegistry.addThirdPartyCommand(new Grep(), Collections.emptyMap());

        cmdRegistry.registerHandler(new AttachmentHandler(this), "attachment");
        cmdRegistry.registerHandler(new PrefixHandler(), "cd", "cn");
        if (!bootInvoker) {
            cmdRegistry.registerHandler(new ClearScreenHandler(), "clear", "cls");
        }
        cmdRegistry.registerHandler(new CommandCommandHandler(cmdRegistry), "command");
        cmdRegistry.registerHandler(new EchoDMRHandler(), "echo-dmr");
        cmdRegistry.registerHandler(new HistoryHandler(), "history");
        cmdRegistry.registerHandler(new LsHandler(this), "ls");
        cmdRegistry.registerHandler(new ASModuleHandler(this), "module");
        cmdRegistry.registerHandler(new PrintWorkingNodeHandler(), "pwd", "pwn");
        cmdRegistry.registerHandler(new QuitHandler(), "quit", "q", "exit");
        cmdRegistry.registerHandler(new ReadAttributeHandler(this), "read-attribute");
        cmdRegistry.registerHandler(new ReadOperationHandler(this), "read-operation");
        cmdRegistry.registerHandler(new ConnectionInfoHandler(), "connection-info");

        // command-timeout
        cmdRegistry.registerHandler(new CommandTimeoutHandler(), "command-timeout");

        // variables
        cmdRegistry.registerHandler(new SetVariableHandler(), "set");
        cmdRegistry.registerHandler(new EchoVariableHandler(), "echo");
        cmdRegistry.registerHandler(new UnsetVariableHandler(), "unset");

        // deployment
        cmdRegistry.registerHandler(new DeployHandler(this), true, "deploy");
        cmdRegistry.registerHandler(new UndeployHandler(this), true, "undeploy");
        cmdRegistry.registerHandler(new DeploymentInfoHandler(this), true, "deployment-info");
        cmdRegistry.registerHandler(new DeploymentOverlayHandler(this), "deployment-overlay");

        // batch commands
        cmdRegistry.registerHandler(new BatchHandler(this), "batch");
        cmdRegistry.registerHandler(new BatchDiscardHandler(), "discard-batch");
        cmdRegistry.registerHandler(new BatchListHandler(), "list-batch");
        cmdRegistry.registerHandler(new BatchHoldbackHandler(), "holdback-batch");
        cmdRegistry.registerHandler(new BatchRunHandler(this), "run-batch");
        cmdRegistry.registerHandler(new BatchClearHandler(), "clear-batch");
        cmdRegistry.registerHandler(new BatchRemoveLineHandler(), "remove-batch-line");
        cmdRegistry.registerHandler(new BatchMoveLineHandler(), "move-batch-line");
        cmdRegistry.registerHandler(new BatchEditLineHandler(), "edit-batch-line");

        // try-catch
        cmdRegistry.registerHandler(new TryHandler(), "try");
        cmdRegistry.registerHandler(new CatchHandler(), "catch");
        cmdRegistry.registerHandler(new FinallyHandler(), "finally");
        cmdRegistry.registerHandler(new EndTryHandler(), "end-try");

        // if else
        cmdRegistry.registerHandler(new IfHandler(), "if");
        cmdRegistry.registerHandler(new ElseHandler(), "else");
        cmdRegistry.registerHandler(new EndIfHandler(), "end-if");

        // for
        cmdRegistry.registerHandler(new ForHandler(), "for");
        cmdRegistry.registerHandler(new DoneHandler(), "done");

        // data-source
        final DefaultCompleter driverNameCompleter = new DefaultCompleter(JDBCDriverNameProvider.INSTANCE);
        final GenericTypeOperationHandler dsHandler = new GenericTypeOperationHandler(this, "/subsystem=datasources/data-source", null);
        dsHandler.addValueCompleter(Util.DRIVER_NAME, driverNameCompleter);
        // override the add operation with the handler that accepts connection props
        final DataSourceAddCompositeHandler dsAddHandler = new DataSourceAddCompositeHandler(this, "/subsystem=datasources/data-source");
        dsAddHandler.addValueCompleter(Util.DRIVER_NAME, driverNameCompleter);
        dsHandler.addHandler(Util.ADD, dsAddHandler);
        cmdRegistry.registerHandler(dsHandler, dsHandler.getCommandName());
        final GenericTypeOperationHandler xaDsHandler = new GenericTypeOperationHandler(this, "/subsystem=datasources/xa-data-source", null);
        xaDsHandler.addValueCompleter(Util.DRIVER_NAME, driverNameCompleter);
        // override the xa add operation with the handler that accepts xa props
        final XADataSourceAddCompositeHandler xaDsAddHandler = new XADataSourceAddCompositeHandler(this, "/subsystem=datasources/xa-data-source");
        xaDsAddHandler.addValueCompleter(Util.DRIVER_NAME, driverNameCompleter);
        xaDsHandler.addHandler(Util.ADD, xaDsAddHandler);
        cmdRegistry.registerHandler(xaDsHandler, xaDsHandler.getCommandName());
        cmdRegistry.registerHandler(new JDBCDriverInfoHandler(this), "jdbc-driver-info");

        // rollout plan
        final GenericTypeOperationHandler rolloutPlan = new GenericTypeOperationHandler(this, "/management-client-content=rollout-plans/rollout-plan", null);
        rolloutPlan.addValueConverter("content", HeadersArgumentValueConverter.INSTANCE);
        rolloutPlan.addValueCompleter("content", RolloutPlanCompleter.INSTANCE);
        cmdRegistry.registerHandler(rolloutPlan, rolloutPlan.getCommandName());

        // supported but hidden from tab-completion until stable implementation
        cmdRegistry.registerHandler(new ArchiveHandler(this), false, "archive");

        AtomicReference<EmbeddedProcessLaunch> embeddedServerLaunch = null;
        if (!bootInvoker) {
            embeddedServerLaunch = EmbeddedControllerHandlerRegistrar.registerEmbeddedCommands(cmdRegistry, this);
            cmdRegistry.registerHandler(new ReloadHandler(this, embeddedServerLaunch), "reload");
            cmdRegistry.registerHandler(new ShutdownHandler(this, embeddedServerLaunch), "shutdown");
        }

        cmdRegistry.addCommand(new SecurityCommand(this, embeddedServerLaunch));

        if (!bootInvoker) {
            registerExtraHandlers();
        }
        extLoader = new ExtensionsLoader(cmdRegistry, aeshCommands.getRegistry(), this);
    }

    private void registerExtraHandlers() throws CommandLineException, CommandLineParserException {
        ServiceLoader<CommandHandlerProvider> loader = ServiceLoader.load(CommandHandlerProvider.class);
        for (CommandHandlerProvider provider : loader) {
            cmdRegistry.registerHandler(provider.createCommandHandler(this), provider.isTabComplete(), provider.getNames());
        }

        aeshCommands.registerExtraCommands();
    }

    public int getExitCode() {
        return exitCode;
    }

    /**
     * Initialise the SSLContext and associated TrustManager for this CommandContext.
     *
     * If no configuration is specified the default mode of operation will be to use a lazily initialised TrustManager with no
     * KeyManager.
     */
    private void initSSLContext() throws CliInitializationException {
        // If the standard properties have been set don't enable and CLI specific stores.
        if (WildFlySecurityManager.getPropertyPrivileged("javax.net.ssl.keyStore", null) != null
                || WildFlySecurityManager.getPropertyPrivileged("javax.net.ssl.trustStore", null) != null) {
            return;
        }

        this.defaultSslContext = config.getSslConfig() == null;
        sslContextFactory = new OneTimeSecurityFactory<>(this::createSslContext);
    }

    private SSLContext createSslContext() throws GeneralSecurityException {
        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;

        String trustStore = null;
        String trustStorePassword = null;
        boolean modifyTrustStore = true;

        SSLConfig sslConfig = config.getSslConfig();
        if (sslConfig != null) {
            String keyStoreLoc = sslConfig.getKeyStore();
            if (keyStoreLoc != null) {
                char[] keyStorePassword = sslConfig.getKeyStorePassword().toCharArray();
                String tmpKeyPassword = sslConfig.getKeyPassword();
                char[] keyPassword = tmpKeyPassword != null ? tmpKeyPassword.toCharArray() : keyStorePassword;

                File keyStoreFile = new File(keyStoreLoc);

                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(keyStoreFile);
                    KeyStore theKeyStore = KeyStore.getInstance("JKS");
                    theKeyStore.load(fis, keyStorePassword);

                    String alias = sslConfig.getAlias();
                    if (alias != null) {
                        KeyStore replacement = KeyStore.getInstance("JKS");
                        replacement.load(null);
                        KeyStore.ProtectionParameter protection = new KeyStore.PasswordProtection(keyPassword);

                        replacement.setEntry(alias, theKeyStore.getEntry(alias, protection), protection);
                        theKeyStore = replacement;
                    }

                    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    keyManagerFactory.init(theKeyStore, keyPassword);
                    keyManagers = keyManagerFactory.getKeyManagers();
                } catch (IOException e) {
                    throw new GeneralSecurityException(e);
                } finally {
                    StreamUtils.safeClose(fis);
                }
            }

            trustStore = sslConfig.getTrustStore();
            trustStorePassword = sslConfig.getTrustStorePassword();
            modifyTrustStore = sslConfig.isModifyTrustStore();
        }

        if (trustStore == null) {
            final String userHome = WildFlySecurityManager.getPropertyPrivileged("user.home", null);
            File trustStoreFile = new File(userHome, ".jboss-cli.truststore");
            trustStore = trustStoreFile.getAbsolutePath();
            trustStorePassword = "cli_truststore"; // Risk of modification but no private keys to be stored in the truststore.
        }

        trustManager = new LazyDelagatingTrustManager(trustStore, trustStorePassword, modifyTrustStore);
        trustManagers = new TrustManager[] { trustManager };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);

        return sslContext;
    }

    /**
     * The underlying SASL mechanisms may require a JAAS definition, unless a more specific definition as been provided use our
     * own definition for GSSAPI.
     */
    private void initJaasConfig() {
        // create the wrapper only once to avoid memory leak
        if (jaasConfigurationWrapper == null) {
            Configuration coreConfig = null;

            try {
                coreConfig = SecurityActions.getGlobalJaasConfiguration();
            } catch (SecurityException e) {
                log.debug("Unable to obtain default configuration", e);
            }

            jaasConfigurationWrapper = new JaasConfigurationWrapper(coreConfig);
            SecurityActions.setGlobalJaasConfiguration(jaasConfigurationWrapper);
        }
    }

    @Override
    public boolean isTerminated() {
        return terminate == TERMINATED;
    }

    private StringBuilder lineBuffer;
    private StringBuilder origLineBuffer;
    private CommandExecutor executor = new CommandExecutor(this);

    @Override
    public void handle(String line) throws CommandLineException {
        if (line.isEmpty() || line.charAt(0) == '#') {
            return; // ignore comments
        }

        int i = line.length() - 1;
        while(i > 0 && line.charAt(i) <= ' ') {
            if(line.charAt(--i) == '\\') {
                break;
            }
        }
        String echoLine = line;
        if(line.charAt(i) == '\\') {
            if(lineBuffer == null) {
                lineBuffer = new StringBuilder();
                origLineBuffer = new StringBuilder();
            }
            lineBuffer.append(line, 0, i);
            lineBuffer.append(' ');
            origLineBuffer.append(line, 0, i);
            origLineBuffer.append('\n');
            return;
        } else if(lineBuffer != null) {
            lineBuffer.append(line);
            origLineBuffer.append(line);
            echoLine = origLineBuffer.toString();
            line = lineBuffer.toString();
            lineBuffer = null;
        }

        if (echoCommand && !INTERACT && redirection == null) {
            printLine(getPrompt() + echoLine);
        }

        if (!INTERACT) { // special case for builtins and pre-processing.
            if (console == null) {
                initBasicConsole(null, false);
            }
            line = console.handleBuiltins(line);
            if (line == null) {
                return;
            }
        }

        resetArgs(line);
        /**
         * All kind of command can be handled by handleCommand. In order to stay
         * on the safe side (another parsing is applied on top of operations and
         * legacy commands by aesh, we only use the wrapped approach if an
         * operator is present. This could be simplified when we have confidence
         * that aesh parsing doesn't fail for complex corner cases.
         */
        try {
            if (redirection != null) {
                redirection.target.handle(this);
            } else if (parsedCmd.hasOperator()) {
                handleCommand(parsedCmd);
            } else if (parsedCmd.getFormat() == OperationFormat.INSTANCE) {
                handleOperation(parsedCmd);
            } else {
                final String cmdName = parsedCmd.getOperationName();
                CommandHandler handler = cmdRegistry.getCommandHandler(cmdName.toLowerCase());
                if (handler != null) {
                    handleLegacyCommand(line, handler, false);
                } else {
                    handleCommand(parsedCmd);
                }
            }
        } catch (CommandLineException e) {
            throw e;
        } catch (Throwable t) {
            if(log.isDebugEnabled()) {
                log.debugf(t, "Failed to handle '%s'", line);
            }
            throw new CommandLineException("Failed to handle '" + line + "'", t);
        } finally {
            // so that getArgumentsString() doesn't return this line
            // during the tab-completion of the next command
            cmdLine = null;
            invocationContext = null;
            clear(Scope.REQUEST);
        }
    }

    // Method called for if condition and low level operation to be guarded by a timeout.
    @Override
    public ModelNode execute(Operation mn, String description) throws CommandLineException, IOException {
        if (client == null) {
            throw new CommandLineException("The connection to the controller "
                    + "has not been established.");
        }
        try {
            return execute(() -> {
                return executor.execute(mn, timeout, TimeUnit.SECONDS);
            }, description);
        } catch (CommandLineException ex) {
            if (ex.getCause() instanceof IOException) {
                throw (IOException) ex.getCause();
            } else {
                throw ex;
            }
        }
    }

    @Override
    public ModelNode execute(ModelNode mn, String description) throws CommandLineException, IOException {
        OperationBuilder opBuilder = new OperationBuilder(mn, true);
        return execute(opBuilder.build(), description);
    }

    // Single execute method to handle exceptions.
    private <T> T execute(Callable<T> c, String msg) throws CommandLineException {
        try {
            return c.call();
        } catch (IOException ex) {
            throw new CommandLineException("IO exception for " + msg, ex);
        } catch (TimeoutException ex) {
            throw new CommandLineException("Timeout exception for " + msg);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CommandLineException("Interrupt exception for " + msg);
        } catch (ExecutionException ex) {
            // Part of command parsing can occur at execution time.
            if(ex.getCause() instanceof CommandFormatException) {
                 throw new CommandFormatException(ex);
            } else {
                throw new CommandLineException(ex);
            }
        } catch (CommandLineException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CommandLineException("Exception for " + msg, ex);
        }
    }

    public void handleSafe(String line) {
        exitCode = 0;
        try {
            handle(line);
        } catch (Throwable t) {
            // Remove exceptions that are wrappers to not pollute error message.
            if (t instanceof CommandLineException
                    && t.getCause() instanceof ExecutionException) {
                // Get the ExecutionException cause.
                Throwable cause = t.getCause().getCause();
                if (cause != null) {
                    t = cause;
                }
            }
            error(Util.getMessagesFromThrowable(t));
        }
    }

    @Override
    public String getArgumentsString() {
        // a little hack to support tab-completion of commands and ops spread across multiple lines
        if(lineBuffer != null) {
            return lineBuffer.toString();
        }
        if (cmdLine != null && parsedCmd.getOperationName() != null) {
            int cmdNameLength = parsedCmd.getOperationName().length();
            if (cmdLine.length() == cmdNameLength) {
                return null;
            } else {
                return cmdLine.substring(cmdNameLength + 1);
            }
        }
        return null;
    }

    @Override
    public void terminateSession() {
        if(terminate == RUNNING) {
            clear(Scope.CONTEXT);
            clear(Scope.REQUEST);
            terminate = TERMINATING;
            disconnectController();
            restoreStdIO();
            if(console != null) {
                console.stop();
            }
            if (shutdownHook != null) {
                CliShutdownHook.remove(shutdownHook);
            }
            executor.cancel();
            terminate = TERMINATED;
        }
    }

    // Only collect when called directly on context.
    public void print(String message, boolean newLine, boolean collect) {
        if (message == null) {
            return;
        }
        final Level logLevel;
        if (exitCode != 0) {
            logLevel = Level.ERROR;
        } else {
            logLevel = Level.INFO;
        }
        if (log.isEnabled(logLevel)) {
            log.log(logLevel, message);
        }

        // Could be a redirection at the aesh command or operation level
        if (invocationContext != null && invocationContext.getConfiguration().getOutputRedirection() != null) {
            OutputDelegate output = invocationContext.getConfiguration().getOutputRedirection();
            output.write(message);
            if (newLine) {
                output.write(Config.getLineSeparator());
            }
            return;
        }

        if (!SILENT) {
            if (console != null) {
                console.print(message, collect);
                if (newLine) {
                    console.printNewLine(collect);
                }
            } else { // non-interactive mode
                cliPrintStream.println(message);
            }
        }
    }

    @Override
    public void printDMR(ModelNode node, boolean compact) {
        if (getConfig().isOutputJSON() && isColorOutput()) {
            printLine(node.toJSONString(compact), node.get("outcome").asString());
        } else if (getConfig().isOutputJSON()) {
            printLine(node.toJSONString(compact));
        } else if (isColorOutput()) {
            if(compact) {
                printLine(Util.compactToString(node), node.get("outcome").asString());
            } else {
                printLine(node.toString(), node.get("outcome").asString());
            }
        } else {
            if(compact) {
                printLine(Util.compactToString(node));
            } else {
                printLine(node.toString());
            }
        }
    }

    private String colorizeMessage(String message, String outcome) {
        if (outcome != null && !outcome.isEmpty()) {
            switch (outcome) {
                case "success":
                    return Util.formatSuccessMessage(message);
                case "fail":
                case "failed":
                case "failure":
                case "error":
                    return Util.formatErrorMessage(message);
                case "cancelled":
                case "warn":
                case "warning":
                    return Util.formatWarnMessage(message);
            }
        }

        return message;
    }

    public final boolean isColorOutput() {
        return colourOutput;
    }

    @Override
    public void printLine(String message) {
        if (isColorOutput()) {
            message = colorizeMessage(message, "");
        }

        print(message, true, true);
    }

    public void printLine(String message, String outcome) {
        if (isColorOutput()) {
            message = colorizeMessage(message, outcome);
        }

        print(message, true, true);
    }

    @Override
    public void print(String message) {
        if (isColorOutput()) {
            message = colorizeMessage(message, "");
        }

        print(message, false, true);
    }

    /**
     * Set the exit code of the process to indicate an error and output the error message.
     *
     * WARNING This method should only be called for unrecoverable errors as once the exit code is set subsequent operations may
     * not be possible.
     *
     * @param message The message to display.
     */
    protected void error(String message) {
        this.exitCode = 1;
        printLine(message, "error");
    }

    // Aesh input API expects InterruptedException so is implemented with input methods
    // That is done in the Shell implementation (CLICommandInvocationBuilder)
    public String input(String prompt, boolean password) throws CommandLineException, InterruptedException, IOException {
        Prompt pr;
        if (password) {
            pr = new Prompt(prompt, (char) 0x00);
        } else {
            pr = new Prompt(prompt);
        }
        return input(pr);
    }

    // Internal prompting doesn't expect InterruptedException.
    private String readLine(String prompt, boolean password) throws CommandLineException, IOException {
        try {
            return input(prompt, password);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CommandLineException(ex);
        }
    }

    // Aesh input API expects InterruptedException so is implemented with input methods
    // That is done in the Shell implementation (CLICommandInvocationBuilder)
    public String input(Prompt prompt) throws CommandLineException, InterruptedException, IOException {
        // Only fail an interact if we're not in interactive.
        if (!INTERACT && ERROR_ON_INTERACT) {
            interactionDisabled();
        }

        if (console == null) {
            initBasicConsole(null, false);
        }

        return console.readLine(prompt);
    }

    // Aesh input API expects InterruptedException so is implemented with input methods
    // That is done in the Shell implementation (CLICommandInvocationBuilder)
    public int[] input() throws CommandLineException, InterruptedException, IOException {
        // Only fail on interact if we're not in interactive.
        if (!INTERACT && ERROR_ON_INTERACT) {
            interactionDisabled();
        }

        if (console == null) {
            initBasicConsole(null, false);
        }

        return console.read();
    }

    protected void interactionDisabled() throws CommandLineException {
        throw new CommandLineException("Invalid Usage. Prompt attempted in non-interactive mode. Please check commands or change CLI mode.");
    }


    @Override
    public void printColumns(Collection<String> col) {
        if (col == null) {
            return;
        }
        if (log.isInfoEnabled()) {
            log.info(col);
        }
        String columns = null;
        if (!SILENT) {
            if (console != null) {
                columns = console.formatColumns(col);
            }
        }
        if (columns == null) {
            for (String s : col) {
                print(s, true, true);
            }
        } else {
            print(columns, false, true);
        }
    }

    @Override
    public void set(Scope scope, String key, Object value) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(key);
        Map<String, Object> store = map.get(scope);
        if (store == null) {
            store = new HashMap<>();
            map.put(scope, store);
        }
        store.put(key, value);
    }

    @Override
    public Object get(Scope scope, String key) {
        Objects.requireNonNull(scope);
        Objects.requireNonNull(key);
        Map<String, Object> store = map.get(scope);
        Object value = null;
        if (store != null) {
            value = store.get(key);
        }
        return value;
    }

    @Override
    public void clear(Scope scope) {
        Objects.requireNonNull(scope);
        Map<String, Object> store = map.remove(scope);
        if (store != null) {
            store.clear();
        }
    }

    @Override
    public Object remove(Scope scope, String key) {
        Objects.requireNonNull(scope);
        Map<String, Object> store = map.get(scope);
        Object value = null;
        if (store != null) {
            value = store.remove(key);
        }
        return value;
    }

    @Override
    public ModelControllerClient getModelControllerClient() {
        return client;
    }

    @Override
    public CommandLineParser getCommandLineParser() {
        return new DefaultOperationRequestParser(this);
    }

    @Override
    public OperationRequestAddress getCurrentNodePath() {
        return prefix;
    }

    @Override
    public NodePathFormatter getNodePathFormatter() {

        return prefixFormatter;
    }

    @Override
    public OperationCandidatesProvider getOperationCandidatesProvider() {
        return operationCandidatesProvider;
    }

    @Override
    public void connectController() throws CommandLineException {
        connectController(null);
    }

    @Override
    public void connectController(String controller) throws CommandLineException {
        connectController(controller, null);
    }

    @Override
    public void connectController(String controller, String clientAddress) throws CommandLineException {

        connectionAddress = addressResolver.resolveAddress(controller);

        // In case the alias mappings cause us to enter some form of loop or a badly
        // configured server does the same,
        Set<ControllerAddress> visited = new HashSet<ControllerAddress>();
        visited.add(connectionAddress);
        boolean retry = false;
        do {
            try {
                CallbackHandler cbh = new AuthenticationCallbackHandler(username, password);
                if (log.isDebugEnabled()) {
                    log.debugf("connecting to %s:%d as %s", connectionAddress.getHost(), connectionAddress.getPort(), username);
                }
                ModelControllerClient tempClient = ModelControllerClientFactory.CUSTOM.getClient(connectionAddress, cbh,
                        disableLocalAuth, sslContextFactory, defaultSslContext, config.getConnectionTimeout(), this, timeoutHandler, clientAddress == null ? clientBindAddress : clientAddress);
                retry = false;
                connInfoBean = new ConnectionInfoBean();
                tryConnection(tempClient, connectionAddress);
                initNewClient(tempClient, connectionAddress, connInfoBean);
                connInfoBean.setDisableLocalAuth(disableLocalAuth);
                connInfoBean.setLoggedSince(new Date());
            } catch (RedirectException re) {
                try {
                    URI location = new URI(re.getLocation());
                    if (Util.isHttpsRedirect(re, connectionAddress.getProtocol())) {
                        int port = location.getPort();
                        if (port < 0) {
                            port = 443;
                        }
                        connectionAddress = addressResolver.resolveAddress(new URI("remote+https", null, location.getHost(), port,
                                null, null, null).toString());
                        if (visited.add(connectionAddress) == false) {
                            throw new CommandLineException("Redirect to address already tried encountered Address="
                                    + connectionAddress.toString());
                        }
                        retry = true;
                    } else if (connectionAddress.getHost().equals(location.getHost()) && connectionAddress.getPort() == location.getPort()
                            && location.getPath() != null && location.getPath().length() > 1) {
                        throw new CommandLineException("Server at " + connectionAddress.getHost() + ":" + connectionAddress.getPort()
                                + " does not support " + connectionAddress.getProtocol());
                    } else {
                        throw new CommandLineException("Unsupported redirect received.", re);
                    }
                } catch (URISyntaxException e) {
                    throw new CommandLineException("Bad redirect location '" + re.getLocation() + "' received.", e);
                }
            } catch (IOException e) {
                throw new CommandLineException("Failed to resolve host '" + connectionAddress.getHost() + "'", e);
            }
        } while (retry);
    }

    @Override
    @Deprecated
    public void connectController(String host, int port) throws CommandLineException {
        try {
            connectController(new URI(null, null, host, port, null, null, null).toString().substring(2));
        } catch (URISyntaxException e) {
            throw new CommandLineException("Unable to construct URI for connection.", e);
        }
    }

    @Override
    public void bindClient(ModelControllerClient newClient) {
        ConnectionInfoBean conInfo = new ConnectionInfoBean();
        conInfo.setLoggedSince(new Date());
        initNewClient(newClient, null, conInfo);
    }

    private void initNewClient(ModelControllerClient newClient, ControllerAddress address, ConnectionInfoBean conInfo) {
        if (newClient != null) {
            if (this.client != null) {
                disconnectController();
            }

            client = newClient;
            this.currentAddress = address;
            this.connInfoBean = conInfo;
            if (connInfoBean != null) {
                this.connInfoBean.setControllerAddress(address);
            }
            if (!bootInvoker) {
                List<String> nodeTypes = Util.getNodeTypes(newClient, new DefaultOperationRequestAddress());
                // this is present even if the host hasn't been added yet.
                domainMode = nodeTypes.contains(Util.HOST);

                // if we're going to add a host manually, don't try to read the extensions,
                // as they won't be there until after the /host:add()
                if (!(nodeTypes.size() == 1 && nodeTypes.get(0).equals(Util.HOST))) {
                    try {
                        extLoader.loadHandlers(currentAddress);
                    } catch (CommandLineException | CommandLineParserException e) {
                        printLine(Util.getMessagesFromThrowable(e));
                    }
                }
            } else {
                domainMode = false;
            }
        }
    }

    @Override
    public File getCurrentDir() {
        return currentDir;
    }

    @Override
    public void setCurrentDir(File dir) {
        if(dir == null) {
            throw new IllegalArgumentException("dir is null");
        }
        this.currentDir = dir;
    }

    @Override
    public void registerRedirection(CommandLineRedirection redirection) throws CommandLineException {
        if(this.redirection != null) {
            throw new CommandLineException("Another redirection is currently active.");
        }
        this.redirection = new CommandLineRedirectionRegistration(redirection);
        redirection.set(this.redirection);
    }

    /**
     * Handle the last SSL failure, prompting the user to accept or reject the certificate of the remote server.
     *
     * @return true if the certificate validation should be retried.
     */
    private void handleSSLFailure(Certificate[] lastChain) throws CommandLineException, IOException {
        printLine("Unable to connect due to unrecognised server certificate");
        for (Certificate current : lastChain) {
            if (current instanceof X509Certificate) {
                X509Certificate x509Current = (X509Certificate) current;
                Map<String, String> fingerprints = FingerprintGenerator.generateFingerprints(x509Current);
                printLine("Subject    - " + x509Current.getSubjectX500Principal().getName());
                printLine("Issuer     - " + x509Current.getIssuerDN().getName());
                printLine("Valid From - " + x509Current.getNotBefore());
                printLine("Valid To   - " + x509Current.getNotAfter());
                for (String alg : fingerprints.keySet()) {
                    printLine(alg + " : " + fingerprints.get(alg));
                }
                printLine("");
            }
        }

        if (trustManager == null) {
            return;
        }

        for (;;) {
            String response;
            if (trustManager.isModifyTrustStore()) {
                response = readLine("Accept certificate? [N]o, [T]emporarily, [P]ermanently : ", false);
            } else {
                response = readLine("Accept certificate? [N]o, [T]emporarily : ", false);
            }
            if (response == null)
                break;
            else if (response.length() == 1) {
                switch (response.toLowerCase(Locale.ENGLISH).charAt(0)) {
                    case 'n':
                        return;
                    case 't':
                        trustManager.storeChainTemporarily(lastChain);
                        return;
                    case 'p':
                        if (trustManager.isModifyTrustStore()) {
                            trustManager.storeChainPermenantly(lastChain);
                            return;
                        }
                }
            }
        }
    }

    /**
     * Used to make a call to the server to verify that it is possible to connect.
     */
    private void tryConnection(final ModelControllerClient client, ControllerAddress address) throws CommandLineException, RedirectException {
        try {
            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            builder.setOperationName(Util.READ_ATTRIBUTE);
            builder.addProperty(Util.NAME, Util.NAME);

            final long start = System.currentTimeMillis();
            final long timeoutMillis = config.getConnectionTimeout() + 1000;
            boolean tryConnection = true;
            while (tryConnection) {
                final ModelNode response = client.execute(builder.buildRequest());
                if (!Util.isSuccess(response)) {
                    // here we check whether the error is related to the access control permissions
                    // WFLYCTL0332: Permission denied
                    // WFLYCTL0313: Unauthorized to execute operation
                    final String failure = Util.getFailureDescription(response);
                    if (failure.contains("WFLYCTL0332")) {
                        StreamUtils.safeClose(client);
                        throw new CommandLineException(
                                "Connection refused based on the insufficient user permissions."
                                        + " Please, make sure the security-realm attribute is specified for the relevant management interface"
                                        + " (standalone.xml/host.xml) and review the access-control configuration (standalone.xml/domain.xml).");
                    } else if (failure.contains("WFLYCTL0379")) { // system boot is in process
                        if (System.currentTimeMillis() - start > timeoutMillis) {
                            throw new CommandLineException("Timeout waiting for the system to boot.");
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            disconnectController();
                            throw new CommandLineException("Interrupted while pausing before trying connection.", e);
                        }
                    } else {
                        // Otherwise, on one hand, we don't actually care what the response is,
                        // we just want to be sure the ModelControllerClient does not throw an Exception.
                        // On the other hand, reading name attribute is a very basic one which should always work
                        printLine("Warning! The connection check resulted in failure: " + Util.getFailureDescription(response));
                        tryConnection = false;
                    }
                } else {
                    tryConnection = false;
                }
            }
        } catch (Exception e) {
            try {
                Throwable current = e;
                while (current != null) {
                    if (current instanceof SaslException) {
                        throw new CommandLineException("Unable to authenticate against controller at " + address.getHost() + ":" + address.getPort(), current);
                    }
                    if (current instanceof SSLException) {
                        throw new CommandLineException("Unable to negotiate SSL connection with controller at "+ address.getHost() + ":" + address.getPort());
                    }
                    if (current instanceof RedirectException) {
                        throw (RedirectException) current;
                    }
                    if (current instanceof CommandLineException) {
                        throw (CommandLineException) current;
                    }
                    current = current.getCause();
                }

                // We don't know what happened, most likely a timeout.
                throw new CommandLineException("The controller is not available at " + address.getHost() + ":" + address.getPort(), e);
            } finally {
                StreamUtils.safeClose(client);
            }
        }
    }

    @Override
    public void disconnectController() {
        if (this.client != null) {
            // Closed by caller
            if (!bootInvoker) {
                StreamUtils.safeClose(client);
            }
            // if(loggingEnabled) {
            // printLine("Closed connection to " + this.controllerHost + ':' +
            // this.controllerPort);
            // }
            client = null;
            this.currentAddress = null;
            domainMode = false;
            notifyListeners(CliEvent.DISCONNECTED);
            connInfoBean = null;
            if (extLoader != null) {
                extLoader.resetHandlers();
            }
        }
        promptConnectPart = null;
        if(console != null && terminate == RUNNING) {
            console.setPrompt(getPrompt());
        }
    }

    @Override
    @Deprecated
    public String getDefaultControllerHost() {
        return config.getDefaultControllerHost();
    }

    @Override
    @Deprecated
    public int getDefaultControllerPort() {
        return config.getDefaultControllerPort();
    }

    @Override
    public ControllerAddress getDefaultControllerAddress() {
        return config.getDefaultControllerAddress();
    }

    @Override
    public String getControllerHost() {
        return currentAddress != null ? currentAddress.getHost() : null;
    }

    @Override
    public int getControllerPort() {
        return currentAddress != null ? currentAddress.getPort() : -1;
    }

    @Override
    public void clearScreen() {
        if(console != null) {
            console.clearScreen();
        }
    }

    String promptConnectPart;

    String getPrompt() {
        if(lineBuffer != null) {
            return "> ";
        }
        StringBuilder buffer = new StringBuilder();
        if (promptConnectPart == null) {
            buffer.append('[');
            String controllerHost = getControllerHost();
            if (client != null) {
                if (domainMode) {
                    buffer.append("domain@");
                } else {
                    buffer.append("standalone@");
                }
                if (controllerHost != null) {
                    buffer.append(controllerHost).append(':').append(getControllerPort()).append(' ');
                } else {
                    buffer.append("embedded ");
                }
                promptConnectPart = buffer.toString();
            } else {
                buffer.append("disconnected ");
            }
        } else {
            buffer.append(promptConnectPart);
        }

        if (isColorOutput()) {
            Util.formatPrompt(buffer);
        }

        if (prefix.isEmpty()) {
            buffer.append('/');
        } else {
            buffer.append(prefix.getNodeType());
            final String nodeName = prefix.getNodeName();
            if (nodeName != null) {
                buffer.append('=').append(nodeName);
            }
        }

        if (isBatchMode()) {
            if (isColorOutput()) {
                buffer.append(Util.formatWorkflowPrompt(" #"));
            } else {
                buffer.append(" #");
            }
        }

        if (isWorkflowMode()) {
            if (isColorOutput()) {
                buffer.append(Util.formatWorkflowPrompt(" ..."));
            } else {
                buffer.append(" ...");
            }
        }

        buffer.append("] ");
        return buffer.toString();
    }

    @Override
    public CommandHistory getHistory() {
        if(console == null) {
            try {
                initBasicConsole(null, INTERACT);
            } catch (CliInitializationException e) {
                throw new IllegalStateException("Failed to initialize console.", e);
            }
        }
        return console.getHistory();
    }

    private void resetArgs(String cmdLine) throws CommandFormatException {
        if (cmdLine != null) {
            parsedCmd.parse(prefix, cmdLine, this, redirection != null);
        }
        this.cmdLine = cmdLine;
    }

    @Override
    public boolean isBatchMode() {
        return batchManager.isBatchActive();
    }

    @Override
    public boolean isWorkflowMode() {
        return redirection != null;
    }

    @Override
    public BatchManager getBatchManager() {
        return batchManager;
    }

    @Override
    public BatchedCommand toBatchedCommand(String line) throws CommandFormatException {
        HandledRequest req = buildRequest(line, true);
        return new DefaultBatchedCommand(this, line, req.getRequest(), req.getResponseHandler());
    }

    @Override
    public ModelNode buildRequest(String line) throws CommandFormatException {
        return buildRequest(line, false).getRequest();
    }

    protected HandledRequest buildRequest(String line, boolean batchMode) throws CommandFormatException {

        if (line == null || line.isEmpty()) {
            throw new OperationFormatException("The line is null or empty.");
        }

        final DefaultCallbackHandler originalParsedArguments = this.parsedCmd;
        final String originalCmdLine = this.cmdLine;
        try {
            this.parsedCmd = new DefaultCallbackHandler();
            resetArgs(line);

            if (parsedCmd.getFormat() == OperationFormat.INSTANCE) {
                final ModelNode request = this.parsedCmd.toOperationRequest(this);
                StringBuilder op = new StringBuilder();
                op.append(prefixFormatter.format(parsedCmd.getAddress()));
                op.append(line.substring(line.indexOf(':')));
                return new HandledRequest(request, null);
            }

            final CommandHandler handler = cmdRegistry.getCommandHandler(parsedCmd.getOperationName());
            if (handler != null) {
                if (batchMode) {
                    if (!handler.isBatchMode(this)) {
                        throw new OperationFormatException("The command is not allowed in a batch.");
                    }
                    Batch batch = getBatchManager().getActiveBatch();
                    return ((OperationCommand) handler).buildHandledRequest(this, batch.getAttachments());
                } else if (!(handler instanceof OperationCommand)) {
                    throw new OperationFormatException("The command does not translate to an operation request.");
                }

                return new HandledRequest(((OperationCommand) handler).buildRequest(this), null);
            } else {
                return buildAeshCommandRequest(parsedCmd, batchMode);
            }
        } finally {
            clear(Scope.REQUEST);
            this.parsedCmd = originalParsedArguments;
            this.cmdLine = originalCmdLine;
        }
    }

    public OperationCommand.HandledRequest buildAeshCommandRequest(ParsedCommandLine parsedCmd, boolean batchMode) throws CommandFormatException {
        AeshCommands.CLIExecution execution = null;
        try {
            execution = aeshCommands.newExecutions(parsedCmd).get(0);
            // We are not going to execute the command, it must be populated explicitly
            // to have options injected in Command instance.
            execution.populateCommand();
        } catch (CommandLineParserException | OptionValidatorException | IOException ex) {
            throw new CommandFormatException(ex);
        } catch (CommandNotFoundException ex) {
            throw new OperationFormatException("No command handler for '" + parsedCmd.getOperationName() + "'.");
        }
        BatchCompliantCommand bc = execution.getBatchCompliant();
        if (batchMode) {
            if (bc == null) {
                throw new OperationFormatException("The command is not allowed in a batch.");
            }
            Batch batch = getBatchManager().getActiveBatch();
            return new OperationCommand.HandledRequest(bc.buildRequest(this, batch.getAttachments()), null);
        } else {
            DMRCommand dmr = execution.getDMRCompliant();
            if (dmr == null) {
                throw new OperationFormatException("The command does not translate to an operation request.");
            }
            return new OperationCommand.HandledRequest(dmr.buildRequest(this), null);
        }
    }

    private void handleCommand(ParsedCommandLine parsed) throws CommandFormatException, CommandLineException {
        String line = parsed.getOriginalLine();
        try {
            List<CLIExecution> executions = aeshCommands.newExecutions(parsed);
            for (CLIExecution exec : executions) {
                CLICommandInvocation invContext = exec.getInvocation();
                this.invocationContext = invContext;
                String opLine = exec.getLine();
                // change ctx parsedCmd to this piece only if not
                // identical (operator in the main command that
                // implies a split of the main command)
                if (opLine != null && !opLine.equals(line)) {
                    resetArgs(opLine);
                }
                try {
                    // Could be an operation.
                    if (exec.isOperation()) {
                        handleOperation(parsedCmd);
                        continue;
                    }
                    // Could be a legacy command.
                    CommandHandler handler = exec.getLegacyHandler();
                    if (handler != null) {
                        handleLegacyCommand(exec.getLine(), handler, false);
                        continue;
                    }
                } finally {
                    // We must close any output redirection, that is automaticaly done
                    // when calling exec.execute something that we are not doing here.
                    if (invContext.getConfiguration().getOutputRedirection() != null) {
                        try {
                            invContext.getConfiguration().getOutputRedirection().close();
                        } catch (IOException ex) {
                            // Message must contain the Exception and the localized message.
                            if (ex instanceof AccessDeniedException) {
                                String message = ex.getMessage();
                                throw new CommandLineException((message != null ? message : line) + " (Access denied)");
                            }
                            throw new CommandLineException(ex.toString());
                        }
                    }
                }
                // Needed to have the command be fully parsed and retrieve the
                // child command. This is caused by aesh 2.0 behavior.
                if (isBatchMode()) {
                    exec.populateCommand();
                }
                BatchCompliantCommand bc = exec.getBatchCompliant();
                if (isBatchMode() && bc != null) {
                    try {
                        Batch batch = getBatchManager().getActiveBatch();
                        BatchCompliantCommand.BatchResponseHandler request = bc.buildBatchResponseHandler(this,
                                batch.getAttachments());

                        // Wrap into legacy API.
                        ResponseHandler rh = null;
                        if (request != null) {
                            rh = (ModelNode step, OperationResponse response) -> {
                                request.handleResponse(step, response);
                            };
                        }
                        BatchedCommand batchedCmd
                                = new DefaultBatchedCommand(this, line,
                                        bc.buildRequest(this, batch.getAttachments()), rh);
                        batch.add(batchedCmd);
                    } catch (CommandFormatException e) {
                        throw new CommandFormatException("Failed to add to batch '" + line + "'", e);
                    }
                } else {
                    execute(() -> {
                        executor.execute(aeshCommands.newExecutableBuilder(exec),
                                timeout, TimeUnit.SECONDS);
                        return null;
                    }, line);
                }
            }
        } catch (CommandNotFoundException ex) {
            // Deprecated commands for backward compat.
            // Commands that are not exposed in completion.
            if (parsedCmd.getFormat() != OperationFormat.INSTANCE) {
                CommandHandler h = cmdRegistry.getCommandHandler(ex.getCommandName().toLowerCase());
                if (h != null) {
                    handleLegacyCommand(line, h, false);
                    return;
                }
            }
            throw new CommandLineException("Unexpected command '" + line + "'. Type 'help --commands' for the list of supported commands.");
        } catch (IOException ex) {
            throw new CommandLineException(ex);
        } catch (CommandLineParserException | OptionValidatorException ex) {
            throw new CommandFormatException(ex);
        }
    }

    private void handleOperation(ParsedCommandLine parsedLine) throws CommandFormatException, CommandLineException {
        if (isBatchMode()) {
            String line = parsedLine.getOriginalLine();
            Batch batch = getBatchManager().getActiveBatch();
            final ModelNode request = Util.toOperationRequest(this,
                    parsedLine, batch.getAttachments());
            StringBuilder op = new StringBuilder();
            op.append(getNodePathFormatter().format(parsedLine.getAddress()));
            op.append(line.substring(line.indexOf(':')));
            DefaultBatchedCommand batchedCmd
                    = new DefaultBatchedCommand(this, op.toString(), request, null);
            batch.add(batchedCmd);
        } else {
            Attachments attachments = new Attachments();
            final ModelNode op = Util.toOperationRequest(CommandContextImpl.this,
                    parsedLine, attachments);
            RequestWithAttachments req = new RequestWithAttachments(op, attachments);
            set(Scope.REQUEST, "OP_REQ", req);
            operationHandler.handle(this);
        }
    }

    private void handleLegacyCommand(String opLine, CommandHandler handler, boolean direct) throws CommandLineException {
        if (isBatchMode() && handler.isBatchMode(this)) {
            if (!(handler instanceof OperationCommand)) {
                throw new CommandLineException("The command is not allowed in a batch.");
            } else {
                try {
                    Batch batch = getBatchManager().getActiveBatch();
                    HandledRequest request = ((OperationCommand) handler).buildHandledRequest(this,
                            batch.getAttachments());
                    BatchedCommand batchedCmd
                            = new DefaultBatchedCommand(this, opLine,
                                    request.getRequest(), request.getResponseHandler());
                    batch.add(batchedCmd);
                } catch (CommandFormatException e) {
                    throw new CommandFormatException("Failed to add to batch '" + opLine + "'", e);
                }
            }
        } else if (direct) {
            handler.handle(CommandContextImpl.this);
        } else {
            execute(() -> {
                executor.execute(handler, timeout, TimeUnit.SECONDS);
                return null;
            }, opLine);
        }
    }


    @Override
    public CommandLineCompleter getDefaultCommandCompleter() {
        return cmdCompleter;
    }

    @Override
    public ParsedCommandLine getParsedCommandLine() {
        return parsedCmd;
    }

    @Override
    public boolean isDomainMode() {
        return domainMode;
    }

    @Override
    public void addEventListener(CliEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener is null.");
        }
        listeners.add(listener);
    }

    @Override
    public CliConfig getConfig() {
        return config;
    }

    protected void notifyListeners(CliEvent event) {
        for (CliEventListener listener : listeners) {
            listener.cliEvent(event, this);
        }
    }

    @Override
    public void interact() {
        INTERACT = true;
        if(cmdCompleter == null) {
            throw new IllegalStateException("The console hasn't been initialized at construction time.");
        }

        if (this.client == null) {
            printLine("You are disconnected at the moment. Type 'connect' to connect to the server or"
                    + " 'help' for the list of supported commands.");
        }

        console.setPrompt(getPrompt());
        if (!console.running()) {
            try {
                // This call is blocking.
                console.start();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        INTERACT = false;
    }

    @Override
    public boolean isResolveParameterValues() {
        return resolveParameterValues;
    }

    @Override
    public void setResolveParameterValues(boolean resolve) {
        this.resolveParameterValues = resolve;
    }

    @Override
    public void handleClose() {
        // if the connection loss was triggered by an instruction to restart/reload
        // then we don't disconnect yet
        if(parsedCmd.getFormat() != null) {
            if(Util.RELOAD.equals(parsedCmd.getOperationName())) {
                // do nothing
            } else if(Util.SHUTDOWN.equals(parsedCmd.getOperationName())) {
                if(CommandFormat.INSTANCE.equals(parsedCmd.getFormat())
                        // shutdown command handler decides whether to disconnect or not
                        || Util.TRUE.equals(parsedCmd.getPropertyValue(Util.RESTART))) {
                    // do nothing
                } else {
                    printLine("");
                    printLine("The controller has closed the connection.");
                    disconnectController();
                }
            } else {
                // we don't disconnect here because the connection may be closed by another
                // CLI instance/session doing a reload (this happens in our testsuite)
            }
        } else {
            // we don't disconnect here because the connection may be closed by another
            // CLI instance/session doing a reload (this happens in our testsuite)
        }
    }

    @Override
    public boolean isSilent() {
        return SILENT;
    }

    @Override
    public void setSilent(boolean silent) {
        SILENT = silent;
    }

    @Override
    public int getTerminalWidth() {
        if (!INTERACT) {
            return 80;
        }

        if(console == null) {
            try {
                this.initBasicConsole(null);
            } catch (CliInitializationException e) {
                this.error("Failed to initialize the console: " + e.getLocalizedMessage());
                return 80;
            }
        }
        return console.getTerminalWidth();
    }

    @Override
    public int getTerminalHeight() {
        if (!INTERACT) {
            return 24; // WFCORE-3540 24 has no special meaning except that it is a value greater than 0
        }

        if(console == null) {
            try {
                this.initBasicConsole(null);
            } catch (CliInitializationException e) {
                this.error("Failed to initialize the console: " + e.getLocalizedMessage());
                return 24;
            }
        }
        return console.getTerminalHeight();
    }

    @Override
    public void setVariable(String name, String value) throws CommandLineException {
        if(name == null || name.isEmpty()) {
            throw new CommandLineException("Variable name can't be null or an empty string");
        }
        if(!Character.isJavaIdentifierStart(name.charAt(0))) {
            throw new CommandLineException("Variable name must be a valid Java identifier (and not contain '$'): '" + name + "'");
        }
        for(int i = 1; i < name.length(); ++i) {
            final char c = name.charAt(i);
            if(!Character.isJavaIdentifierPart(c) || c == '$') {
                throw new CommandLineException("Variable name must be a valid Java identifier (and not contain '$'): '" + name + "'");
            }
        }

        if(value == null) {
            if(variables == null) {
                return;
            }
            variables.remove(name);
        } else {
            if(variables == null) {
                variables = new HashMap<String,String>();
            }
            variables.put(name, value);
        }
    }

    @Override
    public String getVariable(String name) {
        return variables == null ? null : variables.get(name);
    }

    @Override
    public Collection<String> getVariables() {
        return variables == null ? Collections.<String>emptySet() : variables.keySet();
    }

    private class AuthenticationCallbackHandler implements CallbackHandler {

        // After the CLI has connected the physical connection may be re-established numerous times.
        // for this reason we cache the entered values to allow for re-use without pestering the end
        // user.

        private String realm = null;
        private boolean realmShown = false;

        private String username;
        private char[] password;
        private String digest;

        private AuthenticationCallbackHandler(String username, char[] password) {
            // A local cache is used for scenarios where no values are specified on the command line
            // and the user wishes to use the connect command to establish a new connection.
            this.username = username;
            this.password = password;
        }

        private AuthenticationCallbackHandler(String username, String digest) {
            this.username = username;
            this.digest = digest;
        }

        @Override
        public void handle(final Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            try {
                timeoutHandler.suspendAndExecute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            dohandle(callbacks);
                        } catch (IOException | UnsupportedCallbackException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                } else if (e.getCause() instanceof UnsupportedCallbackException) {
                    throw (UnsupportedCallbackException) e.getCause();
                }
                throw e;
            }
        }

        private void dohandle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            // Special case for anonymous authentication to avoid prompting user for their name.
            if (callbacks.length == 1 && callbacks[0] instanceof NameCallback) {
                ((NameCallback) callbacks[0]).setName("anonymous CLI user");
                return;
            }

            for (Callback current : callbacks) {
                if (current instanceof RealmCallback) {
                    RealmCallback rcb = (RealmCallback) current;
                    String defaultText = rcb.getDefaultText();
                    realm = defaultText;
                    rcb.setText(defaultText); // For now just use the realm suggested.
                } else if (current instanceof RealmChoiceCallback) {
                    RealmChoiceCallback rcc = (RealmChoiceCallback) current;
                    final int defaultChoice = rcc.getDefaultChoice();
                    rcc.setSelectedIndex(defaultChoice);
                    realm = rcc.getChoices()[defaultChoice];
                } else if (current instanceof OptionalNameCallback) {
                    NameCallback ncb = (NameCallback) current;
                    // set it if we have one
                    if (username != null) {
                        // use cached name
                        ncb.setName(username);
                        connInfoBean.setUsername(username);
                    } else {
                        final String defaultName = ncb.getDefaultName();
                        if (defaultName != null) {
                            // accept suggested name but do not set our cached name
                            ncb.setName(defaultName);
                            connInfoBean.setUsername(defaultName);
                        }
                    }
                } else if (current instanceof NameCallback) {
                    NameCallback ncb = (NameCallback) current;
                    final String defaultName = ncb.getDefaultName();
                    if (username != null) {
                        // use cached name
                        ncb.setName(username);
                        connInfoBean.setUsername(username);
                    } else if (defaultName != null) {
                        // accept suggested name but do not set our cached name
                        ncb.setName(defaultName);
                        connInfoBean.setUsername(defaultName);
                    } else {
                        showRealm();
                        try {
                            username = readLine("Username: ", false);
                        } catch (CommandLineException e) {
                            // the messages of the cause are lost if nested here
                            throw new IOException("Failed to read username: " + e.getLocalizedMessage());
                        }
                        if (username == null || username.length() == 0) {
                            throw new SaslException("No username supplied.");
                        }
                        ncb.setName(username);
                        connInfoBean.setUsername(username);
                    }
                } else if (current instanceof PasswordCallback && digest == null) {
                    // If a digest had been set support for PasswordCallback is disabled.
                    if (password == null) {
                        showRealm();
                        String temp;
                        try {
                            temp = readLine("Password: ", true);
                        } catch (CommandLineException e) {
                            // the messages of the cause are lost if nested here
                            throw new IOException("Failed to read password: " + e.getLocalizedMessage());
                        }
                        if (temp != null) {
                            password = temp.toCharArray();
                        }
                    }
                    PasswordCallback pcb = (PasswordCallback) current;
                    pcb.setPassword(password);
                } else if (current instanceof CredentialCallback) {
                    final CredentialCallback cc = (CredentialCallback) current;
                    if (digest == null && cc.isCredentialTypeSupported(PasswordCredential.class, ClearPassword.ALGORITHM_CLEAR)) {
                        if (password == null) {
                            showRealm();
                            String temp;
                            try {
                                temp = readLine("Password: ", true);
                            } catch (CommandLineException e) {
                                // the messages of the cause are lost if nested here
                                throw new IOException("Failed to read password: " + e.getLocalizedMessage());
                            }
                            if (temp != null) {
                                password = temp.toCharArray();
                            }
                        }
                        cc.setCredential(new PasswordCredential(ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, password)));
                    } else if (digest != null && cc.isCredentialTypeSupported(PasswordCredential.class, DigestPassword.ALGORITHM_DIGEST_MD5)) {
                        // We don't support an interactive use of this callback so it must have been set in advance.
                        final byte[] bytes = CodePointIterator.ofString(digest).hexDecode().drain();
                        cc.setCredential(new PasswordCredential(DigestPassword.createRaw(DigestPassword.ALGORITHM_DIGEST_MD5, username, realm, bytes)));
                    } else if (cc.isCredentialTypeSupported(BearerTokenCredential.class)) {
                        AuthenticationContext context = AuthenticationContext.captureCurrent();
                        AuthenticationContextConfigurationClient client = AccessController.doPrivileged(AuthenticationContextConfigurationClient.ACTION);

                        AuthenticationConfiguration configuration = client.getAuthenticationConfiguration(URI.create(connectionAddress.toString()), context);
                        CallbackHandler callbackHandler = client.getCallbackHandler(configuration);
                        context.with(MatchRule.ALL, configuration.useCallbackHandler(this)).run((PrivilegedAction<Object>) () -> {
                            try {
                                callbackHandler.handle(callbacks);
                            } catch (Exception ignore) {
                            }
                            return null;
                        });
                    } else {
                        CallbackUtil.unsupported(current);
                    }
                } else {
                    CallbackUtil.unsupported(current);
                }
            }
        }

        private void showRealm() {
            if (realmShown == false && realm != null) {
                realmShown = true;
                printLine("Authenticating against security realm: " + realm);
            }
        }
    }

    /**
     * A trust manager that by default delegates to a lazily initialised TrustManager, this TrustManager also support both
     * temporarily and permanently accepting unknown server certificate chains.
     *
     * This class also acts as an aggregation of the configuration related to TrustStore handling.
     *
     * It is not intended that Certificate management requests occur if this class is registered to a SSLContext
     * with multiple concurrent clients.
     */
    private class LazyDelagatingTrustManager implements X509TrustManager {

        // Configuration based state set on initialisation.

        private final String trustStore;
        private final String trustStorePassword;
        private final boolean modifyTrustStore;

        private Set<X509Certificate> temporarilyTrusted = new HashSet<X509Certificate>();
        private X509TrustManager delegate;

        LazyDelagatingTrustManager(String trustStore, String trustStorePassword, boolean modifyTrustStore) {
            this.trustStore = trustStore;
            this.trustStorePassword = trustStorePassword;
            this.modifyTrustStore = modifyTrustStore;
        }

        /*
         * Methods to allow client interaction for certificate verification.
         */

        boolean isModifyTrustStore() {
            return modifyTrustStore;
        }

        synchronized void storeChainTemporarily(final Certificate[] chain) {
            for (Certificate current : chain) {
                if (current instanceof X509Certificate) {
                    temporarilyTrusted.add((X509Certificate) current);
                }
            }
            delegate = null; // Triggers a reload on next use.
        }

        synchronized void storeChainPermenantly(final Certificate[] chain) {
            FileInputStream fis = null;
            FileOutputStream fos = null;
            try {
                KeyStore theTrustStore = KeyStore.getInstance("JKS");
                File trustStoreFile = new File(trustStore);
                if (trustStoreFile.exists()) {
                    fis = new FileInputStream(trustStoreFile);
                    theTrustStore.load(fis, trustStorePassword.toCharArray());
                    StreamUtils.safeClose(fis);
                    fis = null;
                } else {
                    theTrustStore.load(null);
                }
                for (Certificate current : chain) {
                    if (current instanceof X509Certificate) {
                        X509Certificate x509Current = (X509Certificate) current;
                        theTrustStore.setCertificateEntry(x509Current.getSubjectX500Principal().getName(), x509Current);
                    }
                }

                fos = new FileOutputStream(trustStoreFile);
                theTrustStore.store(fos, trustStorePassword.toCharArray());

            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Unable to operate on trust store.", e);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to operate on trust store.", e);
            } finally {
                StreamUtils.safeClose(fis);
                StreamUtils.safeClose(fos);
            }

            delegate = null; // Triggers a reload on next use.
        }

        /*
         * Internal Methods
         */

        private synchronized X509TrustManager getDelegate() {
            if (delegate == null) {
                FileInputStream fis = null;
                try {
                    KeyStore theTrustStore = KeyStore.getInstance("JKS");
                    File trustStoreFile = new File(trustStore);
                    if (trustStoreFile.exists()) {
                        fis = new FileInputStream(trustStoreFile);
                        theTrustStore.load(fis, trustStorePassword.toCharArray());
                    } else {
                        theTrustStore.load(null);
                    }
                    for (X509Certificate current : temporarilyTrusted) {
                        theTrustStore.setCertificateEntry(UUID.randomUUID().toString(), current);
                    }
                    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    trustManagerFactory.init(theTrustStore);
                    TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
                    for (TrustManager current : trustManagers) {
                        if (current instanceof X509TrustManager) {
                            delegate = (X509TrustManager) current;
                            break;
                        }
                    }
                } catch (GeneralSecurityException e) {
                    throw new IllegalStateException("Unable to operate on trust store.", e);
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to operate on trust store.", e);
                } finally {
                    StreamUtils.safeClose(fis);
                }
            }
            if (delegate == null) {
                throw new IllegalStateException("Unable to create delegate trust manager.");
            }

            return delegate;
        }

        /*
         * X509TrustManager Methods
         */

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // The CLI is only verifying servers.
            getDelegate().checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, String authType) throws CertificateException {
            boolean retry;
            do {
                retry = false;
                try {
                    getDelegate().checkServerTrusted(chain, authType);
                    connInfoBean.setServerCertificates(chain);
                } catch (CertificateException ce) {
                    if (retry == false) {
                        timeoutHandler.suspendAndExecute(new Runnable() {

                            @Override
                            public void run() {
                                try {
                                    handleSSLFailure(chain);
                                } catch (CommandLineException | IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });

                        if (delegate == null) {
                            retry = true;
                        } else {
                            throw ce;
                        }
                    } else {
                        throw ce;
                    }
                }
            } while (retry);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return getDelegate().getAcceptedIssuers();
        }
    }

    private class JaasConfigurationWrapper extends Configuration {

        private final Configuration wrapped;

        private JaasConfigurationWrapper(Configuration toWrap) {
            this.wrapped = toWrap;
        }

        @Override
        public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
            AppConfigurationEntry[] response = wrapped != null ? wrapped.getAppConfigurationEntry(name) : null;
            if (response == null) {
                if ("com.sun.security.jgss.initiate".equals(name)) {
                    HashMap<String, String> options = new HashMap<String, String>(2);
                    options.put("useTicketCache", "true");
                    options.put("doNotPrompt", "true");
                    response = new AppConfigurationEntry[] { new AppConfigurationEntry(
                            "com.sun.security.auth.module.Krb5LoginModule",
                            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options) };
                }

            }

            return response;
        }

    }

    class CommandLineRedirectionRegistration implements CommandLineRedirection.Registration {

        CommandLineRedirection target;

        CommandLineRedirectionRegistration(CommandLineRedirection redirection) {
            if(redirection == null) {
                throw new IllegalArgumentException("Redirection is null");
            }
            this.target = redirection;
        }

        @Override
        public void unregister() throws CommandLineException {
            ensureActive();
            CommandContextImpl.this.redirection = null;
        }

        @Override
        public boolean isActive() {
            return CommandContextImpl.this.redirection == this;
        }

        @Override
        public void handle(ParsedCommandLine parsedLine) throws CommandLineException {

            ensureActive();

            try {
                /**
                 * All kind of command can be handled by handleCommand. In order
                 * to stay on the safe side (another parsing is applied on top
                 * of operations and legacy commands by aesh, we only use the
                 * wrapped approach if an operator is present. This could be
                 * simplified when we have confidence that aesh parsing doesn't
                 * fail for complex corner cases.
                 */
                if (parsedLine.hasOperator()) {
                    handleCommand(parsedCmd);
                } else if (parsedCmd.getFormat() == OperationFormat.INSTANCE) {
                    handleOperation(parsedCmd);
                } else {
                    final String cmdName = parsedCmd.getOperationName();
                    CommandHandler handler = cmdRegistry.getCommandHandler(cmdName.toLowerCase());
                    if (handler != null) {
                        handleLegacyCommand(parsedLine.getOriginalLine(), handler, true);
                    } else {
                        handleCommand(parsedCmd);
                    }
                }
            } finally {
                clear(Scope.REQUEST);
            }
        }

        private void ensureActive() throws CommandLineException {
            if(!isActive()) {
                throw new CommandLineException("The redirection is not registered any more.");
            }
        }
    }

    public ConnectionInfo getConnectionInfo() {
        return connInfoBean;
    }

    @Override
    public void captureOutput(PrintStream captor) {
        assert captor != null;
        // For now make this invalid. Want to be sure that we don't have such case
        if (INTERACT) {
            throw new RuntimeException("Can't replace outputStream in interactive mode");
        }
        cliPrintStream.captureOutput(captor);
    }

    @Override
    public void releaseOutput() {
        cliPrintStream.releaseOutput();
    }

    @Override
    public final void setCommandTimeout(int numSeconds) {
        if (numSeconds < 0) {
            throw new IllegalArgumentException("The command-timeout must be a "
                    + "valid positive integer:" + numSeconds);
        }
        this.timeout = numSeconds;
    }

    @Override
    public final int getCommandTimeout() {
        return timeout;
    }

    @Override
    public final void resetTimeout(TIMEOUT_RESET_VALUE value) {
        switch (value) {
            case CONFIG: {
                timeout = configTimeout;
                break;
            }
            case DEFAULT: {
                timeout = DEFAULT_TIMEOUT;
                break;
            }
        }
    }

    /**
     * Public for testing purpose only.
     *
     * @return
     */
    public ReadlineConsole getConsole() {
        return (ReadlineConsole) console;
    }

    // For testing prupose.
    public AeshCommands getAeshCommands() {
        return aeshCommands;
    }

    public Completer getLegacyCommandCompleter() {
        return legacyCmdCompleter;
    }

    // Required by CLICommandInvocationBuilder
    // in order to expose a CommandContext
    // that properly handles timeout.
    public CommandContext newTimeoutCommandContext() {
        return executor.newTimeoutCommandContext(this);
    }
}
