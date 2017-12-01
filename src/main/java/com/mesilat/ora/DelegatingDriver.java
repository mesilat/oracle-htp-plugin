package com.mesilat.ora;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class DelegatingDriver implements Driver {
    private final Driver driver;
    protected final String name;
    protected final String jarName;

    public boolean equals(String name, String jarName) {
        return name != null && name.equals(this.name) && jarName != null && jarName.equals(this.jarName);
    }

    public Connection connect(String url, Properties info) throws SQLException {
        return driver.connect(url, info);
    }

    public boolean acceptsURL(String url) throws SQLException {
        return this.driver.acceptsURL(url);
    }

    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return this.driver.getPropertyInfo(url, info);
    }

    public int getMajorVersion() {
        return this.driver.getMajorVersion();
    }

    public int getMinorVersion() {
        return this.driver.getMinorVersion();
    }

    public boolean jdbcCompliant() {
        return this.driver.jdbcCompliant();
    }

    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return this.driver.getParentLogger();
    }

    protected DelegatingDriver(Driver driver, String name, String jarName) {
        this.driver = driver;
        this.name = name;
        this.jarName = jarName;
    }
}
