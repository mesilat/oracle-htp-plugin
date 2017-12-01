package com.mesilat.ora;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.json.json.JsonObject;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.Macro.BodyType;
import com.atlassian.confluence.macro.Macro.OutputType;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.renderer.PageContext;
import com.atlassian.confluence.renderer.template.TemplateRenderer;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.TokenType;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.sal.api.message.I18nResolver;
import com.google.common.collect.Maps;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.codec.binary.Base64;

public class OracleHTPMacro extends BaseMacro implements Macro {
    private static final String PLUGIN_KEY = "com.mesilat.oracle-htp-plugin";
    private static final String[] BLOCK_TYPES = { "table", "view", "edit", "json", "bar", "line", "pie", "horiz", "area" };
    
    private final I18nResolver resolver;
    private final SettingsManager settingsManager;
    private final TemplateRenderer templateRenderer;
    private final ActiveObjects ao;

    @Override
    public TokenType getTokenType(Map parameters, String body, RenderContext context) {
        return TokenType.BLOCK;
    }
    @Override
    public boolean hasBody() {
        return true;
    }
    @Override
    public RenderMode getBodyRenderMode() {
        return RenderMode.NO_RENDER;
    }
    @Override
    public BodyType getBodyType() {
        return BodyType.PLAIN_TEXT;
    }
    @Override
    public OutputType getOutputType() {
        return OutputType.BLOCK;
    }

    @Override
    public String execute(Map parameters, String body, RenderContext renderContext) {
        return body;
    }
    @Override
    public String execute(Map params, String body, ConversionContext conversionContext) throws MacroExecutionException {
        try {
            if ("preview".equals(conversionContext.getOutputType())) {
                if (isSqlSelect(body)) {
                    Map<String, Object> map = Maps.newHashMap();
                    map.put("baseUrl", settingsManager.getGlobalSettings().getBaseUrl());
                    map.put("blockTypes", BLOCK_TYPES);
                    map.put("macroParams", serialize(params));
                    map.put("body", body);
                    map.put("autorefresh", 0);
                    return renderFromSoy("macro-resources", "Mesilat.Templates.Oracle.converterOptions.soy", map);
                } else {
                    Map<String, Object> map = Maps.newHashMap();
                    map.put("body", body);
                    return renderFromSoy("macro-resources", "Mesilat.Templates.Oracle.body.soy", map);
                }
            } else {
                String method = params.get("method") == null? "lazy": params.get("method").toString();
                if (method.equalsIgnoreCase("inline")) {
                    return executeInline(body, params, conversionContext);
                } else if (method.equalsIgnoreCase("rest")) {
                    return executeREST(body, params, conversionContext);
                } else if (method.equalsIgnoreCase("lazy")) {
                    return executeLazy(body, params, conversionContext);
                } else if (method.equalsIgnoreCase("c3js")) {
                    return executeC3JS(body, params, conversionContext);
                } else {
                    throw new MacroExecutionException(MessageFormat.format(
                        resolver.getText("com.mesilat.oracle-htp-plugin.error.method"),
                        method)
                    );
                }
            }
        } catch(IOException | MacroExecutionException ex) {
            throw new MacroExecutionException(ex);
        }
    }
    private String executeInline(String body, Map params, ConversionContext conversionContext) throws MacroExecutionException {
        if (params.get("dad") == null) {
            throw new MacroExecutionException(resolver.getText("com.mesilat.oracle-htp-plugin.error.nodad"));
        }
        String dadName = params.get("dad").toString();
        DataAccessDescriptor dad = DadCacheImpl.getInstance().get(dadName);
        if (dad == null) {
            throw new MacroExecutionException(MessageFormat.format(
                resolver.getText("com.mesilat.oracle-htp-plugin.error.badDad"), dadName
            ));
        }
        if (!DadCacheImpl.isUserAuthorized(dad)){
            throw new MacroExecutionException(
                resolver.getText("com.mesilat.oracle-htp-plugin.error.authorized")
            );
        }

        Map<String,String> env = createEnvironment();
        GelfLogSender.debug(AuthenticatedUserThreadLocal.get().getName(), "Method INLINE", "Invoke PLSQL Block", (Page)conversionContext.getEntity(), body, dad.getName(), params, env);

        try {
            Map<String,Object> map = new HashMap<>();
            map.put("macroKey", params.get("myid"));
            map.put("sidebar", !params.containsKey("sidebar") || "true".equals(params.get("sidebar")));
            map.put("display", params.get("display"));
            map.put("hasLinks", hasColumnLinks(params.get("myid")));
            map.put("dad", dad.getName());
            map.put("data", DelegatingConnectionCache.invoke(dad, body, params, env));
            return renderFromSoy("macro-resources", "Mesilat.Templates.Oracle.macroInline.soy", map);
        } catch(Exception ex){
            GelfLogSender.error(AuthenticatedUserThreadLocal.get().getName(), "Error in INLINE Method", ex.getMessage(), (Page)conversionContext.getEntity(), body, dad.getName(), params, env);

            Map<String,Object> map = new HashMap<>();
            map.put("title", resolver.getText("com.mesilat.oracle-htp-plugin.error.caption"));
            map.put("text", ex.getMessage());
            return renderFromSoy("macro-resources", "Mesilat.Templates.Oracle.errorInMacroWithTitle.soy", map);
        }
    }
    private String executeREST(String body, Map params, ConversionContext conversionContext) throws MacroExecutionException {
        PageContext pageContext = conversionContext.getPageContext();
        ContentEntityObject contentEntityObject = pageContext.getEntity();
        String dad = params.get("dad") == null? null: params.get("dad").toString();
        String pageId = contentEntityObject.getIdAsString();
        String macroKey = getMacroKey(body, (Map<String,String>)params, conversionContext);

        Map<String,Object> map = new HashMap<>();
        map.put("body", body);
        map.put("json", String.format("{\n\t\"dad\": \"%s\",\n\t\"pageId\": %s,\n\t\"macroKey\": \"%s\",\n\t\"params\": { ... }\n}", dad, pageId, macroKey));
        String testPage = MessageFormat.format("{0}/plugins/servlet/oracle-htp/test?dad={1}&pageid={2}&macroKey={3}",
            settingsManager.getGlobalSettings().getBaseUrl(), dad, pageId, macroKey
        );
        String testText = MessageFormat.format(resolver.getText("oracle-htp-plugin.oracle-htp-macro.text.4"), testPage);
        map.put("test", testText);
        return renderFromSoy("macro-resources", "Mesilat.Templates.Oracle.macroRest.soy", map);
    }
    private String executeLazy(String body, Map params, ConversionContext conversionContext) throws MacroExecutionException {
        String macroKey = getMacroKey(body, (Map<String,String>)params, conversionContext);
        JsonObject paramObj = new JsonObject();
        if (params != null) {
            for (Object key : params.keySet()) {
                paramObj.setProperty(key.toString(), params.get(key) == null? null: params.get(key).toString());
            }
        }

        Map<String,Object> map = new HashMap<>();
        map.put("body", body);
        map.put("macroKey", macroKey);
        map.put("sidebar", !params.containsKey("sidebar") || "true".equals(params.get("sidebar")));
        map.put("display", params.get("display"));
        map.put("dad", params.get("dad"));
        map.put("params", paramObj.serialize());
        if (params.containsKey("autorefresh")){
            try {
                Duration d = Duration.parse(params.get("autorefresh").toString());
                map.put("autorefresh", d.getSeconds());
            } catch(Throwable t){
                map.put("autorefresh", 0);
            }
        } else {
            map.put("autorefresh", 0);
        }
        return renderFromSoy("macro-resources", "Mesilat.Templates.Oracle.macroLazy.soy", map);
    }
    private String executeC3JS(String body, Map params, ConversionContext conversionContext) throws MacroExecutionException {
        String macroKey = getMacroKey(body, (Map<String,String>)params, conversionContext);
        JsonObject paramObj = new JsonObject();
        if (params != null) {
            for (Object key : params.keySet()) {
                paramObj.setProperty(key.toString(), params.get(key) == null? null: params.get(key).toString());
            }
        }

        Map<String,Object> map = new HashMap<>();
        map.put("body", body);
        map.put("macroKey", macroKey);
        map.put("sidebar", !params.containsKey("sidebar") || "true".equals(params.get("sidebar")));
        map.put("display", params.get("display"));
        map.put("dad", params.get("dad"));
        map.put("params", paramObj.serialize());
        return renderFromSoy("macro-resources", "Mesilat.Templates.Oracle.macroC3js.soy", map);
    }
    private String renderFromSoy(String key, String soyTemplate, Map soyContext) {
        StringBuilder output = new StringBuilder();
        templateRenderer.renderTo(output, String.format("%s:%s", PLUGIN_KEY, key), soyTemplate, soyContext);
        return output.toString();
    }
    private Map<String,String> createEnvironment(){
        Map<String,String> env = new HashMap<>();
        env.put("__USER__", AuthenticatedUserThreadLocal.get().getName());
        return env;
    }

