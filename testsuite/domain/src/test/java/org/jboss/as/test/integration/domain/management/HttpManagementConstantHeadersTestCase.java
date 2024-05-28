/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.management;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.test.http.Authentication;
import org.jboss.as.test.integration.domain.management.util.DomainLifecycleUtil;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

/**
 * Test case to test custom / constant headers are applied to existing contexts.
 * See WFCORE-1110.
 *
 * @author <a href="mailto:tterem@redhat.com">Tomas Terem</a>
 */
public class HttpManagementConstantHeadersTestCase {

   private static final int MGMT_PORT = 9990;
   private static final String ROOT_CTX = "/";
   private static final String MGMT_CTX = "/management";
   private static final String ERROR_CTX = "/error";
   private static final String OPERATION = "operation";

   private static final String TEST_HEADER = "X-All";
   private static final String TEST_HEADER_2 = "X-Management";
   private static final String TEST_HEADER_3 = "X-Error";
   private static final String TEST_VALUE = "All";
   private static final String TEST_VALUE_2 = "Management";
   private static final String TEST_VALUE_3 = "Error";

   private static final String SECURITY_TEST_HEADER = "X-XSS-Protection";
   private static final String SECURITY_TEST_HEADER_2 = "X-Frame-Options";
   private static final String SECURITY_TEST_HEADER_3 = "Content-Security-Policy";
   private static final String SECURITY_TEST_HEADER_4 = "Strict-Transport-Security";
   private static final String SECURITY_TEST_HEADER_5 = "X-Content-Type-Options";
   private static final String SECURITY_TEST_VALUE = "1; mode=block";
   private static final String SECURITY_TEST_VALUE_2 = "SAMEORIGIN";
   private static final String SECURITY_TEST_VALUE_3 = "default-src https: data: 'unsafe-inline' 'unsafe-eval'";
   private static final String SECURITY_TEST_VALUE_4 = "max-age=31536000; includeSubDomains;";
   private static final String SECURITY_TEST_VALUE_5 = "nosniff";

   private static final PathAddress INTERFACE_ADDRESS = PathAddress.pathAddress(PathElement.pathElement(HOST, "primary"), PathElement.pathElement(CORE_SERVICE, MANAGEMENT), PathElement.pathElement(MANAGEMENT_INTERFACE, HTTP_INTERFACE));

   private static DomainTestSupport testSupport;
   private static DomainLifecycleUtil domainPrimaryLifecycleUtil;
   private static DomainLifecycleUtil domainSecondaryLifecycleUtil;
   private URL managementUrl;
   private URL errorUrl;
   private HttpClient httpClient;

   @BeforeClass
   public static void before() {
      testSupport = DomainTestSupport.createAndStartDefaultSupport(HttpManagementConstantHeadersTestCase.class.getSimpleName());
      domainPrimaryLifecycleUtil = testSupport.getDomainPrimaryLifecycleUtil();
      domainSecondaryLifecycleUtil = testSupport.getDomainSecondaryLifecycleUtil();
   }

   @Before
   public void createClient() throws Exception {
      this.managementUrl = new URL("http", DomainTestSupport.primaryAddress, MGMT_PORT, MGMT_CTX);
      this.errorUrl = new URL("http", DomainTestSupport.primaryAddress, MGMT_PORT, ERROR_CTX);

      CredentialsProvider credsProvider = new BasicCredentialsProvider();
      credsProvider.setCredentials(new AuthScope(managementUrl.getHost(), managementUrl.getPort()), new UsernamePasswordCredentials(Authentication.USERNAME, Authentication.PASSWORD));

      this.httpClient = HttpClients.custom()
            .setDefaultCredentialsProvider(credsProvider)
            .build();
   }

   @After
   public void closeClient() {
      if (httpClient instanceof Closeable) {
         try {
            ((Closeable) httpClient).close();
         } catch (IOException e) {
            Logger.getLogger(HttpManagementConstantHeadersTestCase.class).error("Failed closing client", e);
         }
      }
   }

   @After
   public void removeHeaders() throws TimeoutException, InterruptedException, IOException {
      ModelNode undefineAttribute = new ModelNode();
      undefineAttribute.get(ADDRESS).set(INTERFACE_ADDRESS.toModelNode());
      undefineAttribute.get(OPERATION).set(UNDEFINE_ATTRIBUTE_OPERATION);
      undefineAttribute.get(NAME).set(CONSTANT_HEADERS);

      domainPrimaryLifecycleUtil.executeForResult(undefineAttribute);
      reload();
   }

   @AfterClass
   public static void after() {
      try {
         assertNotNull(testSupport);
         testSupport.close();
      } finally {
         domainPrimaryLifecycleUtil = null;
         domainSecondaryLifecycleUtil = null;
         testSupport = null;
      }
   }

   /**
    * Test that a call to the '/management' endpoint returns the expected headers.
    */
   @Test
   public void testManagement() throws Exception {
      configureHeaders();

      HttpGet get = new HttpGet(managementUrl.toURI().toString());
      HttpResponse response = httpClient.execute(get);
      assertEquals(200, response.getStatusLine().getStatusCode());

      Header header = response.getFirstHeader(TEST_HEADER);
      assertEquals(TEST_VALUE, header.getValue());

      header = response.getFirstHeader(TEST_HEADER_2);
      assertEquals(TEST_VALUE_2, header.getValue());

      header = response.getFirstHeader(TEST_HEADER_3);
      assertNull("Header " + TEST_HEADER_3 + " unexpected", header);
   }

