define([], function()
{
    return Class.create({

        initialize : function (tableElement)
        {
            this._tableElement = tableElement;
        },

        showSent : function(messagesFieldName) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matching.ajaxutils.requestSent')");
        },

        showReplyReceived : function(messagesFieldName, value) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matching.ajaxutils.done')");
        },

        showFailure : function(messagesFieldName, message) {
            var message = message || "$services.localization.render('phenotips.matching.ajaxutils.failure')";
            this.showHint(messagesFieldName, message, 'failure');
        },

        clearHint : function(messagesFieldName) {
            $(messagesFieldName).update('');
        },

        showHint : function(messagesFieldName, message, cssClass)
        {
            var messages = $(messagesFieldName);
            messages.update(new Element('div', {'class' : cssClass}).update(message));
        },

        validateScore : function(score, className, messagesFieldName) {
            if (score == undefined || score == "") {
                if (className == 'show-matches-score') {
                    $(className).value = 0.5;
                    return 0.5;
                } else if (className ==  'show-matches-gen-score') {
                    $(className).value = 0.1;
                    return 0.1;
                } else {
                    $(className).value = 0;
                    return 0;
                }
            } else if (isNaN(score) || Number(score) < 0 || Number(score) > 1) {
                this.showHint(messagesFieldName, "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.invalidScore'))", "invalid");
                return undefined;
            }
            return score;
        },

        _roundScore : function(score)
        {
            return Math.floor(Number(score) * 100) / 100;
        },

        _getCookieKey : function (tableId) {
            var userHash = $$('#' + tableId + ' .toggle-filters input[name="user-hash"]')[0];
            userHash = userHash && userHash.value;
            return userHash + '_' + tableId + '_filters_state';
        },

        // checking if a string is blank or contains only white-space
        _isBlank : function(str)
        {
            return (!str || !str.trim());
        },

        // Return true if all elements of the first list are found in the second
        _listIsSubset : function(first, second)
        {
            for (var i = 0; i < first.length; i++) {
                if (second.indexOf(first[i]) === -1) {
                    return false;
                }
            }
            return true;
        },

        _validateEmail: function (email)
        {
            var re = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
            return re.test(String(email).toLowerCase());
        }
    });
});
