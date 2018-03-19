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

            this._CONTACT_SEND_ERROR_HEADER = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.send.error.header'))";
            this._SERVER_ERROR_MESSAGE = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.serverFailed'))";
        },

        sendNotification : function(matches)
        {
            this._matches = matches;
            var ids = this._getMarkedToNotify();

            if (ids.length == 0) {
                this._utils.showFailure('show-matches-messages', "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.notify.noContactSelected'))");
                return;
            }
            this._notifyMatchByIDs(ids);
        },

        notifyUserMatch: function(matchID, subjectPatientId, subjectServerId, emailText, emailSubject)
        {
            new Ajax.Request(this._ajaxURL + 'send-user-notifications', {
                contentType : 'application/json',
                parameters : {'matchId': matchID,
                              'subjectPatientId': subjectPatientId,
                              'subjectServerId': subjectServerId,
                              'emailText': emailText,
                              'emailSubject': emailSubject},
                onCreate : function (response) {
                    this._utils.showSent('send-notifications-messages');
                }.bind(this),
                onSuccess : function (response) {
                    this._onSuccessSendNotification(response, false);
                }.bind(this),
                onFailure : function (response) {
                    this._utils.showContactError(this._CONTACT_SEND_ERROR_HEADER, this._SERVER_ERROR_MESSAGE);
                }.bind(this)
            });
        },

        _notifyMatchByIDs  : function(matchIDs)
        {
            // console.log("Sending " + idsToNotify);
            var idsToNotify = JSON.stringify({ ids: matchIDs});
            new Ajax.Request(this._ajaxURL + 'send-admin-local-notifications', {
                parameters : {'ids' : idsToNotify},
                onCreate : function (response) {
                    this._utils.showSent('send-notifications-messages');
                }.bind(this),
                onSuccess : function (response) {
                    this._onSuccessSendNotification(response, true);
                }.bind(this),
                onFailure : function (response) {
                    this._utils.showContactError(this._CONTACT_SEND_ERROR_HEADER, this._SERVER_ERROR_MESSAGE);
                }.bind(this)
            });
        },

        _onSuccessSendNotification : function(ajaxResponse, isAdminNotification)
        {
            this._utils.showReplyReceived('send-notifications-messages');

            console.log("Send notification - reply received:");
            console.log(ajaxResponse.responseText);

            if (ajaxResponse.responseJSON && ajaxResponse.responseJSON.results) {
                var results = ajaxResponse.responseJSON.results;

                // Update table state
                if (results.success && results.success.length > 0 ) {
                    var event = { 'matchIds' : results.success,
                                  'properties' : {'notified': true, 'state': 'success', 'isAdminNotification': isAdminNotification} };
                    document.fire("notified:success", event);
                }
                if (results.failed && results.failed.length > 0) {
                    var message = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.matchesTable.onFailureAlert')) " + this._getAllEmailsForMatchIds(results.failed);
                    this._utils.showContactError(this._CONTACT_SEND_ERROR_HEADER, message);
                    var event = { 'matchIds' : results.failed,
                                  'properties' : {'state': 'failure', 'isAdminNotification': isAdminNotification} };
                    document.fire("notified:failed", event);
                }
            } else {
                if (!ajaxResponse.responseJSON || !ajaxResponse.responseJSON.results) {
                    this._utils.showContactError(this._CONTACT_SEND_ERROR_HEADER, this._SERVER_ERROR_MESSAGE);
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
        },

        _getAllEmailsForMatchIds : function(matchIds)
        {
            var emails = [];
            matchIds.each(function (matchId) {
                var notifyCheckbox = this._tableElement.down('input[data-matchid="'+matchId+'"');
                var emailsArray = notifyCheckbox.dataset.emails && notifyCheckbox.dataset.emails.split(',');
                emails = emails.concat(emailsArray);
            }.bind(this));
            return emails;
        }

    });
});
