package com.mesilat.ora.converter;

import com.atlassian.sal.api.message.I18nResolver;
import java.sql.Connection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

public class ConverterToJson extends ConverterBase {
    public String convert(Connection conn, String body, Map params, I18nResolver resolver) throws SQLException {
        StringBuilder sb = new StringBuilder();
        
        PreparedStatement ps = null;
        ResultSet rs = null;
        ResultSetMetaData rsmd;
        try {
            ps = conn.prepareStatement(body);
            processParameters(ps, params);
            rs = ps.executeQuery();
            rsmd = rs.getMetaData();

            sb.append("/*\n")
              .append(body)
              .append("\n*/\n")
              .append("declare\n")
              .append("  i binary_integer := 0;\n");
            appendEscapeFunction(sb);
            sb.append("begin\n");
            appendNlsSettings(sb);
            sb.append("  htp.p('{\n")
              .append("  \"data\": [');\n")
              .append("  for rec in (")
              .append(body)
              .append(") loop\n")
              .append("    if (i > 0) then htp.p(','); end if; i := i + 1;\n")
              .append("    htp.p('{");
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                if (i > 1) {
                    sb.append(", ");
                }
                sb.append("\"' || escape('")
                  .append(rsmd.getColumnName(i).replace("'", "''"))
                  .append("') || '\": ");
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
            sb.append("}');\n")
              .append("  end loop;\n")
              .append("  htp.p(']}');\n")
              .append("end;\n");
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch(SQLException ignore) {}
            }
            if (ps != null) {
                try {
                    ps.close();
                } catch(SQLException ignore) {}
            }
        }

        return sb.toString();
    }
}
