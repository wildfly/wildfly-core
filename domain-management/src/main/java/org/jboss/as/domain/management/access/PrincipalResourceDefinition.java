/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.AuthorizerConfiguration;
import org.jboss.as.controller.access.management.WritableAuthorizerConfiguration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * A {@link ResourceDefinition} representing a Principal either specified in the include or exclude list of a role mapping.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class PrincipalResourceDefinition extends SimpleResourceDefinition {

    public enum Type {
        GROUP, USER
    }

    public static final SimpleAttributeDefinition TYPE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.TYPE,
            ModelType.STRING, false).setValidator(EnumValidator.create(Type.class)).build();

    public static final SimpleAttributeDefinition REALM = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.REALM,
            ModelType.STRING, true).setDeprecated(ModelVersion.create(5)).build();

    public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME,
            ModelType.STRING, false).build();

    private PrincipalResourceDefinition(final PathElement pathElement, final OperationStepHandler add,
            final OperationStepHandler remove) {
        super(pathElement, DomainManagementResolver.getResolver("core.access-control.role-mapping.principal"), add, remove);
    }

    static PrincipalResourceDefinition includeResourceDefinition(final WritableAuthorizerConfiguration authorizerConfiguration) {
        return new PrincipalResourceDefinition(PathElement.pathElement(INCLUDE), PrincipalAdd.createForInclude(authorizerConfiguration),
                PrincipalRemove.createForInclude(authorizerConfiguration));
    }

    static PrincipalResourceDefinition excludeResourceDefinition(final WritableAuthorizerConfiguration authorizerConfiguration) {
        return new PrincipalResourceDefinition(PathElement.pathElement(EXCLUDE), PrincipalAdd.createForExclude(authorizerConfiguration),
                PrincipalRemove.createForExclude(authorizerConfiguration));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(TYPE, null);
        resourceRegistration.registerReadOnlyAttribute(REALM, null);
        resourceRegistration.registerReadOnlyAttribute(NAME, null);
    }

    static AuthorizerConfiguration.PrincipalType getPrincipalType(final OperationContext context, final ModelNode model)
            throws OperationFailedException {
        return AuthorizerConfiguration.PrincipalType.valueOf(TYPE.resolveValue(context, model.get(TYPE.getName())).asString());
    }

    static String getRealm(final OperationContext context, final ModelNode model) throws OperationFailedException {
        ModelNode value;
        if ((value = model.get(REALM.getName())).isDefined()) {
            return REALM.resolveValue(context, value).asString();
        } else {
            return null;
        }
    }

    static String getName(final OperationContext context, final ModelNode model) throws OperationFailedException {
        return NAME.resolveValue(context, model.get(NAME.getName())).asString();
    }

}
