define('oracle-htp-plugin/macro-sidebar', ['jquery','ajs','oracle-htp-plugin/util'], function($,AJS,Util){
    function objectHasAnyProperty(obj){
        for (var key in obj){
            return true;
        }
        return false;
    }
    function createDefaultParams(macroKey){
        return {
            'p1': {
                macroKey: macroKey,
                paramId: 'p1',
                description: AJS.I18n.getText("com.mesilat.oracle-htp-plugin.oracle-htp-macro.param.p1.label")
            },
            'p2': {
                macroKey: macroKey,
                paramId: 'p2',
                description: AJS.I18n.getText("com.mesilat.oracle-htp-plugin.oracle-htp-macro.param.p2.label")
            },
            'p3': {
                macroKey: macroKey,
                paramId: 'p3',
                description: AJS.I18n.getText("com.mesilat.oracle-htp-plugin.oracle-htp-macro.param.p3.label")
            },
            'p4': {
                macroKey: macroKey,
                paramId: 'p4',
                description: AJS.I18n.getText("com.mesilat.oracle-htp-plugin.oracle-htp-macro.param.p4.label")
            },
            'p5': {
                macroKey: macroKey,
                paramId: 'p5',
                description: AJS.I18n.getText("com.mesilat.oracle-htp-plugin.oracle-htp-macro.param.p5.label")
            },
            'p6': {
                macroKey: macroKey,
                paramId: 'p6',
                description: AJS.I18n.getText("com.mesilat.oracle-htp-plugin.oracle-htp-macro.param.p6.label")
            },
            'p7': {
                macroKey: macroKey,
                paramId: 'p7',
                description: AJS.I18n.getText("com.mesilat.oracle-htp-plugin.oracle-htp-macro.param.p7.label")
            },
            'p8': {
                macroKey: macroKey,
                paramId: 'p8',
                description: AJS.I18n.getText("com.mesilat.oracle-htp-plugin.oracle-htp-macro.param.p8.label")
            },
            'p9': {
                macroKey: macroKey,
                paramId: 'p9',
                description: AJS.I18n.getText("com.mesilat.oracle-htp-plugin.oracle-htp-macro.param.p9.label")
            },
            'p10': {
                macroKey: macroKey,
                paramId: 'p10',
                description: AJS.I18n.getText("com.mesilat.oracle-htp-plugin.oracle-htp-macro.param.p10.label")
            }
        };
    }
    function showLinksDialog($macro){
        function initSelectPageControl($elt, rec){
            $elt.auiSelect2({
                ajax: {
                    url: AJS.contextPath() + "/rest/prototype/1/search.json",
                    type: 'GET',
                    dataType: 'json',
                    delay: 250,
                    data: function(term){
                        return {
                            'max-results': 10,
                            'query': term,
                            'search': 'name',
                            'type': 'content',
                            'preferredSpaceKey': AJS.Meta.get('space-key')
                        };
                    },
                    results: function(data) {
                        var results = [];
                        data.group.filter(function(group){
                            return group.name === 'content';
                        })[0].result.forEach(function(result){
                            results.push({
                                id: result.id,
                                text: result.title
                            });
                        });
                        return {
                            results: results
                        };
                    }
                },
                escapeMarkup: function(markup){
                    return markup;
                },
                minimumInputLength: 2
            });
            if ('pageId' in rec){
                $elt
                    .val(rec.pageId)
                    .closest('td').find('span.select2-chosen').text(rec.pageTitle);
            }
        }
        function initDeleteLink($elt){
            $elt.find('span.aui-iconfont-remove-label').on('click', function(e){
                e.preventDefault();
                $(e.target).closest('tr').remove();
            });
        }
        function showSetupLinksDialog(data){
            // Pick up column names
            // Table or view form?
            var columnNames = [], columnKeys = {};
            $macro.find('td').each(function(){
                $.each(this.className.split(/\s+/), function(i,className){
                    if (className.substring(0,9) === 'ohp-data-'){
                        var columnName = className.substring(9);
                        if (!(columnName in columnKeys)){
                            columnKeys[columnName] = true;
                        }
                    }
                });
            });
            // Edit form?
            if (!objectHasAnyProperty(columnKeys)){
                $macro.find('form input').each(function(){
                    var columnName = $(this).attr('name');
                    if (typeof columnName !== 'undefined' && !(columnName in columnKeys)){
                        columnKeys[columnName] = true;
                    }
                });
            }
            // C3js Chart?
            if (!objectHasAnyProperty(columnKeys)){
                var c3js = $macro.prop('oracle-htp-c3js-data');
                if (typeof c3js !== 'undefined'){
                    if (c3js.data.type === 'bar' || c3js.data.type === 'line'){
                        for (var i = 1; i < c3js.data.rows[0].length; i++){
                            columnKeys[c3js.data.rows[0][i]] = true;
                        }
                    } else if (c3js.data.type === 'pie') {
                        columnKeys['_DATA_'] = true;
                    }
                }
            }
            
            for (var columnName in columnKeys){
                columnNames.push(columnName);
            }

            var recs = data.results.length === 0? [{}]: data.results; 
            var $dlg = $(Mesilat.Templates.Oracle.setupLinksDialog({
                recs: recs,
                columnNames: columnNames
            }));
            var $tr = $dlg.find('tbody tr');
            for (var i = 0; i < $tr.length; i++){
                $($tr[i]).prop('data', recs[i]);
                initSelectPageControl($($tr[i]).find('td.oracle-htp-links-url input'), recs[i]);
                initDeleteLink($($tr[i]));
            }
            var dlg = AJS.dialog2($dlg);
            $dlg.find('button.addnew').on('click', function(e){
                e.preventDefault();
                var rec = {};
                $tr = $(Mesilat.Templates.Oracle.columnLink({
                    rec: rec,
                    columnNames: columnNames
                }));
                $tr.prop('data', rec);
                initSelectPageControl($tr.find('td.oracle-htp-links-url input'), rec);
                initDeleteLink($tr);
                $dlg.find('table tbody').append($tr);
            });
            $dlg.find('button.save').on('click', function(e){
                e.preventDefault();
                recs = [];
                $dlg.find('tbody tr').each(function(){
                    var $tr = $(this);
                    var data = $tr.prop('data');
                    if ($tr.find('td.oracle-htp-links-column-name select').val() !== ''){
                        data.columnName = $tr.find('td.oracle-htp-links-column-name select').val();
                    }
                    if ($tr.find('td.oracle-htp-links-url input[name="url"]').val() !== ''){
                        data.pageId = $tr.find('td.oracle-htp-links-url input[name="url"]').val();
                    }
                    if ($tr.find('td.oracle-htp-links-param-name input[name="paramName"]').val() !== ''){
                        data.paramName = $tr.find('td.oracle-htp-links-param-name input[name="paramName"]').val();
                    }
                    recs.push(data);
                });

                $.ajax({
                    url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/column-links',
                    type: 'PUT',
                    contentType: 'application/json',
                    data: JSON.stringify({
                        pageId: AJS.params.pageId,
                        macroId: $macro.attr('macro-key'),
                        links: recs
                    }),
                    processData: false,
                    dataType: 'json'
                }).done(function(data){
                    dlg.hide();
                    dlg.remove();                
                }).fail(function(jqxhr){
                    alert(jqxhr.responseText);
                });
            });
            $dlg.find('button.cancel').on('click', function(e){
                e.preventDefault();
                dlg.hide();
                dlg.remove();
            });
            dlg.show();
        }

        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/column-links',
            type: 'GET',
            data: {
                'macro-key': $macro.attr('macro-key')
            },
            dataType: 'json'
        }).done(function(data){
            showSetupLinksDialog(data);
        }).fail(function(jqxhr){
            alert(jqxhr.responseText);
        });            
    }
    function removeMenu($menu){
        $menu.find('button').each(function(){
            $(this).tooltip('destroy');
        });
        $menu.remove();
    }
    function createSideBar($macro,invoke){
        var menu = $macro.prop('oracle-htp-sidebar-menu');
        if (typeof menu !== 'undefined'){
            removeMenu(menu.$menu);
        }
        menu = {};
        var $menu = $(Mesilat.Templates.Oracle.sideMenu({}));
        $menu.insertBefore($macro);
        $menu.offset({
            top: $macro.offset().top,
            left: $macro.offset().left - 28
        });
        $menu.mouseenter(function(){
            if ('timeout' in menu){
                clearTimeout(menu.timeout);
                delete menu.timeout;
            }
        }).mouseleave(function(){
            removeMenu($menu);
        });

        $menu.find('.aui-iconfont-search').each(function(){
            var $btn = $(this).closest('button');
            if (typeof invoke === 'undefined'){
                $btn.attr('aria-disabled','true').attr('disabled','disabled');
            } else {
                $btn.on('click', function(e){
                    $.ajax({
                        url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/param-desc',
                        type: 'GET',
                        data: {
                            'macro-id': $macro.attr('macro-key')
                        },
                        dataType: 'json'
                    }).done(function(data){
                        var params = createDefaultParams();
                        for (var paramId in data.results){
                            params[paramId] = data.results[paramId];
                        }
                        var data = $.extend(true, {}, $macro.prop('oracle-htp-data'));
                        for (var key in data.params){
                            params[key].value = data.params[key];
                        }
                        var $dlg = $(Mesilat.Templates.Oracle.parametersDialog({
                            params: params
                        }));
                        $dlg.find('button.apply').on('click', function(e){
                            e.preventDefault();
                            for (var i = 1; i <= 10; i++){
                                var $input = $dlg.find('input[name="p' + i + '"]');
                                if ($input.val() === ''){
                                    if ('p' + i in data.params){
                                        delete data.params['p' + i];
                                    }
                                } else {
                                    data.params['p' + i] = $input.val();
                                }
                            }
                            AJS.dialog2($dlg).remove();
                            invoke($macro, data);
                        });
                        $dlg.find('button.cancel').on('click', function(e){
                            e.preventDefault();
                            AJS.dialog2($dlg).remove();
                        });
                        AJS.dialog2($dlg).show();
                    });
                    removeMenu($menu);
                });
            }
        });
        $menu.find('.aui-iconfont-link').each(function(){
            var $btn = $(this).closest('button');
            $btn.on('click', function(e){
                showLinksDialog($macro);
                removeMenu($menu);
            });
        });
        $menu.find('.aui-iconfont-build').each(function(){
            var $btn = $(this).closest('button');
            if (typeof invoke === 'undefined'){
                $btn.attr('aria-disabled','true').attr('disabled','disabled');
            } else {
                $btn.on('click', function(e){
                    invoke($macro, $macro.prop('oracle-htp-data'));
                    removeMenu($menu);
                });
            }
        });
        $menu.find('button').each(function(){
            $(this).tooltip({
                gravity: 'w',
                title: function(){
                    return $(this).text();
                }
            });
        });

        menu.$menu = $menu;
        return menu;
    }

    return {
        createSideBar: createSideBar
    };
});