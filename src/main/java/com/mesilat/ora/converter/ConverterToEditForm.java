package com.mesilat.ora.converter;

import com.atlassian.confluence.util.GeneralUtil;
import com.atlassian.sal.api.message.I18nResolver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;

public class ConverterToEditForm extends ConverterFormBase {
    @Override
    public String convert(Connection conn, String body, Map params, I18nResolver resolver) throws SQLException {
        StringBuilder sb = new StringBuilder();
        
        String sql = body.trim();
        TableInfo tableInfo = describeTable(conn, sql);
        if (tableInfo == null){
            throw new SQLException("Failed to describe table");
        }

        sb.append("/*\n")
          .append(body)
          .append("\n*/\n")
          .append("declare\n")
          .append("  v_rowid rowid;\n")
          .append("  function esc_html(s in varchar2) return varchar2 is begin\n")
          .append("    return htf.escape_sc(s);\n")
          .append("  end;\n")
          .append("  function esc_json(s in varchar2) return varchar2 is begin\n")
          .append("    return replace(replace(replace(replace(replace(replace(replace(s, '\\', '\\\\'), '\"','\\\"'),\n")
          .append("      CHR(9),'\\t'),CHR(8),'\\b'),CHR(13),'\\r'),CHR(12),'\\f'),CHR(10),'\\n');\n")
          .append("  end;\n")
          .append("begin\n");

        appendNlsSettings(sb);

        try (PreparedStatement ps = conn.prepareStatement(sql)){
            ResultSetMetaData rsmd = ps.getMetaData();
            // Print form
            sb.append("  htp.p('<form class=\"aui ohp-edit-form\">');\n");
            makeFormFields(sb, tableInfo, rsmd);

            // Print data
            sb.append("\n\n  htp.p('<div class=\"oracle-htp-form-data\">');\n")
              .append("  if owa_util.get_cgi_env('__DATA__') = 'true' then\n")
              .append("    if owa_util.get_cgi_env('__USER__') is null then\n")
              .append("      raise_application_error(-20000, 'User must be authenticated to update data');\n")
              .append("    end if;\n");

                if (tableInfo.canUpdate()) {
                    sb.append("    if owa_util.get_cgi_env('")
                      .append(tableInfo.getPrimaryColumn().getName())
                      .append("') is not null then\n");
                    makeUpdateBlock(sb, tableInfo);
                    sb
                      .append("    end if;\n")
                      .append("    if owa_util.get_cgi_env('")
                      .append(tableInfo.getPrimaryColumn().getName())
                      .append("') is null or sql%rowcount < 1 then\n");
                    makeInsertBlock(sb, tableInfo);
                    sb.append("    end if;\n");
                } else {
                    makeInsertBlock(sb, tableInfo);
                }
                sb.append("    commit;\n");
                makeDataDML(sb, tableInfo, rsmd);

            sb.append("  else\n");

                makeDataSelect(sb, sql, rsmd);

            sb.append("  end if;\n")
              .append("  htp.p('</div>');\n")
              .append("  htp.p('</form>');\n")
              .append("end;");

        }
        return sb.toString();
    }
    private void makeUpdateBlock(StringBuilder sb, TableInfo tableInfo){
        sb.append("      -- Update existing record\n")
          .append("      update ")
          .append(tableInfo.getFullName())
          .append(" set\n");

        for (ColumnInfo column : tableInfo.getColumns().values()) {
            if (!column.isPrimary()){
                sb.append("        \"")
                  .append(column.getName())
                  .append("\" = ");

                switch(column.getDataType()){
                    case "DATE":
                        sb.append("to_date(owa_util.get_cgi_env('")
                          .append(column.getName())
                          .append("'), 'DD-MM-YYYY')");
                        break;
                    default:
                        sb.append("owa_util.get_cgi_env('")
                          .append(column.getName())
                          .append("')");
                }
                
                sb.append(",\n");
            }
        }
        sb.delete(sb.length() - 2, sb.length() - 1)
          .append("      where");

        int i = 0;
        for (ColumnInfo column : tableInfo.getColumns().values()) {
            if (column.isPrimary()){
                sb.append("\n        ")
                  .append(i == 0? "": "and ")
                  .append("\"")
                  .append(column.getName())
                  .append("\" = ");
                
                switch(column.getDataType()){
                    case "DATE":
                        sb.append("to_date(owa_util.get_cgi_env('")
                          .append(column.getName())
                          .append("'), 'DD-MM-YYYY')");
                        break;
                    default:
                        sb.append("owa_util.get_cgi_env('")
                          .append(column.getName())
                          .append("')");
                }
                i ++;
            }
        }
        sb.append("\n      returning rowid into v_rowid;\n");
    }
    private void makeInsertBlock(StringBuilder sb, TableInfo tableInfo) {
        sb.append("      -- Insert new record\n")
          .append("      insert into ")
          .append(tableInfo.getFullName())
          .append(" (\n");

        int i = 0;
        for (ColumnInfo column : tableInfo.getColumns().values()) {
            i++;
            sb.append("        \"")
              .append(column.getName())
              .append("\"")
              .append(i < tableInfo.getColumns().size()? ",\n": "\n");
        }
        sb.append("      ) values (\n");
        i = 0;
        for (ColumnInfo column : tableInfo.getColumns().values()) {
            i++;
            switch(column.getDataType()){
                case "DATE":
                    sb.append("        to_date(owa_util.get_cgi_env('")
                      .append(column.getName())
                      .append("'), 'DD-MM-YYYY')");
                    break;
                default:
                    sb.append("        owa_util.get_cgi_env('")
                      .append(column.getName())
                      .append("')");
            }
            sb.append(i < tableInfo.getColumns().size()? ",\n": "\n");
        }
        sb.append("      ) returning rowid into v_rowid;\n");
    }
    private void makeFormFields(StringBuilder sb, TableInfo tableInfo, ResultSetMetaData rsmd) throws SQLException {
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            sb.append("    htp.p('<div class=\"field-group\">\n")
              .append("      <label for=\"")
              .append(GeneralUtil.htmlEncode(rsmd.getColumnName(i)))
              .append("\">")
              .append(rsmd.getColumnName(i));

            if (tableInfo.containsColumn(rsmd.getColumnName(i)) && !tableInfo.getColumn(rsmd.getColumnName(i)).isNullable()){
                sb.append("\n      <span class=\"aui-icon icon-required\">(required)</span>");
            }

            sb.append("</label>\n")
              .append("      <input class=\"text long-field ")
              .append(rsmd.getColumnTypeName(i))
              .append("\" type=\"text\" name=\"")
              .append(GeneralUtil.htmlEncode(rsmd.getColumnName(i)))
              .append("\"");

            if (tableInfo.containsColumn(rsmd.getColumnName(i)) && tableInfo.getColumn(rsmd.getColumnName(i)).getOptions() != null){
                sb.append(" option-values=\"")
                  .append(GeneralUtil.htmlEncode(toJsonArray(tableInfo.getColumn(rsmd.getColumnName(i)).getOptions()).toString()))
                  .append("\"");
            }

            sb.append("/>");

            if (tableInfo.containsColumn(rsmd.getColumnName(i)) && tableInfo.getColumn(rsmd.getColumnName(i)).getComment()!= null){
                sb.append("\n      <div class=\"description\">")
                  .append(GeneralUtil.htmlEncode(tableInfo.getColumn(rsmd.getColumnName(i)).getComment()))
                  .append("</div>");
            }

            sb.append("\n      </div>');\n");
        }
    }
    private void makeDataDML(StringBuilder sb, TableInfo tableInfo, ResultSetMetaData rsmd) throws SQLException {
        sb.append("    for rec in (select * from ")
          .append(tableInfo.getFullName())
          .append(" where rowid = v_rowid")
          .append(") loop\n");

        makeDataFields(sb, rsmd);

        sb.append("    end loop;\n");
    }
    private void makeDataSelect(StringBuilder sb, String sql, ResultSetMetaData rsmd) throws SQLException {
        sb.append("    for rec in (")
          .append(sql)
          .append(") loop\n");

        makeDataFields(sb, rsmd);

        sb.append("      exit;\n")
          .append("    end loop;\n");
    }    
    private void makeDataFields(StringBuilder sb, ResultSetMetaData rsmd) throws SQLException {
        sb.append("        htp.p('{');\n");
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
            switch (rsmd.getColumnTypeName(i)){
                case "DATE":
                    sb.append("        htp.p(esc_html('\"")
                      .append(rsmd.getColumnName(i))
                      .append("\": \"' || to_char(rec.\"")
                      .append(rsmd.getColumnName(i))
                      .append("\",'DD-MM-YYYY') || '\"")
                      .append(i < rsmd.getColumnCount()? ",": "")
                      .append("'));\n");
                    break;
                case "NUMBER":
                    sb.append("        if rec.\"")
                      .append(rsmd.getColumnName(i))
                      .append("\" is not null then htp.p(esc_html('\"")
                      .append(rsmd.getColumnName(i))
                      .append("\": ' || rec.\"")
                      .append(rsmd.getColumnName(i))
                      .append("\" || '")
                      .append(i < rsmd.getColumnCount()? ",": "")
                      .append("')); end if;\n");
                    break;
                default:
                    sb.append("        htp.p(esc_html('\"")
                      .append(rsmd.getColumnName(i))
                      .append("\": \"' || esc_json(rec.\"")
                      .append(rsmd.getColumnName(i))
                      .append("\") || '\"")
                      .append(i < rsmd.getColumnCount()? ",": "")
                      .append("'));\n");
            }
        }
        sb.append("        htp.p('}');\n");
    }
}