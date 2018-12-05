/*
Copyright 2017 Red Hat, Inc.

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
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import java.io.File;
import java.util.List;
import java.util.UUID;
import org.aesh.command.CommandException;
import org.aesh.readline.Prompt;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_KEY_STORE_NAME;
import org.jboss.as.cli.impl.aesh.cmd.security.ssl.PromptFileCompleter;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * An SSL security builder that handles interaction with user to collect
 * required information.
 *
 * @author jdenise@redhat.com
 */
public class InteractiveSecurityBuilder extends SSLSecurityBuilder {

    private class DNWizard {

        private static final String UNKNOWN = "Unknown";
        private String name = UNKNOWN;
        private String orgUnit = UNKNOWN;
        private String org = UNKNOWN;
        private String city = UNKNOWN;
        private String state = UNKNOWN;
        private String countryCode = UNKNOWN;

        private String buildDN() throws InterruptedException {
            String dnString = null;
            boolean correct = false;
            while (!correct) {
                name = prompt("What is your first and last name?", name);
                orgUnit = prompt("What is the name of your organizational unit?", orgUnit);
                org = prompt("What is the name of your organization?", org);
                city = prompt("What is the name of your City or Locality?", city);
                state = prompt("What is the name of your State or Province?", state);
                countryCode = prompt("What is the two-letter country code for this unit?", countryCode);
                dnString = buildDNString();
                String res = commandInvocation.inputLine(new Prompt("Is " + dnString + " correct y/n [y]?"));
                if (res != null && res.equals("y")) {
                    correct = true;
                    break;
                } else if (res != null && res.equals("n")) {
                    correct = false;
                } else if (res != null && res.length() == 0) {
                    correct = true;
                    break;
                } else {
                    correct = false;
                }
            }
            return dnString;
        }

        private String buildDNString() {
            return "CN=" + name + ", OU=" + orgUnit + ", O=" + org + ", L="
                    + city + ", ST=" + state + ", C=" + countryCode;
        }

        private String prompt(String prompt, String value) throws InterruptedException {
            String res = commandInvocation.inputLine(new Prompt(prompt + " [" + value + "]: "));
            if (res == null || res.length() == 0) {
                res = value;
            }
            return res;
        }
    }

    private String dn;
    private String password;
    private String alias;
    private CLICommandInvocation commandInvocation;
    private String validity;
    private String keyStoreName;
    private String clientCertificate;
    private String trustStoreFileName;
    private String trustStorePassword;
    private boolean validateCertificate;

    public static final String PLACE_HOLDER = "<need user input>";
    private String keyStoreFile;

    private final String defaultKeyStoreFile;
    private final String defaultTrustStoreFile;

    private static final String KEY_ALG = "RSA";
    private static final int KEY_SIZE = 1024;

    public InteractiveSecurityBuilder(String defaultKeyStoreFile, String defaultTrustStoreFile) throws CommandException {
        this.defaultKeyStoreFile = defaultKeyStoreFile;
        this.defaultTrustStoreFile = defaultTrustStoreFile;
    }

    public InteractiveSecurityBuilder setCommandInvocation(CLICommandInvocation commandInvocation) {
        this.commandInvocation = commandInvocation;
        return this;
    }

