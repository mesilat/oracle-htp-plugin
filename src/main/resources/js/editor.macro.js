(function(AJS,$,Confluence){
    var cloneStyles = ['padding', 'padding-top', 'padding-bottom', 'padding-left', 'padding-right',
        'text-align', 'font', 'font-size', 'font-family', 'font-weight',
        'border', 'border-top', 'border-bottom', 'border-left', 'border-right'];
    var Util = require('oracle-htp-plugin/util');

    function addEditParamNameIcon($div){
        $('<span class="aui-icon aui-icon-small aui-iconfont-edit-small" title="Edit param description"></span>')
        .appendTo($div.find('label'))
        .on('click', function(e){
            e.preventDefault();

            $('div.tipsy').remove();
            var $label = $(e.target).closest('label');
            var $editor = $('<input type="text">');
            cloneStyles.forEach(function(prop){
                $editor.css($label.css(prop));
            });

            $editor.val($label.text())
                .appendTo($label.parent())
                .css('position', 'absolute')
                .offset($label.offset())
                .width($label.width())
                .height($label.height())
                .focus()
                .on('blur', function(e){
                    var text = $(e.target).val();
                    $label.text(text);
                    $editor.remove();
                    addEditParamNameIcon($div);
                    $.ajax({
                        url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/param-desc',
                        type: 'PUT',
                        contentType: 'application/json',
                        data: JSON.stringify({
                            macroId: $('#macro-param-myid').val(),
                            paramId: $div.attr('id').substr(16),
                            description: text
                        }),
                        processData: false,
                        dataType: 'json'
                    }).done(function(data){
                    }).fail(function(jqxhr){
                        alert(jqxhr.responseText);
                    });

                }).on('keydown', function(e){
                    if (e.which === 27){
                        e.stopPropagation();
                        e.preventDefault();
                        $editor.remove();
                    }
                });
        }).tooltip();
    }

    AJS.MacroBrowser.setMacroJsOverride('oracle-htp-macro', {
        fields: {
            'string': function(param,options) {
                if (param.name === 'dad') {
                    var paramDiv = $(Confluence.Templates.MacroBrowser.macroParameterSelect());
                    var select = $('select', paramDiv);
                    $.ajax({
                        url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/dad',
                        dataType: 'json',
                        context: select
                    }).done(function(data) {
                        // Reload select element with a list of DADs preserving current value
                        var select = $(this);
                        var value = select.val();
                        select.empty();
                        var dadNames = [];
                        data.results.forEach(function(dad) {
                            dadNames.push(dad.name);
                        });
                        dadNames.sort();
                        for (i = 0; i < dadNames.length; i++) {
                            select.append($('<option/>').attr('value', dadNames[i]).text(dadNames[i]));
                        }
                        try {
                            select.val(value);
                        } catch (err) {
                            Util.logError(err);
                        }
                    });

                    options = options || {};
                    options.setValue = options.setValue || function (value) {
                        // Beacause a list of DADs may not be loaded yet when a value is set
                        // we have to create a dummy option value and inform our user
                        if (!select.children('option').length) {
                            select
                                .append($('<option/>')
                                .attr('value', value)
                                .text(AJS.I18n.getText("com.mesilat.oracle-htp-plugin.oracle-htp-macro.param.dad.loading")));
                        }
                        try {
                            select.val(value);
                        } catch (err) {
                            Util.logError(err);
                        }
                        select.change();
                    };

                    return AJS.MacroBrowser.Field(paramDiv, select, options);
                } else {
                    var paramDiv = $(Confluence.Templates.MacroBrowser.macroParameter());
                    var input = $('input', paramDiv);
                    if (param.required) {
                        input.keyup(AJS.MacroBrowser.processRequiredParameters);
                    }
                    return AJS.MacroBrowser.Field(paramDiv, input, options);
                }
            }
        },
        beforeParamsSet: function (selectedParams, macroSelected) {
            if ('myid' in selectedParams){
                $.ajax({
                    url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/param-desc',
                    type: 'GET',
                    data: {
                        'macro-id': selectedParams.myid
                    },
                    dataType: 'json'
                }).done(function(data){
                    for (var paramId in data.results){
                        var $div = $('#macro-param-div-' + paramId);
                        $div.find('label').text(data.results[paramId].description);
                        addEditParamNameIcon($div);
                    }
                });
            } else {
                $('#macro-param-myid').val(Util.generateId());
            }

            addEditParamNameIcon($('#macro-param-div-p1'));
            addEditParamNameIcon($('#macro-param-div-p2'));
            addEditParamNameIcon($('#macro-param-div-p3'));
            addEditParamNameIcon($('#macro-param-div-p4'));
            addEditParamNameIcon($('#macro-param-div-p5'));
            addEditParamNameIcon($('#macro-param-div-p6'));
            addEditParamNameIcon($('#macro-param-div-p7'));
            addEditParamNameIcon($('#macro-param-div-p8'));
            addEditParamNameIcon($('#macro-param-div-p9'));
            addEditParamNameIcon($('#macro-param-div-p10'));
            
            return selectedParams;
        },
        manipulateMarkup: function(metadata){
            var $iframe = $(window.parent.document.getElementById('wysiwygTextarea_ifr'));
            var macroId = $iframe.prop('oracle-htp-macro-id');
            var plsqlText = $iframe.prop('oracle-htp-converted-plsql');
            setTimeout(function(){
                var $macro = $iframe.contents()
                .find('table[data-macro-name="oracle-htp-macro"]')
                .filter(function(){
                    return $(this).attr('data-macro-parameters').includes('myid=' + macroId);
                });
                $macro.find('td.wysiwyg-macro-body pre').text(plsqlText);
            }, 500);
        }
    });
})(AJS, AJS.$ || $,Confluence);