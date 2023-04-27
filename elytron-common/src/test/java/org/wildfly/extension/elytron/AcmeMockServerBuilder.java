/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
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

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

/**
 * Class used to build up a mock Let's Encrypt server instance.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */

class AcmeMockServerBuilder {

    private ClientAndServer server; // used to simulate a Let's Encrypt server instance

    AcmeMockServerBuilder(ClientAndServer server) {
        server.reset();
        this.server = server;
    }

    public AcmeMockServerBuilder addDirectoryResponseBody(String directoryResponseBody) {
        server.when(
                request()
                        .withMethod("GET")
                        .withPath("/directory")
                        .withBody(""),
                Times.once())
                .respond(
                        response()
                                .withHeader("Cache-Control", "public, max-age=0, no-cache")
                                .withHeader("Content-Type", "application/json")
                                .withBody(directoryResponseBody));
        return this;
    }

    public AcmeMockServerBuilder addNewNonceResponse(String newNonce) {
        server.when(
                request()
                        .withMethod("HEAD")
                        .withPath("/acme/new-nonce")
                        .withBody(""),
                Times.once())
                .respond(
                        response()
                                .withHeader("Cache-Control", "public, max-age=0, no-cache")
                                .withHeader("Replay-Nonce", newNonce)
                                .withStatusCode(204));
        return this;
    }

    public AcmeMockServerBuilder addNewAccountRequestAndResponse(String expectedNewAccountRequestBody, String newAccountResponseBody,
                                                                 String newAccountReplayNonce, String newAccountLocation, int newAccountStatusCode) {
        return addNewAccountRequestAndResponse(expectedNewAccountRequestBody, newAccountResponseBody, newAccountReplayNonce, newAccountLocation,
                newAccountStatusCode, false);
    }

    public AcmeMockServerBuilder addNewAccountRequestAndResponse(String expectedNewAccountRequestBody, String newAccountResponseBody, String newAccountReplayNonce,
                                                                 String newAccountLocation, int newAccountStatusCode, boolean useProblemContentType) {
        String link = "<https://boulder:4431/terms/v7>;rel=\"terms-of-service\"";
        return addPostRequestAndResponse(expectedNewAccountRequestBody, "/acme/new-acct", newAccountResponseBody, newAccountReplayNonce,
                link, newAccountLocation, newAccountStatusCode, useProblemContentType);
    }

    public AcmeMockServerBuilder updateAccountRequestAndResponse(String expectedUpdateAccountRequestBody, String updateAccountResponseBody, String updateAccountReplayNonce,
                                                                 String accountUrl, int updateAccountStatusCode) {
        String link = "<https://boulder:4431/terms/v7>;rel=\"terms-of-service\"";
        return addPostRequestAndResponse(expectedUpdateAccountRequestBody, accountUrl, updateAccountResponseBody, updateAccountReplayNonce,
                link, "", updateAccountStatusCode, false);
    }

    public AcmeMockServerBuilder orderCertificateRequestAndResponse(String expectedOrderCertificateRequestBody, String orderCertificateResponseBody, String orderCertificateReplayNonce,
                                                                    String orderLocation, int orderCertificateStatusCode, boolean useProblemContentType) {
        return addPostRequestAndResponse(expectedOrderCertificateRequestBody, "/acme/new-order", orderCertificateResponseBody, orderCertificateReplayNonce,
                "", orderLocation, orderCertificateStatusCode, useProblemContentType);
    }

    public AcmeMockServerBuilder addAuthorizationResponseBody(String expectedAuthorizationUrl, String expectedAuthorizationRequestBody, String authorizationResponseBody, String authorizationReplayNonce) {
        server.when(
                request()
                        .withMethod("POST")
                        .withPath(expectedAuthorizationUrl)
                        .withBody(expectedAuthorizationRequestBody == null ? "" : expectedAuthorizationRequestBody),
                Times.exactly(10))
                .respond(
                        response()
                                .withHeader("Cache-Control", "public, max-age=0, no-cache")
                                .withHeader("Content-Type", "application/json")
                                .withHeader("Replay-Nonce", authorizationReplayNonce)
                                .withBody(authorizationResponseBody));
        return this;
    }

