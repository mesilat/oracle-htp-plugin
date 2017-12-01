package com.mesilat.ora;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class GelfConfig {
    @XmlElement
    private String server;
    @XmlElement
    private String port;
    @XmlElement
    private String source;

    public String getServer() {
        return server;
    }
    public void setServer(String server) {
        this.server = server;
    }
    public String getPort() {
        return port;
    }
    public void setPort(String port) {
        this.port = port;
    }
    public String getSource() {
        return source;
    }
    public void setSource(String source) {
        this.source = source;
    }

    public void save(PluginSettings settings){
        if (getServer() != null) {
            settings.put(ConfigResource.class.getName() + ".gelfServer", getServer());
        } else {
            settings.remove(ConfigResource.class.getName() + ".gelfServer");
        }
        if (getPort() != null) {
            settings.put(ConfigResource.class.getName() + ".gelfPort", getPort());
        } else {
            settings.remove(ConfigResource.class.getName() + ".gelfPort");
        }
        if (getSource() != null) {
            settings.put(ConfigResource.class.getName() + ".gelfSource", getSource());
        } else {
            settings.remove(ConfigResource.class.getName() + ".gelfSource");
        }
    }

    public GelfConfig() {
    }
    public GelfConfig(GelfConfig o) {
        this.server = o.server;
        this.port = o.port;
        this.source = o.source;
    }
    public GelfConfig(PluginSettings settings) {
        Object obj = settings.get(ConfigResource.class.getName() + ".gelfServer");
        if (obj != null) {
            server = obj.toString();
        }
        obj = settings.get(ConfigResource.class.getName() + ".gelfPort");
        if (obj != null) {
            port = obj.toString();
        }
        obj = settings.get(ConfigResource.class.getName() + ".gelfSource");
        if (obj != null) {
            source = obj.toString();
        }
    }
}