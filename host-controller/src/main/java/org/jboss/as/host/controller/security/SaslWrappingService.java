/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.security;
import static org.jboss.as.server.security.sasl.Constants.JBOSS_DOMAIN_SERVER;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.security.sasl.SaslServerFactory;

import org.jboss.as.server.security.sasl.DomainServerSaslServerFactory;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.MechanismConfigurationSelector;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.evidence.Evidence;
import org.wildfly.security.sasl.util.AggregateSaslServerFactory;

/**
 * The MSC service responsible for wrapping the configured SASL authentication
 * factory to add support for domain server authentication.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SaslWrappingService implements Service {

    private final Supplier<SaslAuthenticationFactory> originalFactorySupplier;
    private final Consumer<SaslAuthenticationFactory> wrappedFactoryConsumer;
    private final Supplier<Predicate<Evidence>> evidenceVerifierSupplier;

    SaslWrappingService(Supplier<SaslAuthenticationFactory> originalFactorySupplier,
            Consumer<SaslAuthenticationFactory> wrappedFactoryConsumer,
            Supplier<Predicate<Evidence>> evidenceVerifierSupplier) {
        this.originalFactorySupplier = originalFactorySupplier;
        this.wrappedFactoryConsumer = wrappedFactoryConsumer;
        this.evidenceVerifierSupplier = evidenceVerifierSupplier;
    }

    @Override
    public void start(StartContext context) throws StartException {
        SaslAuthenticationFactory originalFactory = originalFactorySupplier.get();
        SaslServerFactory originalServerFactory = originalFactory.getFactory();
        SaslServerFactory domainServerSaslFactory = new DomainServerSaslServerFactory(originalFactory.getSecurityDomain(),
                evidenceVerifierSupplier.get());

        MechanismConfigurationSelector originalMechanismConfigurationSelector = originalFactory
                .getMechanismConfigurationSelector();
        MechanismConfigurationSelector forJBossDomainServer = MechanismConfigurationSelector
                .predicateSelector(mi -> JBOSS_DOMAIN_SERVER.equals(mi.getMechanismName()), MechanismConfiguration.EMPTY);

        SaslAuthenticationFactory combinedFactory = SaslAuthenticationFactory.builder()
                .setFactory(new AggregateSaslServerFactory(originalServerFactory, domainServerSaslFactory))
                .setMechanismConfigurationSelector(
                        MechanismConfigurationSelector.aggregate(originalMechanismConfigurationSelector, forJBossDomainServer))
                .setSecurityDomain(originalFactory.getSecurityDomain()).build();
        wrappedFactoryConsumer.accept(combinedFactory);
    }

    @Override
    public void stop(StopContext context) {}

    public static ServiceName install(ServiceTarget serviceTarget, ServiceName originalSaslServerFactory, String forInterface) {
        if (originalSaslServerFactory == null) return null;

        ServiceName wrapperName = originalSaslServerFactory.append("wrapper", forInterface);

        ServiceBuilder<?> sb = serviceTarget.addService(wrapperName);
        Supplier<SaslAuthenticationFactory> originalFactorySupplier = sb.requires(originalSaslServerFactory);
        Consumer<SaslAuthenticationFactory> wrappedFactoryConsumer = sb.provides(wrapperName);
        Supplier<Predicate<Evidence>> evidenceVerifierSupplier = sb.requires(ServerVerificationService.SERVICE_NAME);
        sb.setInstance(new SaslWrappingService(originalFactorySupplier, wrappedFactoryConsumer, evidenceVerifierSupplier))
            .setInitialMode(ServiceController.Mode.ON_DEMAND)
            .install();

        return wrapperName;
    }
}
