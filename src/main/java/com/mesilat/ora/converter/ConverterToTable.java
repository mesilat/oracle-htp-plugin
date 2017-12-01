package com.mesilat.ora.converter;

import com.atlassian.sal.api.message.I18nResolver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

public class ConverterToTable extends ConverterBase {
    @Override
    public String convert(Connection conn, String body, Map params, I18nResolver resolver) throws SQLException {
        StringBuilder sb = new StringBuilder();

        try (PreparedStatement ps = conn.prepareStatement(body)) {
            ResultSetMetaData rsmd = ps.getMetaData();

            sb.append("/*\n")
              .append(body)
              .append("\n*/\n")
              .append("declare\n")
              .append("  function esc(s in varchar2) return varchar2 is begin return htf.escape_sc(s); end;\n")
              .append("begin\n");
            appendNlsSettings(sb);
            sb.append("  htp.p('<table class=\"confluenceTable\">\n")
              .append("    <thead>\n")
              .append("    <tr>\n");
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                sb.append("    <th class=\"confluenceTh\">' || esc('")
                  .append(rsmd.getColumnName(i).replace("'", "''"))
                  .append("') || '</th>\n");
            }
            sb.append("    </tr>\n")
              .append("    </thead>\n")
              .append("    <tbody>');\n")
              .append("  for rec in (")
              .append(body)
              .append(") loop\n")
              .append("    htp.p('<tr>\n");
            rsmd.getColumnCount();
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                sb.append("    <td class=\"confluenceTd ohp-data-")
                  .append(rsmd.getColumnName(i).replaceAll("[^A-Za-z0-9 ]", ""))
                  .append("\">");
                if (isNumericType(rsmd.getColumnType(i))) {
                    sb.append("' || to_char(rec.\"")
                        .append(rsmd.getColumnName(i))
                        .append("\",'999999999999990.00') || '");
                } else if (isDateType(rsmd.getColumnType(i))) {
                    sb.append("' || to_char(rec.\"")
                        .append(rsmd.getColumnName(i))
                        .append("\",'DD-MM-YYYY HH24:MI:SS') || '");
                } else {
                    sb.append("' || esc(rec.\"")
                        .append(rsmd.getColumnName(i))
                        .append("\") || '");
                }
                sb.append("</td>\n");
            }
            sb.append("    </tr>');\n")
                .append("  end loop;\n")
                .append("  htp.p('</tbody>\n")
                .append("  </table>');\n")
                .append("end;");
        }

        return sb.toString();
    }
}