/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.embedded;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.cli.CliEvent;
import org.jboss.as.cli.CliEventListener;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.jboss.as.cli.handlers.SimpleTabCompleter;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.impl.FileSystemPathArgument;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.modules.ModuleLoader;
import org.jboss.stdio.NullOutputStream;
import org.jboss.stdio.StdioContext;
import org.wildfly.core.embedded.Configuration;
import org.wildfly.core.embedded.EmbeddedManagedProcess;
import org.wildfly.core.embedded.EmbeddedProcessFactory;
import org.wildfly.core.embedded.EmbeddedProcessStartException;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Handler for the "embed-server" command.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
class EmbedServerHandler extends CommandHandlerWithHelp {

    private static final String ECHO = "echo";
    private static final String DISCARD_STDOUT = "discard";

    private static final String JBOSS_SERVER_BASE_DIR = "jboss.server.base.dir";
    private static final String JBOSS_SERVER_CONFIG_DIR = "jboss.server.config.dir";
    private static final String JBOSS_SERVER_LOG_DIR = "jboss.server.log.dir";

    private final AtomicReference<EmbeddedProcessLaunch> serverReference;
    private ArgumentWithValue jbossHome;
    private ArgumentWithValue stdOutHandling;
    private ArgumentWithValue adminOnly;
    private ArgumentWithValue serverConfig;
    private ArgumentWithValue dashC;
    private ArgumentWithoutValue emptyConfig;
    private ArgumentWithoutValue removeExisting;
    private ArgumentWithValue timeout;

    static EmbedServerHandler create(final AtomicReference<EmbeddedProcessLaunch> serverReference, CommandContext ctx, boolean modular) {
        EmbedServerHandler result = new EmbedServerHandler(serverReference);
        final FilenameTabCompleter pathCompleter = FilenameTabCompleter.newCompleter(ctx);
        if (!modular) {
            result.jbossHome = new FileSystemPathArgument(result, pathCompleter, "--jboss-home");
        }
        result.stdOutHandling = new ArgumentWithValue(result, new SimpleTabCompleter(new String[]{ECHO, DISCARD_STDOUT}), "--std-out");
        result.serverConfig = new ArgumentWithValue(result, "--server-config");
        result.dashC = new ArgumentWithValue(result, "-c");
        result.dashC.addCantAppearAfter(result.serverConfig);
        result.serverConfig.addCantAppearAfter(result.dashC);
        result.adminOnly = new ArgumentWithValue(result, SimpleTabCompleter.BOOLEAN, "--admin-only");
        result.emptyConfig = new ArgumentWithoutValue(result, "--empty-config");
        result.removeExisting = new ArgumentWithoutValue(result, "--remove-existing");
        result.removeExisting.addRequiredPreceding(result.emptyConfig);
        result.timeout = new ArgumentWithValue(result, "--timeout");

        return result;
    }

