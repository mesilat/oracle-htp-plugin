package com.mesilat.ora.converter;

import com.atlassian.sal.api.message.I18nResolver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

public class ConverterToPieChart extends ConverterBase {
    @Override
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
            if (rsmd.getColumnCount() < 2) {
                throw new SQLException(resolver.getText("com.mesilat.oracle-htp-plugin.error.chart-columns"));
            }

            sb.append("/*\n")
              .append(body)
              .append("\n*/\n")
              .append("-- For more samples and docs please refer to http://c3js.org/examples.html\n")
              .append("declare\n")
              .append("  i binary_integer := 0;\n");
            appendEscapeFunction(sb);
            sb.append("begin\n");
            appendNlsSettings(sb);
            sb.append("  htp.p('{\n")
              .append("  \"data\": {\n")
              .append("    \"columns\": [');\n")
              .append("  for rec in (")
              .append(body)
              .append(") loop\n")
              .append("    if (i > 0) then htp.p(','); end if; i := i + 1;\n");
            sb.append("    htp.p('[");
            appendRowData(sb, rsmd);
            sb.append("]');\n")            
              .append("  end loop;\n")
              .append("  htp.p('],\n")
              .append("  \"type\": \"pie\"\n")
              .append("  }}');\n")
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
