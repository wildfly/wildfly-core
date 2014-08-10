/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.handlers;


import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.util.FingerprintGenerator;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 *
 * @author Claudio Miranda
 */
public class ConnectionInfoHandler implements CommandHandler {

    public static final ConnectionInfoHandler INSTANCE = new ConnectionInfoHandler();

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandHandler#isAvailable(org.jboss.as.cli.CommandContext)
     */
    @Override
    public boolean isAvailable(CommandContext ctx) {
        return true;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandHandler#isBatchMode()
     */
    @Override
    public boolean isBatchMode(CommandContext ctx) {
        return true;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandHandler#handle(org.jboss.as.cli.CommandContext)
     */
    @Override
    public void handle(CommandContext ctx) throws CommandFormatException {
        final StringBuilder buf = new StringBuilder();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if(client == null) {
            buf.append("<connect to the controller and re-run the connection-info command to see the connection information>\n");
        } else {

            boolean disableLocalAuth = (boolean) ctx.get("disableLocalAuth");
            String username = "Local connection authenticated as SuperUser";
            if (disableLocalAuth)
                username = (String) ctx.get("username");
            Date _loggedSince = (Date) ctx.get("logged_since");
            buf.append("Username     - ").append(username).append('\n');
            buf.append("Logged since - ").append(_loggedSince).append('\n');
            X509Certificate[] lastChain = (X509Certificate[]) ctx.get("server_certificate");
            boolean sslConn = lastChain != null;
            if (sslConn) {
                try {
                    for (Certificate current : lastChain) {
                        if (current instanceof X509Certificate) {
                            X509Certificate x509Current = (X509Certificate) current;
                            Map<String, String> fingerprints = FingerprintGenerator.generateFingerprints(x509Current);
                            buf.append("Subject      - " + x509Current.getSubjectX500Principal().getName()).append("\n");
                            buf.append("Issuer       - " + x509Current.getIssuerDN().getName()).append("\n");
                            buf.append("Valid From   - " + x509Current.getNotBefore()).append("\n");
                            buf.append("Valid To     - " + x509Current.getNotAfter()).append("\n");
                            for (String alg : fingerprints.keySet()) {
                                String algName = String.format("%-13s", alg);
                                buf.append(algName + "- " + fingerprints.get(alg)).append("\n");
                            }
                            buf.append("");
                        }
                    }
                } catch (CommandLineException cle) {
                    throw new CommandFormatException("Error trying to generate server certificate fingerprint.", cle);
                }
            } else {
                buf.append("Not an SSL connection.");
            }
        }
        ctx.printLine(buf.toString());
    }

    @Override
    public CommandArgument getArgument(CommandContext ctx, String name) {
        return null;
    }

    @Override
    public boolean hasArgument(CommandContext ctx, String name) {
        return false;
    }

    @Override
    public boolean hasArgument(CommandContext ctx, int index) {
        return false;
    }

    @Override
    public List<CommandArgument> getArguments(CommandContext ctx) {
        return Collections.emptyList();
    }
}
