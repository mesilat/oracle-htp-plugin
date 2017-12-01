package com.mesilat.ora.converter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;

public abstract class ConverterFormBase extends ConverterBase {
    private static final Pattern SQL = Pattern.compile("^(?i)select\\s+.+\\s+from\\s+(?:(\"[^\"]+\"|[^\"\\.]+)\\.)?(\"[^\"]+\"|[^\"\\.\\s]+)\\s*.*$");

    private static final Pattern CHECK = Pattern.compile("^(?i)\\s*([^\\s]+)\\s+in\\s+\\((.+)\\)\\s*$");
    private static final Pattern NAME  = Pattern.compile("^\\s*\"(.+)\"\\s*$");
    private static final Pattern VALUE = Pattern.compile("^\\s*'(.+)'\\s*$");

    protected ArrayNode toJsonArray(List<String> values){
        if (values == null){
            return null;
        } else {
            ObjectMapper mapper = new ObjectMapper();
            ArrayNode array = mapper.createArrayNode();
            for (String value : values){
                array.add(value);
            }
            return array;
        }
    }
    protected static TableInfo describeTable(Connection conn, String sql) throws SQLException {
        Matcher m = SQL.matcher(sql);
        if (!m.matches()){
            return null;
        }

        TableInfo tableInfo = new TableInfo();
        if (m.group(1) != null){
            tableInfo.setSchemaName(getName(m.group(1)));
        }
        tableInfo.setTableName(getName(m.group(2)));

        // Basic column info
        try (PreparedStatement ps = conn.prepareStatement(
            "select column_name, data_type, data_length, data_precision, nullable from all_tab_columns where owner = ? and table_name = ? order by column_id"
        )){
            ps.setString(1, tableInfo.getSchemaName() == null? conn.getSchema(): tableInfo.getSchemaName());
            ps.setString(2, tableInfo.getTableName());
            try (ResultSet rs = ps.executeQuery()){
                while (rs.next()){
                    ColumnInfo columnInfo = new ColumnInfo();
                    columnInfo.setName(rs.getString("column_name"));
                    columnInfo.setDataType(rs.getString("data_type"));
                    columnInfo.setDataLength(rs.getInt("data_length"));
                    columnInfo.setDataPrecision(rs.getInt("data_precision"));
                    columnInfo.setNullable(!"N".equals(rs.getString("nullable")));
                    tableInfo.addColumn(columnInfo);
                }
            }
        }

        // Column value options
        try (PreparedStatement ps = conn.prepareStatement(
            "select search_condition from all_constraints where owner = ? and table_name = ? and constraint_type = 'C'"
        )){
            ps.setString(1, tableInfo.getSchemaName() == null? conn.getSchema(): tableInfo.getSchemaName());
            ps.setString(2, tableInfo.getTableName());
            try (ResultSet rs = ps.executeQuery()){
                while (rs.next()){
                    String checkContraint = rs.getString("search_condition");
                    m = CHECK.matcher(checkContraint);
                    if (m.matches()){
                        String checkColumn = getName(m.group(1));
                        String checkValues = m.group(2);
                        List<String> options = new ArrayList<>();
                        for (String value : checkValues.split(",")){
                            Matcher m2 = VALUE.matcher(value);
                            if (m2.matches()){
                                options.add(m2.group(1));
                            } else {
                                options.add(value.trim());
                            }
                        }
                        tableInfo.getColumn(checkColumn).setOptions(options);
                    }
                }
            }
        }

        // Column comments
        try (PreparedStatement ps = conn.prepareStatement(
            "select column_name, comments from all_col_comments where owner = ? and table_name = ?"
        )){
            ps.setString(1, tableInfo.getSchemaName() == null? conn.getSchema(): tableInfo.getSchemaName());
            ps.setString(2, tableInfo.getTableName());
            try (ResultSet rs = ps.executeQuery()){
                while (rs.next()){
                    tableInfo.getColumn(rs.getString("column_name")).setComment(rs.getString("comments"));
                }
            }
        }

        // Primary key
        try (PreparedStatement ps = conn.prepareStatement("select column_name from all_ind_columns x, all_constraints c"
                + " where x.index_name = c.index_name and x.table_owner = c.owner and x.table_name = c.table_name"
                + " and c.constraint_type = 'P' and c.owner = ? and c.table_name = ?")) {
            ps.setString(1, tableInfo.getSchemaName() == null? conn.getSchema(): tableInfo.getSchemaName());
            ps.setString(2, tableInfo.getTableName());
            try (ResultSet rs = ps.executeQuery()){
                while (rs.next()){
                    tableInfo.getColumn(rs.getString("column_name")).setPrimary(true);
                }
            }
        }

        return tableInfo;
    }
    private static String getName(String name){
        Matcher m = NAME.matcher(name);
        if (m.matches()){
            return m.group(1);
        } else {
            return name.trim().toUpperCase();
        }
    }

