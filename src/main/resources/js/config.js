define('oracle-htp-plugin/config',['jquery','ajs','oracle-htp-plugin/util'],function($,AJS,Util){
    var $TAB;

    function read() {
        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/config',
            dataType: 'json',
            context: $TAB.find('form')
        }).done(function(data) {
            $('#catalina-home').text(data.catalinaBase);
            Util.putData(this, data)
        }).fail(function(jqxhr){
            Util.error(jqxhr.responseText, $TAB.find('form div.aui-message'));
        });
    };

    function save() {
        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/config',
            type: 'PUT',
            contentType: 'application/json',
            data: JSON.stringify(Util.formData($TAB.find('form'))),
            processData: false,
            dataType: 'text'
        }).done(function(msg) {
            Util.success(msg, $TAB.find('form div.aui-message'));
        }).fail(function(jqxhr) {
            Util.error(jqxhr.responseText, $TAB.find('form div.aui-message'));
        });
    };

    function init($tab){
        $TAB = $tab;
        $TAB.find('form').submit(function(e) {
            e.preventDefault();
            save();
        });
        read();
    };

    return {
        init: init
    };
});

require(['oracle-htp-plugin/config'], function(config){
    $(function(){
        $('#tab-ohp-1').each(function(){
            config.init($(this));
        });
    });
});