/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.audit;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.security.KeyStore;
import java.util.logging.ErrorManager;

import javax.net.SocketFactory;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.jboss.as.controller.interfaces.InetAddressUtil;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.handlers.SyslogHandler;
import org.jboss.logmanager.handlers.SyslogHandler.Protocol;
import org.jboss.logmanager.handlers.SyslogHandler.SyslogType;
import org.jboss.logmanager.handlers.TcpOutputStream;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.credential.source.CredentialSource;
import org.wildfly.security.password.interfaces.ClearPassword;
import org.xnio.IoUtils;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SyslogAuditLogHandler extends AuditLogHandler {

    private final PathManagerService pathManager;

    private volatile SyslogHandler handler;
    private volatile String appName;
    private volatile String hostName;
    private volatile SyslogType syslogType = SyslogType.RFC5424;
    private volatile boolean truncate;
    private volatile int maxLength;
    private volatile InetAddress syslogServerAddress;
    private volatile int port = 514;
    private volatile Transport transport = Transport.UDP;
    private volatile MessageTransfer messageTransfer = MessageTransfer.NON_TRANSPARENT_FRAMING;
    private volatile Facility facility;
    private volatile String tlsTrustStorePath;
    private volatile String tlsTrustStoreRelativeTo;
    private volatile String tlsTrustStorePassword;
    private volatile SyslogCredentialReferenceSupplier syslogAuditLogHandlerService;
    private volatile String tlsClientCertStorePath;
    private volatile String tlsClientCertStoreRelativeTo;
    private volatile String tlsClientCertStorePassword;
    private volatile String tlsClientCertStoreKeyPassword;
    private volatile TransportErrorManager errorManager;
    private volatile int reconnectTimeout = -1;
    private volatile long lastErrorTime = -1;
    // This should be guarded by the config lock in the ManagedAuditLoggerImpl
    private boolean connected;

    private static final String APPNAME_FILTER = "[\\P{Print}\\ ]";
    private static final String APPNAME_REPLACE_VALUE = "_";
    private static final char[] EMPTY_PASSWORD = new char[0];

    public SyslogAuditLogHandler(String name, String formatterName, int maxFailureCount, PathManagerService pathManager, SyslogCredentialReferenceSupplier syslogAuditLogHandlerService) {
        super(name, formatterName, maxFailureCount);
        this.pathManager = pathManager;
        this.syslogAuditLogHandlerService = syslogAuditLogHandlerService;
        connected = false;
    }

    public void setHostName(String hostName) {
        assert hostName != null;
        this.hostName = hostName;
    }

    public void setAppName(String appName) {
        assert appName != null;
        // filter any non-printing characters, or ascii spaces to -
        this.appName = appName.replaceAll(APPNAME_FILTER, APPNAME_REPLACE_VALUE);
        //This gets updated immediately
        if (handler != null) {
            handler.setAppName(appName);
        }
    }

    public void setFacility(Facility facility) {
        assert facility != null;
        this.facility = facility;
        //This gets updated immediately
        if (handler != null) {
            handler.setFacility(facility.convert());
        }
    }

    public void setSyslogType(SyslogType syslogType) {
        assert syslogType != null;
        this.syslogType = syslogType;
    }

    public void setTruncate(boolean truncate) {
        this.truncate = truncate;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public void setMessageTransfer(MessageTransfer messageTransfer) {
        assert messageTransfer != null;
        this.messageTransfer = messageTransfer;
    }

    public void setSyslogServerAddress(InetAddress syslogServerAddress) {
        assert syslogServerAddress != null;
        this.syslogServerAddress = syslogServerAddress;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setTransport(Transport transport) {
        assert transport != null;
        this.transport = transport;
    }

    public void setTlsTrustStorePath(String tlsTrustStorePath) {
        this.tlsTrustStorePath = tlsTrustStorePath;
    }

    public void setTlsTrustStoreRelativeTo(String tlsTrustStoreRelativeTo) {
        this.tlsTrustStoreRelativeTo = tlsTrustStoreRelativeTo;
    }

    public void setTlsTruststorePassword(String tlsTrustStorePassword) {
        this.tlsTrustStorePassword = tlsTrustStorePassword;
    }

    public void setTlsClientCertStorePath(String tlsClientCertStorePath) {
        this.tlsClientCertStorePath = tlsClientCertStorePath;
    }

    public void setTlsClientCertStoreRelativeTo(String tlsClientCertStoreRelativeTo) {
        this.tlsClientCertStoreRelativeTo = tlsClientCertStoreRelativeTo;
    }

    public void setTlsClientCertStorePassword(String tlsClientCertStorePassword) {
        this.tlsClientCertStorePassword = tlsClientCertStorePassword;
    }

    public void setTlsClientCertStoreKeyPassword(String tlsClientCertStoreKeyPassword) {
        this.tlsClientCertStoreKeyPassword = tlsClientCertStoreKeyPassword;
    }

    public void setReconnectTimeout(int reconnectTimeout) {
        this.reconnectTimeout = reconnectTimeout;
    }

    private char[] resolvePassword(ExceptionSupplier<CredentialSource, Exception> sourceSupplier, String pwd) {
        try {
            if (sourceSupplier != null) {
                CredentialSource cs = sourceSupplier.get();
                if (cs != null) {
                    org.wildfly.security.credential.PasswordCredential credential = cs.getCredential(org.wildfly.security.credential.PasswordCredential.class);
                    if (credential != null) {
                        ClearPassword password = credential.getPassword(ClearPassword.class);
                        if (password != null) {
                            return password.getPassword();
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ControllerLogger.ROOT_LOGGER.errorObtainingPassword(ex, ex.getMessage());
            return pwd == null ? EMPTY_PASSWORD : pwd.toCharArray();
        }
        return pwd == null ? EMPTY_PASSWORD : pwd.toCharArray();
    }

    @Override
    boolean isActive() {
        if (hasTooManyFailures()) {
            if (reconnectTimeout >= 0) {
                long end = lastErrorTime + reconnectTimeout * 1000;
                return System.currentTimeMillis() > end;
            }
            return false;
        }
        return true;
    }

    @Override
    void initialize() {
        // Only attempt initialization if this if the first invocation, the server has been stopped or a previous
        // initialization invocation has failed
        if (connected) {
            return;
        }
        SyslogHandler handler = this.handler;
        // If the handler is not null, we should clean it up before initializing a new handler
        if (handler != null) {
            ControllerLogger.MGMT_OP_LOGGER.debug("Stopping a previously initialized syslog handler.");
            stop();
        }
        try {
            final Protocol protocol;
            switch (transport) {
            case UDP:
                protocol = Protocol.UDP;
                break;
            case TCP:
                protocol = Protocol.TCP;
                break;
            case TLS:
                protocol = Protocol.SSL_TCP;
                break;
            default:
                //i18n not needed, user code will not end up here
                throw new IllegalStateException("Unknown protocol");
            }
            handler = new SyslogHandler(syslogServerAddress, port, facility.convert(), syslogType, protocol, hostName == null ? InetAddressUtil.getLocalHostName() : hostName);
            handler.setAppName(appName);
            handler.setTruncate(truncate);
            if (maxLength != 0) {
                handler.setMaxLength(maxLength);
            }

            //Common for all protocols
            handler.setSyslogType(syslogType);

            final TransportErrorManager errorManager = new TransportErrorManager();
            handler.setErrorManager(errorManager);

            if (transport != Transport.UDP){
                if (messageTransfer == MessageTransfer.NON_TRANSPARENT_FRAMING) {
                    handler.setUseCountingFraming(false);
                    handler.setMessageDelimiter("\n");
                    handler.setUseMessageDelimiter(true);
                } else {
                    handler.setUseCountingFraming(true);
                    handler.setMessageDelimiter(null);
                    handler.setUseMessageDelimiter(false);
                }

                if (transport == Transport.TLS){
                    final SSLContext context = SSLContext.getInstance("TLS");
                    KeyManager[] keyManagers = null;
                    if (tlsClientCertStorePath != null){
                        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                        final FileInputStream in = new FileInputStream(pathManager.resolveRelativePathEntry(tlsClientCertStorePath, tlsClientCertStoreRelativeTo));
                        try {
                            final KeyStore ks = KeyStore.getInstance("JKS");
                            char[] tlsClientCertStorePwd = resolvePassword(syslogAuditLogHandlerService.getTlsClientCertStoreSupplier(), tlsClientCertStorePassword);
                            ks.load(in, tlsClientCertStorePwd);
                            kmf.init(ks, resolvePassword(syslogAuditLogHandlerService.getTlsClientCertStoreKeySupplier(),tlsClientCertStoreKeyPassword != null ? tlsClientCertStoreKeyPassword : String.valueOf(tlsClientCertStorePwd)));
                            keyManagers = kmf.getKeyManagers();
                        } finally {
                            IoUtils.safeClose(in);
                        }
                    }
                    TrustManager[] trustManagers = null;
                    if (tlsTrustStorePath != null){
                        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                        final FileInputStream in = new FileInputStream(pathManager.resolveRelativePathEntry(tlsTrustStorePath, tlsTrustStoreRelativeTo));
                        try {
                            final KeyStore ks = KeyStore.getInstance("JKS");
                            ks.load(in, resolvePassword(syslogAuditLogHandlerService.getTlsTrustStoreSupplier(), tlsTrustStorePassword));
                            tmf.init(ks);
                            trustManagers = tmf.getTrustManagers();
                        } finally {
                            IoUtils.safeClose(in);
                        }

                    }
                    context.init(keyManagers, trustManagers, null);
                    handler.setOutputStream(new SSLContextOutputStream(context, syslogServerAddress, port));
                } else {
                    handler.setOutputStream(new AuditLogTcpOutputStream(syslogServerAddress, port));
                    handler.setProtocol(transport == Transport.TCP ? Protocol.TCP : Protocol.SSL_TCP);
                }
            }
            this.handler = handler;
            this.errorManager = errorManager;
            connected = true;
        } catch (Exception e) {
            // Failed to initialize the handler, clean up the resources
            connected = false;
            if (handler != null) {
                try {
                    handler.close();
                } catch (Exception ignore){}
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    void stop() {
        connected = false;
        SyslogHandler handler = this.handler;
        this.handler = null;
        if (handler != null) {
            handler.close();
        }

    }

    private boolean isReconnect() {
        return hasTooManyFailures() && isActive();
    }

    FailureCountHandler getFailureCountHandler() {
        return isReconnect() ? new ReconnectFailureCountHandler() : super.getFailureCountHandler();
    }

    @Override
    void writeLogItem(String formattedItem) throws IOException {
        boolean reconnect =  isReconnect();
        if (!reconnect) {
            handler.publish(new ExtLogRecord(Level.WARN, formattedItem, SyslogAuditLogHandler.class.getName()));
            errorManager.getAndThrowError();
        } else {
            ControllerLogger.MGMT_OP_LOGGER.attemptingReconnectToSyslog(name, reconnectTimeout);
            try {
                // Reinitialise the delegating syslog handler if required, if we're already connected we don't need to
                // establish a new connection
                if (!connected) {
                    stop();
                    initialize();
                }
                handler.publish(new ExtLogRecord(Level.WARN, formattedItem, SyslogAuditLogHandler.class.getName()));
                errorManager.getAndThrowError();
                lastErrorTime = -1;
            } catch (Exception e) {
                // A failure has occurred and initialization should be reattempted
                connected = false;
                lastErrorTime = System.currentTimeMillis();
                errorManager.throwAsIoOrRuntimeException(e);
            }
        }
    }

    @Override
    boolean isDifferent(AuditLogHandler other){
        if (other instanceof SyslogAuditLogHandler == false){
            return true;
        }
        SyslogAuditLogHandler otherHandler = (SyslogAuditLogHandler)other;
        if (!name.equals(otherHandler.name)){
            return true;
        }
        if (!getFormatterName().equals(otherHandler.getFormatterName())) {
            return true;
        }
        if (!hostName.equals(otherHandler.hostName)){
            return true;
        }
        if (!syslogType.equals(otherHandler.syslogType)){
            return true;
        }
        if (!truncate == otherHandler.truncate) {
            return true;
        }
        if (maxLength != otherHandler.maxLength) {
            return true;
        }
        if (!syslogServerAddress.equals(otherHandler.syslogServerAddress)){
            return true;
        }
        if (port != otherHandler.port){
            return true;
        }
        if (!transport.equals(otherHandler.transport)){
            return true;
        }
        //These may or not be null depending on the transport
        if (!compare(messageTransfer, otherHandler.messageTransfer)){
            return true;
        }
        if (!compare(tlsTrustStorePath, otherHandler.tlsTrustStorePath)){
            return true;
        }
        if (!compare(tlsTrustStoreRelativeTo, otherHandler.tlsTrustStoreRelativeTo)){
            return true;
        }
        if (!compare(tlsTrustStorePassword, otherHandler.tlsTrustStorePassword)){
            return true;
        }
        if (!compare(tlsClientCertStorePath, otherHandler.tlsClientCertStorePath)){
            return true;
        }
        if (!compare(tlsClientCertStoreRelativeTo, otherHandler.tlsClientCertStoreRelativeTo)){
            return true;
        }
        if (!compare(tlsClientCertStorePassword, otherHandler.tlsClientCertStorePassword)){
            return true;
        }
        if (!compare(tlsClientCertStoreKeyPassword, otherHandler.tlsClientCertStoreKeyPassword)){
            return true;
        }
        return false;
    }

    private boolean compare(Object one, Object two){
        if (one == null && two == null){
            return true;
        }
        if (one == null && two != null){
            return false;
        }
        if (one != null && two == null){
            return false;
        }
        return one.equals(two);
    }

    public enum Transport {
        UDP,
        TCP,
        TLS
    }

    public enum MessageTransfer {
        OCTET_COUNTING,
        NON_TRANSPARENT_FRAMING;
    }

    /**
     * Facility as defined by RFC-5424 (<a href="http://tools.ietf.org/html/rfc5424">http://tools.ietf.org/html/rfc5424</a>)
     * and RFC-3164 (<a href="http://tools.ietf.org/html/rfc3164">http://tools.ietf.org/html/rfc3164</a>).
     */
    public static enum Facility {
        KERNEL(SyslogHandler.Facility.KERNEL),
        USER_LEVEL(SyslogHandler.Facility.USER_LEVEL),
        MAIL_SYSTEM(SyslogHandler.Facility.MAIL_SYSTEM),
        SYSTEM_DAEMONS(SyslogHandler.Facility.SYSTEM_DAEMONS),
        SECURITY(SyslogHandler.Facility.SECURITY),
        SYSLOGD(SyslogHandler.Facility.SYSLOGD),
        LINE_PRINTER(SyslogHandler.Facility.LINE_PRINTER),
        NETWORK_NEWS(SyslogHandler.Facility.NETWORK_NEWS),
        UUCP(SyslogHandler.Facility.UUCP),
        CLOCK_DAEMON(SyslogHandler.Facility.CLOCK_DAEMON),
        SECURITY2(SyslogHandler.Facility.SECURITY2),
        FTP_DAEMON(SyslogHandler.Facility.FTP_DAEMON),
        NTP(SyslogHandler.Facility.NTP),
        LOG_AUDIT(SyslogHandler.Facility.LOG_AUDIT),
        LOG_ALERT(SyslogHandler.Facility.LOG_ALERT),
        CLOCK_DAEMON2(SyslogHandler.Facility.CLOCK_DAEMON2),
        LOCAL_USE_0(SyslogHandler.Facility.LOCAL_USE_0),
        LOCAL_USE_1(SyslogHandler.Facility.LOCAL_USE_1),
        LOCAL_USE_2(SyslogHandler.Facility.LOCAL_USE_2),
        LOCAL_USE_3(SyslogHandler.Facility.LOCAL_USE_3),
        LOCAL_USE_4(SyslogHandler.Facility.LOCAL_USE_4),
        LOCAL_USE_5(SyslogHandler.Facility.LOCAL_USE_5),
        LOCAL_USE_6(SyslogHandler.Facility.LOCAL_USE_6),
        LOCAL_USE_7(SyslogHandler.Facility.LOCAL_USE_7);

        private final SyslogHandler.Facility realFacility;

        private Facility(SyslogHandler.Facility realFacility) {
            this.realFacility = realFacility;
        }

        public SyslogHandler.Facility convert(){
            return realFacility;
        }
    }

    // By default the TcpOutputStream attempts to reconnect on it's own, use our own to avoid the automatic reconnect
    // See LOGMGR-113 for details on a better way to do this in the future
    @SuppressWarnings("deprecation")
    private static class AuditLogTcpOutputStream extends TcpOutputStream {
        protected AuditLogTcpOutputStream(InetAddress host, int port) throws IOException {
            super(SocketFactory.getDefault().createSocket(host, port));
        }
    }


    @SuppressWarnings("deprecation")
    private static class SSLContextOutputStream extends TcpOutputStream {
        protected SSLContextOutputStream(SSLContext sslContext, InetAddress host, int port) throws IOException {
            // Continue to use the deprecated constructor until LOGMGR-113 is resolved
            super(sslContext.getSocketFactory().createSocket(host, port));
        }
    }

    private class TransportErrorManager extends ErrorManager {
        private volatile Exception error;

        public TransportErrorManager() {
        }

        @Override
        public synchronized void error(String msg, Exception ex, int code) {
            error = ex;
            lastErrorTime = System.currentTimeMillis();
        }

        void getAndThrowError() throws IOException {
            Exception error = this.error;
            this.error = null;

            if (error != null) {
                // This method is only invoked by writeLog() which is guarded by the config lock in ManagedAuditLoggerImpl
                // If we've received an error we're going to assume we're not connected to force a reconnect
                connected = false;
                throwAsIoOrRuntimeException(error);
            }
        }

        void throwAsIoOrRuntimeException(Throwable t) throws IOException {
            if (t instanceof PortUnreachableException && transport == Transport.UDP) {
                //This is an exception that may or may not happen, see the javadoc for DatagramSocket.send().
                //We don't want something this unreliable polluting the failure count.
                //With UDP syslogging set up against a non-existent syslog server:
                //On OS X this exception never happens.
                //On Linux this seems to happens every other send, so we end up with a loop
                //    odd send works; failure count = 0
                //    even send fails; failure count = 1
                //    odd send works; failure count reset to 0
                //
                //Also, we don't want the full stack trace for this which would get printed by StandardFailureCountHandler.StandardFailureCountHandler.failure(),
                //which also handles the failure count, so we swallow the exception and print a warning.

                ControllerLogger.MGMT_OP_LOGGER.udpSyslogServerUnavailable(getName(), t.getLocalizedMessage());
                return;
            }
            if (t instanceof IOException) {
                throw (IOException)t;
            }
            if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            }
            throw new RuntimeException(t);
        }
    }
}
