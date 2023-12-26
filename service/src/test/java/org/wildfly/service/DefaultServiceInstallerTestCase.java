/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for {@link GenericServiceInstaller}.
 * @author Paul Ferraro
 */
public class DefaultServiceInstallerTestCase {

    @Test
    public void test() throws StartException {
        ServiceTarget target = mock(ServiceTarget.class);
        ServiceBuilder<?> builder = mock(ServiceBuilder.class);
        ServiceController<?> controller = mock(ServiceController.class);
        ServiceName name = ServiceName.JBOSS;
        Consumer<Object> injector = mock(Consumer.class);
        Function<Object, Object> mapper = mock(Function.class);
        ServiceDependency<Object> dependency = mock(ServiceDependency.class);
        Consumer<Object> captor = mock(Consumer.class);
        Consumer<Object> combinedCaptor = mock(Consumer.class);
        Consumer<Object> startTask = mock(Consumer.class);
        Consumer<Object> stopTask = mock(Consumer.class);
        ArgumentCaptor<Service> service = ArgumentCaptor.forClass(Service.class);
        ArgumentCaptor<ServiceController.Mode> mode = ArgumentCaptor.forClass(ServiceController.Mode.class);

        ServiceInstaller installer = ServiceInstaller.builder(mapper, dependency).provides(name).withDependency(dependency).withCaptor(captor).onStart(startTask).onStop(stopTask).build();

        doReturn(builder).when(target).addService();
        doReturn(injector).when(builder).provides(name);
        doReturn(combinedCaptor).when(injector).andThen(captor);
        doReturn(builder).when(builder).setInstance(service.capture());
        doReturn(builder).when(builder).setInitialMode(mode.capture());
        doReturn(controller).when(builder).install();

        ServiceController<?> result = installer.install(target);

        Assert.assertSame(controller, result);
        Assert.assertSame(ServiceController.Mode.ON_DEMAND, mode.getValue());

        verify(dependency).accept(builder);
        verify(builder).provides(name);

        DefaultServiceTestCase.test(service.getValue(), "value", "mappedValue", combinedCaptor, mapper, dependency, startTask, stopTask);
    }
}
