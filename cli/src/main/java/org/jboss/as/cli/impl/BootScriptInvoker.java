/*
Copyright 2020 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import static org.jboss.as.cli.impl._private.BootScriptInvokerLogger.ROOT_LOGGER;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * CLI script invoker. This is instantiated inside a server during boot in
 * admin-only.
 *
 * @author jdenise
 */
public class BootScriptInvoker implements AdditionalBootCliScriptInvoker {

    private final Properties props = new Properties();
    private final Properties existingProps = new Properties();

    @Override
    public void runCliScript(ModelControllerClient client, File file) {
        ROOT_LOGGER.processScript(file);

        String log = WildFlySecurityManager.getPropertyPrivileged("org.wildfly.internal.cli.boot.hook.script.logging", "false");
        final LogManager logManager = LogManager.getLogManager();
        // Turnoff logger
        if (!Boolean.parseBoolean(log)) {
            if (logManager instanceof org.jboss.logmanager.LogManager) {
                org.jboss.logmanager.LogManager jbossLogManager = (org.jboss.logmanager.LogManager) logManager;
                jbossLogManager.getLogger(CommandContext.class.getName()).setLevel(Level.OFF);
            }
        }
        CommandContext ctx = null;
        String props = WildFlySecurityManager.getPropertyPrivileged("org.wildfly.internal.cli.boot.hook.script.properties", null);

        if (props != null) {
            File propsFile = new File(props);
            if (!propsFile.exists()) {
                throw ROOT_LOGGER.propertiesFileNotFound(propsFile);
            }
            handleProperties(propsFile);
        }
        File logFile = null;
        String logFilePath = WildFlySecurityManager.getPropertyPrivileged("org.wildfly.internal.cli.boot.hook.script.output.file", null);
        if (logFilePath != null) {
            logFile = new File(logFilePath);
        }

        try {
            OutputStream output = null;
            if (logFile != null) {
                output = new FileOutputStream(logFile);
            }
            ctx = new CommandContextImpl(output);
            ctx.bindClient(client);

            processFile(file, ctx);
        } catch (Exception ex) {
            try {
                ROOT_LOGGER.errorProcessingScript(file);
                if (logFile != null) {
                    ROOT_LOGGER.cliOutput();
                    StringBuilder logBuilder = new StringBuilder();
                    for (String line : Files.readAllLines(logFile.toPath())) {
                        logBuilder.append(line).append("\n");
                    }
                    ROOT_LOGGER.error(logBuilder.toString());
                }
            } catch (IOException ex1) {
                RuntimeException rtex = new RuntimeException(ex1);
                rtex.addSuppressed(ex);
                throw rtex;
            }
            throw new RuntimeException(ex);
        } finally {
            if (ctx != null) {
                ctx.terminateSession();
            }
            clearProperties();
        }
        ROOT_LOGGER.doneProcessScript(file);
    }

    private static void processFile(File file, final CommandContext cmdCtx) throws IOException {
        String opline = null;
        try ( BufferedReader reader = new BufferedReader(new FileReader(file))) {
            opline = reader.readLine();
            while (cmdCtx.getExitCode() == 0 && !cmdCtx.isTerminated() && opline != null) {
                ROOT_LOGGER.executeCommand(opline.trim());
                cmdCtx.handle(opline.trim());
                opline = reader.readLine();
            }
        } catch (CommandLineException ex) {
            throw ROOT_LOGGER.unexpectedCommandException(ex, opline, file);
        } catch (Throwable e) {
            throw ROOT_LOGGER.unexpectedException(e, file);
        }
        String warnFile = WildFlySecurityManager.getPropertyPrivileged("org.wildfly.internal.cli.boot.hook.script.warn.file", null);
        if (warnFile != null) {
            File warns = new File(warnFile);
            if (warns.exists()) {
                StringBuilder warnBuilder = new StringBuilder();
                for (String line : Files.readAllLines(warns.toPath())) {
                    warnBuilder.append(line).append("\n");
                }
                ROOT_LOGGER.warn(warnBuilder.toString());
            }

        }
        String errorFile = WildFlySecurityManager.getPropertyPrivileged("org.wildfly.internal.cli.boot.hook.script.error.file", null);
        if (errorFile != null) {
            File errors = new File(errorFile);
            if (errors.exists()) {
                StringBuilder errorBuilder = new StringBuilder();
                for (String line : Files.readAllLines(errors.toPath())) {
                    errorBuilder.append(line).append("\n");
                }
                ROOT_LOGGER.error(errorBuilder.toString());
                throw ROOT_LOGGER.unexpectedErrors(file, errors);
            }

        }
        if (cmdCtx.getExitCode() != 0 || cmdCtx.isTerminated()) {
            throw new RuntimeException("Error applying " + file + " CLI script.");
        }
    }

    private void handleProperties(File propertiesFile) {
        try ( InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(propertiesFile), StandardCharsets.UTF_8)) {
            props.load(inputStreamReader);
            for (String key : props.stringPropertyNames()) {
                String original = WildFlySecurityManager.getPropertyPrivileged(key, null);
                if (original != null) {
                    existingProps.put(key, original);
                }
                WildFlySecurityManager.setPropertyPrivileged(key, props.getProperty(key));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void clearProperties() {
        for (String key : props.stringPropertyNames()) {
            WildFlySecurityManager.clearPropertyPrivileged(key);
        }
        for (String key : existingProps.stringPropertyNames()) {
            WildFlySecurityManager.setPropertyPrivileged(key, existingProps.getProperty(key));
        }
    }

}
