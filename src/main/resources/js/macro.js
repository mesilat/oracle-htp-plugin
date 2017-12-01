define('oracle-htp-plugin/macro', [
    'jquery',
    'ajs',
    'oracle-htp-plugin/d3',
    'oracle-htp-plugin/c3',
    'oracle-htp-plugin/util',
    'oracle-htp-plugin/macro-processing'
], function($,AJS,d3,c3,Util,Proc) {
    var FORMAT_DATE = d3.time.format("%d-%m-%Y %H:%M:%S");
    function parseDate(val){
        return FORMAT_DATE.parse(val);
    };

    // Lazy
    var lazy = {};
    function invokeLazy($macro, data) {
        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/plsql',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(data),
            dataType: 'html',
            processData: false,
            context: {
                $macro: $macro,
                data: data
            },
            beforeSend: function() {
                $macro.html(Mesilat.Templates.Oracle.macroUpdating({})).show().find('span').spin();
            }
        }).done(function(html){
            this.$macro.find('span').spinStop();
            this.$macro.prop('oracle-htp-data', this.data);
            this.$macro.html(html);

            Proc.updateHref(this.$macro);
            Proc.updateExpandCollapse(this.$macro);
            Proc.updateColumnLinks(this.$macro);
            Proc.updateEditForm(this.$macro);
            Proc.updateEditOptions(this.$macro);
            Proc.updateDateFields(this.$macro);
            Proc.updateSortableTable(this.$macro);

            this.$macro.find('form.ohp-edit-form').each(function(){
                var $template = $(Mesilat.Templates.Oracle.editFormButtons({}));
                $template.find('input[type="submit"]').on('click', function(e){
                    e.preventDefault();
                    var $form = $(e.target).closest('form');
                    var fields = {};
                    $form.find('input[name]').each(function(){
                        var $input = $(this);
                        fields[$input.attr('name')] = $input.val();
                    });
                    data.formFields = fields;
                    invokeLazy($(this).closest('div.conf-macro'), data);
                });
                $template.find('a.back').on('click', function(e){
                    e.preventDefault();
                    window.history.back();
                });
                $(this).append($template);
            });
        }).fail(function(jqxhr) {
            Util.logError(jqxhr.responseText);
            this.$macro.find('span').spinStop();
            this.$macro.html(Mesilat.Templates.Oracle.errorInMacro({
                text: jqxhr.responseText
            }));
        });
    };
    function execLazy($macro, pageId, macroKey, params) {
        var data = {
            pageId: pageId,
            macroKey: macroKey,
            dataType: 'html'
        };
        Util.processParams(data, params);
        // if there's a DAD selector on page then we do not actually invoke,
        // but wait to be invoked by selector
        if ($('.ohp-dad-selector').length === 0) {
            invokeLazy($macro, data);
        }
        lazy[macroKey] = data;
    };

    // Charts
    var c3js = {};
    function invokeC3JS($macro, data) {
        var dad = (data.dad)? data.dad: $macro.attr('dad');
        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/plsql',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify(data),
            dataType: 'json',
            processData: false,
            context: {
                $macro: $macro,
                data: data
            },
            beforeSend: function(){
                $macro.html(Mesilat.Templates.Oracle.macroUpdating({})).show().find('span').spin();
            }
        }).done(function(data) {
            console.log('OHP', data);
            this.$macro.find('span').spinStop();
            this.$macro.prop('oracle-htp-data', this.data);

            var macroId = this.$macro.attr('id');
            if (typeof macroId === 'undefined'){
                macroId = 'AUTO_ID_' + Util.generateId();
                this.$macro.attr('id', macroId);
            }
            data.bindto = '#' + macroId;
            if (data.data && data.data.columns) {
                data.data.columns.forEach(function(column) {
                    for (var i = 0; i < column.length; i++) {
                        try {
                            var date = parseDate(column[i]);
                            if (date !== null) {
                                column[i] = date;
                            }
                        } catch(e) {
                        }
                    }
                });
            }
            if (data.data && data.data.rows) {
                data.data.rows.forEach(function(row) {
                    for (var i = 0; i < row.length; i++) {
                        try {
                            var date = parseDate(row[i]);
                            if (date !== null) {
                                row[i] = date;
                            }
                        } catch(e) {
                        }
                    }
                });
            }
            if (data.axis && data.axis.x && data.axis.x.tick && data.axis.x.tick.format) {
                if (data.axis.x.tick.format.startsWith('^')) {
                    data.axis.x.tick.format = d3.format(data.axis.x.tick.format.substring(1));
                }
            }
            if (data.axis && data.axis.y && data.axis.y.tick && data.axis.y.tick.format) {
                if (data.axis.y.tick.format.startsWith('^')) {
                    data.axis.y.tick.format = d3.format(data.axis.y.tick.format.substring(1));
                }
            }

            this.$macro.prop('oracle-htp-c3js-data', data);
            c3.generate(data);

            if (data.axis && data.axis.x && data.axis.x['x-hide']) {
                $(this).find('.c3-axis-x > path.domain').hide();
                $(this).find('.tick > line[x2="-6"]').hide();
            }
            if (data.axis && data.axis.y && data.axis.y['x-hide']) {
                $(this).find('.c3-axis-y > path.domain').hide();
                $(this).find('.tick > line[y2="6"]').hide();
            }
            Proc.updateChartLinks(this.$macro);
        }).fail(function(jqxhr) {
            $macro.find('span').spinStop();
            var p = $('<p>').text(jqxhr.responseText);
            $macro.html($('<div class="aui-message aui-message-error">').append(p));
        });
    };
    function execC3JS($macro, pageId, macroKey, params){
        var data = {
            pageId: pageId,
            macroKey: macroKey,
            dataType: 'html'
        };
        Util.processParams(data, params);
        if ($('.ohp-dad-selector').length === 0) {
            invokeC3JS($macro, data);
        }
        c3js[macroKey] = data;
    };

    var o = {};
    o.lazy = lazy;
    o.c3js = c3js;
    o.invokeLazy = invokeLazy;
    o.execLazy = execLazy;
    o.invokeC3JS = invokeC3JS;
    o.execC3JS = execC3JS;
    return o;
});

