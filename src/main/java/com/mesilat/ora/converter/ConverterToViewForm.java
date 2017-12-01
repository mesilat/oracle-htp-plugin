package com.mesilat.ora.converter;

import com.atlassian.confluence.util.GeneralUtil;
import com.atlassian.sal.api.message.I18nResolver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

public class ConverterToViewForm extends ConverterFormBase {
    @Override
    public String convert(Connection conn, String body, Map params, I18nResolver resolver) throws SQLException {
        StringBuilder sb = new StringBuilder();
        String sql = body.trim();

        try (PreparedStatement ps = conn.prepareStatement(sql)){
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
              .append("      <tr>\n")
              .append("        <th class=\"confluenceTh\">Attribute</th>\n")
              .append("        <th class=\"confluenceTh\">Value</th>\n")
              .append("      </tr>\n")
              .append("    </thead>\n")
              .append("    <tbody>');\n")
              .append("  for rec in (")
              .append(body)
              .append(") loop\n");

            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                sb.append("    htp.p('<tr>")
                  .append("<td class=\"confluenceTd\">")
                  .append(GeneralUtil.htmlEncode(rsmd.getColumnName(i)))
                  .append("</td>")
                  .append("<td class=\"confluenceTd ohp-data-")
                  .append(rsmd.getColumnName(i).replaceAll("[^A-Za-zА-Яа-я0-9 ]", ""))
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
                sb.append("</td>")
                  .append("</tr>');\n");
            }
            sb.append("  end loop;\n")
              .append("  htp.p('</tbody>\n")
              .append("  </table>');\n")
              .append("end;");
        }
        return sb.toString();
    }
}