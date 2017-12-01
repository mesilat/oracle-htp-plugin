define('oracle-htp-plugin/api-test',['jquery','ajs'],function($,AJS){
    function initDAD($select){
        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/dad',
            dataType: 'json',
            context: $select
        }).done(function(data){
            var $select = $(this);
            var value = $select.attr('data-value');
            $select.empty();
            var dadNames = [];
            data.results.forEach(function(dad) {
                dadNames.push(dad.name);
            });
            dadNames.sort();
            for (var i = 0; i < dadNames.length; i++) {
                var $option = $('<option>').attr('value', dadNames[i]).text(dadNames[i]).appendTo($select);
                if (dadNames[i] === value){
                    $option.attr('selected','selected');
                }
            }
        });
    }

    function testInvoke(){
        if (!$("#pageid").val()) {
            alert(AJS.I18n.getText("oracle-htp-plugin.test.pageid.empty"));
            return;
        }

        var data = {
            dad: $('#dad').val(),
            pageId: $('#pageid').val(),
            macroKey: $('#macroKey').val(),
            dataType: 'json',
            params: {}
        };

        do {
            if ($('#param1').val()) {
                data.params['p1'] = $('#param1').val();
            }
            if ($('#param2').val()) {
                data.params['p2'] = $('#param2').val();
            }
            if ($('#param3').val()) {
                data.params['p3'] = $('#param3').val();
            }
            if ($('#param4').val()) {
                data.params['p4'] = $('#param4').val();
            }
            if ($('#param5').val()) {
                data.params['p5'] = $('#param5').val();
            }
            if ($('#param6').val()) {
                data.params['p6'] = $('#param6').val();
            }
            if ($('#param7').val()) {
                data.params['p7'] = $('#param7').val();
            }
            if ($('#param8').val()) {
                data.params['p8'] = $('#param8').val();
            }
            if ($('#param9').val()) {
                data.params['p9'] = $('#param9').val();
            }
            if ($('#param10').val()) {
                data.params['p10'] = $('#param10').val();
            }
        } while(false);

        $.ajax({
            url: AJS.contextPath() + "/rest/oracle-htp-api/1.0/plsql",
            type: "POST",
            contentType: "application/json",
            data: JSON.stringify(data),
            dataType: "json",
            processData: false,
            context: $("#test-result")
        }).done(function(data) {
            $(this)
                .html("<pre>" + JSON.stringify(data, null, 2) + "</pre>")
                .css( "display", "block")
                .removeClass()
                .addClass("aui-message aui-message-success");
        }).fail(function(jqxhr) {
            $(this)
                .html("<p>" + jqxhr.responseText + "</p>")
                .css( "display", "block")
                .removeClass()
                .addClass("aui-message aui-message-error");
        });
    }
    function init(){
        if ($('#test').length) {
            $('#test').submit(function(e) {
                e.preventDefault();
                testInvoke();
            });
            initDAD($('#dad'));
        }
    }

    return {
        init: init
    };
});

require(['oracle-htp-plugin/api-test'], function(Test){
    $(function(){
        Test.init();
    });
});