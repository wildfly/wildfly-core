/*
 * Copyright 2020 Red Hat, Inc.
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

package org.jboss.as.domain.http.server;

import static org.jboss.as.domain.http.server.ConsoleAvailability.CONSOLE_AVAILABILITY_CAPABILITY;
import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistry;
import org.jboss.as.controller.persistence.NullConfigurationPersister;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the transitions of the console availability based on a process state transitions when they are installed as
 * part of a model controller service.
 *
 * @author <a href="mailto:yborgess@redhat.com">Yeray Borges</a>
 */
public class ConsoleAvailabilityUnitTestCase {
    private ServiceContainer container;
    private ControlledProcessState controlledProcessState;
    private Supplier<ConsoleAvailability> caSupplier;

    @Before
    public void setupController() throws InterruptedException {
        container = ServiceContainer.Factory.create("test");
        ServiceTarget target = container.subTarget();

        this.controlledProcessState = new ControlledProcessState(true);

        ServiceBuilder<?> sb = target.addService(ServiceName.of("ModelController"));
        this.caSupplier = sb.requires(CONSOLE_AVAILABILITY_CAPABILITY.getCapabilityServiceName());

        ConsoleAvailabilityControllerTmp caService = new ConsoleAvailabilityControllerTmp(controlledProcessState);

        ControlledProcessStateService.addService(target, controlledProcessState);
        ConsoleAvailabilityService.addService(target, () -> {});

        sb.setInstance(caService)
                .install();

        caService.awaitStartup(30, TimeUnit.SECONDS);
    }

    @After
    public void shutdownServiceContainer() {
        if (container != null) {
            container.shutdown();
            try {
                container.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finally {
                container = null;
            }
        }
    }

    @Test
    public void testConsoleAvailabilityTransitions(){
        assertEquals(ControlledProcessState.State.RUNNING, controlledProcessState.getState());
        ConsoleAvailability consoleAvailability = this.caSupplier.get();
        assertEquals(true, consoleAvailability.isAvailable());

        this.controlledProcessState.setStopping();
        assertEquals(false, consoleAvailability.isAvailable());

        this.controlledProcessState.setStopped();
        assertEquals(false, consoleAvailability.isAvailable());

        this.controlledProcessState.setStarting();
        assertEquals(false, consoleAvailability.isAvailable());

        consoleAvailability.setAvailable();
        assertEquals(true, consoleAvailability.isAvailable());
        assertEquals(ControlledProcessState.State.STARTING, controlledProcessState.getState());

        this.controlledProcessState.setStopping();
        consoleAvailability.setAvailable();
        assertEquals(false, consoleAvailability.isAvailable());

        this.controlledProcessState.setStopped();
        this.controlledProcessState.setStarting();
        this.controlledProcessState.setRunning();
        assertEquals(true, consoleAvailability.isAvailable());
        this.controlledProcessState.setReloadRequired();
        assertEquals(true, consoleAvailability.isAvailable());
        this.controlledProcessState.setRestartRequired();
        assertEquals(true, consoleAvailability.isAvailable());
    }

    class ConsoleAvailabilityControllerTmp extends TestModelControllerService {

        ConsoleAvailabilityControllerTmp(ControlledProcessState controlledProcessState) {
            super(ProcessType.EMBEDDED_SERVER, new NullConfigurationPersister(), controlledProcessState);
        }

        @Override
        protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
            final ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
            final RuntimeCapabilityRegistry capabilityReg = managementModel.getCapabilityRegistry();

            capabilityReg.registerCapability(
                    new RuntimeCapabilityRegistration(PROCESS_STATE_NOTIFIER_CAPABILITY, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));
            capabilityReg.registerCapability(
                    new RuntimeCapabilityRegistration(CONSOLE_AVAILABILITY_CAPABILITY, CapabilityScope.GLOBAL, new RegistrationPoint(PathAddress.EMPTY_ADDRESS, null)));

            rootRegistration.registerCapability(PROCESS_STATE_NOTIFIER_CAPABILITY);
            rootRegistration.registerCapability(CONSOLE_AVAILABILITY_CAPABILITY);
        }
    }
}
