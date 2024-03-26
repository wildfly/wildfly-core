/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.io.OptionList;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * Runtime configuration factory for remoting endpoints.
 *
 * @author Emanuel Muckenhuber
 */
public final class EndpointConfigFactory {

    private EndpointConfigFactory() {
        //
    }

    public static OptionMap populate(final ExpressionResolver resolver, final ModelNode model) throws OperationFailedException {
        OptionMap.Builder builder = OptionMap.builder()
        .set(Options.TCP_NODELAY, Boolean.TRUE)
        .set(Options.REUSE_ADDRESSES, true)
        .addAll(OptionList.resolveOptions(resolver, model, RemotingSubsystemRootResource.OPTIONS));

        return builder.getMap();
    }
}
