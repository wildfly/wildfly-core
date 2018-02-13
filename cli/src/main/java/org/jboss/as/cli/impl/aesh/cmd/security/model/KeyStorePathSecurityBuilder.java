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
import org.aesh.command.CommandException;
import org.jboss.as.cli.CommandContext;
import org.jboss.dmr.ModelNode;

/**
 * An SSL Security builder that builds or reuses a KeyStore based on a file
 * path.
 *
 * @author jdenise@redhat.com
 */
public class KeyStorePathSecurityBuilder extends SSLSecurityBuilder {

    private final File path;
    private final String password;
    private String relativeTo;
    private String type;
    private String name;

    public KeyStorePathSecurityBuilder(File path, String password) throws CommandException {
        if (path == null || password == null) {
            throw new CommandException("key-store path and password can't be null");
        }
        this.path = path;
        this.password = password;
    }

    public KeyStorePathSecurityBuilder setRelativeTo(String relativeTo) {
        this.relativeTo = relativeTo;
        return this;
    }

    public KeyStorePathSecurityBuilder setType(String type) {
        this.type = type;
        return this;
    }

    public KeyStorePathSecurityBuilder setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    protected KeyStore buildKeyStore(CommandContext ctx, boolean buildRequest) throws Exception {
        boolean lookupExisting = false;
        validateOptions(ctx);
        if (name == null) {
            name = DefaultResourceNames.buildDefaultKeyStoreName(path, ctx);
            lookupExisting = true;
        }
        if (type == null) {
            type = DefaultResourceNames.buildDefaultKeyStoreType(type, ctx);
        }

        String kName = null;
        // Lookup for existing resource if no name has been provided.
        if (lookupExisting) {
            kName = ElytronUtil.findMatchingKeyStore(ctx, path, relativeTo, password, type, null, null);
        }
        boolean existing = false;
        if (kName == null) {
            ModelNode request = ElytronUtil.addKeyStore(ctx, name, path, relativeTo, password, type, null, null);
            addStep(request, new FailureDescProvider() {
                @Override
                public String stepFailedDescription() {
                    return "Adding key-store using file "
                            + path;
                }
            });
        } else {
            existing = true;
            name = kName;
        }
        return new KeyStore(name, password, existing);
    }

    private void validateOptions(CommandContext ctx) throws Exception {
        if (relativeTo == null) {
            if (!path.exists()) {
                throw new CommandException("key-store path doesn't exist");
            }
        }
        if (name != null) {
            if (ElytronUtil.keyStoreExists(ctx, name)) {
                throw new CommandException("key-store " + name + " already exists");
            }
        }
    }

    @Override
    protected void doFailureOccured(CommandContext ctx) {
        // Nothing to cleanup.
    }

}
