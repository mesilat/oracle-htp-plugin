define('oracle-htp-plugin/preview',['jquery','ajs','oracle-htp-plugin/util'],function($,AJS,Util){
    function init() {
        var $iframe = $(window.parent.document.getElementById('wysiwygTextarea_ifr'));
        var macroParams = $('#oracle-htp-plugin-preview').attr('oracle-htp-plugin-params');
        if (typeof macroParams !== 'undefined'){
            $.ajax({
                url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/decode',
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({
                    data: macroParams
                }),
                dataType: 'json',
                processData: false
            }).done(function(data){
                $iframe.prop('oracle-htp-macro-id', data.myid);
            }).fail(function(jqxhr){
                Util.logError(jqxhr.responseText);
            });
        }

        $('ul.oracle-htp-block-types a').click(function(e){
            e.preventDefault();

            var data = {
                body: $('#oracle-htp-plugin-body').text(),
                params: $('#oracle-htp-plugin-preview').attr('oracle-htp-plugin-params'),
                converter: $(e.target).attr('oracle-htp-plugin-template')
            };
            $.ajax({
                url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/convert',
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify(data),
                dataType: 'text',
                processData: false
            }).done(function(data){
                var $text = $('<textarea>').text(data);
                $('#oracle-htp-plugin-preview').html($text);
                $text.on('blur', function(e){
                    $iframe.prop('oracle-htp-converted-plsql', $(e.target).text());
                })
                .select();
            }).fail(function(jqxhr){
                Util.logError(jqxhr.responseText);
                $('#oracle-htp-plugin-preview')
                    .html($('<p>').text(jqxhr.responseText))
                    .addClass('aui-message aui-message-warning');
            });
        });
    };

    return {
        init: init
    };
});

require(['jquery','oracle-htp-plugin/preview'], function($,Preview){
    $(function(){
        Preview.init();
    });    
});