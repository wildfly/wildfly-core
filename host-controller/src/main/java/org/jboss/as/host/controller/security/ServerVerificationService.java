/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.security;

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.evidence.Evidence;

/**
 * An intermediary service used for verification of the connecting domain server.
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ServerVerificationService implements Service {

    static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("host", "controller", "server", "verification");

    public static final ServiceName REGISTRATION_NAME = SERVICE_NAME.append("registration");

    private final Consumer<Predicate<Evidence>> evidencePredicate;
    private Consumer<Consumer<Predicate<Evidence>>> predicateInjectionPoint;

    private volatile Predicate<Evidence> currentPredicate;

    private ServerVerificationService(Consumer<Predicate<Evidence>> evidencePredicate,
            Consumer<Consumer<Predicate<Evidence>>> predicateInjectionPoint) {
        this.evidencePredicate = evidencePredicate;
        this.predicateInjectionPoint = predicateInjectionPoint;
    }

    @Override
    public void start(StartContext context) throws StartException {
        predicateInjectionPoint.accept(this::register);
        evidencePredicate.accept(this::verify);
    }

    private boolean verify(final Evidence evidence) {
        Predicate<Evidence> currentPredicate = this.currentPredicate;

        return currentPredicate != null ? currentPredicate.test(evidence) : false;
    }

    private void register(Predicate<Evidence> evidencePredicate) {
        this.currentPredicate = evidencePredicate;
    }

    @Override
    public void stop(StopContext context) {
        currentPredicate = null;
    }

    public static void install(ServiceTarget target) {
        ServiceBuilder<?> sb = target.addService(SERVICE_NAME);
        Consumer<Predicate<Evidence>> evidencePredicate = sb.provides(SERVICE_NAME);
        Consumer<Consumer<Predicate<Evidence>>> predicateInjectionPoint = sb.provides(REGISTRATION_NAME);
        sb.setInstance(new ServerVerificationService(evidencePredicate, predicateInjectionPoint))
            .setInitialMode(ServiceController.Mode.ON_DEMAND)
            .install();
    }

}
