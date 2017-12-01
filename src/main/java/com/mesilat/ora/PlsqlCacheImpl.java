package com.mesilat.ora;

import com.atlassian.confluence.core.BodyContent;
import com.atlassian.confluence.event.events.content.page.PageCreateEvent;
import com.atlassian.confluence.event.events.content.page.PageRemoveEvent;
import com.atlassian.confluence.event.events.content.page.PageRestoreEvent;
import com.atlassian.confluence.event.events.content.page.PageTrashedEvent;
import com.atlassian.confluence.event.events.content.page.PageUpdateEvent;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class PlsqlCacheImpl implements PlsqlCache, InitializingBean, DisposableBean {
    private static final String MACRO_DEFAULT = "__DEFAULT__";
    private static final Logger LOGGER = LoggerFactory.getLogger("oracle-htp-plugin");
    private static PlsqlCacheImpl instance;

    private final PageManager pageManager;
    private final EventPublisher eventPublisher;
    private final Map<Long,Map<String, MacroInfo>> cache = new HashMap<>();
    
    @Override
    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
        instance = this;
        LOGGER.debug("Page event listener started");
    }
    @Override
    public void destroy() throws Exception {
        eventPublisher.unregister(this);
        instance = null;
        LOGGER.debug("Page event listener stopped");
    }

    @EventListener
    public void onPageCreateEvent(PageCreateEvent event) {
        synchronized(cache){
            cache.remove(event.getPage().getId());
        }
    }
    @EventListener
    public void onPageUpdateEvent(PageUpdateEvent event) {
        synchronized(cache){
            cache.remove(event.getPage().getId());
        }
    }
    @EventListener
    public void pageTrashedEvent(PageTrashedEvent event) {
        synchronized(cache){
            cache.remove(event.getPage().getId());
        }
    }
    @EventListener
    public void pageRemoveEvent(PageRemoveEvent event) {
        synchronized(cache){
            cache.remove(event.getPage().getId());
        }
    }
    @EventListener
    public void pageRestoreEvent(PageRestoreEvent event) {
        synchronized(cache){
            cache.remove(event.getPage().getId());
        }
    }

    private void parse(Page page){
        Map<String, MacroInfo> macros = parsePage(page);
        synchronized(cache){
            cache.put(page.getId(), macros);
        }
    }
    public Map<String, MacroInfo> parsePage(Page page){
        Map<String, MacroInfo> macros = new HashMap<>();
        for (BodyContent content : page.getBodyContents()) {
            Document doc = Jsoup.parse(new StringBuilder()
                .append("<body>")
                .append(content.getBody())
                .append("</body>")
                .toString(), "");

            for (Element elt : doc.getElementsByTag("ac:structured-macro")) {
                if (!"oracle-htp-macro".equals(elt.attr("ac:name"))){
                    continue;
                }

                MacroInfo macro = parseMacro(elt);
                if (macros.isEmpty()) {
                    macros.put(MACRO_DEFAULT, macro);
                }
                if (macro.getId() != null){
                    macros.put(macro.getId(), macro);
                }
            }
        }
        return macros;
    }
    public MacroInfo parseMacro(Element macro) {
        String macroId = null;
        StringBuilder body = new StringBuilder();
        Map<String,String> params = new HashMap<>();

        for (Element bodyElt : macro.getElementsByTag("ac:plain-text-body")) {
            String text = getText(bodyElt);
            body.append(text);
        }
        for (Element paramElt : macro.getElementsByTag("ac:parameter")){
            String text = getText(paramElt);
            params.put(paramElt.attr("ac:name"), text);
            if ("myid".equals(paramElt.attr("ac:name"))){
                macroId = text;
            }
        }
        String _body = body.toString();
        if (macroId == null || macroId.isEmpty()){
            macroId = createMacroKey(_body, params);
        }

        return new MacroInfo(macroId, _body, params);
    }
    protected static String getText(Element elt) {
        StringBuilder sb = new StringBuilder();
        for (Node node : elt.childNodes()) {
            if (node instanceof TextNode) {
                TextNode txt = (TextNode)node;
                sb.append(txt.getWholeText());
            }
        }
        return sb.toString();
    }
    public static String createMacroKey(String body, Map<String,String> params){
        try {
            byte[] separator = "~\n~".getBytes();
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(body.getBytes());
            for (Entry<String,String> e : params.entrySet()){
                if (": = | RAW | = :".equals(e.getKey())){
                    continue;
                }
                md.update(separator);
                md.update(e.getKey().getBytes());
                md.update(separator);
                md.update(e.getValue().getBytes());
            }
            UUID id = UUID.nameUUIDFromBytes(md.digest());
            return id.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }

    public PlsqlCacheImpl(PageManager pageManager, EventPublisher eventPublisher) {
        this.pageManager = pageManager;
        this.eventPublisher = eventPublisher;
    }

    public MacroInfo _getMacroInfo(long pageId, String macroId) {
        synchronized(cache){
            if (cache.containsKey(pageId)){
                Map<String, MacroInfo> map = cache.get(pageId);
                String key = (macroId == null || macroId.isEmpty())? MACRO_DEFAULT: macroId;
                return map.get(key);
            }
        }

        Page page = pageManager.getPage(pageId);
        if (page == null){
            return null;
        } else {
            parse(page);
        }

        synchronized(cache){
            if (cache.containsKey(pageId)){
                Map<String, MacroInfo> map = cache.get(pageId);
                String key = (macroId == null || macroId.isEmpty())? MACRO_DEFAULT: macroId;
                return map.get(key);
            }
        }

        return null;
    }
    public static MacroInfo getMacroInfo(long pageId, String macroId){
        if (instance == null){
            return null;
        } else {
            return instance._getMacroInfo(pageId, macroId);
        }
    }
}