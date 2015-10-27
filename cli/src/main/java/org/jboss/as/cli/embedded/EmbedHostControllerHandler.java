/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.cli.embedded;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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
import org.jboss.as.cli.handlers.DefaultFilenameTabCompleter;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.jboss.as.cli.handlers.SimpleTabCompleter;
import org.jboss.as.cli.handlers.WindowsFilenameTabCompleter;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.FileSystemPathArgument;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.process.CommandLineConstants;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.PropertyConfigurator;
import org.jboss.modules.ModuleLoader;
import org.jboss.stdio.NullOutputStream;
import org.jboss.stdio.StdioContext;
import org.wildfly.core.embedded.EmbeddedServerFactory;
import org.wildfly.core.embedded.EmbeddedServerReference;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Handler for the "embed-host-controller" command.
 *
 * @author Ken Wills <kwills@redhat.com> (c) 2015 Red Hat Inc.
 */
class EmbedHostControllerHandler extends CommandHandlerWithHelp {

    private static final String ECHO = "echo";
    private static final String DISCARD_STDOUT = "discard";

    private final AtomicReference<EmbeddedServerLaunch> hostControllerReference;
    private ArgumentWithValue jbossHome;
    private ArgumentWithValue stdOutHandling;
    private ArgumentWithValue domainConfig;
    private ArgumentWithValue hostConfig;
    private ArgumentWithValue dashC;
    private ArgumentWithValue timeout;

    static EmbedHostControllerHandler create(final AtomicReference<EmbeddedServerLaunch> hostControllerReference, final CommandContext ctx, final boolean modular) {
        EmbedHostControllerHandler result = new EmbedHostControllerHandler(hostControllerReference);
        final FilenameTabCompleter pathCompleter = Util.isWindows() ? new WindowsFilenameTabCompleter(ctx) : new DefaultFilenameTabCompleter(ctx);
        if (!modular) {
            result.jbossHome = new FileSystemPathArgument(result, pathCompleter, "--jboss-home");
        }
        result.stdOutHandling = new ArgumentWithValue(result, new SimpleTabCompleter(new String[]{ECHO, DISCARD_STDOUT}), "--std-out");
        result.domainConfig = new ArgumentWithValue(result, "--domain-config");
        result.hostConfig = new ArgumentWithValue(result, "--host-config");
        result.dashC = new ArgumentWithValue(result, "-c");
        result.dashC.addCantAppearAfter(result.domainConfig);
        result.domainConfig.addCantAppearAfter(result.dashC);
        result.timeout = new ArgumentWithValue(result, "--timeout");

        return result;
    }

