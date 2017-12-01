package com.mesilat.ora;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.renderer.template.TemplateRenderer;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.TokenType;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.common.collect.Maps;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;

public class DadSelectorMacro extends BaseMacro implements Macro {
    private static final String PLUGIN_KEY = "com.mesilat.oracle-htp-plugin";

    private final I18nResolver resolver;
    private final SettingsManager settingsManager;
    private final TemplateRenderer templateRenderer;

    @Override
    public TokenType getTokenType(Map parameters, String body, RenderContext context) {
        return TokenType.INLINE;
    }
    @Override
    public boolean hasBody() {
        return false;
    }
    @Override
    public RenderMode getBodyRenderMode() {
        return RenderMode.NO_RENDER;
    }
    @Override
    public BodyType getBodyType() {
        return BodyType.NONE;
    }
    @Override
    public OutputType getOutputType() {
        return OutputType.INLINE;
    }
    
    @Override
    public String execute(Map parameters, String body, RenderContext renderContext) {
        return body;
    }
    @Override
    public String execute(Map params, String body, ConversionContext conversionContext) throws MacroExecutionException {
        String[] dadNames = getDadNames();
        if ("preview".equals(conversionContext.getOutputType())) {
            if (dadNames.length == 0) {
                String url = settingsManager.getGlobalSettings().getBaseUrl() + "/plugins/servlet/oracle-htp/config";
                String msg = MessageFormat.format(resolver.getText("com.mesilat.oracle-htp-plugin.text.no-dads-defined"), url);
                Map<String, Object> map = Maps.newHashMap();
                map.put("msg", msg);
                return renderFromSoy("macro-resources", "Mesilat.Templates.Oracle.noDads.soy", map);
            } else {
                Map<String, Object> map = Maps.newHashMap();
                map.put("dadNames", dadNames);
                map.put("defaultDad", params.get("default"));
                return renderFromSoy("macro-resources", "Mesilat.Templates.Oracle.dadList.soy", map);
            }
        } else {
            Map<String, Object> map = Maps.newHashMap();
            map.put("dadNames", dadNames);
            map.put("defaultDad", params.get("default"));
            return renderFromSoy("macro-resources", "Mesilat.Templates.Oracle.dadSelector.soy", map);
        }
    }
    private String[] getDadNames() {
        String[] result = DadCacheImpl.getInstance().getNames().toArray(new String[]{});
        Arrays.sort(result);
        return result;
    }
    protected String renderFromSoy(String key, String soyTemplate, Map soyContext) {
        StringBuilder output = new StringBuilder();
        templateRenderer.renderTo(output, String.format("%s:%s", PLUGIN_KEY, key), soyTemplate, soyContext);
        return output.toString();
    }
    
    public DadSelectorMacro(PluginSettingsFactory pluginSettingsFactory, I18nResolver resolver,
            SettingsManager settingsManager, TemplateRenderer templateRenderer) {
        this.resolver = resolver;
        this.settingsManager = settingsManager;
        this.templateRenderer = templateRenderer;
    }
}