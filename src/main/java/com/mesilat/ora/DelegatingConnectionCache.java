package com.mesilat.ora;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Enumeration;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.NotCompliantMBeanException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import com.mesilat.jmx.SimpleMBean;
import com.sun.mail.util.MimeUtil;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.ParameterMetaData;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DelegatingConnectionCache extends SimpleMBean
        implements InitializingBean, DisposableBean, DelegatingConnectionCacheMBean, Runnable  {
    public static final int DEFAULT_MAX_CONNECTIONS = 8;
    private static final String DRIVER_NAME = "oracle.jdbc.OracleDriver";
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.oracle-htp-plugin");
    private static DelegatingConnectionCache instance;

    private final PluginSettingsFactory pluginSettingsFactory;

    private String driverPath;
    private int connectTimeout;
    private int inactivityTimeout;
    private DelegatingConnection[] cache;
    private Thread cacheMonitor;
    
    // <editor-fold defaultstate="collapsed" desc="Performance Indicators">
    private int highWaterMark;
    private int maxTimeToConnect;
    private long totalConnections;
    private long totalTimeToConnect;
    private long failedConnections;
    private long reusedConnections;
    private long timeoutedConnections;
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="InitializingBean, DisposableBean, Runnable Implementation">
    @Override
    public void afterPropertiesSet() throws Exception {
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
        Config config = new Config(settings);
        driverPath = config.getDriverPath() == null?
                Config.DEFAULT_DRIVER_PATH: config.getDriverPath();
        connectTimeout = config.getConnectTimeout() == null?
                Config.DEFAULT_CONNECT_TIMEOUT: config.getConnectTimeout();
        inactivityTimeout = config.getInactivityTimeout() == null?
                Config.DEFAULT_INACTIVITY_TIMEOUT: config.getInactivityTimeout();
        int size = config.getConnectionCacheSize() == null?
                Config.DEFAULT_CONNECTION_CACHE_SIZE: config.getConnectionCacheSize();
        cache = new DelegatingConnection[size];
        cacheMonitor = new Thread(this);
        cacheMonitor.start();

        try {
            this.registerMBean();
        } catch(InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException ex) {
            LOGGER.warn("Failed to register DelegatingConnectionCache management bean", ex);
        }
        LOGGER.debug("\nOracle connection cache initialized successfully");
        instance = this;
    }
    @Override
    public void destroy() throws Exception {
        instance = null;
        this.unregisterMBean();
        if (cacheMonitor != null) {
            cacheMonitor.interrupt();
        }
        synchronized(this) {
            for (int i = 0; i < cache.length; i++) {
                DelegatingConnection conn = cache[i];
                if (conn != null) {
                    closeUnderlyingConnectionAsync(cache[i]);
                    cache[i] = null;
                }
            }
        }
        LOGGER.debug("\nOracle connection cache destroyed");
    }
    @Override
    public void run() {
        try {
            while (true) {
                Thread.sleep(1000);
                
                synchronized(this) {
                    long now = System.currentTimeMillis();
                    for (int i = 0; i < cache.length; i++) {
                        if (cache[i] == null) {
                            continue;
                        }
                        try {
                            if (!cache[i].isClosed()) {
                                continue;
                            }
                        } catch(SQLException ignore) {
                        }
                        if (cache[i].getCloseTimeMillis() + getInactivityTimeout() * 1000L < now) {
                            closeUnderlyingConnectionAsync(cache[i]);
                            cache[i] = null;                            
                        }
                    }
                }
            }
        } catch(InterruptedException ignore) {
            LOGGER.debug("\nOracle connection cache monitor thread exited");
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="DelegatingConnectionCacheMBean Implementation">
    @Override
    public int getSize() {
        synchronized(this) {
            return cache.length;
        }
    }
    @Override
    public int getClosedCount() {
        int n = 0;
        synchronized(this) {
            for (DelegatingConnection conn : cache) {
                try {
                    if (conn != null && conn.isClosed()) {
                        n++;
                    }
                } catch(SQLException ignore) {
                }
            }
        }
        return n;
    }
    @Override
    public int getActiveCount() {
        int n = 0;
        synchronized(this) {
            for (DelegatingConnection conn : cache) {
                try {
                    if (conn != null && !conn.isClosed()) {
                        n++;
                    }
                } catch(SQLException ignore) {
                }
            }
        }
        return n;
    }
    @Override
    public int getFreeCount() {
        int n = 0;
        synchronized(this) {
            for (DelegatingConnection conn : cache) {
                if (conn == null) {
                    n++;
                }
            }
        }
        return n;
    }
    @Override
    public void purge() {
        synchronized(this) {
            for (int i = 0; i < cache.length; i++) {
                DelegatingConnection conn = cache[i];
                try {
                    if (conn != null && conn.isClosed()) {
                        closeUnderlyingConnectionAsync(cache[i]);
                        cache[i] = null;
                    }
                } catch(SQLException ignore) {
                }
            }
        }
    }
    public String getDriverPath() {
        return driverPath;
    }
    public void setDriverPath(String driverPath) {
        this.driverPath = driverPath;
    }    
    @Override
    public int getInactivityTimeout() {
        return inactivityTimeout;
    }
    @Override
    public void setInactivityTimeout(int inactivityTimeout) {
        this.inactivityTimeout = inactivityTimeout;
    }
    @Override
    public int getConnectTimeout() {
        return connectTimeout;
    }
    @Override
    public void setConnectTimeout(int timeout) {
        this.connectTimeout = timeout;
    }
    @Override
    public int getHighWaterMark() {
        return highWaterMark;
    }
    @Override

    public int getMaxTimeToConnect() {
        return maxTimeToConnect;
    }
    @Override
    public int getAvgTimeToConnect() {
        if (totalConnections > 0) {
            return (int)(totalTimeToConnect / totalConnections / 1000L);
        } else {
            return 0;
        }
    }
    @Override
    public long getTotalConnections() {
        return totalConnections;
    }
    @Override
    public long getTotalFailedConnections() {
        return failedConnections;
    }
    @Override
    public long getTotalTimeoutedConnections() {
        return timeoutedConnections;
    }
    @Override
    public long getTotalReusedConnections() {
        return reusedConnections;
    }    
    // </editor-fold>

    protected DelegatingConnection getDelegatingConnection(DataAccessDescriptor dad) throws SQLException {
        long startTime = System.currentTimeMillis();
        long threshold;
        if (getConnectTimeout() > 0) {
            threshold = startTime + getConnectTimeout() * 1000L;
        } else {
            threshold = startTime + Config.DEFAULT_CONNECT_TIMEOUT * 1000L;
        }
        while (threshold > System.currentTimeMillis()) {
            DelegatingConnection conn = internalGetDelegatingConnection(dad);
            if (conn == null) {
                try {
                    Thread.sleep(100);
                } catch(InterruptedException ex) {
                    throw new SQLException("Failed to aquire database connection", ex);
                }
            } else {
                long timeToConnect = System.currentTimeMillis() - startTime;
                int secToConnect = (int)(timeToConnect / 1000);
                if (secToConnect > maxTimeToConnect) {
                    maxTimeToConnect = secToConnect;
                }
                totalConnections++;
                totalTimeToConnect += timeToConnect;
                return conn;
            }            
        }
        timeoutedConnections++;
        throw new SQLException("Timeout trying to aquire database connection");
    }
    private DelegatingConnection internalGetDelegatingConnection(DataAccessDescriptor dad) throws SQLException {
        int freeSlot;
        int closedSameDad;
        int closedOtherDad;
        int k;
        freeSlot = closedSameDad = closedOtherDad = k = -1;
        int allocated = 0;

        synchronized(this) {
            int countSameDad = 0;
            for (int i = 0; i < cache.length; i++) {
                DelegatingConnection conn = cache[i];
                if (conn == null) {
                    freeSlot = i;
                    continue;
                } else {
                    allocated++;
                }

                if (conn.getDadName().equals(dad.getName())) {
                    countSameDad++;
                    if (conn.isClosed()) {
                        closedSameDad = i;
                        break;
                    }
                } else {
                    if (conn.isClosed()) {
                        closedOtherDad = i;
                    }
                }
            }

            if (closedSameDad >= 0) {
                k = closedSameDad;
                cache[k].setCloseTimeMillis(0); // Prevent other thread reusing this connection
            } else if (countSameDad >= (dad.getMaxConnections() == null? DEFAULT_MAX_CONNECTIONS: dad.getMaxConnections())) {
                return null;
            } else if (freeSlot >= 0) {
                k = freeSlot;
                cache[k] = new DelegatingConnection(dad.getName());
                allocated++;
            } else if (closedOtherDad >= 0) {
                k = closedOtherDad;
                closeUnderlyingConnectionAsync(cache[k]);
                cache[k] = new DelegatingConnection(dad.getName());
            } else {
                return null;
            }
        }
        
        if (allocated > highWaterMark) {
            highWaterMark = allocated;
        }

        if (cache[k].isDummyConnection()) {
            try {
                return cache[k] = new DelegatingConnection(getUnderlyingConnection(dad), this, dad.getName());
            } catch(SQLException ex) {
                cache[k] = null;
                throw ex;
            }
        } else {
            try {
                cache[k].reset();
                reusedConnections++;
                return cache[k];
            } catch(SQLException ignore) {
                closeUnderlyingConnectionAsync(cache[k]);
                return cache[k] = null;
            }
        }
    }
    private Connection getUnderlyingConnection(DataAccessDescriptor dad) throws SQLException {
        boolean isDelegatingDriverRegistered = false;
        Enumeration e = DriverManager.getDrivers();
        while (e.hasMoreElements()) {
            Driver driver = (Driver)e.nextElement();
            try {
                if (driver instanceof DelegatingDriver && ((DelegatingDriver)driver).equals(DRIVER_NAME, getDriverPath())) {
                    isDelegatingDriverRegistered = true;
                    break;
                }
            } catch (Exception ex) {
                failedConnections++;
                throw new SQLException("Failed to find a DelegatingDriver instance", ex);
            }
        }
        if (!isDelegatingDriverRegistered) {
            try {
                URL url = getJarUrl(getDriverPath());
                URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{ url });
                Driver driver = (Driver)Class.forName(DRIVER_NAME, true, urlClassLoader).newInstance();
                DelegatingDriver delegatingDriver = new DelegatingDriver(driver, DRIVER_NAME, getDriverPath());
                DriverManager.registerDriver(delegatingDriver);
                if (getConnectTimeout() > 0) {
                    DriverManager.setLoginTimeout(getConnectTimeout());
                } else {
                    DriverManager.setLoginTimeout(Config.DEFAULT_CONNECT_TIMEOUT);
                }
            } catch(ClassNotFoundException | IllegalAccessException | InstantiationException | MalformedURLException | SQLException ex) {
                failedConnections++;
                throw new SQLException("Failed to load Oracle JDBC driver", ex);
            }
        }

        String password;
        try {
            password = PasswordEncryption1.unscramble(dad.getPassword());
        } catch(Exception ex) {
            failedConnections++;
            throw new SQLException("Could not unscramble DAD password", ex);
        }

        String url = MessageFormat.format("jdbc:oracle:thin:@{0}:{1}:{2}", dad.getHost(), dad.getPort(), dad.getService());
        try {
            Connection conn = DriverManager.getConnection(url, dad.getUsername(), password);
            conn.setAutoCommit(false);
            return conn;
        } catch(SQLException ex) {
            failedConnections++;
            throw ex;
        }
    }
    protected void onConnectionClose(DelegatingConnection conn) throws SQLException {
        //conn.closeUnderlyingConnection();
    }

    public DelegatingConnectionCache(PluginSettingsFactory pluginSettingsFactory) throws NotCompliantMBeanException {
        super(DelegatingConnectionCacheMBean.class);
        
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    protected static DelegatingConnectionCache getInstance() {
        return DelegatingConnectionCache.instance;
    }
    public static Connection getConnection(DataAccessDescriptor dad) throws SQLException {
        if (dad == null) {
            throw new SQLException("Failed to aquire connection: no DAD specified");
        }
        DelegatingConnectionCache theCache = DelegatingConnectionCache.instance;
        if (theCache == null) {
            throw new SQLException("Oracle connection cache was not initialized");
        } else {
            return theCache.getDelegatingConnection(dad);
        }
    }
    public static String invoke(DataAccessDescriptor dad, String body, Map<String,String> params, Map<String,String> env) throws Exception {
        CallableStatement cs = null;
        if (env == null){
            env = new HashMap<>();
        }
        env.put("__DAD__", dad.getName());

        try (Connection conn = getConnection(dad)) {
            DelegatingConnection dconn = (DelegatingConnection)conn;
            ClassLoader driverClassLoader = dconn.getInnerConnection().getClass().getClassLoader();
            int i = 0;
            String[] paramNames = new String[env.size()],
                paramValues = new String[env.size()];
            for (Map.Entry<String,String> formField : env.entrySet()){
                paramNames[i] = formField.getKey();
                paramValues[i] = formField.getValue();
                i++;
            }
            cs = conn.prepareCall("begin owa.init_cgi_env(num_params => ?, param_name => ?, param_val => ?); end;");
            cs.setInt(1, i);

            Class oracleCallableStatementClass = Class.forName("oracle.jdbc.OracleCallableStatement", true, driverClassLoader);
            Method setPlsqlIndexTableMethod = oracleCallableStatementClass.getMethod("setPlsqlIndexTable", int.class, Object.class, int.class, int.class, int.class, int.class);
            setPlsqlIndexTableMethod.invoke(cs, 2, paramNames,  i, i, /*OracleTypes.VARCHAR*/12, 32000);
            setPlsqlIndexTableMethod.invoke(cs, 3, paramValues, i, i, /*OracleTypes.VARCHAR*/12, 32000);
            cs.execute();
            cs.close();

            cs = conn.prepareCall(body);
            ParameterMetaData pmd = cs.getParameterMetaData();
            for (i = 1; i <= 10 && i <= pmd.getParameterCount(); i++) {
                String paramName = String.format("p%d", i);
                if (params.containsKey(paramName)) {
                    cs.setString(i, params.get(paramName));
                } else {
                    cs.setString(i, null);
                }
            }
            cs.execute();
            cs.close();

            cs = conn.prepareCall(
                "declare" +
                "  function get_text return clob" +
                "  is" +
                "    v_buf htp.htbuf_arr;" +
                "    v_rows int := 999999;" +
                "    v_lob clob;" +
                "  begin" +
                "    dbms_lob.createtemporary(v_lob, true);" +
                "    htp.get_page(v_buf, v_rows);" +
                "    if (v_rows > 3) then" +
                "      for i in 4..v_rows loop" +
                "        dbms_lob.writeappend(v_lob, length(v_buf(i)),v_buf(i));" +
                "      end loop;" +
                "    end if;" +
                "    return v_lob;" +
                "  end; " +
                "begin" +
                "  ? := get_text; " +
                "end;"
            );
            cs.registerOutParameter(1, java.sql.Types.CLOB);
            cs.execute();
            String text = cs.getString(1);

            // Is BLOB download?
            cs = conn.prepareCall("declare function f return char is begin if wpg_docload.is_file_download then return 'Y'; else return 'N'; end if; end; begin :p := f; end;");
            cs.registerOutParameter(1, java.sql.Types.VARCHAR);
            cs.execute();
            boolean isFileDownload = "Y".equals(cs.getString(1));
            cs.close();

            if (isFileDownload){
                cs = conn.prepareCall("declare v_lob blob; begin wpg_docload.get_download_blob(v_lob); :p := v_lob; end;");
                cs.registerOutParameter(1, java.sql.Types.BLOB);
                cs.execute();
                byte[] blob = cs.getBytes(1);
                cs.close();
                String fileName = text;
                if (fileName == null || fileName.isEmpty()){
                    fileName = "data.dat";
                } else {
                    fileName = fileName.trim();
                }
                if (fileName.length() > 128){
                    fileName = fileName.substring(0, 128);
                }
                throw new MakeFileDownloadException(fileName, blob);
            } else {
                return text;
            }
            
        } finally {
            try { if (cs != null) cs.close(); } catch(Exception ignore) {}
        }
    }
    
    private static URL getJarUrl(String dbJar) throws MalformedURLException {
        String urlString;
        File e = new File(dbJar);
        if (e.isAbsolute()) {
            urlString = "jar:file://" + dbJar + "!/";
        } else {
            urlString = "jar:file:" + System.getProperty("catalina.base") + File.separator + dbJar + "!/";
        }
        return new URL(urlString);
    }
    private static void closeUnderlyingConnectionAsync(DelegatingConnection conn) {
        CloseConnectionThread t = new CloseConnectionThread(conn);
        t.start();
    }

    private static class CloseConnectionThread extends Thread {
        private final DelegatingConnection conn;
        
        @Override
        public void run() {
            try {
                conn.closeUnderlyingConnection();
            } catch(SQLException ex) {
                DelegatingConnectionCache.LOGGER.warn(
                    MessageFormat.format("Error closing oracle connection for DAD {0}", conn.getDadName()),
                    ex
                );
            }
        }
        
        public CloseConnectionThread(DelegatingConnection conn) {
            this.conn = conn;
        }
    }
}
