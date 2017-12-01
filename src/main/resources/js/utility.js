define('oracle-htp-plugin/util',['jquery'],function($){
    var URL_PARAMS = getUrlParams();

    // Support for processing page parameters
    function getUrlParams() {
        var url = decodeURI(window.location.href);
        var i = url.indexOf('?');
        if (i <= 0) {
            return {};
        }
        var params = {}, hashes = url.slice(i + 1).split('&');
        for (i = 0; i < hashes.length; i++) {
            var hash = hashes[i].split('=');
            if (hash[0] === 'c3js-point') {
                params[hash[0]] = JSON.parse(decodeURIComponent(hash[1]));
            } else {
                params[hash[0]] = decodeURIComponent(hash[1]);
            }
        }
        return params;
    };
    function processParams(data, params){
        // Extract value from URL parameters
        function getUrlParamValue(key, urlParams) {
            if (key instanceof Array) {
                if (key.length === 0) {
                    return null;
                } else if (key.length === 1) {
                    return getUrlParamValue(key[0], urlParams);
                } else if (key[0] in urlParams) {
                    return getUrlParamValue(key.slice(1), urlParams[key[0]]);
                }
            } else {
                if (key in urlParams) {
                    return urlParams[key];
                } else {
                    return null;
                }
            }
        }

        var urlParams = URL_PARAMS;
        if ('c3js-point' in urlParams) {
            if (typeof urlParams['c3js-point'].dad !== 'undefined') {
                data.dad = urlParams['c3js-point'].dad;
            }
        }

        var pattern = /^\{(.+)\}$/;
        if (typeof params !== 'undefined') {
            data.params = data.params || {};

            for (var i = 1; i <= 10; i++) {
                var key = 'p' + i;
                if (key in params) {
                    // Check if param value is a variable (acceptable from URL or elsewhere)
                    var match = pattern.exec(params[key]);
                    if (match === null) {
                        data.params[key] = params[key];
                    } else {
                        data.params[key] = getUrlParamValue(match[1].split('.'), urlParams);
                    }
                } else {
                    if (key in data.params){
                        delete data.params;
                    }
                }
            }
        }
    };
    function logError(msg){
        console.log('oracle-htp-plugin error: ' + msg);
    }
    function formData($form){
        var data = {};
        $form.find('input[name]').each(function(){
            var $input = $(this);
            data[$input.attr('name')] = $input.val();
        });
        return data;
    };
    function putData($form, data){
        $form.find('input[name]').each(function(){
            var $input = $(this);
            if ($input.attr('name') in data){
                $input.val(data[$input.attr('name')]);
            }
        });
        return data;
    };
    function success(msg,$elt){
        $elt.html($('<p>').text(msg))
            .css('display', 'block')
            .addClass('aui-message-success');
        setTimeout(function($elt){ $elt.hide(500); }, 2000, $elt);
    }
    function error(msg,$elt){
        $elt.html($('<p>').text(msg))
            .css('display', 'block')
            .addClass('aui-message-error');
        setTimeout(function($elt){ $elt.hide(500); }, 2000, $elt);
    }
    function getRandomInt(min, max) {
        min = Math.ceil(min);
        max = Math.floor(max);
        return Math.floor(Math.random() * (max - min)) + min;
    }
    function generateId(){
        var d = new Date();
        return '' + d.getTime() + '_' + getRandomInt(0, 1000);
    }

    return {
        formData: formData,
        putData: putData,
        success: success,
        error: error,
        logError: logError,
        generateId: generateId,
        processParams: processParams,
        urlParams: URL_PARAMS
    };
});