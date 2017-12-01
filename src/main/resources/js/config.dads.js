define('oracle-htp-plugin/config-dads',['jquery','ajs','oracle-htp-plugin/util'],function($,AJS,Util){
    var $TAB;

    function dadEdit($tr){
        var dad = $tr.prop('oracle-htp-data-dad');
        var $form = $TAB.find('form');
        for (var key in dad){
            if (key === 'grantees'){
                $form.find('input[name="' + key + '"]')
                    .prop('images', dad.images)
                    .val(dad.grantees)
                    .trigger('change');
            } else {
                $form.find('input[name="' + key + '"]').val(dad[key]);
            }
        }
    }
    function save($form) {
        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/dad',
            type: 'PUT',
            contentType: 'application/json',
            data: JSON.stringify(Util.formData($form)),
            processData: false
        }).done(function() {
            read();
        }).fail(function(jqxhr){
            Util.error(jqxhr.responseText, $TAB.find('form div.aui-message'));
        });
    }
    function dadDelete($tr){
        var dadName = $tr.prop('oracle-htp-data-dad').name;
        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/dad',
            type: 'DELETE',
            contentType: 'application/json',
            data: JSON.stringify({ name: dadName }),
            processData: false
        }).done(function() {
            read();
            $TAB.find('form input[name]').val('');
        }).fail(function(jqxhr) {
            Util.error(jqxhr.responseText, $tr.find('div.aui-message'));
        });
    }
    function dadTest($tr){
        var dadName = $tr.prop('oracle-htp-data-dad').name;
        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/dad',
            type: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({ name: dadName }),
            dataType: 'text',
            processData: false,
            beforeSend: function (request) {
                request.setRequestHeader('oracle-htp-action', 'test');
            }
        }).done(function(msg){
            Util.success(msg, $tr.find('div.aui-message'));
        }).fail(function(jqxhr){
            Util.error(jqxhr.responseText, $tr.find('div.aui-message'));
        });
    }

    function initUserGroupSelector($picker){
        var showNoResultsIfAllResultsDisabled = true;
        $picker.attr('data-autocomplete-bound', 'true');
        return $picker.auiSelect2({
            multiple: true,
            minimumInputLength: 2,
            formatInputTooShort: function () {
                return AJS.I18n.getText('oracle-htp-plugin.config.dad.grantees.prompt');
            },
            ajax: {
                transport: function(opts) {
                    // Workaround for Select2 bug: https://github.com/ivaynberg/select2/issues/381
                    // Select2 does not display "no results found" if the only results are already selected.
                    var success = opts.success;
                    delete opts.success;
                    return $.ajax.apply($.ajax, arguments).done(success).done(showNoResultsIfAllResultsDisabled);
                },
                data: function (searchTerm) {
                    return {
                        'max-results': 6,
                        query: searchTerm
                    };
                },
                dataType: 'json',
                url: Confluence.getContextPath() + '/rest/prototype/1/search/user-or-group.json',
                results: function (data) {
                    var results = [];
                    $.each(data.result, function () {
                        if (this.type === 'user') {
                            results.push({
                                id: this.username,
                                text: this.title,
                                imgSrc: this.thumbnailLink.href,
                                entity: this
                            });
                        } else {
                            results.push({
                                id: this.name,
                                text: this.name,
                                imgSrc: AJS.contextPath() + '/images/icons/avatar_group_48.png',
                                entity: this
                            });
                        }
                    });
                    return {
                        results: results
                    };
                },
                quietMillis: 300
            },
            formatResult: function (result) {
                return Confluence.UI.Components.UserGroupSelect2.avatarWithName({
                        size: 'small',
                        displayName: result.text,
                        userId: result.id,
                        avatarImageUrl: result.imgSrc
                    });
            },
            // common.Widget.avatarWithName handles escaping so this doesn't have to
            escapeMarkup: function (markup) {
                return markup;
            },
            formatSelection: function(result) {
                return Confluence.UI.Components.UserGroupSelect2.avatarWithName({
                        size: 'xsmall',
                        displayName: result.text,
                        userId: result.id,
                        avatarImageUrl: result.imgSrc
                    });
            },
            initSelection: function($elt, callback) {
                var data = [];
                var images = $elt.prop('images');
                $elt.val().split(',').forEach(function(userOrGroup){
                    var self = {
                        id: userOrGroup,
                        text: userOrGroup,
                        entity: self
                    };
                    if (typeof images !== 'undefined'){
                        self.imgSrc = images[self.id];
                    }                    
                    data.push(self);
                });
                callback(data);
            },
            dropdownCssClass: 'users-dropdown',
            containerCssClass: 'users-autocomplete',
            hasAvatar: true
        });
    }

    function read(){
        $.ajax({
            url: AJS.contextPath() + '/rest/oracle-htp-api/1.0/dad',
            dataType: 'json',
            context: $TAB.find('tbody')
        }).done(function(data){
            var $tbody = this;
            $tbody.empty();
            data.results.forEach(function(dad) {
                var $tr = $(Mesilat.Templates.Oracle.dadTableRow({
                    dad: dad
                }));
                $tr.prop('oracle-htp-data-dad', dad);
                $tr.find('button.oracle-htp-edit')  .on('click', function(e){dadEdit($(e.target).closest('tr'))});
                $tr.find('button.oracle-htp-delete').on('click', function(e){dadDelete($(e.target).closest('tr'))});
                $tr.find('button.oracle-htp-test')  .on('click', function(e){dadTest($(e.target).closest('tr'))});
                $tbody.append($tr);
            });
        }).fail(function(jqxhr){
            Util.error(jqxhr.responseText, $TAB.find('form div.aui-message'));
        });
    };

    function init($tab){
        $TAB = $tab;
        $TAB.find('form').submit(function(e) {
            e.preventDefault();
            save($(e.target));
        });
        read();

        $TAB.find('form input.autocomplete-multiusergroup').each(function(){
            initUserGroupSelector($(this));
        });
    };

    return {
        init: init
    };
});

require(['jquery','oracle-htp-plugin/config-dads'], function($,config){
    $(function(){
        $('#tab-ohp-2').each(function(){
            config.init($(this));
        });
    });
});