    private EmbedServerHandler(final AtomicReference<EmbeddedProcessLaunch> serverReference) {
        super("embed-server", false);
        assert serverReference != null;
        this.serverReference = serverReference;
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        return ctx.getModelControllerClient() == null;
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        final ParsedCommandLine parsedCmd = ctx.getParsedCommandLine();
        final File jbossHome = getJBossHome(parsedCmd);

        // set up the expected properties
        final String baseDir = WildFlySecurityManager.getPropertyPrivileged(JBOSS_SERVER_BASE_DIR, jbossHome + File.separator + "standalone");
        String xml = serverConfig.getValue(parsedCmd);
        if (xml == null) {
            xml = dashC.getValue(parsedCmd);
        }
        boolean adminOnlySetting = true;
        String adminProp = adminOnly.getValue(parsedCmd);
        if (adminProp != null && "false".equalsIgnoreCase(adminProp)) {
            adminOnlySetting = false;
        }
        boolean startEmpty = emptyConfig.isPresent(parsedCmd);
        boolean removeConfig = startEmpty && removeExisting.isPresent(parsedCmd);

        final List<String> args = parsedCmd.getOtherProperties();
        if (!args.isEmpty()) {
            if (args.size() != 1) {
                throw new CommandFormatException("The command accepts 0 unnamed argument(s) but received: " + args);
            }
        }

        Long bootTimeout = null;
        String timeoutString = timeout.getValue(parsedCmd);
        if (timeout.isPresent(parsedCmd) && (timeoutString == null || timeoutString.isEmpty())) {
            throw new CommandFormatException("The --timeout parameter requires a value.");
        }

        if (timeoutString != null) {
            bootTimeout = TimeUnit.SECONDS.toNanos(Long.parseLong(timeoutString));
        }

        String stdOutString = stdOutHandling.getValue(parsedCmd);
        if (stdOutHandling.isPresent(parsedCmd)) {
            if (stdOutString == null || stdOutString.isEmpty()) {
                throw new CommandFormatException("The --std-out parameter requires a value { echo, discard }.");
            }
            if (! (stdOutString.equals(ECHO) || stdOutString.equals(DISCARD_STDOUT))) {
                throw new CommandFormatException("The --std-out parameter should be one of { echo, discard }.");
            }
        }

        final EnvironmentRestorer restorer = new EnvironmentRestorer(JBOSS_SERVER_LOG_DIR);
        boolean ok = false;
        ThreadLocalContextSelector contextSelector = null;
        try {

            Contexts defaultContexts = restorer.getDefaultContexts();

            StdioContext discardStdoutContext = null;
            if (!ECHO.equalsIgnoreCase(stdOutHandling.getValue(parsedCmd))) {
                PrintStream nullStream = new UncloseablePrintStream(NullOutputStream.getInstance());
                StdioContext currentContext = defaultContexts.getStdioContext();
                discardStdoutContext = StdioContext.create(currentContext.getIn(), nullStream, currentContext.getErr());
            }

            // Configure and get the log context, default to baseDir
            String serverLogDir = WildFlySecurityManager.getPropertyPrivileged(JBOSS_SERVER_LOG_DIR, null);
            if (serverLogDir == null) {
                serverLogDir = baseDir + File.separator + "log";
                WildFlySecurityManager.setPropertyPrivileged(JBOSS_SERVER_LOG_DIR, serverLogDir);
            }
            final String serverCfgDir = WildFlySecurityManager.getPropertyPrivileged(JBOSS_SERVER_CONFIG_DIR, baseDir + File.separator + "configuration");
            final LogContext embeddedLogContext = EmbeddedLogContext.configureLogContext(new File(serverLogDir), new File(serverCfgDir), "server.log", ctx);

            Contexts localContexts = new Contexts(embeddedLogContext, discardStdoutContext);
            contextSelector = new ThreadLocalContextSelector(localContexts, defaultContexts);
            contextSelector.pushLocal();

            StdioContext.setStdioContextSelector(contextSelector);
            LogContext.setLogContextSelector(contextSelector);

            List<String> cmdsList = new ArrayList<>();

            if (xml == null && (parsedCmd.hasProperty("--server-config") || parsedCmd.hasProperty("-c"))) {
                throw new CommandFormatException("The --server-config (or -c) parameter requires a value.");
            }

            if (xml != null) {
                xml = xml.trim();
                if (xml.length() == 0) {
                    throw new CommandFormatException("The --server-config parameter requires a value.");
                }
                if (!xml.endsWith(".xml")) {
                    throw new CommandFormatException("The --server-config filename must end with .xml.");
                }
                cmdsList.add("--server-config=" + xml);
            }

            // if --empty-config is present but the config file already exists we error unless --remove-config has also been used
            if (startEmpty && !removeConfig) {
                String configFileName = xml == null ? "standalone.xml" : xml;
                File configFile = new File(serverCfgDir + File.separator + configFileName);
                if (configFile.exists()) {
                    throw new CommandFormatException("The configuration file " + configFileName + " already exists, please use --remove-existing if you wish to overwrite.");
                }
            }

            if (adminOnlySetting) {
                cmdsList.add("--admin-only");
            }
            if (startEmpty) {
                cmdsList.add("--internal-empty-config");
                if (removeConfig) {
                    cmdsList.add("--internal-remove-config");
                }
            }

            String[] cmds = cmdsList.toArray(new String[cmdsList.size()]);

            final Configuration.Builder configBuilder;
            if (this.jbossHome == null) {
                // Modular environment, note that the jbossHome here is resolved from the JBOSS_HOME environment
                // variable and should never be null according to the getJBossHome() method.
                configBuilder = Configuration.Builder.of(jbossHome)
                        .setModuleLoader(ModuleLoader.forClass(getClass()))
                        .setCommandArguments(cmds);
            } else {
                configBuilder = Configuration.Builder.of(jbossHome.getAbsoluteFile())
                        .addSystemPackages(EmbeddedControllerHandlerRegistrar.EXTENDED_SYSTEM_PKGS)
                        .setCommandArguments(cmds);
            }
            // Disables the logging subsystem from registering an embedded log context if the subsystem is present
            WildFlySecurityManager.setPropertyPrivileged("org.wildfly.logging.embedded", "false");
            final EmbeddedManagedProcess server = EmbeddedProcessFactory.createStandaloneServer(configBuilder.build());
            server.start();
            serverReference.set(new EmbeddedProcessLaunch(server, restorer, false));
            ModelControllerClient mcc = new ThreadContextsModelControllerClient(server.getModelControllerClient(), contextSelector);
            if (bootTimeout == null || bootTimeout > 0) {
                // Poll for server state. Alternative would be to get ControlledProcessStateService
                // and do reflection stuff to read the state and register for change notifications
                long expired = bootTimeout == null ? Long.MAX_VALUE : System.nanoTime() + bootTimeout;
                String status = "starting";
                final ModelNode getStateOp = new ModelNode();
                getStateOp.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
                getStateOp.get(ClientConstants.NAME).set("server-state");
                do {
                    try {
                        final ModelNode response = mcc.execute(getStateOp);
                        if (Util.isSuccess(response)) {
                            status = response.get(ClientConstants.RESULT).asString();
                        }
                    } catch (Exception e) {
                        // ignore and try again
                    }

                    if ("starting".equals(status)) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new CommandLineException("Interrupted while waiting for embedded server to start");
                        }
                    } else {
                        break;
                    }
                } while (System.nanoTime() < expired);

                if ("starting".equals(status)) {
                    assert bootTimeout != null; // we'll assume the loop didn't run for decades
                    // Stop server and restore environment
                    StopEmbeddedServerHandler.cleanup(serverReference);
                    throw new CommandLineException("Embedded server did not exit 'starting' status within " +
                            TimeUnit.NANOSECONDS.toSeconds(bootTimeout) + " seconds");
                }

            }
            // Expose the client to the rest of the CLI last so nothing can be done with
            // it until we're ready
            ctx.bindClient(mcc);