    private EmbedHostControllerHandler(final AtomicReference<EmbeddedServerLaunch> hostControllerReference) {
        super("embed-host-controller", false);
        assert hostControllerReference != null;
        this.hostControllerReference = hostControllerReference;
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
       return ctx.getModelControllerClient() == null;
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        final ParsedCommandLine parsedCmd = ctx.getParsedCommandLine();
        final File jbossHome = getJBossHome(parsedCmd);
        String domainXml = domainConfig.getValue(parsedCmd);
        if (domainXml == null) {
            domainXml = dashC.getValue(parsedCmd);
        }

        String hostXml = hostConfig.getValue(parsedCmd);

        final List<String> args = parsedCmd.getOtherProperties();
        if (!args.isEmpty()) {
            if (args.size() != 0) {
                throw new CommandFormatException("The command accepts 0 unnamed argument(s) but received: " + args);
            }
        }

        Long bootTimeout = null;
        String timeoutString = timeout.getValue(parsedCmd);
        if (timeoutString != null) {
            bootTimeout = TimeUnit.SECONDS.toNanos(Long.parseLong(timeoutString));
        }

        final EnvironmentRestorer restorer = new EnvironmentRestorer();
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

            // Create our own LogContext
            final LogContext embeddedLogContext = LogContext.create();
            // Set up logging from standalone/configuration/logging.properties
            configureLogContext(embeddedLogContext, jbossHome, ctx);

            Contexts localContexts = new Contexts(embeddedLogContext, discardStdoutContext);
            contextSelector = new ThreadLocalContextSelector(localContexts, defaultContexts);
            contextSelector.pushLocal();

            StdioContext.setStdioContextSelector(contextSelector);
            LogContext.setLogContextSelector(contextSelector);

            List<String> cmdsList = new ArrayList<>();
            if (domainXml != null && domainXml.trim().length() > 0) {
                cmdsList.add(CommandLineConstants.DOMAIN_CONFIG);
                cmdsList.add(domainXml.trim());
            }

            if (hostXml != null && hostXml.trim().length() > 0) {
                cmdsList.add(CommandLineConstants.HOST_CONFIG);
                cmdsList.add(hostXml.trim());
            }

            String[] cmds = cmdsList.toArray(new String[cmdsList.size()]);

            EmbeddedServerReference hostController;
            if (this.jbossHome == null) {
                // Modular environment
                hostController = EmbeddedServerFactory.createHostController(ModuleLoader.forClass(getClass()), jbossHome, cmds);
            } else {
                hostController = EmbeddedServerFactory.createHostController(jbossHome.getAbsolutePath(), null, null, cmds);
            }
            hostController.start();
            hostControllerReference.set(new EmbeddedServerLaunch(hostController, restorer));

            ModelControllerClient mcc = new ThreadContextsModelControllerClient(hostController.getModelControllerClient(), contextSelector);
            if (bootTimeout == null || bootTimeout > 0) {
                long expired = bootTimeout == null ? Long.MAX_VALUE : System.nanoTime() + bootTimeout;

                String localName = "master";
                String status = "starting";

                // read out the host controller name
                final ModelNode getNameOp = new ModelNode();
                getNameOp.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
                getNameOp.get(ClientConstants.NAME).set(Util.LOCAL_HOST_NAME);

                final ModelNode getStateOp = new ModelNode();
                getStateOp.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
                ModelNode address = getStateOp.get(ClientConstants.ADDRESS);
                address.add(ClientConstants.HOST, localName);
                getStateOp.get(ClientConstants.NAME).set(ClientConstants.HOST_STATE);
                do {
                    try {
                        final ModelNode nameResponse = mcc.execute(getNameOp);
                        if (Util.isSuccess(nameResponse)) {
                            localName = nameResponse.get(ClientConstants.RESULT).asString();
                            address.set(ClientConstants.HOST, localName);
                            final ModelNode stateResponse = mcc.execute(getStateOp);
                            if (Util.isSuccess(stateResponse)) {
                                status = stateResponse.get(ClientConstants.RESULT).asString();
                            }
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
                    StopEmbeddedHostControllerHandler.cleanup(hostControllerReference);
                    throw new CommandLineException("Embedded host controller did not exit 'starting' status within " +
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
                        StopEmbeddedHostControllerHandler.cleanup(hostControllerReference);
                    }
                }
            });
            ok = true;
        } finally {
            if (!ok) {
                ctx.disconnectController();
                restorer.restoreEnvironment();
            } else if (contextSelector != null) {
                contextSelector.restore(null);
            }
        }
    }

    private void configureLogContext(LogContext embeddedLogContext, File jbossHome, CommandContext ctx) {
        File standaloneDir =  new File(jbossHome, "domain");
        File configDir =  new File(standaloneDir, "configuration");
        File logDir =  new File(standaloneDir, "log");
        File bootLog = new File(logDir, "server.log");
        File loggingProperties = new File(configDir, "logging.properties");
        if (loggingProperties.exists()) {

            WildFlySecurityManager.setPropertyPrivileged("org.jboss.boot.log.file", bootLog.getAbsolutePath());

            FileInputStream fis = null;
            try {
                fis = new FileInputStream(loggingProperties);
                new PropertyConfigurator(embeddedLogContext).configure(fis);
            } catch (IOException e) {
                ctx.printLine("Unable to configure embedded host controller logging from " + loggingProperties);
            } finally {
                StreamUtils.safeClose(fis);
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
