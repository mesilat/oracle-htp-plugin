package com.mesilat.ora;

import com.mesilat.jmx.Description;
import com.mesilat.jmx.MBeanName;

@MBeanName("OracleHTPPlugin:Name=DelegatingConnectionCache")
@Description("Delegating connection cache")
public interface DelegatingConnectionCacheMBean {
    @Description("Connection cache size")
    int getSize();
    @Description("A number of closed connections (available to reuse)")
    int getClosedCount();
    @Description("A number of active connections")
    int getActiveCount();
    @Description("A number of free connection slots")
    int getFreeCount();
    @Description("Purge closed connections")
    void purge();
    @Description("Maximum time in seconds an underlying connection can remain idle in a connection cache")
    int getInactivityTimeout();
    void setInactivityTimeout(int idleTime);
    @Description("Maximum time in seconds that a requesting thread will wait for a connection")
    int getConnectTimeout();
    void setConnectTimeout(int connectTimeout);
    @Description("Highest count of simultaneousely allocated connections")
    int getHighWaterMark();
    @Description("Maximum time in seconds that a requesting thread was waiting for a connection")
    int getMaxTimeToConnect();
    @Description("Average time in seconds that a requesting thread was waiting for a connection")
    int getAvgTimeToConnect();
    @Description("Total number of connect requests")
    long getTotalConnections();
    @Description("Total number of failed connection requests")
    long getTotalFailedConnections();
    @Description("Total number of timeouted connection requests")
    long getTotalTimeoutedConnections();
    @Description("Total number of reused connection requests")
    long getTotalReusedConnections();
}
