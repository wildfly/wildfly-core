/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.wildfly.test.security;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import org.jboss.security.plugins.PBEUtils;
import org.jboss.security.vault.SecurityVault;
import org.jboss.security.vault.SecurityVaultException;
import org.jboss.security.vault.SecurityVaultFactory;
import org.picketbox.plugins.vault.PicketBoxSecurityVault;

/**
 * Non-interactive session for {@link VaultTool}
 *
 * @author Peter Skopek
 *
 */
public final class VaultSession {

    public static final String VAULT_ENC_ALGORITHM = "PBEwithMD5andDES";

    static final Charset CHARSET = StandardCharsets.UTF_8;

    private String keystoreURL;
    private String keystorePassword;
    private String keystoreMaskedPassword;
    private String encryptionDirectory;
    private String salt;
    private int iterationCount;

    private SecurityVault vault;
    private String vaultAlias;

    /**
     * Constructor to create VaultSession.
     *
     * @param keystoreURL
     * @param keystorePassword
     * @param encryptionDirectory
     * @param salt
     * @param iterationCount
     * @throws Exception
     */
    public VaultSession(String keystoreURL, String keystorePassword, String encryptionDirectory, String salt, int iterationCount)
            throws Exception {
        this.keystoreURL = keystoreURL;
        this.keystorePassword = keystorePassword;
        this.encryptionDirectory = encryptionDirectory;
        this.salt = salt;
        this.iterationCount = iterationCount;
        validate();
    }

    /**
     * Validate fields sent to this class's constructor.
     */
    private void validate() throws Exception {
        validateKeystoreURL();
        validateEncryptionDirectory();
        validateSalt();
        validateIterationCount();
        validateKeystorePassword();
    }

    protected void validateKeystoreURL() throws Exception {

        File f = new File(keystoreURL);
        if (!f.exists()) {
            throw new Exception(String.format("Keystore '%s' doesn't exist." + "\nkeystore could be created: "
            + "keytool -genseckey -alias Vault -storetype jceks -keyalg AES -keysize 128 -storepass secretsecret -keypass secretsecret -keystore %s",
                    keystoreURL, keystoreURL));
        } else if (!f.canWrite() || !f.isFile()) {
            throw new Exception(String.format("Keystore [%s] is not writable or not a file.", keystoreURL));
        }
    }

    protected void validateKeystorePassword() throws Exception {
        if (keystorePassword == null) {
            throw new Exception("Keystore password has to be specified.");
        }
    }

    protected void validateEncryptionDirectory() throws Exception {
        if (encryptionDirectory == null) {
            throw new Exception("Encryption directory has to be specified.");
        }
        if (!encryptionDirectory.endsWith("/") || encryptionDirectory.endsWith("\\")) {
            encryptionDirectory = encryptionDirectory + (System.getProperty("file.separator", "/"));
        }
        File d = new File(encryptionDirectory);
        if (!d.exists()) {
            if (!d.mkdirs()) {
                throw new Exception(String.format("Cannot create encryption directory %s", d.getAbsolutePath()));
            }
        }
        if (!d.isDirectory()) {
            throw new Exception(String.format("Encryption directory is not a directory or doesn't exist. (%s)", encryptionDirectory));
        }
    }

    protected void validateIterationCount() throws Exception {
        if (iterationCount < 1 && iterationCount > Integer.MAX_VALUE) {
            throw new Exception(String.format("Iteration count has to be within 1 - "
                    + Integer.MAX_VALUE + ", but it is %s.", String.valueOf(iterationCount)));
        }
    }

    protected void validateSalt() throws Exception {
        if (salt == null || salt.length() != 8) {
            throw new Exception("Salt has to be exactly 8 characters long.");
        }
    }

    /**
     * Method to compute masked password based on class attributes.
     *
     * @return masked password prefixed with {link @PicketBoxSecurityVault.PASS_MASK_PREFIX}.
     * @throws Exception
     */
    private String computeMaskedPassword() throws Exception {

        // Create the PBE secret key
        SecretKeyFactory factory = SecretKeyFactory.getInstance(VAULT_ENC_ALGORITHM);

        char[] password = "somearbitrarycrazystringthatdoesnotmatter".toCharArray();
        PBEParameterSpec cipherSpec = new PBEParameterSpec(salt.getBytes(StandardCharsets.UTF_8), iterationCount);
        PBEKeySpec keySpec = new PBEKeySpec(password);
        SecretKey cipherKey = factory.generateSecret(keySpec);

        String maskedPass = PBEUtils.encode64(keystorePassword.getBytes(StandardCharsets.UTF_8), VAULT_ENC_ALGORITHM, cipherKey, cipherSpec);

        return PicketBoxSecurityVault.PASS_MASK_PREFIX + maskedPass;
    }

    /**
     * Initialize the underlying vault.
     *
     * @throws Exception
     */
    private void initSecurityVault() throws Exception {
        try {
            this.vault = SecurityVaultFactory.get();
            this.vault.init(getVaultOptionsMap());
            handshake();
        } catch (SecurityVaultException e) {
            throw new Exception("Exception encountered:", e);
        }
    }

    /**
     * Start the vault with given alias.
     *
     * @param vaultAlias
     * @throws Exception
     */
    public void startVaultSession(String vaultAlias) throws Exception {
        if (vaultAlias == null) {
            throw new Exception("Vault alias has to be specified.");
        }
        this.keystoreMaskedPassword = (org.jboss.security.Util.isPasswordCommand(keystorePassword))
                ? keystorePassword
                : computeMaskedPassword();
        this.vaultAlias = vaultAlias;
        initSecurityVault();
    }

