package com.mesilat.ora;

import java.util.HashMap;
import java.util.Map;

public interface PlsqlCache {
    public static class MacroInfo {
        private final String id;
        private final String body;
        private final Map<String,String> params = new HashMap<>();

        public String getId() {
            return id;
        }

        public String getBody() {
            return body;
        }

        public Map<String,String> getParams() {
            return params;
        }

        public MacroInfo(String id, String body, Map<String,String> params){
            this.id = id;
            this.body = body;
            this.params.putAll(params);
        }
    }
}