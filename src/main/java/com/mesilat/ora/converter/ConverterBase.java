package com.mesilat.ora.converter;

import com.atlassian.sal.api.message.I18nResolver;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

public abstract class ConverterBase {
    public abstract String convert(Connection conn, String body, Map params, I18nResolver resolver) throws SQLException;

    protected static void processParameters(PreparedStatement ps, Map params) throws SQLException {
        ParameterMetaData pmd = ps.getParameterMetaData();
        if (pmd.getParameterCount() > 0) {
            for (int i = 1; i <= pmd.getParameterCount(); i++) {
                if (params.containsKey("p" + i)) {
                    ps.setString(i, params.get("p" + i).toString());
                } else {
                    ps.setNull(i, Types.VARCHAR);
                }
            }
        }
    }
    protected static boolean isDateType(int type) {
        switch (type) {
            case Types.TIMESTAMP:
            case Types.DATE:
            case Types.TIME:
                return true;
            default:
                return false;
        }
    }
    protected static boolean isNumericType(int type) {
        switch (type) {
            case Types.BIGINT:
            case Types.DECIMAL:
            case Types.DOUBLE:
            case Types.FLOAT:
            case Types.INTEGER:
            case Types.NUMERIC:
            case Types.REAL:
            case Types.SMALLINT:
            case Types.TINYINT:
                return true;
            default:
                return false;
        }
    }
    protected static void appendEscapeFunction(StringBuilder sb) {
        sb.append("  function escape(s in varchar2) return varchar2 is begin return replace(replace(s,'\\','\\\\'),'\"','\\\"'); end;\n");
    }
    protected static void appendNlsSettings(StringBuilder sb) {
        sb.append("  dbms_session.set_nls('NLS_LANGUAGE','AMERICAN');\n")
          .append("  dbms_session.set_nls('NLS_TERRITORY','AMERICA');\n");
    }
    protected static int guessXAxis(ResultSetMetaData rsmd) throws SQLException {
        int x = 1;
        for (int i = 2; i <= rsmd.getColumnCount(); i++) {
            if (isNumericType(rsmd.getColumnType(x)) && !isNumericType(rsmd.getColumnType(i))) {
                x = i;
            }
            if (!isDateType(rsmd.getColumnType(x)) && isDateType(rsmd.getColumnType(i))) {
                x = i;
            }
        }
        return x;
    }
    protected static void appendAxes(I18nResolver resolver, StringBuilder sb, ResultSetMetaData rsmd, int x) throws SQLException {
        appendAxes(resolver, sb, rsmd, x, false); 
    }
    protected static void appendAxes(I18nResolver resolver, StringBuilder sb, ResultSetMetaData rsmd, int x, boolean rotated) throws SQLException {
        sb.append("    \"axis\": {\n");
        if (rotated) {
        sb.append("      \"rotated\": true,\n");
        }
        sb.append("      \"x\": {\n")
          .append("        \"label\": {\n")
          .append("          \"text\": \"")
          .append(resolver.getText("oracle-htp-plugin.c3js.x-axis"))
          .append(": ")
          .append(rsmd.getColumnName(x))
          .append("\",\n")
          .append("          \"position\": \"outer-bottom\"\n")
          .append("        }");
        if (isDateType(rsmd.getColumnType(x))) {
        sb.append(",\n        \"type\": \"timeseries\",\n")
          .append("        \"localtime\": true,\n")                    
          .append("        \"tick\": {\n")
          .append("          \"format\": \"%d-%m %H:%M\"\n")
          .append("        }\n");
        } else {
        sb.append(",\n        \"type\": \"category\"\n");
        }
        sb.append("      },\n")
          .append("      \"y\": {\n")
          .append("        \"label\": {\n")
          .append("          \"text\": \"")
          .append(resolver.getText("oracle-htp-plugin.c3js.y-axis"))
          .append(": \"\n")
          .append("        },\n")
          .append("        \"tick\": {\n")
          .append("          \"format\": \"^,.0\"\n")
          .append("        }\n")
          .append("      }\n")
          .append("    }\n");
    }
    protected static void appendRowHeaders(StringBuilder sb, ResultSetMetaData rsmd) throws SQLException {
        appendRowHeadersExX(sb, rsmd, -1);
    }
    protected static void appendRowHeadersExX(StringBuilder sb, ResultSetMetaData rsmd, int x) throws SQLException {
        boolean first = true;
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            if (i == x) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                sb.append(",");
            }
            sb.append("\"")
              .append(rsmd.getColumnName(i).replace(",","''"))
              .append("\"");
        }
    }
    protected static void appendRowData(StringBuilder sb, ResultSetMetaData rsmd) throws SQLException {
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            if (i > 1) { sb.append(","); }
            if (isNumericType(rsmd.getColumnType(i))) {
                sb.append("' || to_char(rec.\"")
                    .append(rsmd.getColumnName(i))
                    .append("\",'999999999999990.00') || '");
            } else if (isDateType(rsmd.getColumnType(i))) {
                sb.append("\"' || to_char(rec.\"")
                    .append(rsmd.getColumnName(i))
                    .append("\",'DD-MM-YYYY HH24:MI:SS') || '\"");
            } else {
                sb.append("\"' || escape(rec.\"")
                    .append(rsmd.getColumnName(i))
                    .append("\") || '\"");
            }
        }
    }
}
