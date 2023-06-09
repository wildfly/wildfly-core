/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.elytron.common.util;

import static org.jboss.logging.Logger.Level.WARN;

import java.io.IOException;
import java.security.KeyStore;
import java.security.Provider;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartException;
import org.wildfly.security.x500.cert.acme.AcmeException;

/**
 * Messages for common components of Elytron.
 *
 * @apiNote These messages share the namespace of the Elytron subsystem.
 * New loggers should use a different project code.
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
@MessageLogger(projectCode = "WFLYELY", length = 5)
public interface ElytronCommonMessages extends BasicLogger {

    /**
     * A root logger with the category of the package name.
     */
    ElytronCommonMessages ROOT_LOGGER = Logger.getMessageLogger(ElytronCommonMessages.class, "org.wildfly.extension.elytron.common");

    /**
     * An {@link IllegalArgumentException} if the supplied operation did not contain an address with a value for the required key.
     *
     * @param key - the required key in the address of the operation.
     * @return The {@link IllegalArgumentException} for the error.
     */
    @Message(id = 3, value = "The operation did not contain an address with a value for '%s'.")
    IllegalArgumentException operationAddressMissingKey(final String key);

    /**
     * A {@link StartException} if it is not possible to initialise the {@link Service}.
     *
     * @param cause the cause of the failure.
     * @return The {@link StartException} for the error.
     */
    @Message(id = 4, value = "Unable to start the service.")
    StartException unableToStartService(@Cause Exception cause);

    /**
     * An {@link OperationFailedException} if it is not possible to access the {@link KeyStore} at RUNTIME.
     *
     * @param cause the underlying cause of the failure
     * @return The {@link OperationFailedException} for the error.
     */
    @Message(id = 5, value = "Unable to access KeyStore to complete the requested operation.")
    OperationFailedException unableToAccessKeyStore(@Cause Exception cause);

    /**
     * An {@link OperationFailedException} where an operation can not proceed as it's required service is not UP.
     *
     * @param serviceName the name of the service that is required.
     * @param state the actual state of the service.
     * @return The {@link OperationFailedException} for the error.
     */
    @Message(id = 7, value = "The required service '%s' is not UP, it is currently '%s'.")
    OperationFailedException requiredServiceNotUp(ServiceName serviceName, ServiceController.State state);

    /**
     * An {@link OperationFailedException} where the name of the operation does not match the expected names.
     *
     * @param actualName the operation name contained within the request.
     * @param expectedNames the expected operation names.
     * @return The {@link OperationFailedException} for the error.
     */
    @Message(id = 8, value = "Invalid operation name '%s', expected one of '%s'")
    OperationFailedException invalidOperationName(String actualName, String... expectedNames);

    /**
     * An {@link RuntimeException} where an operation can not be completed.
     *
     * @param cause the underlying cause of the failure.
     * @return The {@link RuntimeException} for the error.
     */
    @Message(id = 9, value = "Unable to complete operation. '%s'")
    RuntimeException unableToCompleteOperation(@Cause Throwable cause, String causeMessage);

    /**
     * An {@link OperationFailedException} where this an attempt to save a KeyStore without a File defined.
     *
     * @return The {@link OperationFailedException} for the error.
     */
    @Message(id = 10, value = "Unable to save KeyStore - KeyStore file '%s' does not exist.")
    OperationFailedException cantSaveWithoutFile(final String file);

    /**
     * A {@link StartException} where a service can not identify a suitable {@link Provider}
     *
     * @param type the type being searched for.
     * @return The {@link StartException} for the error.
     */
    @Message(id = 12, value = "No suitable provider found for type '%s'")
    StartException noSuitableProvider(String type);

    /**
     * A {@link StartException} where a Key or Trust manager factory can not be created for a specific algorithm.
     *
     * @param type the type of manager factory being created.
     * @param algorithm the requested algorithm.
     * @return The {@link StartException} for the error.
     */
    @Message(id = 18, value = "Unable to create %s for algorithm '%s'.")
    StartException unableToCreateManagerFactory(final String type, final String algorithm);

    /**
     * A {@link StartException} where a specific type can not be found in an injected value.
     *
     * @param type the type required.
     * @return The {@link StartException} for the error.
     */
    @Message(id = 19, value = "No '%s' found in injected value.")
    StartException noTypeFound(final String type);

    @Message(id = 22, value = "KeyStore file '%s' does not exist and required.")
    StartException keyStoreFileNotExists(final String file);

    @LogMessage(level = WARN)
    @Message(id = 23, value = "KeyStore file '%s' does not exist. Used blank.")
    void keyStoreFileNotExistsButIgnored(final String file);

    @LogMessage(level = WARN)
    @Message(id = 24, value = "Certificate [%s] in KeyStore is not valid")
    void certificateNotValid(String alias, @Cause Exception cause);

    @Message(id = 29, value = "Failed to parse URL '%s'")
    OperationFailedException invalidURL(String url, @Cause Exception cause);

    @Message(id = 31, value = "Unable to access CRL file.")
    StartException unableToAccessCRL(@Cause Exception cause);

    @Message(id = 32, value = "Unable to reload CRL file.")
    RuntimeException unableToReloadCRL(@Cause Exception cause);

    /**
     * A {@link StartException} where a specific type is not type of injected value.
     *
     * @param type the type required.
     * @return The {@link StartException} for the error.
     */
    @Message(id = 37, value = "Injected value is not of '%s' type.")
    StartException invalidTypeInjected(final String type);

    @Message(id = 39, value = "Unable to reload CRL file - TrustManager is not reloadable")
    OperationFailedException unableToReloadCRLNotReloadable();

    @Message(id = 43, value = "A cycle has been detected initialising the resources - %s")
    OperationFailedException cycleDetected(String cycle);

    @Message(id = 44, value = "Unexpected name of servicename's parent - %s")
    IllegalStateException invalidServiceNameParent(String canonicalName);

    @Message(id = 48, value = "A string representation of an X.500 distinguished name is required: %s")
    IllegalArgumentException representationOfX500IsRequired(String causeMessage);

    /*
     * Credential Store Section.
     */

    @Message(id = 910, value = "Password cannot be resolved for key-store '%s'")
    IOException keyStorePasswordCannotBeResolved(String path);

    /*
     * Identity Resource Messages - 1000
     */

    @Message(id = 1017, value = "Invalid value for cipher-suite-filter. %s")
    OperationFailedException invalidCipherSuiteFilter(@Cause Throwable cause, String causeMessage);

    @Message(id = 1027, value = "Key password cannot be resolved for key-store '%s'")
    IOException keyPasswordCannotBeResolved(String path);

    @Message(id = 1028, value = "Invalid value for not-before. %s")
    OperationFailedException invalidNotBefore(@Cause Throwable cause, String causeMessage);

    @Message(id = 1029, value = "Alias '%s' does not exist in KeyStore")
    OperationFailedException keyStoreAliasDoesNotExist(String alias);

    @Message(id = 1030, value = "Alias '%s' does not identify a PrivateKeyEntry in KeyStore")
    OperationFailedException keyStoreAliasDoesNotIdentifyPrivateKeyEntry(String alias);

    @Message(id = 1031, value = "Unable to obtain PrivateKey for alias '%s'")
    OperationFailedException unableToObtainPrivateKey(String alias);

    @Message(id = 1032, value = "Unable to obtain Certificate for alias '%s'")
    OperationFailedException unableToObtainCertificate(String alias);

    @Message(id = 1033, value = "No certificates found in certificate reply")
    OperationFailedException noCertificatesFoundInCertificateReply();

    @Message(id = 1034, value = "Public key from certificate reply does not match public key from certificate in KeyStore")
    OperationFailedException publicKeyFromCertificateReplyDoesNotMatchKeyStore();

    @Message(id = 1035, value = "Certificate reply is the same as the certificate from PrivateKeyEntry in KeyStore")
    OperationFailedException certificateReplySameAsCertificateFromKeyStore();

    @Message(id = 1036, value = "Alias '%s' already exists in KeyStore")
    OperationFailedException keyStoreAliasAlreadyExists(String alias);

    @Message(id = 1037, value = "Top-most certificate from certificate reply is not trusted. Inspect the certificate carefully and if it is valid, execute import-certificate again with validate set to false.")
    OperationFailedException topMostCertificateFromCertificateReplyNotTrusted();

    @Message(id = 1038, value = "Trusted certificate is already in KeyStore under alias '%s'")
    OperationFailedException trustedCertificateAlreadyInKeyStore(String alias);

    @Message(id = 1039, value = "Trusted certificate is already in cacerts KeyStore under alias '%s'")
    OperationFailedException trustedCertificateAlreadyInCacertsKeyStore(String alias);

    @Message(id = 1040, value = "Unable to determine if the certificate is trusted. Inspect the certificate carefully and if it is valid, execute import-certificate again with validate set to false.")
    OperationFailedException unableToDetermineIfCertificateIsTrusted();

    @Message(id = 1041, value = "Certificate file does not exist")
    OperationFailedException certificateFileDoesNotExist(@Cause Exception cause);

    @Message(id = 1042, value = "Unable to obtain Entry for alias '%s'")
    OperationFailedException unableToObtainEntry(String alias);

    @Message(id = 1043, value = "Unable to create an account with the certificate authority: %s")
    OperationFailedException unableToCreateAccountWithCertificateAuthority(@Cause Exception cause, String causeMessage);

    @Message(id = 1044, value = "Unable to change the account key associated with the certificate authority: %s")
    OperationFailedException unableToChangeAccountKeyWithCertificateAuthority(@Cause Exception cause, String causeMessage);

    @Message(id = 1045, value = "Unable to deactivate the account associated with the certificate authority: %s")
    OperationFailedException unableToDeactivateAccountWithCertificateAuthority(@Cause Exception cause, String causeMessage);

    @Message(id = 1046, value = "Unable to obtain certificate authority account Certificate for alias '%s'")
    StartException unableToObtainCertificateAuthorityAccountCertificate(String alias);

    @Message(id = 1047, value = "Unable to obtain certificate authority account PrivateKey for alias '%s'")
    StartException unableToObtainCertificateAuthorityAccountPrivateKey(String alias);

    @Message(id = 1048, value = "Unable to update certificate authority account key store: %s")
    OperationFailedException unableToUpdateCertificateAuthorityAccountKeyStore(@Cause Exception cause, String causeMessage);

    @Message(id = 1049, value = "Unable to respond to challenge from certificate authority: %s")
    AcmeException unableToRespondToCertificateAuthorityChallenge(@Cause Exception cause, String causeMessage);

    @Message(id = 1050, value = "Invalid certificate authority challenge")
    AcmeException invalidCertificateAuthorityChallenge();

    @Message(id = 1051, value = "Invalid certificate revocation reason '%s'")
    OperationFailedException invalidCertificateRevocationReason(String reason);

    @Message(id = 1052, value = "Unable to instantiate AcmeClientSpi implementation")
    IllegalStateException unableToInstatiateAcmeClientSpiImplementation();

    @Message(id = 1053, value = "Unable to update the account with the certificate authority: %s")
    OperationFailedException unableToUpdateAccountWithCertificateAuthority(@Cause Exception cause, String causeMessage);

    @Message(id = 1054, value = "Unable to get the metadata associated with the certificate authority: %s")
    OperationFailedException unableToGetCertificateAuthorityMetadata(@Cause Exception cause, String causeMessage);

    @Message(id = 1056, value = "A certificate authority account with this account key already exists. To update the contact" +
            " information associated with this existing account, use %s. To change the key associated with this existing account, use %s.")
    OperationFailedException certificateAuthorityAccountAlreadyExists(String updateAccount, String changeAccountKey);

    @Message(id = 1055, value = "Invalid key size: %d")
    OperationFailedException invalidKeySize(int keySize);

    @Message(id = 1059, value = "Unable to detect KeyStore '%s'")
    StartException unableToDetectKeyStore(String path);

    @Message(id = 1060, value = "Fileless KeyStore needs to have a defined type.")
    OperationFailedException filelessKeyStoreMissingType();

    @Message(id = 1061, value = "Invalid value of host context map: '%s' is not valid hostname pattern.")
    OperationFailedException invalidHostContextMapValue(String hostname);

    @Message(id = 1063, value = "LetsEncrypt certificate authority is configured by default.")
    OperationFailedException letsEncryptNameNotAllowed();

    @Message(id = 1064, value = "Failed to load OCSP responder certificate '%s'.")
    StartException failedToLoadResponderCert(String alias, @Cause Exception exception);

    @Message(id = 1065, value = "Multiple maximum-cert-path definitions found.")
    OperationFailedException multipleMaximumCertPathDefinitions();

    @Message(id = 1066, value = "Invalid value for cipher-suite-names. %s")
    OperationFailedException invalidCipherSuiteNames(@Cause Throwable cause, String causeMessage);

    @Message(id = 1080, value = "Non existing key store needs to have defined type.")
    OperationFailedException nonexistingKeyStoreMissingType();

    @Message(id = 1081, value = "Failed to lazily initialize key manager")
    RuntimeException failedToLazilyInitKeyManager(@Cause  Exception e);

    @Message(id = 1082, value = "Failed to store generated self-signed certificate")
    RuntimeException failedToStoreGeneratedSelfSignedCertificate(@Cause  Exception e);

    @Message(id = 1083, value = "No '%s' found in injected value.")
    RuntimeException noTypeFoundForLazyInitKeyManager(final String type);

    @Message(id = 1084, value = "KeyStore %s not found, it will be auto-generated on first use with a self-signed certificate for host %s")
    @LogMessage(level = WARN)
    void selfSignedCertificateWillBeCreated(String file, String host);

    @Message(id = 1085, value = "Generated self-signed certificate at %s. Please note that self-signed certificates are not secure and should only be used for testing purposes. Do not use this self-signed certificate in production.\nSHA-1 fingerprint of the generated key is %s\nSHA-256 fingerprint of the generated key is %s")
    @LogMessage(level = WARN)
    void selfSignedCertificateHasBeenCreated(String file, String sha1, String sha256);

    @Message(id = 1088, value = "Missing certificate authority challenge")
    AcmeException missingCertificateAuthorityChallenge();

    /*
     * Expression Resolver Section
     */

    @Message(id = 1210, value = "Initialisation of an %s without an active management OperationContext is not allowed.")
    ExpressionResolver.ExpressionResolutionServerException illegalNonManagementInitialization(Class<?> initialzingClass);

    /*
     * Don't just add new errors to the end of the file, there may be an appropriate section above for the resource.
     *
     * If no suitable section is available add a new section.
     */

}
