/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.service;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.junit.Test;

/**
 * Unit test for {@link ChildTargetService}.
 * @author Paul Ferraro
 */
public class ChildTargetServiceTestCase {
    @Test
    public void test() throws StartException {
        ServiceInstaller installer = mock(ServiceInstaller.class);

        Service service = new ChildTargetService(installer);

        ServiceTarget target = mock(ServiceTarget.class);
        StartContext startContext = mock(StartContext.class);

        doReturn(target).when(startContext).getChildTarget();

        service.start(startContext);

        verify(installer).install(target);

        StopContext stopContext = mock(StopContext.class);

        service.stop(stopContext);

        verifyNoMoreInteractions(installer);
        verifyNoInteractions(stopContext);
    }
}
