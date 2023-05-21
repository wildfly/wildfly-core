/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.host.controller.security;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.security.sasl.SaslServerFactory;

import org.jboss.as.host.controller.mgmt.RemoteServerPermission;
import org.jboss.as.server.security.sasl.DomainServerSaslServerFactory;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.evidence.Evidence;
import org.wildfly.security.permission.PermissionVerifier;
import org.wildfly.security.sasl.util.AggregateSaslServerFactory;

/**
 * The MSC service responsible for wrapping the configured SASL authentication
 * factory to add support for domain server authentication.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SaslWrappingService implements Service {

    private static final ServiceName SERVER_INVENTORY_SERVICE_NAME = ServiceName.JBOSS.append("host", "controller", "server-inventory");

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
        //ServerInventory serverInventory = serverInventorySupplier.get();
        SaslServerFactory originalServerFactory = originalFactory.getFactory();
        SaslServerFactory domainServerSaslFactory = new DomainServerSaslServerFactory(originalFactory.getSecurityDomain(),
                evidenceVerifierSupplier.get(), PermissionVerifier.from(RemoteServerPermission.getInstance()));


        SaslAuthenticationFactory combinedFactory = SaslAuthenticationFactory.builder()
                .setFactory(new AggregateSaslServerFactory(originalServerFactory, domainServerSaslFactory))
                .setMechanismConfigurationSelector(originalFactory.getMechanismConfigurationSelector())
                .setSecurityDomain(originalFactory.getSecurityDomain())
                .build();
        wrappedFactoryConsumer.accept(combinedFactory);
    }

    @Override
    public void stop(StopContext context) {}

    public static ServiceName install(ServiceTarget serviceTarget, ServiceName originalSaslServerFactory) {
        if (originalSaslServerFactory == null) return null;

        ServiceName wrapperName = originalSaslServerFactory.append("wrapper");

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
