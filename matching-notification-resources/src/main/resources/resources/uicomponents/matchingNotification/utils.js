define(["jquery"], function($)
{
    return Class.create({

        initialize : function (params)
        {
            this._params = params || {};
        },

        showSent : function(messagesFieldName) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.requestSent')", 'hint-loading');
        },

        showSuccess : function(messagesFieldName, value) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.success')", 'success');
        },

        showFailure : function(messagesFieldName) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.failure')", 'failure');
        },

        showLoading : function(messagesFieldName) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.loadingMatches')", 'hint-loading');
        },

        clearHint : function(messagesFieldName) {
            var messages = $('#' + messagesFieldName);
            messages.empty();
        },

        getResults : function(results)
        {
            var successfulIds = $.grep(results, function(item) {return item.success} ).map(function(item) {return item.id});
            var failedIds     = $.grep(results, function(item) {return !item.success}).map(function(item) {return item.id});
            return [successfulIds, failedIds];
        },

        showHint : function(messagesFieldName, message, messageClass) 
        {
            var messages = $('#' + messagesFieldName);
            messages.empty();
            messages.append(new Element('div', {'class' : 'messages-hint ' + messageClass}).update(message));
            if (messageClass == 'hint-loading') {
                messages.append(new Element('span', {'class' : 'loading', 'style' : "padding-right: 25px;"}));
            }
        }

    });
});
