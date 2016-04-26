/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.manualmode.vault;

import java.io.File;
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

final class TestVaultSession {

    static final String VAULT_ENC_ALGORITHM = "PBEwithMD5andDES";

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
    TestVaultSession(String keystoreURL, String keystorePassword, String encryptionDirectory, String salt, int iterationCount)
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
            throw new Exception(String.format("Keystore '%s' doesn't exist.", keystoreURL));
        } else if (!f.canWrite() || !f.isFile()) {
            throw new Exception(String.format("Keystore [%s] is not writable or not a file.", keystoreURL));
        }
    }

    protected void validateKeystorePassword() throws Exception {
        if (keystorePassword == null) {
            throw new Exception("Keystore password has to be specified");
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
            throw new Exception("Iteration count has to be within 1 - \" + Integer.MAX_VALUE + \", but it is %s.");
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
            throw new Exception(e);
        }
    }

    /**
     * Start the vault with given alias.
     *
     * @param vaultAlias
     * @throws Exception
     */
    void startVaultSession(String vaultAlias) throws Exception {
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
        options.put(PicketBoxSecurityVault.CREATE_KEYSTORE, Boolean.TRUE.toString());
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
    String addSecuredAttribute(String vaultBlock, String attributeName, char[] attributeValue) throws Exception {
        vault.store(vaultBlock, attributeName, attributeValue, null);
        return securedAttributeConfigurationString(vaultBlock, attributeName);
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
     * Returns vault configuration string in user readable form.
     *
     * @return
     */
    String vaultConfiguration() {
        StringBuilder sb = new StringBuilder();
        sb.append("<vault>").append("\n")
                .append("  <vault-option name=\"KEYSTORE_URL\" value=\"" + keystoreURL + "\"/>").append("\n")
                .append("  <vault-option name=\"KEYSTORE_PASSWORD\" value=\"" + keystoreMaskedPassword + "\"/>").append("\n")
                .append("  <vault-option name=\"KEYSTORE_ALIAS\" value=\"" + vaultAlias + "\"/>").append("\n")
                .append("  <vault-option name=\"SALT\" value=\"" + salt + "\"/>").append("\n")
                .append("  <vault-option name=\"ITERATION_COUNT\" value=\"" + iterationCount + "\"/>").append("\n")
                .append("  <vault-option name=\"ENC_FILE_DIR\" value=\"" + encryptionDirectory + "\"/>").append("\n")
                .append("</vault>");
        return sb.toString();
    }

}
