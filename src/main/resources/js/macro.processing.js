define('oracle-htp-plugin/macro-processing', [
    'jquery',
    'ajs',
    'oracle-htp-plugin/c3',
    'oracle-htp-plugin/util'
], function($,AJS,c3,Util) {

    // Support for substitution in href attributes. Use {baseUrl}, {dad}
    function updateHref($macro){
        //console.log('oracle-htp-macro', 'Update href', $macro);
        $macro.find('a').each(function(){
            var $a = $(this);
            var href = $a.attr('href');
            var data = $macro.prop('oracle-htp-data');
            for (var key in Util.urlParams){
                data[key] = Util.urlParams[key];
            }
            for (var key in data){
                href = href.split('{' + key + '}').join(data[key]);
            }
            href = href.split('{baseUrl}').join(AJS.contextPath());
            $a.attr('href', href);
        });
    }
    // Support for Expand macro in PLSQL-generated HTML
    function updateExpandCollapse($macro){
        $macro.find('div.expand-control').each(function(){
            var $div = $(this);
            var $icon = $div.find('span.expand-control-icon');
            var $text = $div.find('span.expand-control-text');
            var $block = $('tbody.ohp-expand-collapse[title="' + $text.text() + '"]');
            $block.hide();
            $icon.on('click', function(e){
                if ($icon.hasClass('expanded')){
                    $block.hide();
                    $icon.removeClass('expanded');
                } else {
                    $block.show();
                    $icon.addClass('expanded');
                }
            });
            $text.on('click', function(e){
                if ($icon.hasClass('expanded')){
                    $block.hide();
                    $icon.removeClass('expanded');
                } else {
                    $block.show();
                    $icon.addClass('expanded');
                }
            });
        });
    }
    // Change input[type=text] for select2 for edit forms with bound data sources
    function makeInputSelectable($input,columnLink){
        function _makeInputSelectable($macro,$input){
            $input.auiSelect2({
                ajax: {
                    url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/plsql',
                    type: 'GET',
                    data: function (text){
                        return {
                            'page-id': columnLink.pageId,
                            'dad': $macro.prop('oracle-htp-data').dad,
                            'p1': text,
                            'url-params': JSON.stringify(Util.urlParams)
                        };
                    },
                    dataType: 'json',
                    delay:    250,
                    results: function (data) {
                        return {
                            results: data
                        };
                    }
                },
                minimumInputLength: 2,
                escapeMarkup: function (markup) {
                    return markup;
                }
            });
        }

        var value = $input.val();
        var $macro = $input.closest('div.conf-macro');
        if (value === ''){
            var $origInput = $input;
            var $input = $('<input class="long-field" type="hidden" name="' + $origInput.attr('name') + '">');
            $origInput.replaceWith($input);
            _makeInputSelectable($macro,$input);
        } else {
            $.ajax({
                url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/plsql',
                type: 'POST',
                contentType: 'application/json',
                data: JSON.stringify({
                    pageId: columnLink.pageId,
                    dad: $macro.prop('oracle-htp-data').dad,
                    params: {
                        p1: value
                    }
                }),
                dataType: 'json',
                processData: false,
                context: $input
            }).done(function(data){
                var $origInput = this;
                var $input = $('<input class="long-field" type="hidden" name="' + $origInput.attr('name') + '">');
                $origInput.replaceWith($input);
                _makeInputSelectable($macro,$input);
                $input.val(data[0].id).closest('div.field-group').find('span.select2-chosen').text(data[0].text);
            }).fail(function(jqxhr){
                Util.logError(jqxhr.resposeText);
            });
        }
    }
    // Support for late-binding links to other wiki pages
    function updateColumnLinks($macro){
        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/column-links',
            type: 'GET',
            data: {
                'macro-key': $macro.attr('macro-key')
            },
            dataType: 'json'
        }).done(function(data){
            var $table = $macro.find('table');
            data.results.forEach(function(columnLink){
                $table.find('td.ohp-data-' + columnLink.columnName).each(function(){
                    var $td = $(this);
                    var value = $td.attr('value');
                    if (typeof value === 'undefined'){
                        value = $td.text();
                    }
                    var data = $macro.prop('oracle-htp-data');
                    var dad = (typeof data === 'undefined')? $macro.attr('dad'): data.dad;
                    var $a = $('<a>').attr('href',
                        AJS.contextPath() + '/pages/viewpage.action?pageId=' + columnLink.pageId
                            + '&' + columnLink.paramName + '=' + encodeURIComponent(value)
                            + '&dad=' + encodeURIComponent(dad)
                    ).text($td.text());
                    $td.empty().append($a);
                });
            });

            var $form = $macro.find('form');
            data.results.forEach(function(columnLink){
               $form.find('input[name="' + columnLink.columnName + '"]').each(function(){
                   makeInputSelectable($(this), columnLink);
               }); 
            });
        }).fail(function(jqxhr){
            Util.logError(jqxhr.responseText);
        });            
    };
    function updateChartLinks($macro){
        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/column-links',
            type: 'GET',
            data: {
                'macro-key': $macro.attr('macro-key')
            },
            dataType: 'json'
        }).done(function(data){
            if ('results' in data){// && data.results.length > 0){
                var columnLinks = {};
                data.results.forEach(function(columnLink){
                    columnLinks[columnLink.columnName] = columnLink;
                });
                $macro.prop('oracle-htp-links', data.results);
                var c3js = $macro.prop('oracle-htp-c3js-data');
                c3js.data.onclick = function(point, elt){
                    //console.log('OHP', point, elt);
                    if (point.id in columnLinks){
                        var columnLink = columnLinks[point.id];
                        var url = AJS.contextPath() + '/pages/viewpage.action?pageId=' + columnLink.pageId
                            + '&' + columnLink.paramName + '=' + encodeURIComponent(c3js.data.rows[point.x + 1][0])
                            + '&dad=' + encodeURIComponent($macro.prop('oracle-htp-data').dad);
                        window.location.href = url;
                    } else if (c3js.data.type === 'pie'){
                        var columnLink = columnLinks['_DATA_'];
                        var url = AJS.contextPath() + '/pages/viewpage.action?pageId=' + columnLink.pageId
                            + '&' + columnLink.paramName + '=' + encodeURIComponent(point.id)
                            + '&dad=' + encodeURIComponent($macro.prop('oracle-htp-data').dad);
                        window.location.href = url;
                    }
                };
                c3js.data.selection = {
                    enabled: true,
                    isselectable: function (point){
                        if (c3js.data.type === 'pie' && '_DATA_' in columnLinks){
                            return true;
                        } else {
                            return point.id in columnLinks;
                        }
                    }
                };
                $macro.empty();
                c3.generate(c3js);

                if (data.axis && data.axis.x && data.axis.x['x-hide']) {
                    $(this).find('.c3-axis-x > path.domain').hide();
                    $(this).find('.tick > line[x2="-6"]').hide();
                }
                if (data.axis && data.axis.y && data.axis.y['x-hide']) {
                    $(this).find('.c3-axis-y > path.domain').hide();
                    $(this).find('.tick > line[y2="6"]').hide();
                }
                
            }
        }).fail(function(jqxhr){
            Util.logError(jqxhr.responseText);
        });            
    }
    // EDIT form: fill fields with values
    function updateEditForm($macro){
        $macro.find('form.ohp-edit-form').each(function(){
            var $form = $(this);
            var $dataDiv = $form.find('div.oracle-htp-form-data');
            if ($dataDiv.length){
                var text = $dataDiv.text().trim();
                if (text !== ''){
                    try {
                        var data = JSON.parse(text);
                        for (var key in data){
                            $form.find('input[name="' + key + '"]').val(data[key]);
                        }
                    } catch(e){
                        Util.logError('Failed to parse JSON:');
                        Util.logError(text);
                    }
                }
            }
        });
    }
    // EDIT form: for fields with options to select from
    function updateEditOptions($macro){
        $macro.find('form.ohp-edit-form input[option-values]').each(function(){
            var $input = $(this);
            var data = [];
            JSON.parse($input.attr('option-values')).forEach(function(value){
                data.push({
                    id:   value,
                    text: value
                });
            });
            var $elt = $('<input type="hidden" class="long-field">');
            $elt.attr('name', $input.attr('name'));
            $elt.attr('value', $input.attr('value'));
            $elt.attr('tabindex', $input.attr('tabindex'));
            $input.replaceWith($elt);
            $elt.select2({
                data: data
            });
        });
    }
    // EDIT form: create datepicker control
    function updateDateFields($macro){
        $macro.find('form.ohp-edit-form input.DATE').each(function(){
            var $input = $(this);
            $input.datePicker({
                'overrideBrowserDefault': true,
                'dateFormat': 'dd-mm-yy'
            });
        });
    }
    // Support for sortable tables
    function updateSortableTable($macro){
        $macro.find('.aui-table-sortable').each(function(){
            $(this).tablesorter({sortMultiSortKey: '', headers: {}, debug: false});
        });
    }

    return {
        updateHref:          updateHref,
        updateExpandCollapse:updateExpandCollapse,
        makeInputSelectable: makeInputSelectable,
        updateColumnLinks:   updateColumnLinks,
        updateChartLinks:    updateChartLinks,
        updateEditForm:      updateEditForm,
        updateEditOptions:   updateEditOptions,
        updateDateFields:    updateDateFields,
        updateSortableTable: updateSortableTable
    };
});