/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.common;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;



/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class HttpRequest {

    private static String execute(final Callable<String> task, final long timeout, final TimeUnit unit) throws TimeoutException, IOException {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<String> result = executor.submit(task);
        try {
            return result.get(timeout, unit);
        } catch (TimeoutException e) {
            result.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            // should not happen
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            // by virtue of the Callable redefinition above I can cast
            throw new IOException(e);
        } finally {
            executor.shutdownNow();
            try {
                executor.awaitTermination(timeout, unit);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    public static String get(final String spec, final long timeout, final TimeUnit unit) throws IOException, ExecutionException, TimeoutException {
        final URL url = new URL(spec);
        Callable<String> task = new Callable<String>() {
            @Override
            public String call() throws Exception {
                final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                return processResponse(conn);
            }
        };
        return execute(task, timeout, unit);
    }

    /**
     * Returns the URL response as a string.
     *
     * @param spec  URL spec
     * @param waitUntilAvailableMs  maximum timeout in milliseconds to wait for the URL to return non 404 response
     * @param responseTimeout  the timeout to read the response
     * @param responseTimeoutUnit  the time unit for responseTimeout
     * @return  URL response
     * @throws IOException
     * @throws ExecutionException
     * @throws TimeoutException
     */
    public static String get(final String spec, final long waitUntilAvailableMs, final long responseTimeout, final TimeUnit responseTimeoutUnit) throws IOException, ExecutionException, TimeoutException {
        final URL url = new URL(spec);
        Callable<String> task = new Callable<String>() {
            @Override
            public String call() throws Exception {
                final long startTime = System.currentTimeMillis();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                while(conn.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    if(System.currentTimeMillis() - startTime >= waitUntilAvailableMs) {
                        break;
                    }
                    try {
                        Thread.sleep(500);
                    } catch(InterruptedException e) {
                        break;
                    } finally {
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setDoInput(true);
                    }
                }
                return processResponse(conn);
            }
        };
        return execute(task, responseTimeout, responseTimeoutUnit);
    }

    public static String get(final String spec, final String username, final String password, final long timeout, final TimeUnit unit) throws IOException, TimeoutException {
        final URL url = new URL(spec);
        Callable<String> task = new Callable<String>() {
            @Override
            public String call() throws IOException {
                final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                if (username != null) {
                    final String userpassword = username + ":" + password;
                    final String basicAuthorization = Base64.getEncoder().encodeToString(userpassword.getBytes(StandardCharsets.UTF_8));
                    conn.setRequestProperty("Authorization", "Basic " + basicAuthorization);
                }
                conn.setDoInput(true);
                return processResponse(conn);
            }
        };
        return execute(task, timeout, unit);
    }

    private static String read(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        while((b = in.read()) != -1) {
            out.write(b);
        }
        return out.toString();
    }

    private static String processResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            final InputStream err = conn.getErrorStream();
            try {
                String response = err != null ? read(err) : null;
                throw new IOException(String.format("HTTP Status %d Response: %s", responseCode, response));
            }
            finally {
                if (err != null) {
                    err.close();
                }
            }
        }
        final InputStream in = conn.getInputStream();
        try {
            return read(in);
        }
        finally {
            in.close();
        }
    }

    public static String put(final String spec, final String message, final long timeout, final TimeUnit unit) throws MalformedURLException, ExecutionException, TimeoutException {
        return execRequestMethod(spec, message, timeout, unit, "PUT");
    }

    /**
     * Executes an HTTP request to write the specified message.
     *
     * @param spec The {@link URL} in String form
     * @param message Message to write
     * @param timeout Timeout value
     * @param unit Timeout units
     * @param requestMethod Name of the HTTP method to execute (ie. HEAD, GET, POST)
     * @return
     * @throws MalformedURLException
     * @throws ExecutionException
     * @throws TimeoutException
     */
    private static String execRequestMethod(final String spec, final String message, final long timeout, final TimeUnit unit, final String requestMethod) throws MalformedURLException, ExecutionException, TimeoutException {

        if(requestMethod==null||requestMethod.isEmpty()){
            throw new IllegalArgumentException("Request Method must be specified (ie. GET, PUT, DELETE etc)");
        }

        final URL url = new URL(spec);
        Callable<String> task = new Callable<String>() {
            @Override
            public String call() throws Exception {
                final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setRequestMethod(requestMethod);
                final OutputStream out = conn.getOutputStream();
                try {
                    write(out, message);
                    return processResponse(conn);
                }
                finally {
                    out.close();
                }
            }
        };
        try {
            return execute(task, timeout, unit);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String post(final String spec, final String message, final long timeout, final TimeUnit unit) throws MalformedURLException, ExecutionException, TimeoutException {
        return execRequestMethod(spec, message, timeout, unit, "POST");
    }

    public static String delete(final String spec, final String message, final long timeout, final TimeUnit unit) throws MalformedURLException, ExecutionException, TimeoutException {
        return execRequestMethod(spec, message, timeout, unit, "DELETE");
    }

    public static String head(final String spec, final String message, final long timeout, final TimeUnit unit) throws MalformedURLException, ExecutionException, TimeoutException {
        return execRequestMethod(spec, message, timeout, unit, "HEAD");
    }

    private static void write(OutputStream out, String message) throws IOException {
        final OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write(message);
        writer.flush();
    }
}