            // Stop the server on any disconnect event
            ctx.addEventListener(new CliEventListener() {
                @Override
                public void cliEvent(CliEvent event, CommandContext ctx) {
                    if (event == CliEvent.DISCONNECTED) {
                        StopEmbeddedServerHandler.cleanup(serverReference);
                    }
                }
            });

            ok = true;
        } catch (RuntimeException | EmbeddedProcessStartException e) {
            throw new CommandLineException("Cannot start embedded server", e);
        } finally {
            if (!ok) {
                ctx.disconnectController();
                restorer.restoreEnvironment();
            } else if (contextSelector != null) {
                contextSelector.restore(null);
            }
        }
    }

    private File getJBossHome(final ParsedCommandLine parsedCmd) throws CommandLineException {
        String jbossHome = this.jbossHome == null ? null : this.jbossHome.getValue(parsedCmd);
        if (jbossHome == null || jbossHome.length() == 0) {
            jbossHome = WildFlySecurityManager.getEnvPropertyPrivileged("JBOSS_HOME", null);
            if (jbossHome == null || jbossHome.length() == 0) {
                if (this.jbossHome != null) {
                    throw new CommandLineException("Missing configuration value for --jboss-home and environment variable JBOSS_HOME is not set");
                } else {
                    throw new CommandLineException("Environment variable JBOSS_HOME is not set");
                }
            }
            return validateJBossHome(jbossHome, "environment variable JBOSS_HOME");
        } else {
            return validateJBossHome(jbossHome, "argument --jboss-home");
        }
    }

    private static File validateJBossHome(String jbossHome, String source) throws CommandLineException {

        File f = new File(jbossHome);
        if (!f.exists()) {
            throw new CommandLineException(String.format("File %s specified by %s does not exist", jbossHome, source));
        } else if (!f.isDirectory()) {
            throw new CommandLineException(String.format("File %s specified by %s is not a directory", jbossHome, source));
        }
        return f;
    }

}