   /**
    * Test that a call to the '/error' endpoint returns the expected headers.
    */
   @Test
   public void testError() throws Exception {
      configureHeaders();

      HttpGet get = new HttpGet(errorUrl.toURI().toString());
      HttpResponse response = httpClient.execute(get);
      assertEquals(200, response.getStatusLine().getStatusCode());

      Header header = response.getFirstHeader(TEST_HEADER);
      assertEquals(TEST_VALUE, header.getValue());

      header = response.getFirstHeader(TEST_HEADER_3);
      assertEquals(TEST_VALUE_3, header.getValue());

      header = response.getFirstHeader(TEST_HEADER_2);
      assertNull("Header " + TEST_HEADER_2 + " unexpected", header);
   }

   /**
    * Configure one header to be applied to all endpoints (X-All), one to be applied only to /management endpoints
    * (X-Management) and one to be applied only to /error endpoint (X-Error)
    */
   private void configureHeaders() throws InterruptedException, TimeoutException, IOException {
      Map<String, Map<String, String>> headersMap = new HashMap<>();
      headersMap.put(ROOT_CTX, Collections.singletonMap(TEST_HEADER, TEST_VALUE));
      headersMap.put(MGMT_CTX, Collections.singletonMap(TEST_HEADER_2, TEST_VALUE_2));
      headersMap.put(ERROR_CTX, Collections.singletonMap(TEST_HEADER_3, TEST_VALUE_3));

      domainPrimaryLifecycleUtil.executeForResult(createConstantHeadersOperation(headersMap));
      reload();
   }

   /**
    * Test that security headers can be configured and are returned from '/management' endpoint
    */
   @Test
   public void testSecurity() throws Exception {
      configureSecurityHeaders();

      HttpGet get = new HttpGet(managementUrl.toURI().toString());
      HttpResponse response = httpClient.execute(get);
      assertEquals(200, response.getStatusLine().getStatusCode());

      Header header = response.getFirstHeader(SECURITY_TEST_HEADER);
      assertEquals(SECURITY_TEST_VALUE, header.getValue());

      header = response.getFirstHeader(SECURITY_TEST_HEADER_2);
      assertEquals(SECURITY_TEST_VALUE_2, header.getValue());

      header = response.getFirstHeader(SECURITY_TEST_HEADER_3);
      assertEquals(SECURITY_TEST_VALUE_3, header.getValue());

      header = response.getFirstHeader(SECURITY_TEST_HEADER_4);
      assertEquals(SECURITY_TEST_VALUE_4, header.getValue());

      header = response.getFirstHeader(SECURITY_TEST_HEADER_5);
      assertEquals(SECURITY_TEST_VALUE_5, header.getValue());
   }

   /**
    * Configure security headers for /management endpoint.
    */
   private void configureSecurityHeaders() throws InterruptedException, TimeoutException, IOException {
      Map<String, Map<String, String>> headersMap = new HashMap<>();

      Map<String, String> headers = new HashMap<>();
      headers.put(SECURITY_TEST_HEADER, SECURITY_TEST_VALUE);
      headers.put(SECURITY_TEST_HEADER_2, SECURITY_TEST_VALUE_2);
      headers.put(SECURITY_TEST_HEADER_3, SECURITY_TEST_VALUE_3);
      headers.put(SECURITY_TEST_HEADER_4, SECURITY_TEST_VALUE_4);
      headers.put(SECURITY_TEST_HEADER_5, SECURITY_TEST_VALUE_5);

      headersMap.put(MGMT_CTX, headers);

      domainPrimaryLifecycleUtil.executeForResult(createConstantHeadersOperation(headersMap));

      reload();
   }

   private static ModelNode createConstantHeadersOperation(final Map<String, Map<String, String>> constantHeadersValues) {
      ModelNode writeAttribute = new ModelNode();
      writeAttribute.get(ADDRESS).set(INTERFACE_ADDRESS.toModelNode());
      writeAttribute.get(OPERATION).set(WRITE_ATTRIBUTE_OPERATION);
      writeAttribute.get(NAME).set(CONSTANT_HEADERS);

      ModelNode constantHeaders = new ModelNode();
      for (Map.Entry<String, Map<String, String>> entry : constantHeadersValues.entrySet()) {
         constantHeaders.add(createHeaderMapping(entry.getKey(), entry.getValue()));
      }

      writeAttribute.get(VALUE).set(constantHeaders);

      return writeAttribute;
   }

   private static ModelNode createHeaderMapping(final String path, final Map<String, String> headerValues) {
      ModelNode headerMapping = new ModelNode();
      headerMapping.get(PATH).set(path);
      ModelNode headers = new ModelNode();
      headers.add();     // Ensure the type of 'headers' is List even if no content is added.
      headers.remove(0);
      for (Map.Entry<String, String> entry : headerValues.entrySet()) {
         ModelNode singleMapping = new ModelNode();
         singleMapping.get(NAME).set(entry.getKey());
         singleMapping.get(VALUE).set(entry.getValue());
         headers.add(singleMapping);
      }
      headerMapping.get(HEADERS).set(headers);

      return headerMapping;
   }

   private void reload() throws IOException, TimeoutException, InterruptedException {
      DomainLifecycleUtil.ReloadParameters parameters = new DomainLifecycleUtil.ReloadParameters()
              .setWaitForServers(true);

      domainPrimaryLifecycleUtil.reload("primary", parameters);
      assertNotNull(domainSecondaryLifecycleUtil);
      domainSecondaryLifecycleUtil.awaitServers(System.currentTimeMillis());
      domainSecondaryLifecycleUtil.awaitHostController(System.currentTimeMillis());
   }
}
