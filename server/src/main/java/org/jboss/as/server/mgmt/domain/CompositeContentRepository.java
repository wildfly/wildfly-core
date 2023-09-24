/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.mgmt.domain;

import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.DeploymentFileRepository;

/**
 * A combination of {@link ContentRepository} and {@code DeploymentFileRepository} when used in the managed domain.
 *
 * @author Emanuel Muckenhuber
 */
interface CompositeContentRepository extends ContentRepository, DeploymentFileRepository {

}
