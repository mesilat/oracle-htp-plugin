package com.mesilat.ora;

import com.atlassian.confluence.json.json.Json;
import com.atlassian.confluence.json.json.JsonObject;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserManager;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/plsql")
@AnonymousAllowed
public class PlsqlResource extends ResourceBase {
    public static final String ERROR_FORMAT_HTML = "html";
    public static final String ERROR_FORMAT_JSON = "json";

    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.oracle-htp-plugin");
    private static final Pattern PARAM = Pattern.compile("^\\{(.+)\\}$");

    private final PageManager pageManager;
    private final I18nResolver resolver;
    private final PermissionManager permissionManager;

    private Map<String,String> createEnvironment(final HttpServletRequest request){
        Map<String,String> env = new HashMap<>();
        if (request.getHeader("X-Forwarded-For") != null){
            env.put("__HOST__", request.getHeader("X-Forwarded-For"));
        } else {
            env.put("__HOST__", request.getRemoteHost());
        }
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user != null){
            env.put("__USER__", user.getName());
        }
        return env;
    }
    
    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response get(
        final @QueryParam("page-id") Long pageId,
        final @QueryParam("macro-id") String macroId,
        final @QueryParam("dad") String dad,
        final @QueryParam("p1") String p1,
        final @QueryParam("url-params") String urlParams,
        @Context HttpServletRequest request,
        @Context HttpServletResponse response
    ){
        Page page = pageManager.getPage(pageId);
        if (page == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(
                MessageFormat.format(resolver.getText("com.mesilat.oracle-htp-plugin.error.badPageId"), pageId)
            ).build();
        } else {
            LOGGER.debug("Calling database with PLSQL block at {}", page.getUrlPath());
        }

        if (!isUserAuthorized(page)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(
                resolver.getText("com.mesilat.oracle-htp-plugin.error.authorized")
            ).build();
        }

        PlsqlCache.MacroInfo macro = PlsqlCacheImpl.getMacroInfo(pageId, macroId);
        Map<String,String> params = new HashMap<>();
        params.putAll(macro.getParams());
        if (urlParams != null){
            ObjectMapper mapper = new ObjectMapper();
            try {
                ObjectNode node = (ObjectNode)mapper.readTree(urlParams);
                Map<String,String> _params = new HashMap<>();
                for (Entry<String,String> e : params.entrySet()) {
                    Matcher m = PARAM.matcher(e.getValue());
                    if (m.matches()) {
                        if (node.has(m.group(1))) {
                            _params.put(e.getKey(), node.get(m.group(1)).asText());
                        }
                    }
                }
                params.putAll(_params);
            } catch (IOException ex) {
                LOGGER.warn(String.format("Failed to parse as JSON \"%s\"", urlParams), ex);
            }
        }

        String dadName = dad == null || dad.isEmpty()? macro.getParams().get("dad"): dad;
        if (dadName == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(
                resolver.getText("com.mesilat.oracle-htp-plugin.error.nodad2")
            ).build();
        }

        DataAccessDescriptor _dad = DadCacheImpl.getInstance().get(dadName);
        if (_dad == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity(
                MessageFormat.format(resolver.getText("com.mesilat.oracle-htp-plugin.error.badDad"), dadName)
            ).build();
        }
        if (!DadCacheImpl.isUserAuthorized(_dad)){
            return Response.status(Response.Status.UNAUTHORIZED).entity(
                resolver.getText("com.mesilat.oracle-htp-plugin.error.authorized")
            ).build();
        }

        try {
            if (p1 != null){
                params.put("p1", p1);
            }
            Map<String,String> env = createEnvironment(request);
            ConfluenceUser user = AuthenticatedUserThreadLocal.get();
            GelfLogSender.debug(user == null? "Anonymous": user.getName(), "Method GET", "Invoke PLSQL Block", page, macro.getId(), _dad.getName(), params, env);
            return Response.ok(
                DelegatingConnectionCache.invoke(_dad, macro.getBody(), params, env)
            ).build();
        } catch(MakeFileDownloadException ex){
            response.setHeader("Content-disposition","attachment; filename*=UTF-8''" + encodeURIComponent(ex.getFileName()));
            return Response.ok(ex.getData()).build();
        } catch(Exception ex) {
            LOGGER.warn("PLSQL execution failed", ex);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(
                ex.getMessage()
            ).build();
        }
    }
    
    @POST
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response post(final PlsqlCall plsqlCall, @Context HttpServletRequest request) {
        boolean errorFormatJson = !ERROR_FORMAT_HTML.equalsIgnoreCase(plsqlCall.getDataType());

        Page page = pageManager.getPage(plsqlCall.getPageId());
        if (page == null) {
            if (errorFormatJson) {
                return Response.ok(
                    new Result(Result.STATUS_FAILURE,
                            MessageFormat.format(resolver.getText("com.mesilat.oracle-htp-plugin.error.badPageId"), plsqlCall.getPageId())
                    ).toJson().serialize()
                ).build();
            } else {
                return Response
                    .status(plsqlCall.getPageId() == null? Response.Status.BAD_REQUEST: Response.Status.NOT_FOUND)
                    .entity(MessageFormat.format(resolver.getText("com.mesilat.oracle-htp-plugin.error.badPageId"), plsqlCall.getPageId()))
                    .build();
            }
        } else {
            LOGGER.debug("Calling database with PLSQL block at {}", page.getUrlPath());
        }

        if (!isUserAuthorized(page)) {
            if (errorFormatJson) {
                return Response.ok(
                    new Result(Result.STATUS_FAILURE,
                            resolver.getText("com.mesilat.oracle-htp-plugin.error.authorized")
                    ).toJson().serialize()
                ).build();
            } else {
                return Response
                    .status(plsqlCall.getPageId() == null? Response.Status.BAD_REQUEST: Response.Status.NOT_FOUND)
                    .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.authorized"))
                    .build();
            }
        }

        PlsqlCache.MacroInfo macro = PlsqlCacheImpl.getMacroInfo(plsqlCall.getPageId(), plsqlCall.getMacroKey());

        if (macro == null) {
            LOGGER.debug("Failed to find the required macro {}", plsqlCall.getMacroKey());
            if (errorFormatJson) {
                return Response.ok(
                    new Result(Result.STATUS_FAILURE,
                            MessageFormat.format(resolver.getText("com.mesilat.oracle-htp-plugin.error.badMacroId"), plsqlCall.getMacroKey())
                    ).toJson().serialize()
                ).build();
            } else {
                return Response
                    .status(Response.Status.NOT_FOUND)
                    .entity(MessageFormat.format(resolver.getText("com.mesilat.oracle-htp-plugin.error.badMacroId"), plsqlCall.getMacroKey()))
                    .build();
            }
        }

        String dadName = macro.getParams().get("dad");
        if (plsqlCall.getDad() != null) {
            dadName = plsqlCall.getDad();
        }
        if (dadName == null) {
            LOGGER.warn("No DAD specified for database call and no DAD in macro configuration", page.getUrlPath());
            return Response.ok(
                new Result(Result.STATUS_FAILURE, resolver.getText("com.mesilat.oracle-htp-plugin.error.nodad2")).toJson().serialize()
            ).build();
        }

        if (macro.getBody() == null || macro.getBody().isEmpty()) {
            LOGGER.warn("No plsql procedure specified for database call at {}", page.getUrlPath());
            if (errorFormatJson) {
                return Response.ok(
                    new Result(Result.STATUS_FAILURE, resolver.getText("com.mesilat.oracle-htp-plugin.error.noplsql")).toJson().serialize()
                ).build();
            } else {
                return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.noplsql"))
                    .build();
            }
        }

        DataAccessDescriptor dad = DadCacheImpl.getInstance().get(dadName);
        if (dad == null) {
            LOGGER.warn("Invalid DAD name specified at {}", page.getUrlPath());
            if (errorFormatJson) {
                return Response.ok(
                    new Result(Result.STATUS_FAILURE,
                        MessageFormat.format(resolver.getText("com.mesilat.oracle-htp-plugin.error.badDad"), dadName)
                    ).toJson().serialize()
                ).build();
            } else {
                return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(MessageFormat.format(resolver.getText("com.mesilat.oracle-htp-plugin.error.badDad"), dadName))
                    .build();
            }
        }
        if (!DadCacheImpl.isUserAuthorized(dad)){
            if (errorFormatJson) {
                return Response.ok(
                    new Result(Result.STATUS_FAILURE,
                        resolver.getText("com.mesilat.oracle-htp-plugin.error.authorized")
                    ).toJson().serialize()
                ).build();
            } else {
                return Response
                    .status(Response.Status.UNAUTHORIZED)
                    .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.authorized"))
                    .build();
            }
        }

        Map<String,String> params = new HashMap<>();
        params.putAll(macro.getParams());
        params.putAll(plsqlCall.getParams());

        Map<String,String> env = createEnvironment(request);
        if (plsqlCall.getFormFields() != null) {
            env.put("__DATA__", "true");
            env.putAll(plsqlCall.getFormFields());
        }
        try {
            ConfluenceUser user = AuthenticatedUserThreadLocal.get();
            GelfLogSender.debug(user == null? "Anonymous": user.getName(), "Method POST", "Invoke PLSQL Block", page, macro.getId(), dad.getName(), params, env);
            return Response.ok(DelegatingConnectionCache.invoke(dad, macro.getBody(), params, env)).build();
        } catch(Exception ex) {
            LOGGER.warn("PLSQL execution failed", ex);
            if (errorFormatJson) {
                return Response.ok(
                    new Result(Result.STATUS_FAILURE, ex.getMessage()).toJson().serialize()
                ).build();
            } else {
                return Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity(ex.getLocalizedMessage())
                    .build();
            }
        }
    }
    public boolean isUserAuthorized(Page page) {
        return permissionManager.hasPermission(AuthenticatedUserThreadLocal.get(), Permission.VIEW, page);
    }
    public static String encodeURIComponent(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8")
                .replaceAll("\\+", "%20")
                .replaceAll("\\%21", "!")
                .replaceAll("\\%27", "'")
                .replaceAll("\\%28", "(")
                .replaceAll("\\%29", ")")
                .replaceAll("\\%7E", "~");
        } catch (UnsupportedEncodingException ignore) {
            return s;
        }
    }

    public PlsqlResource(UserManager userManager, PageManager pageManager,
            I18nResolver resolver, PermissionManager permissionManager) {
        super(userManager);

        this.pageManager = pageManager;
        this.resolver = resolver;
        this.permissionManager = permissionManager;
    }
    
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PlsqlCall {
        @XmlElement
        private Long pageId;
        @XmlElement
        private String macroKey;
        @XmlElement
        private String dad;
        @XmlElement
        private Map<String,String> params;
        @XmlElement
        private String dataType;
        @XmlElement
        private Map<String,String> formFields;
        @XmlElement
        private Map<String,String> urlParams;

        public Long getPageId() {
            return pageId;
        }
        public void setPageId(Long pageId) {
            this.pageId = pageId;
        }
        public String getMacroKey() {
            return macroKey;
        }
        public void setMacroKey(String macroKey) {
            this.macroKey = macroKey;
        }
        public String getDad() {
            return dad;
        }
        public void setDad(String dad) {
            this.dad = dad;
        }
        public Map<String,String> getParams() {
            return params;
        }
        public void setParams(Map<String,String> params) {
            this.params = params;
        }
        public String getParam(String key) {
            return params.containsKey(key)? params.get(key): null;
        }
        public String getDataType() {
            return dataType;
        }
        public void setDataType(String dataType) {
            this.dataType = dataType;
        }
        public Map<String,String> getFormFields() {
            return formFields;
        }
        public void setFormFields(Map<String,String> formFields) {
            this.formFields = formFields;
        }
        public Map<String,String> getUrlParams() {
            return urlParams;
        }
        public void setUrlParams(Map<String,String> urlParams) {
            this.urlParams = urlParams;
        }
    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public class Result {
        public static final String STATUS_SUCCESS = "SUCCESS";
        public static final String STATUS_FAILURE = "FAILURE";

        @XmlElement
        private String status;
        @XmlElement
        private String message;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Json toJson() {
            JsonObject json = new JsonObject();
            json.setProperty("status", getStatus());
            json.setProperty("message", getMessage());
            return json;
        }

        public Result() {
        }

        public Result(String status) {
            this.status = status;
        }

        public Result(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }
}