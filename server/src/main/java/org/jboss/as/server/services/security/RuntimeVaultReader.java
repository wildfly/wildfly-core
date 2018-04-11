/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.as.server.services.security;

import static java.security.AccessController.doPrivileged;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.server.logging.ServerLogger;
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
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class RuntimeVaultReader extends AbstractVaultReader {

    private volatile Object lazyVaultReader;
    /**
     * This constructor should remain protected to keep the vault as invisible
     * as possible, but it needs to be exposed for service plug-ability.
     */
    public RuntimeVaultReader() {
        //if PB is not available we want to throw an exception here
        //also if we are in a modular environment we want to avoid loading/linking
        //the picketbox module, as loading the module too early breaks
        //EE8 preview mode
        if(getClass().getClassLoader() instanceof ModuleClassLoader) {
            if (!Module.getBootModuleLoader().iterateModules("org.picketbox", false).hasNext()) {
                throw new RuntimeException();
            }
        } else {
            try {
                Class.forName("org.jboss.security.vault.SecurityVaultFactory");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private VaultReaderImpl getVault() {
        if(lazyVaultReader == null) {
            synchronized (this) {
                if(lazyVaultReader == null) {
                    try {
                        Class<?> lazyVaultReader = getClass().getClassLoader().loadClass("org.jboss.as.server.services.security.VaultReaderImpl");
                        this.lazyVaultReader = lazyVaultReader.getConstructor().newInstance();
                    } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                        throw new VaultReaderException(e);
                    }
                }
            }
        }
        return (VaultReaderImpl) lazyVaultReader;
    }

    @Override
    protected void createVault(String fqn, Map<String, Object> options) throws VaultReaderException {
        getVault().createVault(fqn, options);
    }

    @Override
    protected void createVault(String fqn, String module, Map<String, Object> options) throws VaultReaderException {
        getVault().createVault(fqn, module, options);
    }

    @Override
    protected void destroyVault() {
        getVault().destroyVault();
    }

    @Override
    public boolean isVaultFormat(String toCheck) {
        return getVault().isVaultFormat(toCheck);
    }

    @Override
    public String retrieveFromVault(String vaultedData) {
        return getVault().retrieveFromVault(vaultedData);
    }

}

final class VaultReaderImpl extends AbstractVaultReader {

    private volatile Object vault;
    private final AtomicBoolean missingVaultLogged = new AtomicBoolean();

    public VaultReaderImpl() {

    }

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
                    } else if (module == null ){
                        return SecurityVaultFactory.get(fqn);
                    } else {
                        return SecurityVaultFactory.get(getModuleClassLoader(module), fqn);
                    }
                }
            });
        } catch (PrivilegedActionException e) {
            Throwable t = e.getCause();
            if (!(t instanceof Error)) {
                throw ServerLogger.ROOT_LOGGER.cannotCreateVault(t, t);
            }
            throw (Error) t;
        }
        try {
            vault.init(vaultOptions);
        } catch (SecurityVaultException e) {
            throw ServerLogger.ROOT_LOGGER.cannotCreateVault(e, e);
        }
        this.vault = vault;
    }

    protected void destroyVault() {
        //TODO - there are no cleanup methods in the vault itself
        vault = null;
        missingVaultLogged.set(false);
    }

    @Override
    public String retrieveFromVault(final String vaultedData) throws VaultReaderException {
        if (isVaultFormat(vaultedData)) {
            SecurityVault theVault = (SecurityVault) vault;
            if (theVault != null) {
                try {
                    char[] val = getValue(theVault, vaultedData);
                    if (val != null) {
                        return new String(val);
                    }
                } catch (SecurityVaultException e) {
                    // We assume that SVE represents some sort of error with the vault
                    // or some sort of security violation like a vault impl that uses the
                    // shared key (PicketBoxSecurityVault doesn't) and the provided key is incorrect.
                    // We don't treat these as lookup failures. In theory an incorrect shared key
                    // could be regarded as such, but we choose not to, partly because a
                    // wrong key is more serious, and partly because there is no way to
                    // discriminate between different types of SecurityVaultException.

                    // Wrap in the appropriate runtime exception and rethrow
                    throw ServerLogger.ROOT_LOGGER.vaultReaderException(e);
                }
            } else {
                // No vault is the same as a lookup miss.

                // One time only log an ERROR, since using vaulted data without a vault configured
                // is likely a mistake and the logging will help the user understand the reasons for
                // the NoSuchItemException exception we will throw below
                if (missingVaultLogged.compareAndSet(false, true)) {
                    ServerLogger.ROOT_LOGGER.vaultNotInitializedException();
                }
            }

            // If we get here some sort of lookup miss occurred
            throw new NoSuchItemException();
        }
        return vaultedData;
    }

    @Override
    public boolean isVaultFormat(String str) {
        return str != null && STANDARD_VAULT_PATTERN.matcher(str).matches();
    }

    private static char[] getValue(SecurityVault vault, String vaultString) throws SecurityVaultException {
        String[] tokens = tokens(vaultString);
        byte[] sharedKey = null;
        if (tokens.length > 3) {
            // only in case of conversion of old vault implementation
            sharedKey = tokens[3].getBytes(StandardCharsets.UTF_8);
        }

        // Check for existence before retrieving as retrieving nonexistent data throws SecurityVaultException
        return vault.exists(tokens[1], tokens[2]) ? vault.retrieve(tokens[1], tokens[2], sharedKey) : null;
    }

    private static String[] tokens(String vaultString) {
        StringTokenizer tokenizer = new StringTokenizer(vaultString, "::");
        int length = tokenizer.countTokens();
        String[] tokens = new String[length];

        int index = 0;
        while (tokenizer.hasMoreTokens()) {
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