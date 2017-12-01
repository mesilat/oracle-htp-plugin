package com.mesilat.ora;

import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.user.UserManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/decode")
public class MacroParamsDecodeResource extends ResourceBase {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.oracle-htp-plugin");

    @POST
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response post(final ObjectNode data, @Context HttpServletRequest request){
        String base64 = data.get("data").asText();
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(base64)))) {
            Map map = (Map)in.readObject();
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode node = mapper.createObjectNode();
            for (Object key : map.keySet()){
                node.put(key.toString(), map.get(key).toString());
            }
            return Response.ok(node.toString()).build();
        } catch (ClassNotFoundException | IOException ex) {
            LOGGER.warn("Failed to decode Java object", ex);
            return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
        }
    }

    public MacroParamsDecodeResource(UserManager userManager, PageManager pageManager,
            PluginSettingsFactory pluginSettingsFactory, I18nResolver resolver,
            PermissionManager permissionManager) {
        super(userManager);
    }
}
