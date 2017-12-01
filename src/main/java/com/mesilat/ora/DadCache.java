package com.mesilat.ora;

import java.util.List;
import org.codehaus.jackson.node.ObjectNode;

public interface DadCache {
    DataAccessDescriptor[] get();
    DataAccessDescriptor get(String name);
    List<String> getNames();
    void put(ObjectNode dad);
    void delete(String name);
}