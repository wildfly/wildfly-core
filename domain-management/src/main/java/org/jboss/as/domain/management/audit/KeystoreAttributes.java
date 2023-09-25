/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.audit;

import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.domain.management.ModelDescriptionConstants;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Utility class to hold some {@link org.jboss.as.controller.AttributeDefinition}s used in different resources.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class KeystoreAttributes {

    public static final String KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME = "key-password-credential-reference";
    public static final String KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME = "keystore-password-credential-reference";

    public static final SimpleAttributeDefinition ALIAS = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ALIAS,
            ModelType.STRING, true).setXmlName(ModelDescriptionConstants.ALIAS)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES).build();

        public static final ObjectTypeAttributeDefinition KEY_PASSWORD_CREDENTIAL_REFERENCE = CredentialReference.getAttributeBuilder(KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME, KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME, true, false)
                    .setAlternatives(ModelDescriptionConstants.KEY_PASSWORD)
                    .build();

    public static final SimpleAttributeDefinition KEY_PASSWORD = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.KEY_PASSWORD, ModelType.STRING, true)
            .setXmlName(ModelDescriptionConstants.KEY_PASSWORD)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setAlternatives(KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME)
            .build();

    public static final ObjectTypeAttributeDefinition KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE = CredentialReference.getAttributeBuilder(KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME, KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME, true, false)
                    .setAlternatives(ModelDescriptionConstants.KEYSTORE_PASSWORD)
                    .build();

    public static final SimpleAttributeDefinition KEYSTORE_PASSWORD = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.KEYSTORE_PASSWORD, ModelType.STRING, true)
            .setXmlName(ModelDescriptionConstants.KEYSTORE_PASSWORD)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setAlternatives(KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME)
            .build();

    public static final SimpleAttributeDefinition KEYSTORE_PATH = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.KEYSTORE_PATH, ModelType.STRING, true)
            .setXmlName(ModelDescriptionConstants.PATH)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES).build();

    public static final SimpleAttributeDefinition KEYSTORE_RELATIVE_TO = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.KEYSTORE_RELATIVE_TO, ModelType.STRING, true)
            .setXmlName(ModelDescriptionConstants.RELATIVE_TO).setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES).build();

    public static final SimpleAttributeDefinition KEYSTORE_PROVIDER = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.KEYSTORE_PROVIDER, ModelType.STRING, true)
            .setXmlName(ModelDescriptionConstants.PROVIDER)
            .setDefaultValue(new ModelNode(ModelDescriptionConstants.JKS))
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleAttributeDefinition GENERATE_SELF_SIGNED_CERTIFICATE_HOST = new SimpleAttributeDefinitionBuilder(org.jboss.as.controller.descriptions.ModelDescriptionConstants.GENERATE_SELF_SIGNED_CERTIFICATE_HOST, ModelType.STRING, true)
            .setAllowExpression(true)
            .setRequired(false)
            .setRequires(ModelDescriptionConstants.KEY_PASSWORD)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();
    /** Prevent instantiation */
    private KeystoreAttributes() {}
}
