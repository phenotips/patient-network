require(["jquery",
         "matchingNotification/matchesTable",
         "matchingNotification/matchesNotifier",
         "matchingNotification/utils"],
        function($, matchesTable, notifier, utils)
{
    var loadMNM = function($, matchesTable, notifier, utils) {
        new PhenoTips.widgets.UnnotifiedMatchesTable($, matchesTable, notifier, utils);
    };

    (XWiki.domIsLoaded && loadMNM($, matchesTable, notifier, utils)) || document.observe("xwiki:dom:loaded", loadMNM.bind(this, $, matchesTable, notifier, utils));
});

var PhenoTips = (function (PhenoTips) {
    var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
    widgets.UnnotifiedMatchesTable = Class.create({

    initialize : function ($, matchesTable, notifier, utils)
    {
        this._ajaxURL = new XWiki.Document('RequestHandler', 'MatchingNotification').getURL('get') + '?outputSyntax=plain';
        this._$ = $;

        this._tableElement = this._$('#matchesTable');

        this._utils = new utils();
        this._showMatchTypes = {"notified" : false, "rejected" : false, "saved" : true, "uncategorized" : true};
        this._matchesTable = new matchesTable(this._tableElement, this._afterProcessTableRegisterStatus.bind(this));
        this._notifier = new notifier({
            ajaxHandler  : this._ajaxURL,
            onSuccess    : this._onSuccessSendNotification.bind(this),
            onFailure    : this._onFailSendNotification.bind(this)
        });

        $('#show-matches-button').on('click', this._showMatches.bind(this));
        $('#send-notifications-button').on('click', this._sendNotification.bind(this));
        $('#rejected').on('click', this._setFilter.bind(this));
        $('#saved').on('click', this._setFilter.bind(this));
        $('#uncategorized').on('click', this._setFilter.bind(this));

        this._setFilter();
    },

    // callback for after matches table is drawn
    _afterProcessTableRegisterStatus : function()
    {
        this._$('#matchesTable').find('.status').on('change', function(event) {
            this._setMatchStatus(event.target);
        }.bind(this));
    },

    _showMatches : function()
    {
        var score = this._checkScore('show-matches-score', 'show-matches-messages');
        var phenScore = this._checkScore('show-matches-phen-score', 'show-matches-messages');
        var genScore = this._checkScore('show-matches-gen-score', 'show-matches-messages');
        if (score == undefined || phenScore == undefined || genScore == undefined) {
            return;
        }
        new Ajax.Request(this._ajaxURL, {
            parameters : {
                    action    : 'show-matches',
                    score     : score,
                    phenScore : phenScore,
                    genScore  : genScore,
                    notified  : false },
            onSuccess : function (response) {
                    if (response.responseJSON) {
                        this._utils.showReplyReceived('show-matches-messages');

                        console.log("Show matches response JSON (min scores: " + score + "/" + phenScore + "/" + genScore + "):");
                        console.log(response.responseJSON);

                        var matches = response.responseJSON.matches;
                        this._matchesTable.update(matches);
                    } else {
                        this._utils.showFailure('show-matches-messages');
                    }
                }.bind(this),
            onFailure : function (response) {
                    this._utils.showFailure('show-matches-messages');
                }.bind(this)
        });

        this._utils.showSent('show-matches-messages');
    },

    _setMatchStatus : function(target)
    {
        var matchId = String(this._$(target).data("matchid"));
        var status = this._$(target).val();
        var ids = JSON.stringify({ ids: matchId.split(",")});

        new Ajax.Request(this._ajaxURL, {
            parameters : {action : 'set-status',
                          ids    : ids,
                          status : status
            },
            onSuccess : function (response) {
                this._onSuccessSetMatchStatus(response.responseJSON.results, status);
            }.bind(this),
            onFailure : function (response) {
                console.log(response);
            }.bind(this)
        });
    },

    _checkScore : function(scoreFieldName, messagesFieldName) {
        var score = this._$('#' + scoreFieldName).val();
        if (score == undefined || score == "") {
            return 0;
        } else if (isNaN(score) || Number(score) < 0 || Number(score) > 1) {
            this._utils.showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.invalidScore')", "invalid");
            return undefined;
        }
        return score;
    },

    _sendNotification : function()
    {
        var idsToNotify = this._matchesTable.getMarkedToNotify();
        this._notifier.sendNotification(idsToNotify);
        this._utils.showSent('send-notifications-messages');
    },

    _onSuccessSendNotification : function(ajaxResponse)
    {
        this._utils.showReplyReceived('send-notifications-messages');

        console.log("Send notification - reply received:");
        console.log(ajaxResponse.responseText);

        if (ajaxResponse.responseJSON && ajaxResponse.responseJSON.results && ajaxResponse.responseJSON.results.length > 0) {
            var [successfulIds, failedIds] = this._utils.getResults(ajaxResponse.responseJSON.results);

            if (failedIds.length > 0) {
                alert("Sending notification failed for the matches with the following ids: " + failedIds.join());
            }

            // Update table state
            this._matchesTable.setState(successfulIds, { 'notified': true, 'notify': false, 'status': 'success' });
            this._matchesTable.setState(failedIds, { 'notify': true, 'status': 'failure' });
            this._matchesTable.update();
        } else {
            if (!ajaxResponse.responseJSON || !ajaxResponse.responseJSON.results) {
                this._utils.showFailure('send-notifications-messages');
            }
        }
    },

    _onFailSendNotification : function()
    {
        this._utils.showFailure('send-notifications-messages');
    },

    // When reject is true, request was sent to set new status. When false, request was sent to unreject.
    _onSuccessSetMatchStatus : function(results, status)
    {
        var [successfulIds, failedIds] = this._utils.getResults(results);

        if (failedIds.length > 0) {
            var operation = reject ? "Setting status" : "Status setted";
            if (failedIds.length == 1) {
                alert(operation + " match failed.");
            } else {
                alert(operation + " matches with the following ids failed: " + failedIds.join());
            }
        }

        // Update table state
        this._matchesTable.setState(successfulIds, { 'status': status });
        this._matchesTable.setState(failedIds, { 'status': 'failure' });
        this._matchesTable.update();
    },

    _identifyMatch : function(successfulIds)
    {
        return function(match)
        {
            // checks if match needs to be marked: all its ids are in successfulIds
            if (this._$.isArray(match.id)) {
                for (var i=0; i<match.id.length; i++) {
                    if (this._$.inArray(match.id[i], successfulIds)==-1) {
                        return false;
                    }
                }
                return true;
            } else {
                return (this._$.inArray(match.id, successfulIds)>-1);
            }
        }.bind(this);
    },

    _setFilter : function(event)
    {
        if (event && event.target && event.target.id) {
            this._showMatchTypes[event.target.id] = !event.target.checked;
        }
        this._matchesTable.setFilter( function (match) {
            return this._showMatchTypes[match.status];
        }.bind(this) );
    }
    });
    return PhenoTips;
}(PhenoTips || {}));
