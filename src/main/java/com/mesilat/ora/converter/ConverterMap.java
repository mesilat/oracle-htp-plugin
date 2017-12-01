package com.mesilat.ora.converter;

import java.util.HashMap;
import java.util.Map;

public class ConverterMap {
    private static final Map<String,ConverterBase> CONVERTERS = new HashMap<String,ConverterBase>();
    
    public static ConverterBase getConverter(String type) {
        return CONVERTERS.containsKey(type)? CONVERTERS.get(type): null;
    }
    
    static {
        CONVERTERS.put("table", new ConverterToTable());
        CONVERTERS.put("view", new ConverterToViewForm());
        CONVERTERS.put("edit", new ConverterToEditForm());

        CONVERTERS.put("json", new ConverterToJson());
        CONVERTERS.put("pie", new ConverterToPieChart());
        CONVERTERS.put("bar", new ConverterToBarChart());

        CONVERTERS.put("line", new ConverterToLineChart());
        CONVERTERS.put("horiz", new ConverterToHorizChart());
        CONVERTERS.put("area", new ConverterToAreaChart());
    }
}
