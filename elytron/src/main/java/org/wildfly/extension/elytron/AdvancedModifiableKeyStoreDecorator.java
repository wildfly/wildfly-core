/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DIRECTORY;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.wildfly.extension.elytron.Capabilities.CERTIFICATE_AUTHORITY_ACCOUNT_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_STORE_CAPABILITY;
import static org.wildfly.extension.elytron.CertificateChainAttributeDefinitions.writeCertificate;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron.ElytronExtension.isServerOrHostController;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.RELATIVE_TO;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CRLReason;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;

import javax.security.auth.x500.X500Principal;

import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.LongRangeValidator;
import org.jboss.as.controller.operations.validation.StringAllowedValuesValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.wildfly.common.bytes.ByteStringBuilder;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.FileAttributeDefinitions.PathResolver;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.pem.Pem;
import org.wildfly.security.x500.X500;
import org.wildfly.security.x500.cert.PKCS10CertificateSigningRequest;
import org.wildfly.security.x500.cert.SelfSignedX509CertificateAndSigningKey;
import org.wildfly.security.x500.cert.X509CertificateChainAndSigningKey;
import org.wildfly.security.x500.cert.acme.AcmeAccount;
import org.wildfly.security.x500.cert.acme.AcmeClientSpi;
import org.wildfly.security.x500.cert.acme.AcmeException;

