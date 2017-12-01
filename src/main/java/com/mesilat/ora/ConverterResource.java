package com.mesilat.ora;

import com.atlassian.confluence.pages.PageManager;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.mesilat.ora.converter.ConverterBase;
import com.mesilat.ora.converter.ConverterMap;
import java.io.IOException;
import java.sql.Connection;
import java.text.MessageFormat;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/convert")
public class ConverterResource extends ResourceBase {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.oracle-htp-plugin");

    private final I18nResolver resolver;

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response post(final ConverterCall converterCall, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
/*
        if (!isUserAdmin(userKey)) {
            return Response
                .status(Response.Status.UNAUTHORIZED)
                .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.not-admin"))
                .build();
        }
*/
        LOGGER.debug("Invoke converter");

        ConverterBase converter = ConverterMap.getConverter(converterCall.getConverter());
        if (converter == null) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(MessageFormat.format(resolver.getText("com.mesilat.oracle-htp-plugin.error.badConverter"), converterCall.getConverter()))
                .build();
        }
        
        Map params;
        try {
            params = (Map)(converterCall.getParams() == null? null: OracleHTPMacro.deserialize(converterCall.getParams()));
        } catch(IOException ex) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.badParams"))
                .build();
        } catch(ClassNotFoundException ex) {
            return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.badParams"))
                .build();
        }

        if (params == null || !params.containsKey("dad")) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(resolver.getText("com.mesilat.oracle-htp-plugin.error.noDad"))
                .build();
        }
        String dadName = params.get("dad").toString();
        DataAccessDescriptor dad = DadCacheImpl.getInstance().get(dadName);
        try (Connection conn = DelegatingConnectionCache.getConnection(dad)) {
            return Response
                .ok(converter.convert(conn, converterCall.getBody(), params, resolver))
                .build();
        } catch(Exception ex) {
            LOGGER.warn("Failed to convert SQL to PLSQL block", ex);
            return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ex.getLocalizedMessage())
                .build();
        }
    }

    public ConverterResource(UserManager userManager, PageManager pageManager, I18nResolver resolver) {
        super(userManager);

        this.resolver = resolver;
    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ConverterCall {
        @XmlElement
        private String body;
        @XmlElement
        private String params;
        @XmlElement
        private String converter;

        public String getBody() {
            return body;
        }
        public void setBody(String body) {
            this.body = body;
        }
        public String getParams() {
            return params;
        }
        public void setParams(String params) {
            this.params = params;
        }
        public String getConverter() {
            return converter;
        }
        public void setConverter(String converter) {
            this.converter = converter;
        }
    }
}