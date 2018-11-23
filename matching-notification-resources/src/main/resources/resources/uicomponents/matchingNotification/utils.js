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
            messages.update(new Element('div', {'class' : cssClass || ''}).update(message));
        },

        roundScore : function(score)
        {
            return Math.floor(Number(score) * 100) / 100;
        },

        getCookieKey : function (tableId) {
            var userHash = $$('#' + tableId + ' .toggle-filters input[name="user-hash"]')[0];
            userHash = userHash && userHash.value;
            return userHash + '_' + tableId + '_filters_state';
        },

        // checking if a string is blank or contains only white-space
        isBlank : function(str)
        {
            return (!str || !str.trim());
        },

        // Return true if there is at least one common element in two lists
        listsIntersect : function(first, second)
        {
            for (var i = 0; i < first.length; i++) {
                if (second.indexOf(first[i]) != -1) {
                    return true;
                }
            }
            return false;
        },

        validateEmail: function (email)
        {
            var re = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
            return re.test(String(email).toLowerCase());
        }
    });
});
