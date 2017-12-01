define('oracle-htp-plugin/config-gelf',['jquery','ajs','oracle-htp-plugin/util'],function($,AJS,Util){
    var $TAB;

    function save($form){
        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/config/gelf',
            type: 'PUT',
            contentType: 'application/json',
            data: JSON.stringify(Util.formData($form)),
            processData: false,
            dataType: 'text',
            context: $form
        }).done(function(msg){
            Util.success(msg, $TAB.find('form div.aui-message'));
        }).fail(function(jqxhr) {
            Util.error(jqxhr.responseText, $TAB.find('form div.aui-message'));
        });
    };
    function read(){
        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/config/gelf',
            dataType: "json",
            context: $TAB.find('form')
        }).done(function(data){
            Util.putData($(this), data);
        }).fail(function(jqxhr) {
            Util.logError(jqxhr.responseText);
        });
    };

    function init($tab){
        $TAB = $tab;
        $tab.find('form').submit(function(e) {
            e.preventDefault();
            save($(e.target));
        });
        read();
    };

    return {
        init: init
    };
});

require(['jquery','oracle-htp-plugin/config-gelf'], function($,config){
    $(function(){
        $('#tab-ohp-3').each(function(){
            config.init($(this));
        });
    });
});