define(["matchingNotification/utils"], function(utils)
{
    return Class.create({

        // params:
        // tableElement - matching notification table container
        // ajaxURL      - matching notification service ajax URL
        initialize : function (tableElement, ajaxURL)
        {
            this._tableElement = tableElement;
            this._ajaxURL = ajaxURL;
            this._utils = new utils(this._tableElement);
        },

        _sendNotification : function(matches)
        {
            this._matches = matches;
            var ids = this._getMarkedToNotify();

            if (ids.length == 0) {
                this._utils.showFailure('show-matches-messages', "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.notify.noContactSelected'))");
                return;
            }
            // console.log("Sending " + idsToNotify);
            var idsToNotify = JSON.stringify({ ids: ids});
            new Ajax.Request(this._ajaxURL + 'send-notifications', {
                parameters : {'ids' : idsToNotify},
                onCreate : function (response) {
                    this._utils.showSent('send-notifications-messages');
                }.bind(this),
                onSuccess : function (response) {
                    this._onSuccessSendNotification(response);
                }.bind(this),
                onFailure : function (response) {
                    this._utils.showFailure('show-matches-messages');
                }.bind(this)
            });
        },

        _onSuccessSendNotification : function(ajaxResponse)
        {
            this._utils.showReplyReceived('send-notifications-messages');

            console.log("Send notification - reply received:");
            console.log(ajaxResponse.responseText);

            if (ajaxResponse.responseJSON && ajaxResponse.responseJSON.results && ajaxResponse.responseJSON.results.length > 0) {
                var [successfulIds, failedIds] = this._utils.getResults(ajaxResponse.responseJSON.results);

                if (failedIds.length > 0) {
                    alert("$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.matchesTable.onFailureAlert')) " + failedIds.join());
                }

                // Update table state
                if (successfulIds.length > 0) {
                    var event = { 'matchIds' : successfulIds, 'properties' : {'notified': true, 'state': 'success'} };
                    document.fire("notified:success", event);
                }
                if (failedIds.length > 0) {
                    var event = { 'matchIds' : failedIds, 'properties' : {'state': 'failure'} };
                    document.fire("notified:failed", event);
                }
                this._matchesTable.update();
            } else {
                if (!ajaxResponse.responseJSON || !ajaxResponse.responseJSON.results) {
                    this._utils.showFailure('send-notifications-messages');
                }
            }
        },

        _getMarkedToNotify : function()
        {
            var ids = [];
            var checkedElms = this._tableElement.select('input[data-patientid]:checked');

            checkedElms.each(function (elm, index) {
                if (elm.dataset.matchid && elm.dataset.patientid) {
                    ids.push({'matchId' : elm.dataset.matchid, 'patientId' : elm.dataset.patientid});
                }
            });
            return ids;
        }

    });
});
