package com.mesilat.ora;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;
import com.atlassian.sal.api.user.UserManager;
import java.util.Arrays;
import java.util.Collection;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/param-desc")
public class ParameterDescriptionResource extends ResourceBase {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.oracle-htp-plugin");

    private final ActiveObjects ao;

    @AnonymousAllowed
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(final @QueryParam("macro-id") String macroId, @Context HttpServletRequest request) {
        final ObjectMapper mapper = new ObjectMapper();
        ObjectNode obj = toObject(mapper, ao.executeInTransaction(() -> {
            return Arrays.asList(ao.find(ParameterDescription.class, "MACRO_ID = ?", macroId));
        }));
        ObjectNode results = mapper.createObjectNode();
        results.put("results", obj);
        return Response.ok(results.toString()).build();
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response put(final ObjectNode node, @Context HttpServletRequest request) {
        final ObjectMapper mapper = new ObjectMapper();
        try {
            ParameterDescription paramDesc = ao.executeInTransaction(() -> {
                ParameterDescription[] _paramDescs = ao.find(
                    ParameterDescription.class, "MACRO_ID = ? AND PARAM_ID = ?",
                    node.get("macroId").asText(), node.get("paramId").asText()
                );
                if (_paramDescs.length == 0){
                    ParameterDescription _paramDesc = ao.create(ParameterDescription.class);
                    _paramDesc.setMacroId(node.get("macroId").asText());
                    _paramDesc.setParamId(node.get("paramId").asText());
                    _paramDesc.setDescription(node.get("description").asText());
                    _paramDesc.save();
                    return _paramDesc;
                } else {
                    ParameterDescription _paramDesc = _paramDescs[0];
                    _paramDesc.setDescription(node.get("description").asText());
                    _paramDesc.save();
                    return _paramDesc;
                }
            });
            ObjectNode results = mapper.createObjectNode();
            results.put("results", toObject(mapper, paramDesc));
            return Response.ok(results.toString()).build();
        } catch(RuntimeException ex){
            return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage() == null? "Invalid data provided": ex.getMessage()).build();
        }
    }

    public ObjectNode toObject(ObjectMapper mapper, ParameterDescription paramDescription){
        ObjectNode obj = mapper.createObjectNode();
        obj.put("id", paramDescription.getID());
        obj.put("macroId", paramDescription.getMacroId());
        obj.put("paramId", paramDescription.getParamId());
        obj.put("description", paramDescription.getDescription());
        return obj;
    }
    public ObjectNode toObject(ObjectMapper mapper, Collection<ParameterDescription> paramDescriptions){
        ObjectNode obj = mapper.createObjectNode();
        for (ParameterDescription paramDescription : paramDescriptions){
            obj.put(paramDescription.getParamId(), toObject(mapper, paramDescription));
        }
        return obj;
    }

    public ParameterDescriptionResource(UserManager userManager, ActiveObjects ao){
        super(userManager);
        this.ao = ao;
    }
}