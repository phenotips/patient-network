define(["jquery"], function($)
{
    return Class.create({

        initialize : function (params)
        {
            this._params = params || {};
        },

        showSent : function(messagesFieldName) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.requestSent')");
        },

        showSuccess : function(messagesFieldName, value) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.success')");
        },

        showFailure : function(messagesFieldName) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.failure')");
        },

        getResults : function(results)
        {
            var successfulIds = $.grep(results, function(item) {return item.success} ).map(function(item) {return item.id});
            var failedIds     = $.grep(results, function(item) {return !item.success}).map(function(item) {return item.id});
            return [successfulIds, failedIds];
        },

        showHint : function(messagesFieldName, message) 
        {
            var messages = $('#' + messagesFieldName);
            messages.empty();
            messages.append(new Element('div', {'class' : 'xHint'}).update(message));
        }

    });
});
