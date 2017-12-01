(function(AJS,$,Confluence){
    var Util = require('oracle-htp-plugin/util');
    AJS.MacroBrowser.setMacroJsOverride('dad-selector-macro', {
        fields: {
            'string': function(param,options){

                if (param.name === 'default') {
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
                            select.append($('<option/>').attr('value', value).text(AJS.I18n.getText("com.mesilat.oracle-htp-plugin.dad-selector-macro.param.default.loading")));
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
        }
    });
})(AJS, AJS.$ || $,Confluence);