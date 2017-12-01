package com.mesilat.ora;

import net.java.ao.Entity;
import net.java.ao.OneToMany;
import net.java.ao.Preload;
import net.java.ao.schema.Table;
import net.java.ao.schema.Unique;

@Preload
@Table("dad")
public interface DataAccessDescriptor extends Entity {
    @Unique
    String getName();
    void setName(String name);
    String getHost();
    void setHost(String host);
    String getPort();
    void setPort(String port);
    String getService();
    void setService(String service);
    String getUsername();
    void setUsername(String username);
    String getPassword();
    void setPassword(String password);
    Integer getMaxConnections();
    void setMaxConnections(Integer maxConnections);
    @OneToMany
    DataAccessGrant[] getGrants();
    @OneToMany
    void setGrants(DataAccessGrant[] grants);
}