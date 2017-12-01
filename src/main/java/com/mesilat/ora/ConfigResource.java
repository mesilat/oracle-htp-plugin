package com.mesilat.ora;

import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.mesilat.ora.GelfLogSender.LEVEL;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/config")
public class ConfigResource extends ResourceBase {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.oracle-htp-plugin");

    private final PluginSettingsFactory pluginSettingsFactory;
    private final TransactionTemplate transactionTemplate;
    private final I18nResolver resolver;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        if (!isUserAdmin(userKey)) {
            return Response
                .status(Status.UNAUTHORIZED)
                .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.not-admin"))
                .build();
        }
        return Response.ok(transactionTemplate.execute(() -> {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            return new Config(settings);
        })).build();
    }
    @Path("/gelf")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getGelf(@Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        if (!isUserAdmin(userKey)) {
            return Response
                .status(Status.UNAUTHORIZED)
                .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.not-admin"))
                .build();
        }
        return Response.ok(transactionTemplate.execute(() -> {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            return new GelfConfig(settings);
        })).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(final Config config, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        if (!isUserAdmin(userKey)) {
            return Response
                .status(Status.UNAUTHORIZED)
                .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.not-admin"))
                .build();
        }
        return Response.ok(transactionTemplate.execute(() -> {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            config.save(settings);
            DelegatingConnectionCache cache = DelegatingConnectionCache.getInstance();
            if (cache != null) {
                cache.setDriverPath(config.getDriverPath());
            }
            return resolver.getText("oracle-htp-plugin.config.save.success");
        })
        ).build();
    }
    @Path("/gelf")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response putGelf(final GelfConfig config, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        if (!isUserAdmin(userKey)) {
            return Response
                .status(Status.UNAUTHORIZED)
                .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.not-admin"))
                .build();
        }
        return transactionTemplate.execute(() -> {
            try {
                GelfLogSender.send(config, AuthenticatedUserThreadLocal.get().getName(), LEVEL.DEBUG, "Test message", "This message is to test Gelf feature of Orale HTP Plugin for Confluence", null, null, null, null, null);
            } catch(IOException | NumberFormatException ex){
                return Response.status(Status.BAD_REQUEST).entity(ex.getMessage()).build();
            }
            config.save(pluginSettingsFactory.createGlobalSettings());
            GelfLogSender.init(config);
            return Response.ok(resolver.getText("oracle-htp-plugin.config.gelf.success")).build();
        });
    }

    public ConfigResource(UserManager userManager, PluginSettingsFactory pluginSettingsFactory,
            TransactionTemplate transactionTemplate, I18nResolver resolver) {
        super(userManager);

        this.pluginSettingsFactory = pluginSettingsFactory;
        this.transactionTemplate = transactionTemplate;
        this.resolver = resolver;
    }
}