    public AcmeMockServerBuilder addChallengeRequestAndResponse(String expectedChallengeRequestBody, String expectedChallengeUrl, String challengeResponseBody,
                                                                String challengeReplayNonce, String challengeLocation, String challengeLink,
                                                                int challengeStatusCode, boolean useProblemContentType, String verifyChallengePath,
                                                                String challengeFileContents, String expectedAuthorizationUrl, String authorizationResponseBody,
                                                                String authorizationReplayNonce) {
        server.when(
                request()
                        .withMethod("POST")
                        .withPath(expectedChallengeUrl)
                        .withHeader("Content-Type", "application/jose+json")
                        .withBody(expectedChallengeRequestBody),
                Times.once())
                .respond(request -> {
                    HttpResponse response = response()
                            .withHeader("Cache-Control", "public, max-age=0, no-cache")
                            .withHeader("Content-Type", useProblemContentType ? "application/problem+json" : "application/json")
                            .withHeader("Replay-Nonce", challengeReplayNonce)
                            .withBody(challengeResponseBody)
                            .withStatusCode(challengeStatusCode);
                    if (! challengeLocation.isEmpty()) {
                        response = response.withHeader("Location", challengeLocation);
                    }
                    if (! challengeLink.isEmpty()) {
                        response = response.withHeader("Link", challengeLink);
                    }

                    byte[] challengeResponseBytes = null;
                    try {
                        // Simply validate that the file was created and has the correct contents (attempting to retrieve
                        // the file via the challenge url would require the Undertow subsystem)
                        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(new File(System.getProperty("jboss.home.dir") + verifyChallengePath)))) {
                            challengeResponseBytes = IOUtils.toByteArray(inputStream);
                        }
                    } catch (Exception e) {
                        //
                    }
                    if (challengeFileContents.equals(new String(challengeResponseBytes, StandardCharsets.UTF_8))) {
                        addAuthorizationResponseBody(expectedAuthorizationUrl, null, authorizationResponseBody, authorizationReplayNonce);
                    }
                    return response;
                });
        return this;
    }

    public AcmeMockServerBuilder addFinalizeRequestAndResponse(String finalResponseBody, String finalizeReplayNonce,
                                                               String finalizeUrl, String finalizeOrderLocation, int finalizeStatusCode) {
        return addFinalizeRequestAndResponse(finalResponseBody, finalizeReplayNonce, finalizeUrl, finalizeOrderLocation, finalizeStatusCode, false);
    }

    public AcmeMockServerBuilder addFinalizeRequestAndResponse(String finalResponseBody, String finalizeReplayNonce,
                                                               String finalizeUrl, String orderLocation, int finalizeStatusCode, boolean useProblemContentType) {
        return addPostRequestAndResponse("", finalizeUrl, finalResponseBody, finalizeReplayNonce, "",
                orderLocation, finalizeStatusCode, useProblemContentType);
    }

    public AcmeMockServerBuilder addCertificateRequestAndResponse(String certificateUrl, String expectedCertificateRequestBody, String certificateResponseBody, String certificateReplayNonce, int certificateStatusCode) {
        HttpResponse response = response()
                .withHeader("Cache-Control", "public, max-age=0, no-cache")
                .withHeader("Content-Type", "application/pem-certificate-chain")
                .withHeader("Replay-Nonce", certificateReplayNonce)
                .withBody(certificateResponseBody)
                .withStatusCode(certificateStatusCode);
        server.when(
                request()
                        .withMethod("POST")
                        .withPath(certificateUrl)
                        .withBody(expectedCertificateRequestBody),
                Times.once())
                .respond(response);

        return this;
    }

    public AcmeMockServerBuilder addCheckOrderRequestAndResponse(String orderUrl, String expectedCheckCertificateRequestBody, String checkCertificateResponseBody, String checkOrderReplayNonce, int checkCertificateStatusCode) {
        HttpResponse response = response()
                .withHeader("Cache-Control", "public, max-age=0, no-cache")
                .withHeader("Content-Type", "application/json")
                .withHeader("Replay-Nonce", checkOrderReplayNonce)
                .withBody(checkCertificateResponseBody)
                .withStatusCode(checkCertificateStatusCode);
        server.when(
                request()
                        .withMethod("POST")
                        .withPath(orderUrl)
                        .withBody(expectedCheckCertificateRequestBody),
                Times.once())
                .respond(response);

        return this;
    }

    public AcmeMockServerBuilder addRevokeCertificateRequestAndResponse(String expectedRevokeCertificateRequestBody, String revokeCertificateReplayNonce, int revokeCertificateStatusCode) {
        return addPostRequestAndResponse(expectedRevokeCertificateRequestBody, "/acme/revoke-cert", "", revokeCertificateReplayNonce,
                "", "", revokeCertificateStatusCode, false);
    }

    public AcmeMockServerBuilder addChangeKeyRequestAndResponse(String expectedChangeKeyRequestBody, String changeKeyResponseBody, String changeKeyReplaceNonce, int changeKeyResponseCode) {
        return addPostRequestAndResponse(expectedChangeKeyRequestBody, "/acme/key-change", changeKeyResponseBody, changeKeyReplaceNonce,
                "", "", changeKeyResponseCode, false);
    }

    public AcmeMockServerBuilder addPostRequestAndResponse(String expectedPostRequestBody, String postPath, String responseBody, String replayNonce, String link, String location, int responseCode, boolean useProblemContentType) {
        HttpResponse response = response()
                .withHeader("Cache-Control", "public, max-age=0, no-cache")
                .withHeader("Replay-Nonce", replayNonce)
                .withStatusCode(responseCode);
        if (! responseBody.isEmpty()) {
            response = response
                    .withHeader("Content-Type", useProblemContentType ? "application/problem+json" : "application/json")
                    .withBody(responseBody);

        }
        if (! link.isEmpty()) {
            response = response.withHeader("Link", link);
        }
        if (! location.isEmpty()) {
            response = response.withHeader("Location", location);
        }
        HttpRequest request = request()
                .withMethod("POST")
                .withPath(postPath) ;
        if (! expectedPostRequestBody.isEmpty()) {
            request = request.withBody(expectedPostRequestBody);
        }
        server.when(
                request,
                Times.once())
                .respond(response);

        return this;
    }

    public ClientAndServer build() {
        return server;
    }
}
