/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.management.util;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.WildFlyManagedConfiguration;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.wildfly.test.api.Authentication;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
public class CLITestUtil {

    private static final String JBOSS_CLI_CONFIG = "jboss.cli.config";

    private static final String serverAddr = TestSuiteEnvironment.getServerAddress();
    private static final int serverPort = TestSuiteEnvironment.getServerPort();

    private static final String username = Authentication.username;
    private static final String password = Authentication.password;
    private static final boolean isRemote = Boolean.parseBoolean(System.getProperty("org.jboss.as.test.integration.remote", "false"));

    public static CommandContext getCommandContext() throws CliInitializationException {
        setJBossCliConfig();
        return CommandContextFactory.getInstance().newCommandContext(constructUri("remote+http", serverAddr , serverPort), isRemote ? username : null, isRemote ? password.toCharArray() : null);
    }

    public static CommandContext getCommandContext(DomainTestSupport domainTestSupport) throws CliInitializationException {
        return getCommandContext(domainTestSupport.getDomainMasterConfiguration());
    }

    public static CommandContext getCommandContext(WildFlyManagedConfiguration config) throws CliInitializationException {
        setJBossCliConfig();
        return CommandContextFactory.getInstance().newCommandContext(
                constructUri(config.getHostControllerManagementProtocol(),
                        config.getHostControllerManagementAddress(),
                        config.getHostControllerManagementPort()),  isRemote ? username : null, isRemote ? password.toCharArray() : null);
    }

    public static CommandContext getCommandContext(String address, int port, InputStream in, OutputStream out)
            throws CliInitializationException {
        setJBossCliConfig();
        return CommandContextFactory.getInstance().newCommandContext(address + ":" + port, isRemote ? username : null, isRemote ? password.toCharArray() : null, in, out);
    }

    public static CommandContext getCommandContext(String address, int port, InputStream in, OutputStream out, int connectionTimeout)
            throws CliInitializationException {
        setJBossCliConfig();
        return CommandContextFactory.getInstance().newCommandContext(new CommandContextConfiguration.Builder()
                .setController(address + ":" + port)
                .setUsername(isRemote ? username : null)
                .setPassword(isRemote ? password.toCharArray() : null)
                .setConsoleInput(in)
                .setConsoleOutput(out)
                .setDisableLocalAuth(false)
                .setInitConsole(false)
                .setConnectionTimeout(connectionTimeout)
                .build());
    }

    public static CommandContext getCommandContext(String address, int port, InputStream in, OutputStream out, boolean colorOutput, boolean echoCommand)
            throws CliInitializationException {
        setJBossCliConfig();
        return CommandContextFactory.getInstance().newCommandContext(new CommandContextConfiguration.Builder()
                .setController(address + ":" + port)
                .setUsername(isRemote ? username : null)
                .setPassword(isRemote ? password.toCharArray() : null)
                .setConsoleInput(in)
                .setConsoleOutput(out)
                .setDisableLocalAuth(false)
                .setInitConsole(false)
                .setColorOutput(colorOutput)
                .setEchoCommand(echoCommand)
                .build());
    }

    public static CommandContext getCommandContext(String protocol, String address, int port)
            throws CliInitializationException {
        setJBossCliConfig();
        return CommandContextFactory.getInstance().newCommandContext(constructUri(protocol, address, port), isRemote ? username : null, isRemote ? password.toCharArray() : null);
    }

    public static CommandContext getCommandContext(OutputStream out) throws CliInitializationException {
        setJBossCliConfig();
        return CommandContextFactory.getInstance().newCommandContext(constructUri(null, serverAddr , serverPort), isRemote ? username : null, isRemote ? password.toCharArray() : null, null, out);
    }

    public static CommandContext getCommandContext(DomainTestSupport domainTestSupport, InputStream in, OutputStream out) throws CliInitializationException {
        setJBossCliConfig();
        WildFlyManagedConfiguration config = domainTestSupport.getDomainMasterConfiguration();
        return CommandContextFactory.getInstance().
                newCommandContext(constructUri(config.getHostControllerManagementProtocol(),
                        config.getHostControllerManagementAddress(),
                        config.getHostControllerManagementPort()), isRemote ? username : null, isRemote ? password.toCharArray() : null,
                        in, out);
    }

    protected static void setJBossCliConfig() {
        final String jbossCliConfig = SecurityActions.getSystemProperty(JBOSS_CLI_CONFIG);
        if(jbossCliConfig == null) {
            final String jbossDist = System.getProperty("jboss.dist");
            if(jbossDist == null) {
                fail("jboss.dist system property is not set");
            }
            SecurityActions.setSystemProperty(JBOSS_CLI_CONFIG, jbossDist + File.separator + "bin" + File.separator + "jboss-cli.xml");
        }
    }

    private static String constructUri(final String protocol, final String host, final int port) throws CliInitializationException {
        try {
            URI uri = new URI(protocol, null, host, port, null, null, null);
            // String the leading '//' if there is no protocol.
            return protocol == null ? uri.toString().substring(2) : uri.toString();
        } catch (URISyntaxException e) {
            throw new CliInitializationException("Unable to convert URI", e);
        }
    }

    public static CommandContextConfiguration.Builder getCommandContextBuilder(String address, int port, InputStream in, OutputStream out)
            throws CliInitializationException {
        setJBossCliConfig();
        return new CommandContextConfiguration.Builder()
                .setController(address + ":" + port)
                .setUsername(isRemote ? username : null)
                .setPassword(isRemote ? password.toCharArray() : null)
                .setConsoleInput(in)
                .setConsoleOutput(out)
                .setDisableLocalAuth(false)
                .setInitConsole(false);
    }
}