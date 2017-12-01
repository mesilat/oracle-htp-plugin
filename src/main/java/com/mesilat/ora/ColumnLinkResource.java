package com.mesilat.ora;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;

@Path("/column-links")
public class ColumnLinkResource extends ResourceBase {
    //private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.oracle-htp-plugin");

    private final I18nResolver resolver;
    private final PageManager pageManager;
    private final PermissionManager permissionManager;
    private final ActiveObjects ao;

    @AnonymousAllowed
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(final @QueryParam("macro-key") String macroKey, @Context HttpServletRequest request) {
        final ObjectMapper mapper = new ObjectMapper();
        ArrayNode arr = toArray(mapper, ao.executeInTransaction(() -> {
            return Arrays.asList(ao.find(ColumnLink.class, "MACRO_ID = ?", macroKey));
        }));
        ObjectNode results = mapper.createObjectNode();
        results.put("results", arr);
        return Response.ok(results.toString()).build();
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response put(final ObjectNode node, @Context HttpServletRequest request) {
        final ObjectMapper mapper = new ObjectMapper();
        long pageId = node.get("pageId").asInt();
        String macroId = node.get("macroId").asText();
        Page page = pageManager.getPage(pageId);
        if (!permissionManager.hasPermission(AuthenticatedUserThreadLocal.get(), Permission.EDIT, page)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(
                resolver.getText("com.mesilat.oracle-htp-plugin.error.not-authorized")
            ).build();
        }

        List<ColumnLink> columnLinks = new ArrayList<>();
        try {
            ao.executeInTransaction(() -> {
                Arrays.asList(ao.find(ColumnLink.class, "MACRO_ID = ?", macroId)).forEach((columnLink) -> {
                    ao.delete(columnLink);
                });

                ((ArrayNode)node.get("links")).getElements().forEachRemaining((obj) -> {
                    ColumnLink columnLink = toColumnLink(obj, macroId);
                    columnLink.save();
                    columnLinks.add(columnLink);
                });
                return null;
            });
        } catch(RuntimeException ex){
            return Response.status(Response.Status.BAD_REQUEST).entity(
                ex.getMessage() == null? resolver.getText("com.mesilat.oracle-htp-plugin.error.invalid-data"): ex.getMessage()
            ).build();
        }
        ObjectNode results = mapper.createObjectNode();
        results.put("results", toArray(mapper, columnLinks));
        return Response.ok(results.toString()).build();
    }

    public ObjectNode toObject(ObjectMapper mapper, ColumnLink columnLink){
        ObjectNode obj = mapper.createObjectNode();
        obj.put("id", columnLink.getID());
        obj.put("macroId", columnLink.getMacroId());
        obj.put("columnName", columnLink.getColumnName());
        obj.put("pageId", columnLink.getPageId());
        Page page = pageManager.getPage(columnLink.getPageId());
        if (page != null){
            obj.put("pageTitle", page.getTitle());
        }
        obj.put("paramName", columnLink.getParamName());
        return obj;
    }
    public ArrayNode toArray(ObjectMapper mapper, Collection<ColumnLink> columnLinks){
        ArrayNode arr = mapper.createArrayNode();
        for (ColumnLink columnLink : columnLinks){
            arr.add(toObject(mapper, columnLink));
        }
        return arr;
    }
    public ColumnLink toColumnLink(JsonNode obj, String macroId){
        return ao.executeInTransaction(() -> {
            ColumnLink link = ao.create(ColumnLink.class);
            link.setMacroId(macroId);
            link.setColumnName(obj.get("columnName").asText());
            link.setPageId(obj.get("pageId").asLong());
            link.setParamName(obj.get("paramName").asText());
            return link;
        });
    }

    public ColumnLinkResource(UserManager userManager, I18nResolver resolver,
        PageManager pageManager, PermissionManager permissionManager, ActiveObjects ao
    ) {
        super(userManager);

        this.resolver = resolver;
        this.pageManager = pageManager;
        this.permissionManager = permissionManager;
        this.ao = ao;
    }
}