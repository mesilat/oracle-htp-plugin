package com.mesilat.ora;

import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import java.net.URI;
import java.sql.Connection;
import java.text.MessageFormat;
import java.util.ArrayList;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dad")
public class DadResource extends ResourceBase {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.oracle-htp-plugin");

    private final I18nResolver resolver;
    private final SettingsManager settingsManager;

    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(@Context HttpServletRequest request) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arr = mapper.createArrayNode();
        for (DataAccessDescriptor dad : DadCacheImpl.getInstance().get()){
            if (DadCacheImpl.isUserAuthorized(dad)){
                arr.add(toObject(mapper, dad));
            }
        }
        ObjectNode results = mapper.createObjectNode();
        results.put("results", arr);
        return Response.ok(results.toString()).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(final ObjectNode dad, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        if (!isUserAdmin(userKey)) {
            return Response
                .status(Response.Status.UNAUTHORIZED)
                .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.not-admin"))
                .build();
        }

        LOGGER.debug("Save oracle-htp-plugin Database Access Descriptor");
        try {
            DadCacheImpl.getInstance().put(dad);
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode results = mapper.createObjectNode();
            results.put("results", toObject(mapper, DadCacheImpl.getInstance().get(dad.get("name").asText())));
            return Response.ok(results.toString()).build();
        } catch(Throwable ex) {
            LOGGER.error(String.format("Failed to save DAD %s", dad), ex);
            return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
        }
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    public Response delete(final ObjectNode dad, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        if (!isUserAdmin(userKey)) {
            return Response
                .status(Response.Status.UNAUTHORIZED)
                .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.not-admin"))
                .build();
        }

        LOGGER.debug("Delete oracle-htp-plugin Database Access Descriptor");
        DadCacheImpl.getInstance().delete(dad.get("name").asText());
        return Response.ok().build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response post(final ObjectNode dad, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        if (!isUserAdmin(userKey)) {
            return Response
                .status(Response.Status.UNAUTHORIZED)
                .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.not-admin"))
                .build();
        }

        String action = request.getHeader("oracle-htp-action");
        if ("test".equalsIgnoreCase(action)) {
            LOGGER.debug("Test database connectivity with oracle-htp-plugin Database Access Descriptor");

            DataAccessDescriptor _dad = DadCacheImpl.getInstance().get(dad.get("name").asText());

            try (Connection conn = DelegatingConnectionCache.getConnection(_dad)) {
                return Response.ok(resolver.getText("oracle-htp-plugin.config.dad.test.success")).build();
            } catch(Exception ex) {
                return Response
                    .status(Response.Status.FORBIDDEN)
                    .entity(MessageFormat.format(resolver.getText("oracle-htp-plugin.config.dad.test.failure"), ex.getLocalizedMessage()))
                    .build();
            }
        } else {
            LOGGER.debug("Unknown action in POST-request: {}", action);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    public DadResource(UserManager userManager, I18nResolver resolver, SettingsManager settingsManager){
        super(userManager);

        this.resolver = resolver;
        this.settingsManager = settingsManager;
    }

    public ObjectNode toObject(ObjectMapper mapper, DataAccessDescriptor dad){
        ObjectNode obj = mapper.createObjectNode();
        obj.put("id", dad.getID());
        obj.put("name", dad.getName());
        obj.put("host", dad.getHost());
        obj.put("port", dad.getPort());
        obj.put("service", dad.getService());
        obj.put("username", dad.getUsername());
        obj.put("password", dad.getPassword());
        obj.put("maxConnections", dad.getMaxConnections() == null? DelegatingConnectionCache.DEFAULT_MAX_CONNECTIONS: dad.getMaxConnections());

        if (dad.getGrants() != null){
            ArrayList<String> grantees = new ArrayList<>();
            ObjectNode images = mapper.createObjectNode();
            for (DataAccessGrant grant : dad.getGrants()){
                grantees.add(grant.getGrantee());
                UserProfile userProfile = getUserManager().getUserProfile(grant.getGrantee());
                if (userProfile == null){
                    images.put(grant.getGrantee(), getBaseUrl() + "/images/icons/avatar_group_48.png");
                } else {
                    URI pictureUri = userProfile.getProfilePictureUri();
                    images.put(grant.getGrantee(), pictureUri == null? getBaseUrl() + "/images/icons/profilepics/default.png": pictureUri.toString());
                }
            }
            obj.put("grantees", StringUtils.join(grantees, ","));
            obj.put("images", images);
        } else {
            obj.put("grantees", "");
        }

        return obj;
    }
    public String getBaseUrl(){
        return settingsManager.getGlobalSettings().getBaseUrl();
    }
}