    public static String getMacroKey(String body, Map<String,String> params, ConversionContext conversionContext){
        String macroId = params.get("myid");
        if (macroId == null || macroId.isEmpty()){
            macroId = PlsqlCacheImpl.createMacroKey(body, params);
        }
        return macroId;
    }

    protected static boolean isSqlSelect(String sql) {
        if (sql == null) {
            return false;
        }
        Pattern p = Pattern.compile("^\\s*select\\s.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        return p.matcher(sql).matches();
    }
    protected static String serialize(Object obj) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(buf)) {
            oos.writeObject(obj);
        }
        return Base64.encodeBase64String(buf.toByteArray());
    }
    protected static Object deserialize(String str) throws IOException, ClassNotFoundException {
        ByteArrayInputStream buf = new ByteArrayInputStream(Base64.decodeBase64(str));
        ObjectInputStream ois = new ObjectInputStream(buf);
        return ois.readObject();
    }
    protected boolean hasColumnLinks(Object macroKey){
        return ao.executeInTransaction(() -> {
            return ao.count(ColumnLink.class, "MACRO_ID = ?", macroKey) > 0;
        });
    }

    public OracleHTPMacro(final I18nResolver resolver,
        final SettingsManager settingsManager, final TemplateRenderer templateRenderer,
        final ActiveObjects ao
    ){
        this.resolver = resolver;
        this.settingsManager = settingsManager;
        this.templateRenderer = templateRenderer;
        this.ao = ao;
    }
}