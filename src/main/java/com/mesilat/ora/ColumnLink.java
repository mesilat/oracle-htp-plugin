package com.mesilat.ora;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Table;

@Preload
@Table("column_links")
public interface ColumnLink extends Entity {
    String getMacroId();
    void setMacroId(String macroId);
    String getColumnName();
    void setColumnName(String columnName);
    Long getPageId();
    void setPageId(Long pageId);
    String getParamName();
    void setParamName(String paramName);
}