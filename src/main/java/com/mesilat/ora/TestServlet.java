package com.mesilat.ora;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.templaterenderer.TemplateRenderer;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TestServlet extends HttpServlet {
    private final UserManager userManager;
    private final LoginUriProvider loginUriProvider;
    private final TemplateRenderer renderer;
    private final PageManager pageManager;
    private final PermissionManager permissionManager;
    private final ActiveObjects ao;
  
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        UserKey userKey = userManager.getRemoteUserKey(request);
        if (userKey == null) {
            redirectToLogin(request, response);
            return;
        }

        Map<String,Object> context = new HashMap<>();
        context.put("pageid", request.getParameter("pageid"));
        Page page = pageManager.getPage(Long.parseLong(request.getParameter("pageid")));
        if (!isUserAuthorized(page)) {
            context.put("notAuthorized", Boolean.TRUE);
        }
        context.put("dad", request.getParameter("dad"));
        context.put("pageid", request.getParameter("pageid"));
        context.put("macroKey", request.getParameter("macroKey"));
        Map<String,ParameterDescription> params = new HashMap<>();
        Arrays.asList(ao.executeInTransaction(()->{
            return ao.find(ParameterDescription.class, "MACRO_ID = ?", request.getParameter("macroKey"));
        })).forEach((paramDesc)->{
            params.put(paramDesc.getParamId(), paramDesc);
        });
        context.put("params", params);
        response.setContentType("text/html;charset=utf-8");
        renderer.render("/templates/test.vm", context, response.getWriter());
    }
    private void redirectToLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(loginUriProvider.getLoginUri(getUri(request)).toASCIIString());
    }
    private URI getUri(HttpServletRequest request) {
        StringBuffer builder = request.getRequestURL();
        if (request.getQueryString() != null)
        {
            builder.append("?");
            builder.append(request.getQueryString());
        }
        return URI.create(builder.toString());
    }
    
    public TestServlet(UserManager userManager, LoginUriProvider loginUriProvider,
            TemplateRenderer renderer, PageManager pageManager, PermissionManager permissionManager,
            ActiveObjects ao) {
        this.userManager = userManager;
        this.loginUriProvider = loginUriProvider;
        this.renderer = renderer;
        this.pageManager = pageManager;
        this.permissionManager = permissionManager;
        this.ao = ao;
    }

    public boolean isUserAuthorized(Page page) {
        return permissionManager.hasPermission(AuthenticatedUserThreadLocal.get(), Permission.VIEW, page);
    }
}