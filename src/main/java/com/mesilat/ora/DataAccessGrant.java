package com.mesilat.ora;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Table;

@Preload
@Table("dad_grant")
public interface DataAccessGrant extends Entity {
    DataAccessDescriptor getDataAccessDescriptor();
    void setDataAccessDescriptor(DataAccessDescriptor dad);
    String getGrantee();
    void setGrantee(String grantee);
}