    @Override
    public void buildRequest(CommandContext ctx, boolean buildRequest) throws Exception {
        // Collect all information

        if (buildRequest) {
            keyStoreFile = PLACE_HOLDER;
            dn = PLACE_HOLDER;
            password = PLACE_HOLDER;
            alias = PLACE_HOLDER;
            validity = PLACE_HOLDER;
            clientCertificate = File.separator + PLACE_HOLDER;
            trustStorePassword = PLACE_HOLDER;
            trustStoreFileName = PLACE_HOLDER;
        } else {
            ctx.printLine("Please provide required pieces of information to enable SSL:");
        }
        String relativeTo = Util.JBOSS_SERVER_CONFIG_DIR;
        boolean ok = false;
        Long v = null;
        String certName;
        String csrName;
        // First keystore file name
        while (keyStoreFile == null) {
            keyStoreFile = commandInvocation.inputLine(new Prompt("Key-store file name (default " + defaultKeyStoreFile + "): "));
            if (keyStoreFile != null && keyStoreFile.length() == 0) {
                keyStoreFile = defaultKeyStoreFile;
            }
            //Check that we are not going to corrupt existing key-stores that are referencing the exact same file.
            List<String> ksNames = ElytronUtil.findMatchingKeyStores(ctx, new File(keyStoreFile), relativeTo);
            if (!ksNames.isEmpty()) {
                throw new CommandException("Error, the file " + keyStoreFile + " is already referenced from " + ksNames
                        + " resources. Use " + SecurityCommand.formatOption(OPT_KEY_STORE_NAME) + " option or choose another file name.");
            }
        }
        int i = keyStoreFile.indexOf(".");
        if (i > 0) {
            certName = keyStoreFile.substring(0, i);
        } else {
            certName = keyStoreFile;
        }
        csrName = certName + ".csr";
        certName += ".pem";

        // then password
        while (password == null) {
            password = commandInvocation.inputLine(new Prompt("Password (blank generated): "));
            if (password != null && password.length() == 0) {
                password = SSLSecurityBuilder.generateRandomPassword();
            }
        }

        // then prompt for dn
        if (dn == null) {
            dn = new DNWizard().buildDN();
        }

        // then validity
        while (validity == null) {
            validity = commandInvocation.inputLine(new Prompt("Validity (in days, blank default): "));
            if (validity != null) {
                if (validity.length() == 0) {
                    v = null;
                } else {
                    try {
                        v = Long.parseLong(validity);
                    } catch (NumberFormatException e) {
                        ctx.printLine("Invalid number " + validity);
                        validity = null;
                    }
                }
            }
        }

        // then alias
        while (alias == null) {
            alias = commandInvocation.inputLine(new Prompt("Alias (blank generated): "));
            if (alias != null && alias.length() == 0) {
                alias = "alias-" + UUID.randomUUID().toString();
            }
        }

        boolean mutual = false;
        if (!buildRequest) {
            // Two way.
            String twoWay = null;
            while (twoWay == null) {
                twoWay = commandInvocation.inputLine(new Prompt("Enable SSL Mutual Authentication y/n (blank n):"));
                if (twoWay != null && twoWay.equals("y")) {
                    mutual = true;
                    break;
                } else if (twoWay != null && twoWay.equals("n")) {
                    mutual = false;
                    break;
                } else if (twoWay != null && twoWay.length() == 0) {
                    mutual = false;
                    break;
                } else {
                    twoWay = null;
                }
            }
            if (mutual) {
                // Prompt for certificate.
                PromptFileCompleter completer = new PromptFileCompleter(commandInvocation.getConfiguration().getAeshContext());
                while (clientCertificate == null || clientCertificate.length() == 0) {
                    clientCertificate = commandInvocation.inputLine(new Prompt("Client certificate (path to pem file): "), completer);
                    if (clientCertificate != null && clientCertificate.length() > 0) {
                        if (!new File(clientCertificate).exists()) {
                            clientCertificate = null;
                            ctx.printLine("The specified file doesn't exist");
                        }
                    }
                }

                // Prompt for validity.
                String val = null;
                while (val == null) {
                    val = commandInvocation.inputLine(new Prompt("Validate certificate y/n (blank y): "));
                    if (val != null && val.equals("y")) {
                        validateCertificate = true;
                        break;
                    } else if (val != null && val.equals("n")) {
                        validateCertificate = false;
                        break;
                    } else if (val != null && val.length() == 0) {
                        validateCertificate = true;
                        break;
                    } else {
                        val = null;
                    }
                }

                // a trust-store file name
                while (trustStoreFileName == null) {
                    trustStoreFileName = commandInvocation.inputLine(new Prompt("Trust-store file name (" + defaultTrustStoreFile + "): "));
                    if (trustStoreFileName != null && trustStoreFileName.length() == 0) {
                        trustStoreFileName = defaultTrustStoreFile;
                    }
                    // Check that we are not going to corrupt existing key-stores that are referencing the exact same file.
                    List<String> ksNames = ElytronUtil.findMatchingKeyStores(ctx, new File(trustStoreFileName), Util.JBOSS_SERVER_CONFIG_DIR);
                    if (!ksNames.isEmpty()) {
                        throw new CommandException("Error, the file " + trustStoreFileName + " is already referenced from " + ksNames
                                + " resources. Use " + SecurityCommand.formatOption(SecurityCommand.OPT_TRUST_STORE_NAME) + " option or choose another file name.");
                    }
                }

                // a trust-store password
                while (trustStorePassword == null) {
                    trustStorePassword = commandInvocation.inputLine(new Prompt("Password (blank generated): "));
                    if (trustStorePassword != null && trustStorePassword.length() == 0) {
                        trustStorePassword = SSLSecurityBuilder.generateRandomPassword();
                    }
                }
            }
        }

        if (!buildRequest) {
            String reply = null;
            while (reply == null) {
                ctx.printLine("\nSSL options:");
                ctx.printLine("key store file: " + keyStoreFile + "\n"
                        + "distinguished name: " + dn + "\n"
                        + "password: " + password + "\n"
                        + "validity: " + (validity.length() == 0 ? "default" : validity) + "\n"
                        + "alias: " + alias);
                if (mutual) {
                    ctx.printLine("client certificate: " + clientCertificate);
                    ctx.printLine("trust store file: " + trustStoreFileName);
                    ctx.printLine("trust store password: " + trustStorePassword);
                }
                ctx.printLine("Server keystore file " + keyStoreFile
                        + ", certificate file " + certName + " and " + csrName
                        + " file will be generated in server configuration directory.");
                if (mutual) {
                    ctx.printLine("Server truststore file " + trustStoreFileName + " will be generated in server configuration directory.");
                }

                reply = commandInvocation.inputLine(new Prompt("Do you confirm y/n :"));
                if (reply != null && reply.equals("y")) {
                    ok = true;
                    break;
                } else if (reply != null && !reply.equals("n")) {
                    reply = null;
                }
            }
            if (!ok) {
                throw new CommandException("Ignoring, command not executed.");
            }
        }

        String type = DefaultResourceNames.buildDefaultKeyStoreType(null, ctx);

        String id = UUID.randomUUID().toString();
        setKeyManagerName("key-manager-" + id);
        setSSLContextName("ssl-context-" + id);

        keyStoreName = "key-store-" + id;
        ModelNode request = ElytronUtil.addKeyStore(ctx, keyStoreName, new File(keyStoreFile), relativeTo, password, type, false, null);
        try {
            // For now that is a workaround because we can't add and call operation in same composite.
            // REMOVE WHEN WFCORE-3491 is fixed.
            if (buildRequest) { // echo-dmr
                addStep(request, NO_DESC);
            } else {
                SecurityCommand.execute(ctx, request, SecurityCommand.DEFAULT_FAILURE_CONSUMER);
            }
            // Hard coded algorithm and key size.
            ModelNode request2 = ElytronUtil.generateKeyPair(ctx, keyStoreName, dn, alias, v, KEY_ALG, KEY_SIZE);
            addStep(request2, new FailureDescProvider() {
                @Override
                public String stepFailedDescription() {
                    return "Generating key-pair from "
                            + keyStoreName;
                }
            });
            needKeyStoreStore(keyStoreName);
            final String cName = certName;
            ModelNode request4 = ElytronUtil.exportCertificate(ctx, keyStoreName, new File(certName), relativeTo, alias, true);
            addFinalstep(request4, new FailureDescProvider() {
                @Override
                public String stepFailedDescription() {
                    return "Exporting certificate " + cName
                            + " from key-store " + keyStoreName;
                }
            });
            ModelNode request5 = ElytronUtil.generateSigningRequest(ctx, keyStoreName, new File(csrName), relativeTo, alias);
            addFinalstep(request5, new FailureDescProvider() {
                @Override
                public String stepFailedDescription() {
                    return "Generating signing request "
                            + " from key-store " + keyStoreName;
                }
            });
            // Two way certificate
            if (clientCertificate != null) {
                setTrustedCertificatePath(new File(clientCertificate));
                setTrustStoreFileName(trustStoreFileName);
                setTrustStoreFilePassword(trustStorePassword);
                setValidateCertificate(validateCertificate);
            }
        } catch (Exception ex) {
            try {
                failureOccured(ctx, null);
            } catch (Exception ex2) {
                ex.addSuppressed(ex2);
            }
            throw ex;
        }
        super.buildRequest(ctx, buildRequest);
    }

    @Override
    protected KeyStore buildKeyStore(CommandContext ctx, boolean buildRequest) throws Exception {
        return new KeyStore(keyStoreName, password, alias, false);
    }

    @Override
    public void doFailureOccured(CommandContext ctx) throws Exception {
        // REMOVE WHEN WFCORE-3491 is fixed.
        if (keyStoreName != null) {
            ModelNode req = ElytronUtil.removeKeyStore(ctx, keyStoreName);
            SecurityCommand.execute(ctx, req, SecurityCommand.DEFAULT_FAILURE_CONSUMER, false);
        }
    }
}
