package com.mesilat.ora;

import com.atlassian.confluence.pages.Page;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class GelfLogSender implements InitializingBean, DisposableBean {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.oracle-htp-plugin");
    private static final String PLUGIN_KEY = "oracle-htp-plugin";
    private static final int DEFAULT_PORT = 12201;
    private static final String DEFAULT_SOURCE = "127.0.0.1";

    public enum LEVEL {
        DEBUG("debug"),
        INFO("info"),
        WARN("warn"),
        ERROR("error");

        private final String level;

        public String getLevel(){
            return level;
        }
        @Override
        public String toString(){
            return level;
        }

        LEVEL(String level){
            this.level = level;
        }
    };

    private static GelfLogSender instance;

    private final PluginSettingsFactory pluginSettingsFactory;
    private DatagramSocket socket;
    private GelfConfig config;

    @Override
    public void afterPropertiesSet() throws Exception {
        socket = new DatagramSocket();
        config = new GelfConfig(pluginSettingsFactory.createGlobalSettings());
        instance = this;
    }
    @Override
    public void destroy() throws Exception {
        socket.close();
        instance = null;
    }

    public void send(byte[] message, InetAddress address, int port) throws IOException{
        DatagramPacket packet = new DatagramPacket(message, message.length, address, port);
        socket.send(packet);
    }
    public static void debug(String user, String title, String message, Page page, String macroKey, String dad, Map<String,String> params, Map<String,String> env) {
        send(user, LEVEL.DEBUG, title, message, page, macroKey, dad, params, env);
    }
    public static void info(String user, String title, String message, Page page, String macroKey, String dad, Map<String,String> params, Map<String,String> env) {
        send(user, LEVEL.INFO, title, message, page, macroKey, dad, params, env);
    }
    public static void warn(String user, String title, String message, Page page, String macroKey, String dad, Map<String,String> params, Map<String,String> env) {
        send(user, LEVEL.WARN, title, message, page, macroKey, dad, params, env);
    }
    public static void error(String user, String title, String message, Page page, String macroKey, String dad, Map<String,String> params, Map<String,String> env) {
        send(user, LEVEL.ERROR, title, message, page, macroKey, dad, params, env);
    }
    public static void send(String user, LEVEL level, String title, String message, Page page, String macroKey, String dad, Map<String,String> params, Map<String,String> env) {
        if (instance != null){
            GelfConfig config;
            synchronized(instance) {
                config = new GelfConfig(instance.config);
            }
            if (config.getServer() == null || config.getServer().isEmpty()){
                return;
            }
            try {
                send(config, user, level, title, message, page, macroKey, dad, params, env);
            } catch(IOException ex) {
                LOGGER.warn("Failed to send log to Gelf", ex);
            }
        }
    }
    public static void send(GelfConfig config, String user, LEVEL level, String title, String message, Page page, String macroKey, String dad, Map<String,String> params, Map<String,String> env)
            throws IOException, NumberFormatException {
        if (config.getServer() != null) {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode obj = mapper.createObjectNode();
            obj.put("short_message", title);
            obj.put("full_message", message);
            obj.put("host", config.getSource() == null || config.getSource().isEmpty()? DEFAULT_SOURCE: config.getSource());
            obj.put("_Application", PLUGIN_KEY);
            obj.put("_Level", level.toString());
            obj.put("_User", user);
            if (page != null){
                obj.put("_Page", String.format("Page ID: %d (%s:%s)", page.getId(), page.getSpaceKey(), page.getTitle()));
            }
            if (macroKey != null){
                obj.put("_Macro", macroKey);
            }
            if (dad != null){
                obj.put("_DAD", dad);
            }
            if (params != null){
                obj.put("_Params", params.toString());
            }
            if (env != null){
                obj.put("_Env", env.toString());
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            mapper.writeValue(buf, obj);
            instance.send(buf.toByteArray(), InetAddress.getByName(config.getServer()), config.getPort() == null? DEFAULT_PORT: Integer.parseInt(config.getPort()));
        }
    }
    public static void init(GelfConfig config) {
        if (instance != null){
            synchronized(instance){
                instance.config = config;
            }
        }
    }

    public GelfLogSender(PluginSettingsFactory pluginSettingsFactory){
        this.pluginSettingsFactory = pluginSettingsFactory;
    }
}