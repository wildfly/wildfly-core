/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.io.OptionList;
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

    /**
     * creates option map for remoting connections
     * @param resolver
     * @param model
     * @param defaults
     * @return
     * @throws OperationFailedException
     * @deprecated configuring xnio worker options is no longer supported and should be replaced for referencing IO subsystem
     */
    @Deprecated
    public static OptionMap create(final ExpressionResolver resolver, final ModelNode model, final OptionMap defaults) throws OperationFailedException {
        final OptionMap map = OptionMap.builder()
                .addAll(defaults)
                .set(Options.WORKER_READ_THREADS, RemotingSubsystemRootResource.WORKER_READ_THREADS.resolveModelAttribute(resolver, model).asInt())
                .set(Options.WORKER_TASK_CORE_THREADS, RemotingSubsystemRootResource.WORKER_TASK_CORE_THREADS.resolveModelAttribute(resolver, model).asInt())
                .set(Options.WORKER_TASK_KEEPALIVE, RemotingSubsystemRootResource.WORKER_TASK_KEEPALIVE.resolveModelAttribute(resolver, model).asInt())
                .set(Options.WORKER_TASK_LIMIT, RemotingSubsystemRootResource.WORKER_TASK_LIMIT.resolveModelAttribute(resolver, model).asInt())
                .set(Options.WORKER_TASK_MAX_THREADS, RemotingSubsystemRootResource.WORKER_TASK_MAX_THREADS.resolveModelAttribute(resolver, model).asInt())
                .set(Options.WORKER_WRITE_THREADS, RemotingSubsystemRootResource.WORKER_WRITE_THREADS.resolveModelAttribute(resolver, model).asInt())
                .set(Options.WORKER_READ_THREADS, RemotingSubsystemRootResource.WORKER_READ_THREADS.resolveModelAttribute(resolver, model).asInt())
                .getMap();
        return map;
    }

}
