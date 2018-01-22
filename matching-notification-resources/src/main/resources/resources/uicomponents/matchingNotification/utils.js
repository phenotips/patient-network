define(["jquery"], function($)
{
    return Class.create({

        initialize : function (params)
        {
            this._params = params || {};
        },

        showSent : function(messagesFieldName) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matching.ajaxutils.requestSent')");
        },

        showReplyReceived : function(messagesFieldName, value) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matching.ajaxutils.done')");
        },

        showFailure : function(messagesFieldName) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matching.ajaxutils.failure')");
        },

        getResults : function(results)
        {
            var successfulIds = $.grep(results, function(item) {return item.success} ).map(function(item) {return item.id});
            var failedIds     = $.grep(results, function(item) {return !item.success}).map(function(item) {return item.id});
            return [successfulIds, failedIds];
        },

        showHint : function(messagesFieldName, message, cssClass)
        {
            var messages = $('#' + messagesFieldName);
            messages.empty();
            messages.append(new Element('div', {'class' : cssClass ? cssClass : ''}).update(message));
        }

    });
});
