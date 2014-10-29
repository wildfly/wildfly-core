/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package org.jboss.as.test.manualmode.vault.module;

import static java.security.AccessController.doPrivileged;

import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.jboss.as.server.services.security.AbstractVaultReader;
import org.jboss.as.server.services.security.VaultReaderException;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.security.vault.SecurityVault;
import org.jboss.security.vault.SecurityVaultException;
import org.jboss.security.vault.SecurityVaultFactory;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.security.manager.action.GetModuleClassLoaderAction;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class RuntimeVaultReader extends AbstractVaultReader {

    private static final Pattern VAULT_PATTERN = Pattern.compile("VAULT::.*::.*::.*");

    private volatile SecurityVault vault;


    /**
     * This constructor should remain protected to keep the vault as invisible
     * as possible, but it needs to be exposed for service plug-ability.
     */
    public RuntimeVaultReader() {
    }

    @Deprecated
    @SuppressWarnings("deprecation")
    protected void createVault(final String fqn, final Map<String, Object> options) throws VaultReaderException {
        createVault(fqn, null, options);
    }

    protected void createVault(final String fqn, final String module, final Map<String, Object> options) throws VaultReaderException {
        Map<String, Object> vaultOptions = new HashMap<String, Object>(options);
        SecurityVault vault = null;
        try {
            vault = AccessController.doPrivileged(new PrivilegedExceptionAction<SecurityVault>() {
                @Override
                public SecurityVault run() throws Exception {
                    if (fqn == null || fqn.isEmpty()) {
                        return SecurityVaultFactory.get();
                    } else if (module == null) {
                        return SecurityVaultFactory.get(fqn);
                    } else {
                        return SecurityVaultFactory.get(getModuleClassLoader(module), fqn);
                    }
                }
            });
        } catch (PrivilegedActionException e) {
            Throwable t = e.getCause();
            if (t instanceof SecurityVaultException) {
                throw new VaultReaderException(t);
            }
            if (t instanceof RuntimeException) {
                throw new VaultReaderException(t);
            }
            throw new RuntimeException(t);
        }
        try {
            vault.init(vaultOptions);
        } catch (SecurityVaultException e) {
            throw new VaultReaderException(e);
        }
        this.vault = vault;
    }

    protected void destroyVault() {
        vault = null;
    }

    public String retrieveFromVault(final String password) throws SecurityException {
        if (isVaultFormat(password)) {

            if (vault == null) {
                throw new SecurityException("vault not initialized");
            }

            try {
                return getValueAsString(password);
            } catch (SecurityVaultException e) {
                throw new SecurityException(e);
            }

        }
        return password;
    }

    private String getValueAsString(String vaultString) throws SecurityVaultException {
        char[] val = getValue(vaultString);
        if (val != null)
            return new String(val);
        return null;
    }

    public boolean isVaultFormat(String str) {
        return str != null && VAULT_PATTERN.matcher(str).matches();
    }

    private char[] getValue(String vaultString) throws SecurityVaultException {
        String[] tokens = tokens(vaultString);
        byte[] sharedKey = null;
        if (tokens.length > 2) {
            // only in case of conversion of old vault implementation
            sharedKey = tokens[3].getBytes(StandardCharsets.UTF_8);
        }

        return vault.retrieve(tokens[1], tokens[2], sharedKey);
    }

    private String[] tokens(String vaultString) {
        StringTokenizer tokenizer = new StringTokenizer(vaultString, "::");
        int length = tokenizer.countTokens();
        String[] tokens = new String[length];

        int index = 0;
        while (tokenizer != null && tokenizer.hasMoreTokens()) {
            tokens[index++] = tokenizer.nextToken();
        }
        return tokens;
    }

    private ModuleClassLoader getModuleClassLoader(final String moduleSpec) throws ModuleLoadException {
        ModuleLoader loader = Module.getCallerModuleLoader();
        final Module module = loader.loadModule(ModuleIdentifier.fromString(moduleSpec));
        return WildFlySecurityManager.isChecking() ? doPrivileged(new GetModuleClassLoaderAction(module)) : module.getClassLoader();
    }
}
