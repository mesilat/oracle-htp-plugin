package com.mesilat.ora;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.java.ao.DBParam;
import net.java.ao.Query;
import org.codehaus.jackson.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class DadCacheImpl implements InitializingBean, DisposableBean, DadCache, Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger("oracle-htp-plugin");
    private static final String CONFLUENCE_ANONYMOUS = "confluence-anonymous";
    private static DadCacheImpl instance;

    private final PluginSettingsFactory pluginSettingsFactory;
    private final ActiveObjects ao;
    private final UserManager userManager;

    private Thread thread;

    @Override
    public void afterPropertiesSet() throws Exception {
        instance = this;
        thread = new Thread(this);
        thread.start();
    }
    @Override
    public void destroy() throws Exception {
        instance = null;
    }
    @Override
    public DataAccessDescriptor[] get() {
        return ao.executeInTransaction(()->{
            return ao.find(DataAccessDescriptor.class);
        });
    }
    @Override
    public DataAccessDescriptor get(String name) {
        return ao.executeInTransaction(()->{
            DataAccessDescriptor[] dads = ao.find(DataAccessDescriptor.class, "NAME = ?", name);
            if (dads.length > 0){
                return dads[0];
            } else {
                return null;
            }
        });
    }
    @Override
    public List<String> getNames() {
        return ao.executeInTransaction(()->{
            List<String> names = new ArrayList<>();
            for (DataAccessDescriptor dad : ao.find(DataAccessDescriptor.class, Query.select("ID,NAME"))){
                names.add(dad.getName());
            }
            return names;
        });
    }
    @Override
    public void put(ObjectNode dad) {
        ao.executeInTransaction(()->{
            DataAccessDescriptor _dad;

            if (dad.get("id") == null || dad.get("id").asText().isEmpty()){
                Map<String,Object> map = new HashMap<>();
                _dad = ao.create(DataAccessDescriptor.class, new DBParam("NAME", dad.get("name").asText()));
                _dad.setHost(dad.get("host").asText());
                _dad.setPort(dad.get("port").asText());
                _dad.setService(dad.get("service").asText());
                _dad.setUsername(dad.get("username").asText());
                _dad.setPassword(obfuscate(dad.get("password").asText()));
            } else {
                _dad = ao.get(DataAccessDescriptor.class, dad.get("id").asInt());
                _dad.setName(dad.get("name").asText());
                _dad.setHost(dad.get("host").asText());
                _dad.setPort(dad.get("port").asText());
                _dad.setService(dad.get("service").asText());
                _dad.setUsername(dad.get("username").asText());
                _dad.setPassword(obfuscate(dad.get("password").asText()));
            }
            // TODO: test if connection is valid
            _dad.save();

            if (_dad.getGrants() != null){
                Arrays.asList(_dad.getGrants()).forEach((grant)->{
                    ao.delete(grant);
                });
            }

            if (dad.get("grantees") != null && !dad.get("grantees").asText().isEmpty()){
                for (String grantee: dad.get("grantees").asText().split(",")){
                    DataAccessGrant grant = ao.create(DataAccessGrant.class);
                    grant.setDataAccessDescriptor(_dad);
                    grant.setGrantee(grantee);
                    grant.save();
                }
            }

            return null;
        });
    }
    @Override
    public void delete(String name) {
        ao.executeInTransaction(()->{
            DataAccessDescriptor[] dads = ao.find(DataAccessDescriptor.class, "NAME = ?", name);
            for (DataAccessDescriptor dad : dads){
                if (dad.getGrants() != null && dad.getGrants().length > 0){
                    for (DataAccessGrant grant : dad.getGrants()){
                        ao.delete(grant);
                    }
                }
                ao.delete(dad);
            }
            return null;
        });
    }
    @Override
    public void run() {
        while (true){
            try {
                PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
                Object o = settings.get(DadCacheImpl.class.toString());
                if (o == null){
                    // Import settings from plugin settings
                    List<String> list = (List<String>)settings.get(ConfigResource.class.getName() + ".dads");
                    if (list != null){
                        ao.executeInTransaction(()->{
                            for (String text : list){
                                String[] s = text.split("\\t");
                                DataAccessDescriptor dad = ao.create(DataAccessDescriptor.class, new DBParam("NAME", s[0]));
                                //dad.setName(s[0]);
                                dad.setHost(s[1]);
                                dad.setPort(s[2]);
                                dad.setService(s[3]);
                                dad.setUsername(s[4]);
                                dad.setPassword(s[5]);
                                try {
                                    dad.setMaxConnections(s.length > 6? Integer.parseInt(s[6]): DelegatingConnectionCache.DEFAULT_MAX_CONNECTIONS);
                                } catch(NumberFormatException ignore) {
                                    dad.setMaxConnections(DelegatingConnectionCache.DEFAULT_MAX_CONNECTIONS);
                                }
                                dad.save();
                            }
                            settings.put(DadCacheImpl.class.toString(), Boolean.TRUE.toString());
                            //settings.remove(ConfigResource.class.getName() + ".dads");
                            LOGGER.debug("DAD storage upgraded");
                            return null;
                        });
                    }
                }
                return;
            } catch(IllegalStateException ignore){
                try {
                    Thread.sleep(1000); // Wait for AO to initialize...
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }        
    }

    public DadCacheImpl(final PluginSettingsFactory pluginSettingsFactory,
        final ActiveObjects ao, final UserManager userManager){
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.ao = ao;
        this.userManager = userManager;
    }

    public static DadCache getInstance(){
        return instance;
    }
    public static boolean isUserAuthorized(DataAccessDescriptor dad){
        return instance == null? false: instance._isUserAuthorized(dad);
    }
    private boolean _isUserAuthorized(DataAccessDescriptor dad){
        ConfluenceUser user = AuthenticatedUserThreadLocal.get();
        if (user == null){
            for (DataAccessGrant grant : dad.getGrants()){
                if (grant.getGrantee().equals(CONFLUENCE_ANONYMOUS)){
                    return true;
                }
            }
            return false;
        }

        UserKey userKey = user.getKey();
        String userName = user.getName();
        if (userManager.isAdmin(userKey)) {
            return true;
        }
        if (userManager.isUserInGroup(userKey, "oracle-dba")){
            return true;
        }
        if (dad.getGrants() != null){
            for (DataAccessGrant grant : dad.getGrants()){
                if (grant.getGrantee().equals(userName)){
                    return true;
                }
                if (userManager.isUserInGroup(userKey, grant.getGrantee())){
                    return true;
                }
            }
        }
        return false;
    }
    private static boolean isPasswordObfuscated(String password) {
        try {
            PasswordEncryption1.unscramble(password);
            return true;
        } catch(Exception ignore) {
            return false;
        }
    }
    private static String obfuscate(String password){
        try {
            if (!isPasswordObfuscated(password)){
                return PasswordEncryption1.scramble(password);
            } else {
                return password;
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    private static String unobfuscate(String password){
        try {
            return PasswordEncryption1.unscramble(password);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}