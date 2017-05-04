define(["jquery", "dynatable"], function($, dyna)
{
    return Class.create({

        // params:
        //    ajaxHandler
        //    onCreate
        //    onSuccess
        //    onFailure
        //    matchesTable
        initialize : function (params)
        {
            this._params = params || {};
        },

        sendNotification : function(ids)
        {
            var idsToNotify = JSON.stringify({ ids: ids});
            console.log("Sending " + idsToNotify);

            new Ajax.Request(this._params.ajaxHandler, {
                parameters : {action : 'send-notifications',
                              ids    : idsToNotify
                },
                onCreate : function () {
                    if (this._params.onCreate) {
                        this._params.onCreate();
                    }
                }.bind(this),
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
        }

    });
});
