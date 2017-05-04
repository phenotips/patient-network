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
            onCreate     : this._onCreateSendNotification.bind(this),
            onSuccess    : this._onSuccessSendNotification.bind(this),
            onFailure    : this._onFailSendNotification.bind(this)
        });

        $('#find-matches-button').on('click', this._findMatches.bind(this));
        $('#show-matches-button').on('click', this._showMatches.bind(this));
        $('#send-notifications-button').on('click', this._sendNotification.bind(this));
        $('#rejected').on('click', this._setFilter.bind(this));
        $('#saved').on('click', this._setFilter.bind(this));
        $('#uncategorized').on('click', this._setFilter.bind(this));
        $('#find-matches-score').on('change', function() {this._utils.clearHint('find-matches-messages');}.bind(this));
        $('#show-matches-score').on('change', function() {this._utils.clearHint('show-matches-messages');}.bind(this));

        this._setFilter();
    },

    // callback for after matches table is drawn
    _afterProcessTableRegisterStatus : function()
    {
        this._$('#matchesTable').find('.status').on('change', function(event) {
            this._setMatchStatus(event.target);
        }.bind(this));
    },

    _findMatches : function()
    {
        this._utils.clearHint('send-notifications-messages');
        var score = this._checkScore('find-matches-score', 'find-matches-messages');
        if (score == undefined) {
            return;
        }
        new Ajax.Request(this._ajaxURL, {
            parameters : {action : 'find-matches',
                          score  : score
            },
            onCreate : function() {
                this._utils.showSent('find-matches-messages');
            }.bind(this),
            onSuccess : function (response) {
                this._utils.showSuccess('find-matches-messages');
                //console.log("find matches result, score = " + score);
                //console.log(response.responseJSON);

                this._$('#show-matches-score').val(score);
                this._showMatches();
            }.bind(this),
            onFailure : function (response) {
                this._utils.showFailure('find-matches-messages');
            }.bind(this),
        });
    },

    _showMatches : function()
    {
        var score = this._checkScore('show-matches-score', 'show-matches-messages');
        if (score == undefined) {
            return;
        }
        new Ajax.Request(this._ajaxURL, {
            parameters : {action   : 'show-matches',
                          score    : score,
                          notified : false
            },
            onCreate : function() {
                this._utils.showLoading('show-matches-messages');
            }.bind(this),
            onSuccess : function (response) {
                this._utils.showSuccess('show-matches-messages');
                //console.log("show matches result, score = " + score);
                //console.log(response.responseJSON);

                var matches = response.responseJSON.matches;
                this._matchesTable.update(matches);
            }.bind(this),
            onFailure : function (response) {
                this._utils.showFailure('show-matches-messages');
            }.bind(this)
        });
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
            this._utils.showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.emptyScore')");
            return;
        } else if (isNaN(score)) {
            this._utils.showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.invalidScore')");
            return;
        };
        scoreNumber = Number(score);
        if (scoreNumber < 0 || scoreNumber > 1) {
            this._utils.showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.invalidScore')");
            return;
        }
        return score;
    },

    _sendNotification : function()
    {
        var idsToNotify = this._matchesTable.getMarkedToNotify();
        this._notifier.sendNotification(idsToNotify);
    },

    _onCreateSendNotification : function() {
        this._utils.showSent('send-notifications-messages');
    },

    _onSuccessSendNotification : function(ajaxResponse)
    {
        //console.log("onSuccess, received:");
        //console.log(ajaxResponse.responseText);
        this._utils.showSuccess('send-notifications-messages');

        var [successfulIds, failedIds] = this._utils.getResults(ajaxResponse.responseJSON.results);

        if (failedIds.length > 0) {
            alert("Sending notification failed for the matches with the following ids: " + failedIds.join());
        }

        // Update table state
        this._matchesTable.setState(successfulIds, { 'notified': true, 'notify': false, 'status': 'success' });
        this._matchesTable.setState(failedIds, { 'notify': true, 'status': 'failure' });
        this._matchesTable.update();

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
