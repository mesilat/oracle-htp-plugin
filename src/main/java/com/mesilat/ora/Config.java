package com.mesilat.ora;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Config {
    public static final String CATALINA_BASE = System.getProperty("catalina.base");
    public static final String DEFAULT_DRIVER_PATH = "lib/ojdbc7.jar";
    public static final int DEFAULT_CONNECTION_CACHE_SIZE = 40;
    public static final int DEFAULT_CONNECT_TIMEOUT = 10;
    public static final int DEFAULT_INACTIVITY_TIMEOUT = 180;

    @XmlElement
    private final String catalinaBase = CATALINA_BASE;
    @XmlElement
    private String driverPath;
    @XmlElement
    private Integer connectionCacheSize;
    @XmlElement
    private Integer connectTimeout;
    @XmlElement
    private Integer inactivityTimeout;

    public String getCatalinaBase() {
        return catalinaBase;
    }
    public void setCatalinaBase(String catalinaBase) {
    }
    public String getDriverPath() {
        return driverPath;
    }
    public void setDriverPath(String driverPath) {
        this.driverPath = driverPath;
    }
    public Integer getConnectionCacheSize() {
        return connectionCacheSize;
    }
    public void setConnectionCacheSize(Integer connectionCacheSize) {
        this.connectionCacheSize = connectionCacheSize;
    }
    public Integer getConnectTimeout() {
        return connectTimeout;
    }
    public void setConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
    public Integer getInactivityTimeout() {
        return inactivityTimeout;
    }
    public void setInactivityTimeout(Integer inactivityTimeout) {
        this.inactivityTimeout = inactivityTimeout;
    }

    public void save(PluginSettings settings) {
        if (getDriverPath() != null) {
            settings.put(ConfigResource.class.getName() + ".driver", getDriverPath());
        } else {
            settings.remove(ConfigResource.class.getName() + ".driver");
        }
        if (getConnectionCacheSize() != null) {
            settings.put(ConfigResource.class.getName() + ".connectionCacheSize", getConnectionCacheSize().toString());
        } else {
            settings.remove(ConfigResource.class.getName() + ".connectionCacheSize");
        }
        if (getConnectTimeout() != null) {
            settings.put(ConfigResource.class.getName() + ".connectTimeout", getConnectTimeout().toString());
        } else {
            settings.remove(ConfigResource.class.getName() + ".connectTimeout");
        }
        if (getInactivityTimeout() != null) {
            settings.put(ConfigResource.class.getName() + ".inactivityTimeout", getInactivityTimeout().toString());
        } else {
            settings.remove(ConfigResource.class.getName() + ".inactivityTimeout");
        }
    }

    public Config() {
    }
    public Config(PluginSettings settings) {
        Object obj = settings.get(ConfigResource.class.getName() + ".driver");
        if (obj != null) {
            driverPath = obj.toString();
        } else {
            driverPath = Config.DEFAULT_DRIVER_PATH;
        }

        obj = settings.get(ConfigResource.class.getName() + ".connectionCacheSize");
        if (obj != null) {
            try {
                connectionCacheSize = Integer.parseInt(obj.toString());
            } catch(Exception ignore) {
            }
        } else {
            connectionCacheSize = Config.DEFAULT_CONNECTION_CACHE_SIZE;
        }

        obj = settings.get(ConfigResource.class.getName() + ".connectTimeout");
        if (obj != null) {
            try {
                connectTimeout = Integer.parseInt(obj.toString());
            } catch(Exception ignore) {
            }
        } else {
            connectTimeout = Config.DEFAULT_CONNECT_TIMEOUT;
        }

        obj = settings.get(ConfigResource.class.getName() + ".inactivityTimeout");
        if (obj != null) {
            try {
                inactivityTimeout = Integer.parseInt(obj.toString());
            } catch(Exception ignore) {
            }
        } else {
            inactivityTimeout = Config.DEFAULT_INACTIVITY_TIMEOUT;
        }
    }
}