    protected static class ColumnInfo {
        private String name;
        private String dataType;
        private Integer dataLength;
        private Integer dataPrecision;
        private String comment;
        private boolean nullable = true;
        private List<String> options;
        private boolean primary = false;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public String getDataType() {
            return dataType;
        }
        public void setDataType(String dataType) {
            this.dataType = dataType;
        }
        public Integer getDataLength() {
            return dataLength;
        }
        public void setDataLength(Integer dataLength) {
            this.dataLength = dataLength;
        }
        public Integer getDataPrecision() {
            return dataPrecision;
        }
        public void setDataPrecision(Integer dataPrecision) {
            this.dataPrecision = dataPrecision;
        }
        public String getComment() {
            return comment;
        }
        public void setComment(String comment) {
            this.comment = comment;
        }
        public boolean isNullable() {
            return nullable;
        }
        public void setNullable(boolean nullable) {
            this.nullable = nullable;
        }
        public List<String> getOptions() {
            return options;
        }
        public void setOptions(List<String> options) {
            this.options = options;
        }
        public boolean isPrimary() {
            return primary;
        }
        public void setPrimary(boolean primary) {
            this.primary = primary;
        }
        public String getType(){
            switch (dataType) {
                case "VARCHAR2":
                case "NVARCHAR2":
                case "CHAR":
                case "RAW":
                    return String.format("%s(%d)", dataType, dataLength);
                case "NUMBER":
                    return String.format("%s(%d,%d)", dataType, dataLength, dataPrecision);
                default:
                    return dataType;
            }
        }
    }
    protected static class TableInfo {
        private String schemaName;
        private String tableName;
        private final Map<String,ColumnInfo> columns = new HashMap<>();

        public String getSchemaName() {
            return schemaName;
        }
        public void setSchemaName(String schemaName) {
            this.schemaName = schemaName;
        }
        public String getTableName() {
            return tableName;
        }
        public void setTableName(String tableName) {
            this.tableName = tableName;
        }
        public Map<String,ColumnInfo> getColumns() {
            return columns;
        }
        public ColumnInfo getColumn(String name){
            return columns.containsKey(name)? columns.get(name): null;
        }
        public void addColumn(ColumnInfo column) {
            this.columns.put(column.getName(), column);
        }
        public String getFullName() {
            StringBuilder sb = new StringBuilder();
            if (schemaName != null){
                sb.append("\"")
                  .append(schemaName)
                  .append("\".");
            }
            sb.append("\"")
              .append(tableName)
              .append("\"");
            return sb.toString();
        }
        public ColumnInfo getPrimaryColumn(){
            for (ColumnInfo column : columns.values()) {
                if (column.isPrimary()) {
                    return column;
                }
            }
            return null;
        }
        public boolean canUpdate(){
            for (ColumnInfo column : columns.values()) {
                if (column.isPrimary()) {
                    return true;
                }
            }
            return false;
        }
        public boolean containsColumn(String name){
            return columns.containsKey(name);
        }
    }
}