/**
 * A {@link ResourceDefinition} that wraps an existing resource and adds operations for advanced {@link KeyStore} manipulation.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
class AdvancedModifiableKeyStoreDecorator extends ModifiableKeyStoreDecorator {

    static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    static final DateTimeFormatter NOT_VALID_BEFORE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT).withZone(ZoneId.systemDefault());

    // Common attributes
    static final SimpleAttributeDefinition ALIAS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALIAS, ModelType.STRING)
            .setAllowExpression(true)
            .setMinSize(1)
            .build();

    static final SimpleAttributeDefinition SIGNATURE_ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SIGNATURE_ALGORITHM, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .build();

    static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NAME, ModelType.STRING)
            .setAllowExpression(true)
            .setMinSize(1)
            .build();

    static final SimpleAttributeDefinition CRITICAL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CRITICAL, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VALUE, ModelType.STRING)
            .setAllowExpression(true)
            .setMinSize(1)
            .build();

    static final ObjectTypeAttributeDefinition EXTENSION = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.EXTENSION, NAME, CRITICAL, VALUE)
            .build();

    static final ObjectListAttributeDefinition EXTENSIONS = new ObjectListAttributeDefinition.Builder(ElytronDescriptionConstants.EXTENSIONS, EXTENSION)
            .setRequired(false)
            .setAllowDuplicates(false)
            .build();

    static final ObjectTypeAttributeDefinition CREDENTIAL_REFERENCE = CredentialReference.getAttributeBuilder(true, true).build();

    static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(FileAttributeDefinitions.PATH)
            .setRequired(true)
            .build();

    private static final AcmeClientSpi acmeClient;
    static {
        acmeClient = loadAcmeClient();
    }

    private static AcmeClientSpi loadAcmeClient() {
        for (AcmeClientSpi acmeClient : ServiceLoader.load(AcmeClientSpi.class, ElytronSubsystemMessages.class.getClassLoader())) {
            return acmeClient;
        }
        throw ROOT_LOGGER.unableToInstatiateAcmeClientSpiImplementation();
    }

    static ResourceDefinition wrap(ResourceDefinition resourceDefinition) {
        return new AdvancedModifiableKeyStoreDecorator(resourceDefinition);
    }

    private AdvancedModifiableKeyStoreDecorator(ResourceDefinition resourceDefinition) {
        super(resourceDefinition);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        ResourceDescriptionResolver resolver = ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.MODIFIABLE_KEY_STORE);

        if (isServerOrHostController(resourceRegistration)) { // server-only operations
            // Create key pair / self-signed certificate
            GenerateKeyPairHandler.register(resourceRegistration, resolver);

            // Create PKCS #10 CSR
            GenerateCertificateSigningRequestHandler.register(resourceRegistration, resolver);

            // Import certificate or certificate chain from a file
            ImportCertificateHandler.register(resourceRegistration, resolver);

            // Export certificate to a file
            ExportCertificateHandler.register(resourceRegistration, resolver);

            // Move an existing entry to a new alias
            ChangeAliasHandler.register(resourceRegistration, resolver);

            // Obtain a signed certificate from a certificate authority
            ObtainCertificateHandler.register(resourceRegistration, resolver);

            // Revoke a signed certificate
            RevokeCertificateHandler.register(resourceRegistration, resolver);

            // Check if a signed certificate is due for renewal
            ShouldRenewCertificateHandler.register(resourceRegistration, resolver);
        }
    }

    static class GenerateKeyPairHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING, true)
                .setAllowExpression(true)
                .setMinSize(1)
                .build();

        static final SimpleAttributeDefinition KEY_SIZE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_SIZE, ModelType.INT, true)
                .setAllowExpression(true)
                .setValidator(new KeySizeValidator())
                .build();

        static final SimpleAttributeDefinition DISTINGUISHED_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DISTINGUISHED_NAME, ModelType.STRING)
                .setAllowExpression(true)
                .setMinSize(1)
                .build();

        static final SimpleAttributeDefinition NOT_BEFORE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NOT_BEFORE, ModelType.STRING, true)
                .setAllowExpression(true)
                .setValidator(new NotBeforeValidator())
                .build();

        static final SimpleAttributeDefinition VALIDITY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VALIDITY, ModelType.LONG, true)
                .setAllowExpression(true)
                .setValidator(new LongRangeValidator(1L))
                .setDefaultValue(new ModelNode(90))
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.GENERATE_KEY_PAIR, descriptionResolver)
                            .setParameters(ALIAS, ALGORITHM, SIGNATURE_ALGORITHM, KEY_SIZE, DISTINGUISHED_NAME,
                                    NOT_BEFORE, VALIDITY, EXTENSIONS, CREDENTIAL_REFERENCE)
                            .setRuntimeOnly()
                            .build(),
                    new GenerateKeyPairHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            ModifiableKeyStoreService keyStoreService = getModifiableKeyStoreService(context);
            KeyStore keyStore = keyStoreService.getModifiableValue();

            String alias = ALIAS.resolveModelAttribute(context, operation).asString();
            String algorithm = ALGORITHM.resolveModelAttribute(context, operation).asStringOrNull();
            String signatureAlgorithm = SIGNATURE_ALGORITHM.resolveModelAttribute(context, operation).asStringOrNull();
            Integer keySize = KEY_SIZE.resolveModelAttribute(context, operation).asIntOrNull();
            String distinguishedName = DISTINGUISHED_NAME.resolveModelAttribute(context, operation).asString();
            String notBefore = NOT_BEFORE.resolveModelAttribute(context, operation).asStringOrNull();
            Long validity = VALIDITY.resolveModelAttribute(context, operation).asLong();
            ModelNode extensions = EXTENSIONS.resolveModelAttribute(context, operation);
            ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier = null;
            ModelNode credentialReference = CREDENTIAL_REFERENCE.resolveModelAttribute(context, operation);
            if (credentialReference.isDefined()) {
                credentialSourceSupplier = CredentialReference.getCredentialSourceSupplier(context, CREDENTIAL_REFERENCE, operation, null);
            }
            char[] keyPassword = resolveKeyPassword(((KeyStoreService) keyStoreService), credentialSourceSupplier);

            try {
                if (keyStore.containsAlias(alias)) {
                    throw ROOT_LOGGER.keyStoreAliasAlreadyExists(alias);
                }
                SelfSignedX509CertificateAndSigningKey.Builder certAndKeyBuilder = SelfSignedX509CertificateAndSigningKey.builder();
                certAndKeyBuilder.setDn(new X500Principal(distinguishedName));
                if (algorithm != null) {
                    certAndKeyBuilder.setKeyAlgorithmName(algorithm);
                }
                if (signatureAlgorithm != null) {
                    certAndKeyBuilder.setSignatureAlgorithmName(signatureAlgorithm);
                }
                if (keySize != null) {
                    certAndKeyBuilder.setKeySize(keySize);
                }
                final ZonedDateTime notBeforeDateTime;
                if (notBefore != null) {
                    notBeforeDateTime = ZonedDateTime.from(NOT_VALID_BEFORE_FORMATTER.parse(notBefore));
                } else {
                    notBeforeDateTime = ZonedDateTime.now();
                }
                certAndKeyBuilder.setNotValidBefore(notBeforeDateTime);
                certAndKeyBuilder.setNotValidAfter(notBeforeDateTime.plusDays(validity));
                if (extensions.isDefined()) {
                    for (ModelNode extension : extensions.asList()) {
                        Boolean critical = CRITICAL.resolveModelAttribute(context, extension).asBoolean();
                        String extensionName = NAME.resolveModelAttribute(context, extension).asString();
                        String extensionValue = VALUE.resolveModelAttribute(context, extension).asString();
                        certAndKeyBuilder.addExtension(critical, extensionName, extensionValue);
                    }
                }

                SelfSignedX509CertificateAndSigningKey certAndKey = certAndKeyBuilder.build();
                final PrivateKey privateKey = certAndKey.getSigningKey();
                final X509Certificate[] certChain = new X509Certificate[1];
                certChain[0] = certAndKey.getSelfSignedCertificate();
                keyStore.setKeyEntry(alias, privateKey, keyPassword, certChain);
            } catch (Exception e) {
                rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, credentialReference);
                if (e instanceof IllegalArgumentException) {
                    throw new OperationFailedException(e);
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static class GenerateCertificateSigningRequestHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition DISTINGUISHED_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DISTINGUISHED_NAME, ModelType.STRING, true)
                .setAllowExpression(true)
                .setMinSize(1)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.GENERATE_CERTIFICATE_SIGNING_REQUEST, descriptionResolver)
                            .setParameters(ALIAS, SIGNATURE_ALGORITHM, DISTINGUISHED_NAME,
                                    EXTENSIONS, CREDENTIAL_REFERENCE, PATH, RELATIVE_TO)
                            .setRuntimeOnly()
                            .build(),
                    new GenerateCertificateSigningRequestHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            ModifiableKeyStoreService keyStoreService = getModifiableKeyStoreService(context);
            KeyStore keyStore = keyStoreService.getModifiableValue();

            String alias = ALIAS.resolveModelAttribute(context, operation).asString();
            String signatureAlgorithm = SIGNATURE_ALGORITHM.resolveModelAttribute(context, operation).asStringOrNull();
            String distinguishedName = DISTINGUISHED_NAME.resolveModelAttribute(context, operation).asStringOrNull();
            ModelNode extensions = EXTENSIONS.resolveModelAttribute(context, operation);
            ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier = null;
            ModelNode credentialReference = CREDENTIAL_REFERENCE.resolveModelAttribute(context, operation);
            if (credentialReference.isDefined()) {
                credentialSourceSupplier = CredentialReference.getCredentialSourceSupplier(context, CREDENTIAL_REFERENCE, operation, null);
            }
            char[] keyPassword = resolveKeyPassword(((KeyStoreService) keyStoreService), credentialSourceSupplier);
            String path = PATH.resolveModelAttribute(context, operation).asString();
            String relativeTo = RELATIVE_TO.resolveModelAttribute(context, operation).asStringOrNull();
            PathResolver pathResolver = pathResolver();
            File resolvedPath = ((KeyStoreService) keyStoreService).getResolvedPath(pathResolver, path, relativeTo);

            try {
                if (! keyStore.containsAlias(alias)) {
                    throw ROOT_LOGGER.keyStoreAliasDoesNotExist(alias);
                }
                if (! keyStore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class)) {
                    throw ROOT_LOGGER.keyStoreAliasDoesNotIdentifyPrivateKeyEntry(alias);
                }
                final PrivateKey privateKey;
                try {
                    privateKey = (PrivateKey) keyStore.getKey(alias, keyPassword);
                } catch (UnrecoverableKeyException e) {
                    throw ROOT_LOGGER.unableToObtainPrivateKey(alias);
                }
                if (privateKey == null) {
                    throw ROOT_LOGGER.unableToObtainPrivateKey(alias);
                }
                final Certificate certificate = keyStore.getCertificate(alias);
                if (certificate == null) {
                    throw ROOT_LOGGER.unableToObtainCertificate(alias);
                }

                PKCS10CertificateSigningRequest.Builder csrBuilder = PKCS10CertificateSigningRequest.builder();
                csrBuilder.setSigningKey(privateKey);
                csrBuilder.setCertificate(certificate);
                if (signatureAlgorithm != null) {
                    csrBuilder.setSignatureAlgorithmName(signatureAlgorithm);
                }
                if (distinguishedName != null) {
                    csrBuilder.setSubjectDn(new X500Principal(distinguishedName));
                }
                if (extensions.isDefined()) {
                    for (ModelNode extension : extensions.asList()) {
                        Boolean critical = CRITICAL.resolveModelAttribute(context, extension).asBoolean();
                        String extensionName = NAME.resolveModelAttribute(context, extension).asString();
                        String extensionValue = VALUE.resolveModelAttribute(context, extension).asString();
                        csrBuilder.addExtension(critical, extensionName, extensionValue);
                    }
                }

                PKCS10CertificateSigningRequest csr = csrBuilder.build();
                try (FileOutputStream fos = new FileOutputStream(resolvedPath)) {
                    fos.write(csr.getPem());
                }
            } catch (Exception e) {
                rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, credentialReference);
                if (e instanceof OperationFailedException) {
                    throw (OperationFailedException) e;
                } else if (e instanceof IllegalArgumentException) {
                    throw new OperationFailedException(e);
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static class ImportCertificateHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition TRUST_CACERTS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.TRUST_CACERTS, ModelType.BOOLEAN, true)
                .setAllowExpression(true)
                .setDefaultValue(ModelNode.FALSE)
                .build();

        static final SimpleAttributeDefinition VALIDATE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VALIDATE, ModelType.BOOLEAN, true)
                .setAllowExpression(true)
                .setDefaultValue(ModelNode.TRUE)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.IMPORT_CERTIFICATE, descriptionResolver)
                            .setParameters(ALIAS, CREDENTIAL_REFERENCE, PATH, RELATIVE_TO, TRUST_CACERTS, VALIDATE)
                            .setRuntimeOnly()
                            .build(),
                    new ImportCertificateHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            ModifiableKeyStoreService keyStoreService = getModifiableKeyStoreService(context);
            KeyStore keyStore = keyStoreService.getModifiableValue();

            String alias = ALIAS.resolveModelAttribute(context, operation).asString();
            ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier = null;
            ModelNode credentialReference = CREDENTIAL_REFERENCE.resolveModelAttribute(context, operation);
            if (credentialReference.isDefined()) {
                credentialSourceSupplier = CredentialReference.getCredentialSourceSupplier(context, CREDENTIAL_REFERENCE, operation, null);
            }
            char[] keyPassword = resolveKeyPassword(((KeyStoreService) keyStoreService), credentialSourceSupplier);
            String path = PATH.resolveModelAttribute(context, operation).asString();
            String relativeTo = RELATIVE_TO.resolveModelAttribute(context, operation).asStringOrNull();
            PathResolver pathResolver = pathResolver();
            File resolvedPath = ((KeyStoreService) keyStoreService).getResolvedPath(pathResolver, path, relativeTo);
            boolean trustCacerts = TRUST_CACERTS.resolveModelAttribute(context, operation).asBoolean();
            boolean validate = VALIDATE.resolveModelAttribute(context, operation).asBoolean();

            try {
                final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                if (keyStore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class)) {
                    // import the response from a certificate signing request
                    final PrivateKey privateKey;
                    try {
                        privateKey = (PrivateKey) keyStore.getKey(alias, keyPassword);
                    } catch (UnrecoverableKeyException e) {
                        throw ROOT_LOGGER.unableToObtainPrivateKey(alias);
                    }
                    if (privateKey == null) {
                        throw ROOT_LOGGER.unableToObtainPrivateKey(alias);
                    }
                    final Certificate certificate = keyStore.getCertificate(alias);
                    if (certificate == null) {
                        throw ROOT_LOGGER.unableToObtainCertificate(alias);
                    }
                    final PublicKey publicKey = certificate.getPublicKey();
                    final Collection<? extends Certificate> reply;
                    try (FileInputStream fis = new FileInputStream(resolvedPath)) {
                        reply = certificateFactory.generateCertificates(fis);
                    } catch (FileNotFoundException e) {
                        throw ROOT_LOGGER.certificateFileDoesNotExist(e);
                    }
                    if (reply.isEmpty()) {
                        throw ROOT_LOGGER.noCertificatesFoundInCertificateReply();
                    }
                    final Certificate[] replyCertificates = reply.toArray(new Certificate[reply.size()]);
                    X509Certificate[] certificateChain;

                    if (replyCertificates.length == 1) {
                        // reply is a single certificate, build up certificate chain
                        final X509Certificate replyCertificate = (X509Certificate) replyCertificates[0];
                        if (! replyCertificate.getPublicKey().equals(publicKey)) {
                            throw ROOT_LOGGER.publicKeyFromCertificateReplyDoesNotMatchKeyStore();
                        }
                        if (replyCertificate.equals(certificate)) {
                            throw ROOT_LOGGER.certificateReplySameAsCertificateFromKeyStore();
                        }
                        final HashMap<Principal, HashSet<X509Certificate>> certificatesMap = getKeyStoreCertificates(keyStore, getCacertsKeyStore(trustCacerts));
                        certificateChain = X500.createX509CertificateChain(replyCertificate, certificatesMap);
                    } else {
                        // reply is a certificate chain, ensure the certificates in the chain are in the correct order (PKCS #7 certificate chains are unordered)
                        certificateChain = X500.asOrderedX509CertificateChain(publicKey, replyCertificates);
                        if (validate) {
                            // check that the top-most certificate in the chain is actually trusted
                            final X509Certificate lastCertificate = certificateChain[certificateChain.length - 1];
                            final X509Certificate certificateOrIssuer = getCertificateOrIssuerFromKeyStores(lastCertificate, keyStore, getCacertsKeyStore(trustCacerts));
                            if (certificateOrIssuer == null) {
                                writeCertificate(context.getResult().get(ElytronDescriptionConstants.CERTIFICATE), lastCertificate);
                                throw ROOT_LOGGER.topMostCertificateFromCertificateReplyNotTrusted();
                            }
                            if (! lastCertificate.equals(certificateOrIssuer)) {
                                X509Certificate[] newCertificateChain = Arrays.copyOf(certificateChain, certificateChain.length + 1);
                                newCertificateChain[newCertificateChain.length - 1] = certificateOrIssuer;
                                certificateChain = newCertificateChain;
                            }
                        }
                    }
                    keyStore.setKeyEntry(alias, privateKey, keyPassword, certificateChain);
                } else if (! keyStore.containsAlias(alias)) {
                    // import a trusted certificate
                    final X509Certificate trustedCertificate;
                    try (FileInputStream fis = new FileInputStream(resolvedPath)) {
                        trustedCertificate = (X509Certificate) certificateFactory.generateCertificate(fis);
                    } catch (FileNotFoundException e) {
                        throw ROOT_LOGGER.certificateFileDoesNotExist(e);
                    }
                    if (validate) {
                        String trustedCertificateAlias = keyStore.getCertificateAlias(trustedCertificate);
                        if (trustedCertificateAlias != null) {
                            throw ROOT_LOGGER.trustedCertificateAlreadyInKeyStore(trustedCertificateAlias);
                        }
                        final KeyStore cacertsKeyStore = getCacertsKeyStore(trustCacerts);
                        if (trustedCertificate.getIssuerDN().equals(trustedCertificate.getSubjectDN())) {
                            trustedCertificate.verify(trustedCertificate.getPublicKey());
                            // self-signed, not found in keystore
                            if (cacertsKeyStore != null) {
                                trustedCertificateAlias = cacertsKeyStore.getCertificateAlias(trustedCertificate);
                                if (trustedCertificateAlias != null) {
                                    throw ROOT_LOGGER.trustedCertificateAlreadyInCacertsKeyStore(trustedCertificateAlias);
                                }
                            }
                            writeCertificate(context.getResult().get(ElytronDescriptionConstants.CERTIFICATE), trustedCertificate);
                            throw ROOT_LOGGER.unableToDetermineIfCertificateIsTrusted();
                        } else {
                            try {
                                final HashMap<Principal, HashSet<X509Certificate>> certificatesMap = getKeyStoreCertificates(keyStore, cacertsKeyStore);
                                X500.createX509CertificateChain(trustedCertificate, certificatesMap);
                            } catch (IllegalArgumentException e) {
                                writeCertificate(context.getResult().get(ElytronDescriptionConstants.CERTIFICATE), trustedCertificate);
                                throw ROOT_LOGGER.unableToDetermineIfCertificateIsTrusted();
                            }
                        }
                    }
                    keyStore.setCertificateEntry(alias, trustedCertificate);
                }
            } catch (Exception e) {
                rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, credentialReference);
                if (e instanceof OperationFailedException) {
                    throw (OperationFailedException) e;
                } else {
                    throw new RuntimeException(e);
                }
            }
        }

        private static KeyStore getCacertsKeyStore(final boolean trustCacerts) throws Exception {
            KeyStore cacertsKeyStore = null;
            if (trustCacerts) {
                File cacertsFile = new File(System.getProperty("java.home") + File.separator + "lib"
                        + File.separator + "security" + File.separator + "cacerts");
                try (FileInputStream fis = new FileInputStream(cacertsFile)) {
                    cacertsKeyStore = KeyStore.getInstance("JKS");
                    cacertsKeyStore.load(fis, null);
                }
            }
            return cacertsKeyStore;
        }

        private static HashMap<Principal, HashSet<X509Certificate>> getKeyStoreCertificates(final KeyStore... keyStores) throws KeyStoreException {
            final HashMap<Principal, HashSet<X509Certificate>> certificatesMap = new HashMap<>();
            for (KeyStore keyStore : keyStores) {
                if (keyStore != null) {
                    Enumeration<String> aliases = keyStore.aliases();
                    while (aliases.hasMoreElements()) {
                        String alias = aliases.nextElement();
                        Certificate certificate = keyStore.getCertificate(alias);
                        if (certificate != null && certificate instanceof X509Certificate) {
                            X509Certificate x509Certificate = ((X509Certificate) certificate);
                            HashSet<X509Certificate> principalCertificates = certificatesMap.get(x509Certificate.getSubjectDN());
                            if (principalCertificates == null) {
                                principalCertificates = new HashSet<>();
                                certificatesMap.put(x509Certificate.getSubjectDN(), principalCertificates);
                            }
                            principalCertificates.add(x509Certificate);
                        }
                    }
                }
            }
            return certificatesMap;
        }

        private static X509Certificate getCertificateOrIssuerFromKeyStores(final X509Certificate certificate, final KeyStore... keyStores) throws KeyStoreException {
            for (KeyStore keyStore : keyStores) {
                if (keyStore != null) {
                    if (keyStore.getCertificateAlias(certificate) != null) {
                        return certificate;
                    } else {
                        Enumeration<String> aliases = keyStore.aliases();
                        while (aliases.hasMoreElements()) {
                            String alias = aliases.nextElement();
                            Certificate issuerCertificate = keyStore.getCertificate(alias);
                            if (issuerCertificate != null && issuerCertificate instanceof X509Certificate) {
                                X509Certificate x509IssuerCertificate = ((X509Certificate) issuerCertificate);
                                if (x509IssuerCertificate.getSubjectDN().equals(certificate.getIssuerDN())) {
                                    try {
                                        certificate.verify(x509IssuerCertificate.getPublicKey());
                                        return x509IssuerCertificate;
                                    } catch (Exception e) {
                                        // keep looking
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return null;
        }
    }

    static class ExportCertificateHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition PEM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PEM, ModelType.BOOLEAN, true)
                .setAllowExpression(true)
                .setDefaultValue(ModelNode.FALSE)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.EXPORT_CERTIFICATE, descriptionResolver)
                            .setParameters(ALIAS, PATH, RELATIVE_TO, PEM)
                            .setRuntimeOnly()
                            .build(),
                    new ExportCertificateHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            ModifiableKeyStoreService keyStoreService = getModifiableKeyStoreService(context);
            KeyStore keyStore = keyStoreService.getModifiableValue();

            String alias = ALIAS.resolveModelAttribute(context, operation).asString();
            String path = PATH.resolveModelAttribute(context, operation).asString();
            String relativeTo = RELATIVE_TO.resolveModelAttribute(context, operation).asStringOrNull();
            PathResolver pathResolver = pathResolver();
            File resolvedPath = ((KeyStoreService) keyStoreService).getResolvedPath(pathResolver, path, relativeTo);
            boolean pem = PEM.resolveModelAttribute(context, operation).asBoolean();

            try {
                if (! keyStore.containsAlias(alias)) {
                    throw ROOT_LOGGER.keyStoreAliasDoesNotExist(alias);
                }
                final X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
                if (certificate == null) {
                    throw ROOT_LOGGER.unableToObtainCertificate(alias);
                }
                try (FileOutputStream fos = new FileOutputStream(resolvedPath)) {
                    if (pem) {
                        ByteStringBuilder pemCertificate = new ByteStringBuilder();
                        Pem.generatePemX509Certificate(pemCertificate, certificate);
                        fos.write(pemCertificate.toArray());
                    } else {
                        fos.write(certificate.getEncoded());
                    }
                }
            } catch (Exception e) {
                if (e instanceof OperationFailedException) {
                    throw (OperationFailedException) e;
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static class ChangeAliasHandler extends ElytronRuntimeOnlyHandler {

        static final SimpleAttributeDefinition NEW_ALIAS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NEW_ALIAS, ModelType.STRING)
                .setAllowExpression(true)
                .setMinSize(1)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.CHANGE_ALIAS, descriptionResolver)
                            .setParameters(ALIAS, NEW_ALIAS, CREDENTIAL_REFERENCE)
                            .setRuntimeOnly()
                            .build(),
                    new ChangeAliasHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            ModifiableKeyStoreService keyStoreService = getModifiableKeyStoreService(context);
            KeyStore keyStore = keyStoreService.getModifiableValue();

            String alias = ALIAS.resolveModelAttribute(context, operation).asString();
            String newAlias = NEW_ALIAS.resolveModelAttribute(context, operation).asString();
            ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier = null;
            ModelNode credentialReference = CREDENTIAL_REFERENCE.resolveModelAttribute(context, operation);
            if (credentialReference.isDefined()) {
                credentialSourceSupplier = CredentialReference.getCredentialSourceSupplier(context, CREDENTIAL_REFERENCE, operation, null);
            }
            char[] keyPassword = resolveKeyPassword(((KeyStoreService) keyStoreService), credentialSourceSupplier);

            try {
                if (! keyStore.containsAlias(alias)) {
                    throw ROOT_LOGGER.keyStoreAliasDoesNotExist(alias);
                }
                if (keyStore.containsAlias(newAlias)) {
                    throw ROOT_LOGGER.keyStoreAliasAlreadyExists(newAlias);
                }
                KeyStore.Entry entry;
                KeyStore.PasswordProtection passwordProtection = null;
                try {
                    // try without a password first
                    entry = keyStore.getEntry(alias, passwordProtection);
                } catch (UnrecoverableEntryException e) {
                    passwordProtection = new KeyStore.PasswordProtection(keyPassword);
                    try {
                        entry = keyStore.getEntry(alias, passwordProtection);
                    } catch (UnrecoverableEntryException ex) {
                        throw ROOT_LOGGER.unableToObtainEntry(alias);
                    }
                }
                if (entry == null) {
                    throw ROOT_LOGGER.unableToObtainEntry(alias);
                }
                keyStore.setEntry(newAlias, entry, passwordProtection);
                if (keyStore.containsAlias(alias)) {
                    keyStore.deleteEntry(alias);
                }
            } catch (Exception e) {
                rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, credentialReference);
                if (e instanceof OperationFailedException) {
                    throw (OperationFailedException) e;
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static class ObtainCertificateHandler extends ElytronRuntimeOnlyHandler {

        static final StringListAttributeDefinition DOMAIN_NAMES = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.DOMAIN_NAMES)
                .build();

        static final SimpleAttributeDefinition CERTIFICATE_AUTHORITY_ACCOUNT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT, ModelType.STRING, false)
                .setMinSize(1)
                .setRestartAllServices()
                .setCapabilityReference(CERTIFICATE_AUTHORITY_ACCOUNT_CAPABILITY, KEY_STORE_CAPABILITY, true)
                .build();

        static final SimpleAttributeDefinition AGREE_TO_TERMS_OF_SERVICE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AGREE_TO_TERMS_OF_SERVICE, ModelType.BOOLEAN, true)
                .setAllowExpression(true)
                .build();

        static final SimpleAttributeDefinition STAGING = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.STAGING, ModelType.BOOLEAN, true)
                .setAllowExpression(true)
                .setDefaultValue(ModelNode.FALSE)
                .build();

        static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING, true)
                .setAllowExpression(true)
                .setValidator(new StringAllowedValuesValidator("RSA", "EC"))
                .setDefaultValue(new ModelNode("RSA"))
                .setMinSize(1)
                .build();

        static final SimpleAttributeDefinition KEY_SIZE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEY_SIZE, ModelType.INT, true)
                .setAllowExpression(true)
                .setDefaultValue(new ModelNode(2048))
                .setValidator(new KeySizeValidator())
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.OBTAIN_CERTIFICATE, descriptionResolver)
                            .setParameters(ALIAS, DOMAIN_NAMES, CERTIFICATE_AUTHORITY_ACCOUNT, AGREE_TO_TERMS_OF_SERVICE, STAGING, ALGORITHM, KEY_SIZE, CREDENTIAL_REFERENCE)
                            .setRuntimeOnly()
                            .build(),
                    new ObtainCertificateHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            ModifiableKeyStoreService keyStoreService = getModifiableKeyStoreService(context);
            KeyStore keyStore = keyStoreService.getModifiableValue();

            String alias = ALIAS.resolveModelAttribute(context, operation).asString();
            List<String> domainNames = DOMAIN_NAMES.unwrap(context, operation);
            String certificateAuthorityAccountName = CERTIFICATE_AUTHORITY_ACCOUNT.resolveModelAttribute(context, operation).asString();
            Boolean agreeToTermsOfService = AGREE_TO_TERMS_OF_SERVICE.resolveModelAttribute(context, operation).asBooleanOrNull();
            boolean staging = STAGING.resolveModelAttribute(context, operation).asBoolean();
            String algorithm = ALGORITHM.resolveModelAttribute(context, operation).asString();
            Integer keySize = KEY_SIZE.resolveModelAttribute(context, operation).asInt();
            ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier = null;
            ModelNode credentialReference = CREDENTIAL_REFERENCE.resolveModelAttribute(context, operation);
            if (credentialReference.isDefined()) {
                credentialSourceSupplier = CredentialReference.getCredentialSourceSupplier(context, CREDENTIAL_REFERENCE, operation, null);
            }
            char[] keyPassword = resolveKeyPassword(((KeyStoreService) keyStoreService), credentialSourceSupplier);

            try {
                final AcmeAccount acmeAccount = getAcmeAccount(context, certificateAuthorityAccountName, staging);
                if (agreeToTermsOfService != null) {
                    acmeAccount.setTermsOfServiceAgreed(agreeToTermsOfService);
                }
                // ensure we have an account with the certificate authority and that it's up to date
                boolean created = false;
                if (acmeAccount.getAccountUrl() == null) {
                    created = acmeClient.createAccount(acmeAccount, staging);
                }
                if (! created) {
                    acmeClient.updateAccount(acmeAccount, staging, acmeAccount.isTermsOfServiceAgreed(), acmeAccount.getContactUrls());
                }
                X509CertificateChainAndSigningKey certificateChainAndSigningKey = acmeClient.obtainCertificateChain(acmeAccount, staging, algorithm, keySize, domainNames.toArray(new String[domainNames.size()]));
                keyStore.setKeyEntry(alias, certificateChainAndSigningKey.getSigningKey(), keyPassword, certificateChainAndSigningKey.getCertificateChain());
                ((KeyStoreService) keyStoreService).save();
            } catch (Exception e) {
                rollbackCredentialStoreUpdate(CREDENTIAL_REFERENCE, context, credentialReference);
                if (e instanceof IllegalArgumentException || e instanceof AcmeException) {
                    throw new OperationFailedException(e);
                } else {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static class RevokeCertificateHandler extends ElytronRuntimeOnlyHandler {
        static final String UNSPECIFIED = "UNSPECIFIED";
        static final String KEY_COMPROMISE = "KEYCOMPROMISE";
        static final String CA_COMPROMISE = "CACOMPROMISE";
        static final String AFFILIATION_CHANGED = "AFFILIATIONCHANGED";
        static final String SUPERSEDED = "SUPERSEDED";
        static final String CESSATION_OF_OPERATION = "CESSATIONOFOPERATION";
        static final String CERTIFICATE_HOLD = "CERTIFICATEHOLD";
        static final String REMOVE_FROM_CRL = "REMOVEFROMCRL";
        static final String PRIVILEGE_WITHDRAWN = "PRIVILEGEWITHDRAWN";
        static final String AA_COMPROMISE = "AACOMPROMISE";
        static final String[] ALLOWED_VALUES = { UNSPECIFIED, KEY_COMPROMISE, CA_COMPROMISE, AFFILIATION_CHANGED, SUPERSEDED,
                CESSATION_OF_OPERATION, CERTIFICATE_HOLD, REMOVE_FROM_CRL, PRIVILEGE_WITHDRAWN, AA_COMPROMISE };

        static final SimpleAttributeDefinition REASON = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REASON, ModelType.STRING, true)
                .setAllowExpression(true)
                .setAllowedValues(ALLOWED_VALUES)
                .setMinSize(1)
                .build();

        static final SimpleAttributeDefinition CERTIFICATE_AUTHORITY_ACCOUNT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT, ModelType.STRING, false)
                .setMinSize(1)
                .setRestartAllServices()
                .setCapabilityReference(CERTIFICATE_AUTHORITY_ACCOUNT_CAPABILITY, KEY_STORE_CAPABILITY, true)
                .build();

        static final SimpleAttributeDefinition STAGING = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.STAGING, ModelType.BOOLEAN, true)
                .setAllowExpression(true)
                .setDefaultValue(ModelNode.FALSE)
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.REVOKE_CERTIFICATE, descriptionResolver)
                            .setParameters(ALIAS, REASON, CERTIFICATE_AUTHORITY_ACCOUNT, STAGING)
                            .setRuntimeOnly()
                            .build(),
                    new RevokeCertificateHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            ModifiableKeyStoreService keyStoreService = getModifiableKeyStoreService(context);
            KeyStore keyStore = keyStoreService.getModifiableValue();

            String alias = ALIAS.resolveModelAttribute(context, operation).asString();
            String reason = REASON.resolveModelAttribute(context, operation).asStringOrNull();
            String certificateAuthorityAccountName = CERTIFICATE_AUTHORITY_ACCOUNT.resolveModelAttribute(context, operation).asString();
            boolean staging = STAGING.resolveModelAttribute(context, operation).asBoolean();

            try {
                if (! keyStore.containsAlias(alias)) {
                    throw ROOT_LOGGER.keyStoreAliasDoesNotExist(alias);
                }
                X509Certificate certificateToRevoke = (X509Certificate) keyStore.getCertificate(alias);
                if (certificateToRevoke == null) {
                    throw ROOT_LOGGER.unableToObtainCertificate(alias);
                }
                final AcmeAccount acmeAccount = getAcmeAccount(context, certificateAuthorityAccountName, staging);
                if (reason != null) {
                    acmeClient.revokeCertificate(acmeAccount, staging, certificateToRevoke, getCRLReason(reason));
                } else {
                    acmeClient.revokeCertificate(acmeAccount, staging, certificateToRevoke);
                }
                keyStore.deleteEntry(alias);
                ((KeyStoreService) keyStoreService).save();
            } catch (IllegalArgumentException | AcmeException e) {
                throw new OperationFailedException(e);
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }

        static CRLReason getCRLReason(String reason) throws OperationFailedException {
            switch (reason.toUpperCase(Locale.ENGLISH)) {
                case UNSPECIFIED:
                    return CRLReason.UNSPECIFIED;
                case KEY_COMPROMISE:
                    return CRLReason.KEY_COMPROMISE;
                case CA_COMPROMISE:
                    return CRLReason.CA_COMPROMISE;
                case AFFILIATION_CHANGED:
                    return CRLReason.AFFILIATION_CHANGED;
                case SUPERSEDED:
                    return CRLReason.SUPERSEDED;
                case CESSATION_OF_OPERATION:
                    return CRLReason.CESSATION_OF_OPERATION;
                case CERTIFICATE_HOLD:
                    return CRLReason.CERTIFICATE_HOLD;
                case REMOVE_FROM_CRL:
                    return CRLReason.REMOVE_FROM_CRL;
                case PRIVILEGE_WITHDRAWN:
                    return CRLReason.PRIVILEGE_WITHDRAWN;
                case AA_COMPROMISE:
                    return CRLReason.AA_COMPROMISE;
                default:
                    throw ROOT_LOGGER.invalidCertificateRevocationReason(reason);
            }
        }
    }

    static class ShouldRenewCertificateHandler extends ElytronRuntimeOnlyHandler {
        static final SimpleAttributeDefinition EXPIRATION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.EXPIRATION, ModelType.LONG, true)
                .setAllowExpression(true)
                .setValidator(new LongRangeValidator(1L))
                .setDefaultValue(new ModelNode(30))
                .build();

        static void register(ManagementResourceRegistration resourceRegistration, ResourceDescriptionResolver descriptionResolver) {
            resourceRegistration.registerOperationHandler(
                    new SimpleOperationDefinitionBuilder(ElytronDescriptionConstants.SHOULD_RENEW_CERTIFICATE, descriptionResolver)
                            .setParameters(ALIAS, EXPIRATION)
                            .setRuntimeOnly()
                            .build(),
                    new ShouldRenewCertificateHandler());
        }

        @Override
        protected void executeRuntimeStep(final OperationContext context, final ModelNode operation) throws OperationFailedException {
            ModifiableKeyStoreService keyStoreService = getModifiableKeyStoreService(context);
            KeyStore keyStore = keyStoreService.getModifiableValue();

            String alias = ALIAS.resolveModelAttribute(context, operation).asString();
            Long expiration = EXPIRATION.resolveModelAttribute(context, operation).asLong();

            try {
                if (! keyStore.containsAlias(alias)) {
                    throw ROOT_LOGGER.keyStoreAliasDoesNotExist(alias);
                }
                X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
                if (certificate == null) {
                    throw ROOT_LOGGER.unableToObtainCertificate(alias);
                }
                ZonedDateTime current = ZonedDateTime.now().withZoneSameInstant(ZoneId.of("UTC")).withNano(0);
                ZonedDateTime notAfter = ZonedDateTime.ofInstant(certificate.getNotAfter().toInstant(), ZoneId.of("UTC"));
                long daysToExpiry = ChronoUnit.DAYS.between(current, notAfter);
                ModelNode result = context.getResult();
                if (daysToExpiry <= 0) {
                    // already expired
                    result.get(ElytronDescriptionConstants.SHOULD_RENEW_CERTIFICATE).set(ModelNode.TRUE);
                    daysToExpiry = 0;
                } else {
                    if (daysToExpiry <= expiration) {
                        result.get(ElytronDescriptionConstants.SHOULD_RENEW_CERTIFICATE).set(ModelNode.TRUE);
                    } else {
                        result.get(ElytronDescriptionConstants.SHOULD_RENEW_CERTIFICATE).set(ModelNode.FALSE);
                    }
                }
                result.get(ElytronDescriptionConstants.DAYS_TO_EXPIRY).set(new ModelNode(daysToExpiry));
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class NotBeforeValidator extends StringLengthValidator {

        NotBeforeValidator() {
            super(1, true, true);
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);
            if (value.isDefined()) {
                try {
                    NOT_VALID_BEFORE_FORMATTER.parse(value.asString());
                } catch (DateTimeParseException e){
                    throw ROOT_LOGGER.invalidNotBefore(e, e.getLocalizedMessage());
                }
            }
        }
    }

    static class KeySizeValidator extends IntRangeValidator {

        KeySizeValidator() {
            super(1);
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);
            if (value.isDefined()) {
                int intValue = value.asInt();
                // check that the given value is a power of 2
                if ((intValue & (intValue - 1)) != 0) {
                    throw ROOT_LOGGER.invalidKeySize(intValue);
                }
            }
        }
    }

    private static char[] resolveKeyPassword(final KeyStoreService keyStoreService, final ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier) throws RuntimeException {
        try {
            return keyStoreService.resolveKeyPassword(credentialSourceSupplier);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static AcmeAccount getAcmeAccount(OperationContext context, String certificateAuthorityAccountName, boolean staging) throws OperationFailedException {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);
        RuntimeCapability<Void> runtimeCapability = CERTIFICATE_AUTHORITY_ACCOUNT_RUNTIME_CAPABILITY.fromBaseCapability(certificateAuthorityAccountName);
        ServiceName serviceName = runtimeCapability.getCapabilityServiceName();

        ServiceController<AcmeAccount> serviceContainer = getRequiredService(serviceRegistry, serviceName, AcmeAccount.class);
        ServiceController.State serviceState = serviceContainer.getState();
        if (serviceState != ServiceController.State.UP) {
            throw ROOT_LOGGER.requiredServiceNotUp(serviceName, serviceState);
        }
        AcmeAccount acmeAccount = serviceContainer.getService().getValue();
        return resetAcmeAccount(acmeAccount, staging);
    }

    static AcmeAccount resetAcmeAccount(AcmeAccount acmeAccount, boolean staging) {
        String accountUrl = acmeAccount.getAccountUrl();
        if (accountUrl != null && acmeAccount.getStagingServerUrl() != null) {
            String stagingEndpoint = acmeAccount.getStagingServerUrl().substring(0, acmeAccount.getStagingServerUrl().indexOf("/" + DIRECTORY));
            if ((accountUrl.startsWith(stagingEndpoint) && ! staging) || (! accountUrl.startsWith(stagingEndpoint) && staging)) {
                // need to reset the account information so it will get populated with the correct staging / non-staging account URL
                AcmeAccount.Builder acmeAccountBuilder = AcmeAccount.builder();
                acmeAccountBuilder
                        .setServerUrl(acmeAccount.getServerUrl())
                        .setStagingServerUrl(acmeAccount.getStagingServerUrl())
                        .setDn(acmeAccount.getDn())
                        .setKey(acmeAccount.getCertificate(), acmeAccount.getPrivateKey())
                        .setTermsOfServiceAgreed(acmeAccount.isTermsOfServiceAgreed());
                if (acmeAccount.getContactUrls() != null) {
                    acmeAccountBuilder.setContactUrls(acmeAccount.getContactUrls());
                }
                return acmeAccountBuilder.build();
            }
        }
        return acmeAccount;
    }

}
