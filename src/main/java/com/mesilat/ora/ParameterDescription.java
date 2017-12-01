package com.mesilat.ora;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Table;

@Preload
@Table("param_description")
public interface ParameterDescription extends Entity {
    String getMacroId();
    void setMacroId(String macroId);
    String getParamId();
    void setParamId(String paramId);
    String getDescription();
    void setDescription(String description);
}