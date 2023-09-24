/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import org.aesh.command.CommandException;
import org.jboss.as.cli.CommandContext;

/**
 * An SSL Security builder that retrieves an existing KeyStore.
 *
 * @author jdenise@redhat.com
 */
public class KeyStoreNameSecurityBuilder extends SSLSecurityBuilder {

    private final String name;

    public KeyStoreNameSecurityBuilder(String name) throws CommandException {
        this.name = name;
    }

    @Override
    protected KeyStore buildKeyStore(CommandContext ctx, boolean buildRequest) throws Exception {
        return ElytronUtil.getKeyStore(ctx, name);
    }

    @Override
    protected void doFailureOccured(CommandContext ctx) {
    }

}
