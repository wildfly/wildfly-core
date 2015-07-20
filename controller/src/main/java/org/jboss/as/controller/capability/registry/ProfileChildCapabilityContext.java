/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller.capability.registry;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;

import java.util.Map;
import java.util.Set;

/**
 * {@link CapabilityContext} for the children of a Host Controller {@code profile} resource.
 * Note this does not include the profile capability itself.
 *
 * @author Brian Stansberry
 *
 * @see ProfilesCapabilityContext
 */
public class ProfileChildCapabilityContext extends IncludingResourceCapabilityContext {

    private static final CapabilityResolutionContext.AttachmentKey<Map<String, Set<CapabilityContext>>> PROFILE_KEY =
            CapabilityResolutionContext.AttachmentKey.create(Map.class);

    public ProfileChildCapabilityContext(String value) {
        super(PROFILE_KEY, PROFILE, value);
    }

    @Override
    public boolean canSatisfyRequirement(CapabilityId dependent, String required, CapabilityResolutionContext context) {
        CapabilityContext dependentContext = dependent.getContext();
        boolean result = equals(dependentContext);
        if (!result && dependentContext instanceof ProfileChildCapabilityContext) {
            Set<CapabilityContext> includers = getIncludingContexts(context);
            result = includers.contains(dependentContext);
        }
        return result;
    }

    @Override
    public boolean requiresConsistencyCheck() {
        return false;
    }

    @Override
    protected CapabilityContext createIncludedContext(String name) {
        return new ProfileChildCapabilityContext(name);
    }
}