    private Map<String, Object> getVaultOptionsMap() {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put(PicketBoxSecurityVault.KEYSTORE_URL, keystoreURL);
        options.put(PicketBoxSecurityVault.KEYSTORE_PASSWORD, keystoreMaskedPassword);
        options.put(PicketBoxSecurityVault.KEYSTORE_ALIAS, vaultAlias);
        options.put(PicketBoxSecurityVault.SALT, salt);
        options.put(PicketBoxSecurityVault.ITERATION_COUNT, Integer.toString(iterationCount));
        options.put(PicketBoxSecurityVault.ENC_FILE_DIR, encryptionDirectory);
        return options;
    }

    private void handshake() throws SecurityVaultException {
        Map<String, Object> handshakeOptions = new HashMap<String, Object>();
        handshakeOptions.put(PicketBoxSecurityVault.PUBLIC_CERT, vaultAlias);
        vault.handshake(handshakeOptions);
    }

    /**
     * Add secured attribute to specified vault block. This method can be called only after successful
     * startVaultSession() call.
     *
     * @param vaultBlock
     * @param attributeName
     * @param attributeValue
     * @return secured attribute configuration
     */
    public String addSecuredAttribute(String vaultBlock, String attributeName, char[] attributeValue) throws Exception {
        vault.store(vaultBlock, attributeName, attributeValue, null);
        return securedAttributeConfigurationString(vaultBlock, attributeName);
    }

    /**
     * Add secured attribute to specified vault block. This method can be called only after successful
     * startVaultSession() call.
     * After successful storage the secured attribute information will be displayed at standard output.
     * For silent method @see addSecuredAttribute
     *
     * @param vaultBlock
     * @param attributeName
     * @param attributeValue
     * @throws Exception
     */
    public void addSecuredAttributeWithDisplay(String vaultBlock, String attributeName, char[] attributeValue) throws Exception {
        vault.store(vaultBlock, attributeName, attributeValue, null);
        attributeCreatedDisplay(vaultBlock, attributeName);
    }

    /**
     * Check whether secured attribute is already set for given vault block and attribute name. This method can be called only after
     * successful startVaultSession() call.
     *
     * @param vaultBlock
     * @param attributeName
     * @return true is password already exists for given vault block and attribute name.
     * @throws Exception
     */
    public boolean checkSecuredAttribute(String vaultBlock, String attributeName) throws Exception {
        return vault.exists(vaultBlock, attributeName);
    }

    /**
     * This method removes secured attribute stored in {@link SecurityVault}.
     * After successful remove operation returns true. Otherwise false.
     *
     * @param vaultBlock security vault block
     * @param attributeName Attribute name stored in security vault
     * @return true is operation is successful, otherwise false
     * @throws Exception
     */
    public boolean removeSecuredAttribute(String vaultBlock, String attributeName) throws Exception {
        return vault.remove(vaultBlock, attributeName, null);
    }

    /**
     * Display info about stored secured attribute.
     *
     * @param vaultBlock
     * @param attributeName
     */
    private void attributeCreatedDisplay(String vaultBlock, String attributeName) {
        System.out.println(String.format("Secured attribute value has been stored in Vault.\n" +
            "Please make note of the following:\n" +
            "********************************************\n" +
            "Vault Block:%s\n" + "Attribute Name:%s\n" +
            "Configuration should be done as follows:\n" +
            "%s\n" +
            "********************************************", vaultBlock, attributeName, securedAttributeConfigurationString(vaultBlock, attributeName)));
    }

    /**
     * Returns configuration string for secured attribute.
     *
     * @param vaultBlock
     * @param attributeName
     * @return
     */
    private String securedAttributeConfigurationString(String vaultBlock, String attributeName) {
        return "VAULT::" + vaultBlock + "::" + attributeName + "::1";
    }

    /**
     * Display info about vault itself in form of AS7 configuration file.
     */
    public void vaultConfigurationDisplay() {
        System.out.println("Vault Configuration in WildFly configuration file:");
        System.out.println("********************************************");
        System.out.println("...");
        System.out.println("</extensions>");
        System.out.print(vaultConfiguration());
        System.out.println("<management> ...");
        System.out.println("********************************************");
    }

    /**
     * Returns vault configuration string in user readable form.
     * @return
     */
    public String vaultConfiguration() {
        StringBuilder sb = new StringBuilder();
        sb.append("<vault>").append("\n");
        sb.append("  <vault-option name=\"KEYSTORE_URL\" value=\"").append(keystoreURL).append("\"/>").append("\n");
        sb.append("  <vault-option name=\"KEYSTORE_PASSWORD\" value=\"").append(keystoreMaskedPassword).append("\"/>").append("\n");
        sb.append("  <vault-option name=\"KEYSTORE_ALIAS\" value=\"").append(vaultAlias).append("\"/>").append("\n");
        sb.append("  <vault-option name=\"SALT\" value=\"").append(salt).append("\"/>").append("\n");
        sb.append("  <vault-option name=\"ITERATION_COUNT\" value=\"").append(iterationCount).append("\"/>").append("\n");
        sb.append("  <vault-option name=\"ENC_FILE_DIR\" value=\"").append(encryptionDirectory).append("\"/>").append("\n");
        sb.append("</vault>");
        return sb.toString();
    }

    /**
     * Method to get keystore masked password to use further in configuration.
     * Has to be used after {@link startVaultSession} method.
     *
     * @return the keystoreMaskedPassword
     */
    public String getKeystoreMaskedPassword() {
        return keystoreMaskedPassword;
    }

    /**
     * Display format for couple of vault block and attribute name.
     *
     * @param vaultBlock
     * @param attributeName
     * @return formatted {@link String}
     */
    static String blockAttributeDisplayFormat(String vaultBlock, String attributeName) {
        return "[" + vaultBlock + "::" + attributeName + "]";
    }

}
