/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import org.apache.commons.io.IOUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Assert;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

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
                                .withHeader("Retry-After", "0")
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
                                .withHeader("Retry-After", "0")
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

    public AcmeMockServerBuilder addAuthorizationResponseBody(String expectedAuthorizationUrl, String authorizationResponseBody, String authorizationReplayNonce) {
        server.when(
                request()
                        .withMethod("POST")
                        .withPath(expectedAuthorizationUrl),
                Times.exactly(10))
                .respond(
                        response()
                                .withHeader("Retry-After", "0")
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
                        .withHeader("Content-Type", "application/jose+json"),
                Times.once())
                .respond(request -> {
                    HttpResponse response = response()
                            .withHeader("Retry-After", "0")
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
                        String jbossHome = TestSuiteEnvironment.getSystemProperty("jboss.inst");
                        if (jbossHome == null) {
                            jbossHome = TestSuiteEnvironment.getJBossHome();
                        }
                        Assert.assertNotNull("Could not find the JBoss home directory", jbossHome);

                        String challengeDir = jbossHome +  verifyChallengePath;
                        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(new File(challengeDir)))) {
                            challengeResponseBytes = IOUtils.toByteArray(inputStream);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    if (challengeFileContents.equals(new String(challengeResponseBytes, StandardCharsets.UTF_8))) {
                        addAuthorizationResponseBody(expectedAuthorizationUrl, authorizationResponseBody, authorizationReplayNonce);
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

    public AcmeMockServerBuilder addCertificateRequestAndResponse(String certificateUrl, String certificateResponseBody, int certificateStatusCode, String certificateReplayNonce) {
        HttpResponse response = response()
                .withHeader("Retry-After", "0")
                .withHeader("Cache-Control", "public, max-age=0, no-cache")
                .withHeader("Content-Type", "application/pem-certificate-chain")
                .withHeader("Replay-Nonce", certificateReplayNonce)
                .withBody(certificateResponseBody)
                .withStatusCode(certificateStatusCode);
        server.when(
                request()
                        .withMethod("POST")
                        .withPath(certificateUrl)
                        .withBody(""),
                Times.once())
                .respond(response);

        return this;
    }

    public AcmeMockServerBuilder addCheckOrderRequestAndResponse(String orderUrl, String checkCertificateResponseBody, int checkCertificateStatusCode, String checkOrderReplayNonce) {
        HttpResponse response = response()
                .withHeader("Retry-After", "0")
                .withHeader("Cache-Control", "public, max-age=0, no-cache")
                .withHeader("Content-Type", "application/json")
                .withHeader("Replay-Nonce", checkOrderReplayNonce)
                .withBody(checkCertificateResponseBody)
                .withStatusCode(checkCertificateStatusCode);
        server.when(
                request()
                        .withMethod("POST")
                        .withPath(orderUrl)
                        .withBody(""),
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
                .withHeader("Retry-After", "0")
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


    //ignoreTOSAcceptance - ignores whether user acceptedTOS
    public static ClientAndServer setupTestObtainCertificateWithKeySize(ClientAndServer s, boolean ignoreTOSAcceptance) {

        // set up a mock Let's Encrypt server
        final String ACCT_PATH = "/acme/acct/398";
        final String DIRECTORY_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"R0Qoi70t57s\": \"https://community.letsencrypt.org/t/adding-random-entries-to-the-directory/33417\"," + System.lineSeparator() +
                "  \"keyChange\": \"http://localhost:4001/acme/key-change\"," + System.lineSeparator() +
                "  \"meta\": {" + System.lineSeparator() +
                "    \"caaIdentities\": [" + System.lineSeparator() +
                "      \"happy-hacker-ca.invalid\"" + System.lineSeparator() +
                "    ]," + System.lineSeparator() +
                "    \"termsOfService\": \"https://boulder:4431/terms/v7\"," + System.lineSeparator() +
                "    \"website\": \"https://github.com/letsencrypt/boulder\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"newAccount\": \"http://localhost:4001/acme/new-acct\"," + System.lineSeparator() +
                "  \"newNonce\": \"http://localhost:4001/acme/new-nonce\"," + System.lineSeparator() +
                "  \"newOrder\": \"http://localhost:4001/acme/new-order\"," + System.lineSeparator() +
                "  \"revokeCert\": \"http://localhost:4001/acme/revoke-cert\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String NEW_NONCE_RESPONSE = "jR7PUuYjk-llJPIByXxUrxN_4Ugkh8Y35DO_H74nCLk";


        String QUERY_ACCT_REQUEST_BODY_1 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImp3ayI6eyJlIjoiQVFBQiIsImt0eSI6IlJTQSIsIm4iOiJoNWlULUY4UzZMczJLZlRMNUZpNV9hRzhpdWNZTl9yajJVXy16ck8yckpxczg2WHVHQnY1SDdMZm9vOWxqM3lsaXlxNVQ2ejdkY3RZOW1rZUZXUEIxaEk0Rjg3em16azFWR05PcnM5TV9KcDlPSVc4QVllNDFsMHBvWVpNQTllQkE0ZnV6YmZDTUdONTdXRjBfMjhRRmJuWTVXblhXR3VPa0N6QS04Uk5IQlRxX3Q1a1BWRV9jNFFVemRJcVoyZG54el9FZ05jdU1hMXVHZEs3YmNybEZIdmNrWjNxMkpsT0NEckxEdEJpYW96ZnlLR0lRUlpheGRYSlE2cl9tZVdHOWhmZUJuMTZKcG5nLTU4TFd6X0VIUVFtLTN1bl85UVl4d2pIY2RDdVBUQ1RXNEFwcFdnZ1FWdE00ZTd6U1ZzMkZYczdpaVZKVzhnMUF1dFFINU53Z1EifSwibm9uY2UiOiJqUjdQVXVZamstbGxKUElCeVh4VXJ4Tl80VWdraDhZMzVET19INzRuQ0xrIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LWFjY3QifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AZXhhbXBsZS5jb20iXX0\",\"signature\":\"RhnB-JjfGene8dHSJHI6z8X5ZEtx6YX6oJiZqhFa-n7ugx6RTP78ZHbWDZXtPN-pIBiQZLR0GOh-2AmqC33DOX-0-8IB3OTZFbSR08UdahBgQ9S-FqKuvBWTMVFiPo-_D9U1n7dQNbcRyNoY5AIJqIHuoXVrJ2Da5ROAKCHirFemr7ibqvTWNpRjyhpk306ORKb69x-XQ58p8cJooJSRxmZ2Nb2j7YnVqKvWVeT546RmM9ZtfYbZMNPIu7Zxcwb2nArsEbbnBG90kxTmZiruBsMZ6LcVKpNTQGqG64N5PWFKIPm5fVDi3CCoPnClFWzUtGaHDe8Z102akAaIm97tGA\"}";
        if(ignoreTOSAcceptance) {
            QUERY_ACCT_REQUEST_BODY_1 = "";
        }

        final String QUERY_ACCT_RESPONSE_BODY_1= "";

        final String QUERY_ACCT_REPLAY_NONCE_1 = "0ueRZEEzs5mQ82ENOemcg9ePFWSzQxHpTggVH2hA1uM";
        final String ACCT_LOCATION = "http://localhost:4001/acme/acct/398";

        String QUERY_ACCT_REQUEST_BODY_2 = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMzk4Iiwibm9uY2UiOiIwdWVSWkVFenM1bVE4MkVOT2VtY2c5ZVBGV1N6UXhIcFRnZ1ZIMmhBMXVNIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvYWNjdC8zOTgifQ\",\"payload\":\"eyJ0ZXJtc09mU2VydmljZUFncmVlZCI6dHJ1ZSwiY29udGFjdCI6WyJtYWlsdG86YWRtaW5AZXhhbXBsZS5jb20iXX0\",\"signature\":\"J__rbJZS5CUpy6rt86e8GEmqbRyrAQgnpkLjJrcSAFThwVqfifsxbCeihsiRv0NZkgLccgNV0ipa0amazsw-ZGXK2qZwead5HaAkUX4mHpFJPHEXHqIoFBTrWzofiP2EmyKNTE5y1FFpOdD_C28eB7hN_Hk15TwR96NZiY3AIniXPOv_BcclNjRS3FJt3TN3DqbNKI3GIUEsKRXfHKljlevSn5M9pTIzCBi5SlWygJqP6rywNSEniodmlOChNy4bhcct3iwWKLx5SMeaGsOj90O-DtZBq-ljpzjVA1K-hNEqyd1rWdrId-V3H56_qp2FO1Xf3nTQmogOlLRdP-ZMVA\"}";
        if(ignoreTOSAcceptance) {
            QUERY_ACCT_REQUEST_BODY_2 = "";
        }

        final String QUERY_ACCT_RESPONSE_BODY_2= "{" + System.lineSeparator() +
                "  \"id\": 398," + System.lineSeparator() +
                "  \"key\": {" + System.lineSeparator() +
                "    \"kty\": \"RSA\"," + System.lineSeparator() +
                "    \"n\": \"h5iT-F8S6Ls2KfTL5Fi5_aG8iucYN_rj2U_-zrO2rJqs86XuGBv5H7Lfoo9lj3yliyq5T6z7dctY9mkeFWPB1hI4F87zmzk1VGNOrs9M_Jp9OIW8AYe41l0poYZMA9eBA4fuzbfCMGN57WF0_28QFbnY5WnXWGuOkCzA-8RNHBTq_t5kPVE_c4QUzdIqZ2dnxz_EgNcuMa1uGdK7bcrlFHvckZ3q2JlOCDrLDtBiaozfyKGIQRZaxdXJQ6r_meWG9hfeBn16Jpng-58LWz_EHQQm-3un_9QYxwjHcdCuPTCTW4AppWggQVtM4e7zSVs2FXs7iiVJW8g1AutQH5NwgQ\"," + System.lineSeparator() +
                "    \"e\": \"AQAB\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"contact\": [" + System.lineSeparator() +
                "    \"mailto:admin@example.com\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"initialIp\": \"127.0.0.1\"," + System.lineSeparator() +
                "  \"createdAt\": \"2018-06-22T11:02:28-04:00\"," + System.lineSeparator() +
                "  \"status\": \"valid\"" + System.lineSeparator() +
                "}";

        final String QUERY_ACCT_REPLAY_NONCE_2 = "ggf6K6xbBH8NK5ND69jGbM9TRMFO7HssBxzgWKDq0Js";

        final String ORDER_CERT_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMzk4Iiwibm9uY2UiOiJnZ2Y2SzZ4YkJIOE5LNU5ENjlqR2JNOVRSTUZPN0hzc0J4emdXS0RxMEpzIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvbmV3LW9yZGVyIn0\",\"payload\":\"eyJpZGVudGlmaWVycyI6W3sidHlwZSI6ImRucyIsInZhbHVlIjoiaW5sbmVzZXBwd2tmd2V3LmNvbSJ9XX0\",\"signature\":\"LbOPleWSCBaL1id8fw5fc5xm8eqFGLMOv_kwFXBr8QxfYF5RIDkW6Jsi-6gqCvDY7w5A7UcX-Fzcc2nUwAfwdHEOsUM9hkSBZkS54LmYWxZPLsIuBdTvkCSCSS94bqAnSnZXIch7seiJ4ZR1VQXVRnkMk5hD-_ipIOMYgVSwGqALz2NpW222QoY03LPaA5NkhnMdnIOia5aPzla5NQ9MXmOHBI5MIlTYIrYoccEXhM3jiqu1eDQohvMirUV76e2iAv8BovR8ys7fVC2AC36ithZNA-hRaxcHzJzXg9RGei4yOXcFoCHg6Xn1wygxshd2cc2Ov61TvTx9NUPmeDqK7g\"}";

        final String ORDER_CERT_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-04T18:27:35.087023897Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/398/186\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String ORDER_CERT_REPLAY_NONCE = "u7eY2Z97yZOJTU82Z3nNa9-gFTe4-srSEECIUpa-63c";
        final String ORDER_LOCATION = "http://localhost:4001/acme/order/398/186";

        final String AUTHZ_URL = "/acme/authz/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s";
        final String AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-04T14:27:35-04:00\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/535\"," + System.lineSeparator() +
                "      \"token\": \"AYnykYJWn-VsPeMLf6IFIXH1h9el6vmJf4LuX3qitwI\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/536\"," + System.lineSeparator() +
                "      \"token\": \"yLCOHl4TTraVOukhyFglf2u6bV7yhc3bQULkUJ1KWKI\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/537\"," + System.lineSeparator() +
                "      \"token\": \"6X7dIybvt_0JwQ8qUSJQqs83vS40mac5o0rhi8-_xl8\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMzk4Iiwibm9uY2UiOiJ1N2VZMlo5N3laT0pUVTgyWjNuTmE5LWdGVGU0LXNyU0VFQ0lVcGEtNjNjIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvY2hhbGxlbmdlL0xKUkgzLWdqVVB0NVU1djh3SDFDaDNlRmN4dThVSy11b3RqdXRtNU5COXMvNTM3In0\",\"payload\":\"e30\",\"signature\":\"gCp9SSPiVyJNAQ9PUB8rsBVb5aceV-XrjyjtWiXa8JJ5kgN1V4T_KIz372FLd1Bn7w6wGt1uMND_KBHvHRkTTspPJZxfQaJPDLzHvnswPjLsKK1-KHH5Bz3wjXDN379H9rVD8Qo0ZWU2VrI3d5JeuN4VEh5-PpQHJifCCd1pe7eNyOtN2aAZK8Up6HdDU__1CqtBgxbjqVy2uzZ-YiQJptZ5Zp0KnxHbeOPFJlfStoJdl6Xw0B_AFggRiDMOjIU3A4NCAKFdZjo06nd4XNFHusmgPKZTymRmmA6qhfn-NUgVxxv-KhvwMWOJkG61KNyliSjvNUADEKTauc664rENhA\"}";
        final String CHALLENGE_URL = "/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/537";

        final String CHALLENGE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"type\": \"http-01\"," + System.lineSeparator() +
                "  \"status\": \"pending\"," + System.lineSeparator() +
                "  \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/537\"," + System.lineSeparator() +
                "  \"token\": \"6X7dIybvt_0JwQ8qUSJQqs83vS40mac5o0rhi8-_xl8\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CHALLENGE_REPLAY_NONCE = "rjB3PBI-cOW5kdhoWhhruGwub0UnLVn_0PnlwdHP5aI";
        final String CHALLENGE_LOCATION = "http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/537";
        final String CHALLENGE_LINK = "<http://localhost:4001/acme/authz/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s>;rel=\"up\"";
        final String VERIFY_CHALLENGE_URL = "/.well-known/acme-challenge/6X7dIybvt_0JwQ8qUSJQqs83vS40mac5o0rhi8-_xl8";
        final String CHALLENGE_FILE_CONTENTS = "6X7dIybvt_0JwQ8qUSJQqs83vS40mac5o0rhi8-_xl8.w2Peh-j-AQnRWPMr_Xjf-IdvQBZYnSj__5h29xxhwkk";

        final String UPDATED_AUTHZ_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"identifier\": {" + System.lineSeparator() +
                "    \"type\": \"dns\"," + System.lineSeparator() +
                "    \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "  }," + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-27T14:27:35-04:00\"," + System.lineSeparator() +
                "  \"challenges\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/535\"," + System.lineSeparator() +
                "      \"token\": \"AYnykYJWn-VsPeMLf6IFIXH1h9el6vmJf4LuX3qitwI\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"tls-sni-01\"," + System.lineSeparator() +
                "      \"status\": \"pending\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/536\"," + System.lineSeparator() +
                "      \"token\": \"yLCOHl4TTraVOukhyFglf2u6bV7yhc3bQULkUJ1KWKI\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"http-01\"," + System.lineSeparator() +
                "      \"status\": \"valid\"," + System.lineSeparator() +
                "      \"url\": \"http://localhost:4001/acme/challenge/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s/537\"," + System.lineSeparator() +
                "      \"token\": \"6X7dIybvt_0JwQ8qUSJQqs83vS40mac5o0rhi8-_xl8\"," + System.lineSeparator() +
                "      \"validationRecord\": [" + System.lineSeparator() +
                "        {" + System.lineSeparator() +
                "          \"url\": \"http://inlneseppwkfwew.com.com:5002/.well-known/acme-challenge/6X7dIybvt_0JwQ8qUSJQqs83vS40mac5o0rhi8-_xl8\"," + System.lineSeparator() +
                "          \"hostname\": \"finlneseppwkfwew.com\"," + System.lineSeparator() +
                "          \"port\": \"5002\"," + System.lineSeparator() +
                "          \"addressesResolved\": [" + System.lineSeparator() +
                "            \"127.0.0.1\"" + System.lineSeparator() +
                "          ]," + System.lineSeparator() +
                "          \"addressUsed\": \"127.0.0.1\"" + System.lineSeparator() +
                "        }" + System.lineSeparator() +
                "      ]" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REQUEST_BODY = "{\"protected\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6Imh0dHA6Ly9sb2NhbGhvc3Q6NDAwMS9hY21lL2FjY3QvMzk4Iiwibm9uY2UiOiJyakIzUEJJLWNPVzVrZGhvV2hocnVHd3ViMFVuTFZuXzBQbmx3ZEhQNWFJIiwidXJsIjoiaHR0cDovL2xvY2FsaG9zdDo0MDAxL2FjbWUvZmluYWxpemUvMzk4LzE4NiJ9\",\"payload\":\"eyJjc3IiOiJNSUlFc3pDQ0Fwc0NBUUF3SGpFY01Cb0dBMVVFQXd3VGFXNXNibVZ6WlhCd2QydG1kMlYzTG1OdmJUQ0NBaUl3RFFZSktvWklodmNOQVFFQkJRQURnZ0lQQURDQ0Fnb0NnZ0lCQU1pSHBocGhaMTNRaG5zM0E5ZDVaSUx5MEpoNkNrZDhCNU1uTEJxbVl6UEwzWWR2VXFSSHdiZTBNWDVUQWRYdmR1eDZzNHBNbUg1a21xMUc2STFXU3pzcFBSN1g2TWZ6SGl5dEhPNlc1c0NidlpwVFRBYmw4NGMtZE5UckctdjJaMDA2dHRKWXp2NV9oYzI4UV82TnpZdkM1TWQ0T0QxelZXWnE5eU9KYjF0aHl4RUdwc0lIRVh4LUV4M3lWM2tpbmNfYnBvN2RUQjBndldIeDdiaHhLTUdmM1ktSm9mVUd0WHBWcTY1dDBfdnBQVHRIelBDbWppaG93WXc4S3RwVm5xcTdYVVMyUFFHU3UtcGNFYzBma0huSnNJUmdoRTRPR3E5THR5SkI3Zi1YLWdhRi14NzBDVEdoZ25JMFhaMlNBakYwTVdsVll2Mk9XNUQxOUM2YU1GeS12VlFqUU5VcjFUZGpvZVd3eEJEV0ZvNkwxTVZCcE5lUWVCZXJWT3lVelREZk1XLXBDSXJJUnMxV3lYVmV2WmJMc0pQTUZxRV9RNmV4bWdvU2NNVWUzN3gxenVCMV95VURZZkF4dVZVaVJfT3FUeHRfUUd1ZU8xVTJmQXpfRy0tN3VmbFhSQWw0OXZQM0hGc0ZlZHFKTXdNb2pvTDBSMWFvdEZBNGRSZ1dMc1l0Z3hqM1MtRVZYZWZScjJrTFFuTU1vbngwTjdOMTFuV09seGNSSDhOeld2NGMwYWh0TEliaUtmN2x2YTNMMklPN21RQlVjSHFkbm9pNndpbXJKTTZrQndIU0RyRDVXcVpTenQ0aFZTMmxtOTNEUDVGX2VuVEpDVnl4OUJVVUhoeDljeUxweEtyZ3BLcnk2OVp4MUdnbUUtTTNvVDNYMU4tdy1rMGViNVZSQWdNQkFBR2dVREJPQmdrcWhraUc5dzBCQ1E0eFFUQV9NQjRHQTFVZEVRUVhNQldDRTJsdWJHNWxjMlZ3Y0hkclpuZGxkeTVqYjIwd0hRWURWUjBPQkJZRUZOVmR6WV9nNTQxem80d0VIZUtJZjl5Mml5a1dNQTBHQ1NxR1NJYjNEUUVCREFVQUE0SUNBUUE3VTFYRll0RUM2QmxZY1BpUXhyQmg3eVoyNmM3cGw0U05IMFd4R2tQS1MwbVdDUlUzdm5zb05xblJjZXlxR2oyQmN4WnVUSms0U0IyOTJPc015Qm1sc0l3VWNYMHlodmpYT3RQLTRVUlBaS0o0WUdldmxlMW1xaGttZWpMT2R1bXEtNXFmajd5QXJsXzlETlUwam5UMDY1cHd6UkxCcS1VcXNtQXgxQ3czLW40LWE5VlIyemltNVFUUjZ1ZTF2NUJsTmxBTmI5eGZac3VHVXJ3akhsQ0NQU3FUWERKWnZNdGs4Y05SNUJtY21lZXFiZE9Yc1ZLSktaYTBhaW9ZcG9tT1pmREExQTZpT3RuNzRKc2tWNHBraEVmZUc1a0FEUnBJbmtkWkNIMlB6V1JvSWJRSmViNXY5RzU4aENyS182LWVaV1FjQW5sMkxTcDl5T0JkT2FPOGF4OGRwQUZYQVNyOVdKTFNWcHRuaDVKNnlaWER0eXFiYnctRXVpbjZTektmdTRYWlhUaHNnSmVfeWlncmNpZjRIQnNGc0wwWGFaTXUyY3U3cV9jaHM4bkJpOG5VM0F4RmZoVFZIeURjYkxLa1Z2Qm05WUZFUlFrWEl1WDZid1U0clhWLVFtcUpGNzJWV2ItZ0R0d040UnotWlFzaDZxS01HNTI3Mi15NWZaTjZMQkpTZTJ5WWpBbHhiM2xzZ0hNbFRKYzlPMkhnUE9udkREWmhCYUdPc1EtbTdha3ZJcWc2ZFVuYTB5c2t3MmI5WVd3TDRoMzdPT2JHZVJ1T2t4QTY5Z1N0bmZ1aFIxbGItQVRrMmZYaHFQRlAteGxIT2JtNUh2ekZKVE5ZRU5Rc3VnaFRIY3hoNERUM0F1dVo3aWZtVjQtNHVWTGdKVkdsVHdFcXJTS29PQSJ9\",\"signature\":\"NNtlMV9rfVtUvxgK9ucvWfXxwynELu5KeB-CGYrrM2VavfAeHWYDCr5Hs8Y3_UyOXSwXANUcVR4VjJnfoxsVn4TM-Zd0T6osmorVTIZGaI-xsWyxBckZ5g6xb7AGE6VLYKvCR4if_DhYq9M31Ge7l95rUTgxPg6xQbibGkbUfT1K-CcNetPWfCtQOhEf4V4jIO78MZUKuyb7eQXdWJqP5-ed4UAuqoclKqJ259zxrs1QcqbJGVjV5OJOpL-4odc086dkHvKPEkKIG3s-vFeYcToAVerR1rmIXPFenDu_JN9qqYtuyMrpfT_AhSavyN-DMaFKGvZ6YISQ5A4gq4ESJQ\"}";
        final String FINALIZE_URL = "/acme/finalize/398/186";

        final String FINALIZE_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-04T18:27:35Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/398/186\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/ffba1352e17b57c2032136e6729b0c2ebac9\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String FINALIZE_REPLAY_NONCE = "CZleS8d9p38tiIdjbzLa1PRJIEFcbLevx_jtlZQzYbo";
        final String FINALIZE_LOCATION = "http://localhost:4001/acme/order/398/186";

        final String CHECK_ORDER_URL = "/acme/order/398/186";

        final String CHECK_ORDER_RESPONSE_BODY = "{" + System.lineSeparator() +
                "  \"status\": \"valid\"," + System.lineSeparator() +
                "  \"expires\": \"2018-05-04T18:27:35Z\"," + System.lineSeparator() +
                "  \"identifiers\": [" + System.lineSeparator() +
                "    {" + System.lineSeparator() +
                "      \"type\": \"dns\"," + System.lineSeparator() +
                "      \"value\": \"inlneseppwkfwew.com\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"authorizations\": [" + System.lineSeparator() +
                "    \"http://localhost:4001/acme/authz/LJRH3-gjUPt5U5v8wH1Ch3eFcxu8UK-uotjutm5NB9s\"" + System.lineSeparator() +
                "  ]," + System.lineSeparator() +
                "  \"finalize\": \"http://localhost:4001/acme/finalize/398/186\"," + System.lineSeparator() +
                "  \"certificate\": \"http://localhost:4001/acme/cert/ffba1352e17b57c2032136e6729b0c2ebac9\"" + System.lineSeparator() +
                "}" + System.lineSeparator();

        final String CERT_URL = "/acme/cert/ffba1352e17b57c2032136e6729b0c2ebac9";

        final String CERT_RESPONSE_BODY = "-----BEGIN CERTIFICATE-----" + System.lineSeparator() +
                "MIIGZjCCBU6gAwIBAgITAP+6E1Lhe1fCAyE25nKbDC66yTANBgkqhkiG9w0BAQsF" + System.lineSeparator() +
                "ADAfMR0wGwYDVQQDDBRoMnBweSBoMmNrZXIgZmFrZSBDQTAeFw0xODA0MjcxNzI3" + System.lineSeparator() +
                "MzlaFw0xODA3MjYxNzI3MzlaMB4xHDAaBgNVBAMTE2lubG5lc2VwcHdrZndldy5j" + System.lineSeparator() +
                "b20wggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIKAoICAQDIh6YaYWdd0IZ7NwPX" + System.lineSeparator() +
                "eWSC8tCYegpHfAeTJywapmMzy92Hb1KkR8G3tDF+UwHV73bserOKTJh+ZJqtRuiN" + System.lineSeparator() +
                "Vks7KT0e1+jH8x4srRzulubAm72aU0wG5fOHPnTU6xvr9mdNOrbSWM7+f4XNvEP+" + System.lineSeparator() +
                "jc2LwuTHeDg9c1VmavcjiW9bYcsRBqbCBxF8fhMd8ld5Ip3P26aO3UwdIL1h8e24" + System.lineSeparator() +
                "cSjBn92PiaH1BrV6VauubdP76T07R8zwpo4oaMGMPCraVZ6qu11Etj0BkrvqXBHN" + System.lineSeparator() +
                "H5B5ybCEYIRODhqvS7ciQe3/l/oGhfse9AkxoYJyNF2dkgIxdDFpVWL9jluQ9fQu" + System.lineSeparator() +
                "mjBcvr1UI0DVK9U3Y6HlsMQQ1haOi9TFQaTXkHgXq1TslM0w3zFvqQiKyEbNVsl1" + System.lineSeparator() +
                "Xr2Wy7CTzBahP0OnsZoKEnDFHt+8dc7gdf8lA2HwMblVIkfzqk8bf0BrnjtVNnwM" + System.lineSeparator() +
                "/xvvu7n5V0QJePbz9xxbBXnaiTMDKI6C9EdWqLRQOHUYFi7GLYMY90vhFV3n0a9p" + System.lineSeparator() +
                "C0JzDKJ8dDezddZ1jpcXER/Dc1r+HNGobSyG4in+5b2ty9iDu5kAVHB6nZ6IusIp" + System.lineSeparator() +
                "qyTOpAcB0g6w+VqmUs7eIVUtpZvdwz+Rf3p0yQlcsfQVFB4cfXMi6cSq4KSq8uvW" + System.lineSeparator() +
                "cdRoJhPjN6E919TfsPpNHm+VUQIDAQABo4ICmjCCApYwDgYDVR0PAQH/BAQDAgWg" + System.lineSeparator() +
                "MB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAMBgNVHRMBAf8EAjAAMB0G" + System.lineSeparator() +
                "A1UdDgQWBBTVXc2P4OeNc6OMBB3iiH/ctospFjAfBgNVHSMEGDAWgBT7eE8S+WAV" + System.lineSeparator() +
                "gyyfF380GbMuNupBiTBkBggrBgEFBQcBAQRYMFYwIgYIKwYBBQUHMAGGFmh0dHA6" + System.lineSeparator() +
                "Ly8xMjcuMC4wLjE6NDAwMi8wMAYIKwYBBQUHMAKGJGh0dHA6Ly9ib3VsZGVyOjQ0" + System.lineSeparator() +
                "MzAvYWNtZS9pc3N1ZXItY2VydDAeBgNVHREEFzAVghNpbmxuZXNlcHB3a2Z3ZXcu" + System.lineSeparator() +
                "Y29tMCcGA1UdHwQgMB4wHKAaoBiGFmh0dHA6Ly9leGFtcGxlLmNvbS9jcmwwYQYD" + System.lineSeparator() +
                "VR0gBFowWDAIBgZngQwBAgEwTAYDKgMEMEUwIgYIKwYBBQUHAgEWFmh0dHA6Ly9l" + System.lineSeparator() +
                "eGFtcGxlLmNvbS9jcHMwHwYIKwYBBQUHAgIwEwwRRG8gV2hhdCBUaG91IFdpbHQw" + System.lineSeparator() +
                "ggEDBgorBgEEAdZ5AgQCBIH0BIHxAO8AdgDdmTT8peckgMlWaH2BNJkISbJJ97Vp" + System.lineSeparator() +
                "2Me8qz9cwfNuZAAAAWMIXFWgAAAEAwBHMEUCIAXRs9kJpmcgC2u5ErVOqK1OMUkx" + System.lineSeparator() +
                "xgnft0tykRpsUCRJAiEAzSVDO8nVa1MuAT4ak5G8gLy416yx/A2otdf9m7PejScA" + System.lineSeparator() +
                "dQAW6GnB0ZXq18P4lxrj8HYB94zhtp0xqFIYtoN/MagVCAAAAWMIXFWhAAAEAwBG" + System.lineSeparator() +
                "MEQCIF9IqHmvenOE4Oezwe4WdtRyEFoPbSdlXsO4owIuhaTFAiB2V77wpchHm1Gd" + System.lineSeparator() +
                "J4IyR23E6h+w69l3hT7GJAViHM8SoDANBgkqhkiG9w0BAQsFAAOCAQEACQvKKtNy" + System.lineSeparator() +
                "o0vlQq06Qmm8RRZUZCeWbaYUcMDxQhWgHaG89rG2JKhk/l1/raxPBj+q/StoFtwM" + System.lineSeparator() +
                "fOobIYqthjn0tMO+boRyI63CWTS5iQAAOxN/iV1noCejGYWyeRY3O1hqKn5xzflV" + System.lineSeparator() +
                "GAMCjvIVo3IBn4BjIBfcx+wj7giADWSaZI6jef7lPvFG1zekOtois4/SK1U9DUQB" + System.lineSeparator() +
                "pMdRMQKbH8BOC5WzpOAxJqg9M3BUAg+uqknX9c9A/OBm+Aw56aNrHUq9bX1svWht" + System.lineSeparator() +
                "RUBIKAHFtzW+W3R/KUddkuwYDDrTiZRWPNO4MjC8edLBLZV80XJzVoEmwocIcBjG" + System.lineSeparator() +
                "53PzUdxmaWsaTQ==" + System.lineSeparator() +
                "-----END CERTIFICATE-----" + System.lineSeparator() +
                "" + System.lineSeparator() +
                "-----BEGIN CERTIFICATE-----" + System.lineSeparator() +
                "MIIERTCCAy2gAwIBAgICElowDQYJKoZIhvcNAQELBQAwKzEpMCcGA1UEAwwgY2Fj" + System.lineSeparator() +
                "a2xpbmcgY3J5cHRvZ3JhcGhlciBmYWtlIFJPT1QwHhcNMTYwMzIyMDI0NzUyWhcN" + System.lineSeparator() +
                "MjEwMzIxMDI0NzUyWjAfMR0wGwYDVQQDDBRoMnBweSBoMmNrZXIgZmFrZSBDQTCC" + System.lineSeparator() +
                "ASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMIKR3maBcUSsncXYzQT13D5" + System.lineSeparator() +
                "Nr+Z3mLxMMh3TUdt6sACmqbJ0btRlgXfMtNLM2OU1I6a3Ju+tIZSdn2v21JBwvxU" + System.lineSeparator() +
                "zpZQ4zy2cimIiMQDZCQHJwzC9GZn8HaW091iz9H0Go3A7WDXwYNmsdLNRi00o14U" + System.lineSeparator() +
                "joaVqaPsYrZWvRKaIRqaU0hHmS0AWwQSvN/93iMIXuyiwywmkwKbWnnxCQ/gsctK" + System.lineSeparator() +
                "FUtcNrwEx9Wgj6KlhwDTyI1QWSBbxVYNyUgPFzKxrSmwMO0yNff7ho+QT9x5+Y/7" + System.lineSeparator() +
                "XE59S4Mc4ZXxcXKew/gSlN9U5mvT+D2BhDtkCupdfsZNCQWp27A+b/DmrFI9NqsC" + System.lineSeparator() +
                "AwEAAaOCAX0wggF5MBIGA1UdEwEB/wQIMAYBAf8CAQAwDgYDVR0PAQH/BAQDAgGG" + System.lineSeparator() +
                "MH8GCCsGAQUFBwEBBHMwcTAyBggrBgEFBQcwAYYmaHR0cDovL2lzcmcudHJ1c3Rp" + System.lineSeparator() +
                "ZC5vY3NwLmlkZW50cnVzdC5jb20wOwYIKwYBBQUHMAKGL2h0dHA6Ly9hcHBzLmlk" + System.lineSeparator() +
                "ZW50cnVzdC5jb20vcm9vdHMvZHN0cm9vdGNheDMucDdjMB8GA1UdIwQYMBaAFOmk" + System.lineSeparator() +
                "P+6epeby1dd5YDyTpi4kjpeqMFQGA1UdIARNMEswCAYGZ4EMAQIBMD8GCysGAQQB" + System.lineSeparator() +
                "gt8TAQEBMDAwLgYIKwYBBQUHAgEWImh0dHA6Ly9jcHMucm9vdC14MS5sZXRzZW5j" + System.lineSeparator() +
                "cnlwdC5vcmcwPAYDVR0fBDUwMzAxoC+gLYYraHR0cDovL2NybC5pZGVudHJ1c3Qu" + System.lineSeparator() +
                "Y29tL0RTVFJPT1RDQVgzQ1JMLmNybDAdBgNVHQ4EFgQU+3hPEvlgFYMsnxd/NBmz" + System.lineSeparator() +
                "LjbqQYkwDQYJKoZIhvcNAQELBQADggEBAKvePfYXBaAcYca2e0WwkswwJ7lLU/i3" + System.lineSeparator() +
                "GIFM8tErKThNf3gD3KdCtDZ45XomOsgdRv8oxYTvQpBGTclYRAqLsO9t/LgGxeSB" + System.lineSeparator() +
                "jzwY7Ytdwwj8lviEGtiun06sJxRvvBU+l9uTs3DKBxWKZ/YRf4+6wq/vERrShpEC" + System.lineSeparator() +
                "KuQ5+NgMcStQY7dywrsd6x1p3bkOvowbDlaRwru7QCIXTBSb8TepKqCqRzr6YREt" + System.lineSeparator() +
                "doIw2FE8MKMCGR2p+U3slhxfLTh13MuqIOvTuA145S/qf6xCkRc9I92GpjoQk87Z" + System.lineSeparator() +
                "v1uhpkgT9uwbRw0Cs5DMdxT/LgIUSfUTKU83GNrbrQNYinkJ77i6wG0=" + System.lineSeparator() +
                "-----END CERTIFICATE-----" + System.lineSeparator();

        final String AUTHZ_REPLAY_NONCE = "KvD4oVF2ahe2w2RtqbjYP9nJH_xzVWeHJIlhDRNn-N4";
        final String UPDATED_AUTHZ_REPLAY_NONCE = "jBxAXwYy9_19Bue5Wcij8aiAegiC4nqGTFD_42k3HQQ";
        final String CHECK_ORDER_REPLAY_NONCE = "yuXkl473reHRMcaVgTyTZ1AWO8Z_HbiHo9oj3RdoUog";
        final String CERT_REPLAY_NONCE = "9Ir87CU21P5mNNGfhBASf2dkD7QpJdZfB9BGMIzQW9Q";

        return new AcmeMockServerBuilder(s)
                .addDirectoryResponseBody(DIRECTORY_RESPONSE_BODY)
                .addNewNonceResponse(NEW_NONCE_RESPONSE)
                .addNewAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY_1, QUERY_ACCT_RESPONSE_BODY_1, QUERY_ACCT_REPLAY_NONCE_1, ACCT_LOCATION, 200)
                .updateAccountRequestAndResponse(QUERY_ACCT_REQUEST_BODY_2, QUERY_ACCT_RESPONSE_BODY_2, QUERY_ACCT_REPLAY_NONCE_2, ACCT_PATH, 200)
                .orderCertificateRequestAndResponse(ORDER_CERT_REQUEST_BODY, ORDER_CERT_RESPONSE_BODY, ORDER_CERT_REPLAY_NONCE, ORDER_LOCATION, 201, false)
                .addAuthorizationResponseBody(AUTHZ_URL, AUTHZ_RESPONSE_BODY, AUTHZ_REPLAY_NONCE)
                .addChallengeRequestAndResponse(CHALLENGE_REQUEST_BODY, CHALLENGE_URL, CHALLENGE_RESPONSE_BODY, CHALLENGE_REPLAY_NONCE, CHALLENGE_LOCATION, CHALLENGE_LINK, 200, false, VERIFY_CHALLENGE_URL, CHALLENGE_FILE_CONTENTS, AUTHZ_URL, UPDATED_AUTHZ_RESPONSE_BODY, UPDATED_AUTHZ_REPLAY_NONCE)
                .addFinalizeRequestAndResponse(FINALIZE_RESPONSE_BODY, FINALIZE_REPLAY_NONCE, FINALIZE_URL, FINALIZE_LOCATION, 200)
                .addCheckOrderRequestAndResponse(CHECK_ORDER_URL, CHECK_ORDER_RESPONSE_BODY, 200, CHECK_ORDER_REPLAY_NONCE)
                .addCertificateRequestAndResponse(CERT_URL, CERT_RESPONSE_BODY, 200, CERT_REPLAY_NONCE)
                .build();
    }

}
