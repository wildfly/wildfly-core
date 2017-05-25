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
package org.jboss.as.cli.handlers;


import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Map;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.ConnectionInfo;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.util.FingerprintGenerator;
import org.jboss.as.cli.util.SimpleTable;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Claudio Miranda
 */
public class ConnectionInfoHandler extends CommandHandlerWithHelp {

    public ConnectionInfoHandler() {
        this("connection-info");
    }

    public ConnectionInfoHandler(String command) {
        super(command);
    }


    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandHandler#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        final ModelControllerClient client = ctx.getModelControllerClient();
        if(client == null) {
            ctx.printLine("<connect to the controller and re-run the connection-info command to see the connection information>\n");
        } else {

            ConnectionInfo connInfo = ctx.getConnectionInfo();
            String username = null;

            final ModelNode req = new ModelNode();
            req.get(Util.OPERATION).set("whoami");
            req.get(Util.ADDRESS).setEmptyList();
            req.get("verbose").set(true);

            try {
                final ModelNode response = client.execute(req);
                if(Util.isSuccess(response)) {
                    if (response.hasDefined(Util.RESULT)) {
                        final ModelNode result = response.get(Util.RESULT);
                        if(result.hasDefined("identity")) {
                            username = result.get("identity").get("username").asString();
                        }
                        if (result.hasDefined("mapped-roles")) {
                            String strRoles = result.get("mapped-roles").asString();
                            String grantedStr = "granted role";
                            // a comma is contained in the string if there is more than one role
                            if (strRoles.indexOf(',') > 0)
                                grantedStr = "granted roles";
                            username = username + ", "+ grantedStr + " " + strRoles;
                        } else {
                            username = username + " has no role associated.";
                        }
                    } else {
                        username = "result was not available.";
                    }
                } else {
                    ctx.printLine(Util.getFailureDescription(response));
                }
            } catch (IOException e) {
                throw new CommandFormatException("Failed to get the AS release info: " + e.getLocalizedMessage());
            }

            SimpleTable st = new SimpleTable(2, ctx.getTerminalWidth());
            st.addLine(new String[]{"Username", username});
            st.addLine(new String[]{"Logged since", connInfo.getLoggedSince().toString()});
            X509Certificate[] lastChain = connInfo.getServerCertificates();
            boolean sslConn = lastChain != null;
            if (sslConn) {
                try {
                    for (Certificate current : lastChain) {
                        if (current instanceof X509Certificate) {
                            X509Certificate x509Current = (X509Certificate) current;
                            Map<String, String> fingerprints = FingerprintGenerator.generateFingerprints(x509Current);
                            st.addLine(new String[] {"Subject", x509Current.getSubjectX500Principal().getName()});
                            st.addLine(new String[] {"Issuer", x509Current.getIssuerDN().getName()});
                            st.addLine(new String[] {"Valid from", x509Current.getNotBefore().toString()});
                            st.addLine(new String[] {"Valid to", x509Current.getNotAfter().toString()});
                            for (String alg : fingerprints.keySet()) {
                                st.addLine(new String[] {alg, fingerprints.get(alg)});
                            }
                        }
                    }
                } catch (CommandLineException cle) {
                    throw new CommandFormatException("Error trying to generate server certificate fingerprint.", cle);
                }
            } else {
                st.addLine(new String[] {"Not an SSL connection.", ""});
            }
            ctx.printLine(st.toString());
        }
    }


}
