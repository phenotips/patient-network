define(["jquery", "dynatable"], function($, dyna)
{
    return Class.create({

        // params:
        //    ajaxHandler
        //    onSuccess
        //    onFailure
        //    matchesTable
        initialize : function (params)
        {
            this._params = params || {};
        },

        sendNotification : function()
        {
            var ids = this._readMatchesToNotify();
            console.log("Sending " + ids);

            new Ajax.Request(this._params.ajaxHandler, {
                parameters : {action : 'send-notifications',
                              ids    : ids
                },
                onSuccess : function (response) {
                    if (this._params.onSuccess) {
                        this._params.onSuccess(response);
                    }
                }.bind(this),
                onFailure : function (response) {
                    if (this._params.onFailure) {
                        this._params.onFailure();
                    }
                }.bind(this)
            });
        },

        //////////////////

        _readMatchesToNotify : function() 
        {
            var idsToNotify = [];
            $(this._params.matchesTable).find(".notify").each(function (index, elm) {
                if (elm.checked && !elm.disabled) {
                    var allIds = String($(elm).data('matchid'));
                    allIds.split(",").each(function(id) {idsToNotify.push(id)});
                }
            }.bind(this));
            idsToNotify = JSON.stringify({ ids: idsToNotify});
            return idsToNotify;
        }
    });
});