require([
    'ajs',
    'jquery',
    'oracle-htp-plugin/util',
    'oracle-htp-plugin/macro',
    'oracle-htp-plugin/macro-sidebar',
    'oracle-htp-plugin/macro-processing'
], function(AJS,$,Util,Macro,Sidebar,Proc){

    function refreshLazy($macro,period){
        console.log('oracle-htp-macro', 'Refresh lazy');
        Macro.execLazy($macro, AJS.params.pageId, $macro.attr('macro-key'), $macro[0]._ohp_params);
        setTimeout(refreshLazy, period, $macro, period);
    }
    function refreshC3JS($macro,period){
        console.log('oracle-htp-macro', 'Refresh C3JS');
        Macro.execC3JS($macro, AJS.params.pageId, $macro.attr('macro-key'), $macro[0]._ohp_params);
        setTimeout(refreshC3JS, period, $macro, period);
    }

    $(function(){
        $('div.conf-macro.oracle-htp-macro-display-inline').each(function(){
            this.style.display = 'inline-block';
        });

        $('div.conf-macro.oracle-htp-macro-lazy').each(function(){
            var $macro = $(this);
            this._ohp_params = JSON.parse($macro.text());
            Macro.execLazy($macro, AJS.params.pageId, $macro.attr('macro-key'), this._ohp_params);
        });

        $('div.conf-macro.oracle-htp-macro-c3js').each(function(){
            var $macro = $(this);
            this._ohp_params = JSON.parse($macro.text());
            Macro.execC3JS($macro, AJS.params.pageId, $macro.attr('macro-key'), this._ohp_params);
        });

        $('div.conf-macro.oracle-htp-macro-inline,div.conf-macro.oracle-htp-macro-lazy,div.conf-macro.oracle-htp-macro-c3js').each(function(){
            var $macro = $(this);
            if ($macro.hasClass('oracle-htp-macro-sidebar')){
                $macro.mouseenter(function(){
                    if ($macro.hasClass('oracle-htp-macro-lazy')){
                        $macro.prop('oracle-htp-sidebar-menu', Sidebar.createSideBar($macro, Macro.invokeLazy));
                    } else if ($macro.hasClass('oracle-htp-macro-c3js')){
                        $macro.prop('oracle-htp-sidebar-menu', Sidebar.createSideBar($macro, Macro.invokeC3JS));
                    } else if ($macro.hasClass('oracle-htp-macro-inline')){
                        $macro.prop('oracle-htp-sidebar-menu', Sidebar.createSideBar($macro));
                    }
                }).mouseleave(function(){
                    var menu = $macro.prop('oracle-htp-sidebar-menu');
                    if (typeof menu !== 'undefined'){
                        menu.timeout = setTimeout(function(){
                            menu.$menu.remove();
                        }, 300);
                    }
                });
            }
        });
        
        $('div.conf-macro.oracle-htp-macro-links').each(function(){
            Proc.updateColumnLinks($(this));
        });

        function refreshMacrosOnPage(dad){
            $('div.conf-macro[data-macro-name="oracle-htp-macro"]').each(function(){
                var data = Macro.lazy[$(this).attr('macro-key')];
                if (typeof data !== 'undefined') {
                    data.dad = dad;
                    Macro.invokeLazy($(this), data);
                    return;
                }
                data = Macro.c3js[$(this).attr('macro-key')];
                if (typeof data !== 'undefined') {
                    data.dad = dad;
                    Macro.invokeC3JS($(this), data);
                    return;
                }
            });
        }

        $('.ohp-dad-selector').each(function(){
            $(this).auiSelect2();
            $(this).on('change', function(e){
                refreshMacrosOnPage($(e.target).val());
            });

            if ('c3js-point' in Util.urlParams && typeof Util.urlParams['c3js-point'].dad !== 'undefined') {
                var dad = Util.urlParams['c3js-point'].dad;
                if (dad !== $(this).val()) {
                    $(this).val(dad);
                }
            } else if ('dad' in Util.urlParams){
                $(this).val(Util.urlParams.dad);
            }

            $(this).trigger('change');
        });

        $('.ohp-dad-refresh').on('click', function(e){
            refreshMacrosOnPage($(e.target).closest('div').find('select').val());
        });

        $('div.conf-macro.oracle-htp-macro-lazy').each(function(){
            var $macro = $(this);
            var autorefresh = $macro.attr('oracle-htp-autorefresh');
            if (typeof autorefresh !== 'undefined'){
                setTimeout(refreshLazy, 1000 * parseInt(autorefresh), $macro, 1000 * parseInt(autorefresh));
            }
        });
        $('div.conf-macro.oracle-htp-macro-c3js').each(function(){
            var $macro = $(this);
            var autorefresh = $macro.attr('oracle-htp-autorefresh');
            if (typeof autorefresh !== 'undefined'){
                setTimeout(refreshC3JS, 1000 * parseInt(autorefresh), $macro, 1000 * parseInt(autorefresh));
            }
        });
    });
});

(function(AJS,$){
    AJS.bind("init.rte", function() {
        var onPaste = tinymce.activeEditor.plugins.confluencepaste || tinymce.activeEditor.plugins.aePaste;
        var Util = require('oracle-htp-plugin/util');
        onPaste.onPreProcess.listeners.push({
            cb: function(ed,obj){
                var elements = $('<div>').append(obj.content);
                elements.find('table[data-macro-name="oracle-htp-macro"]').each(function() {
                    var $macro = $(this);
                    var macroParams = Confluence.MacroParameterSerializer.deserialize($macro.data('macro-parameters'));
                    macroParams.myid = Util.generateId();
                    $macro.attr('macro-key', macroParams.myid);
                    $macro.attr('data-macro-parameters', Confluence.MacroParameterSerializer.serialize(macroParams));
                });
                obj.content = elements.html();
            }
        });
    });
})(AJS, AJS.$||$);