/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceTarget;

/**
 * {@link org.jboss.msc.service.ServiceTarget} whose builders support capability requirements.
 * @author Paul Ferraro
 */
public interface RequirementServiceTarget extends ServiceTarget {

    @Override
    RequirementServiceBuilder<?> addService();

    @Override
    RequirementServiceTarget addListener(LifecycleListener listener);

    @Override
    RequirementServiceTarget removeListener(LifecycleListener listener);

    @Override
    RequirementServiceTarget subTarget();
}
