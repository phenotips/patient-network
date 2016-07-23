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
        this._matches = undefined;

        this._tableElement = this._$('#matchesTable');

        this._utils = new utils();
        this._matchesTable = new matchesTable(this._tableElement, this._afterProcessTableRegisterReject.bind(this));
        this._notifier = new notifier({
            ajaxHandler  : this._ajaxURL,
            onSuccess    : this._onSuccessSendNotification.bind(this),
            onFailure    : this._onFailSendNotification.bind(this),
            matchesTable : this._tableElement
        });

        $('#find-matches-button').on('click', this._findMatches.bind(this));
        $('#show-matches-button').on('click', this._showMatches.bind(this));
        $('#send-notifications-button').on('click', this._sendNotification.bind(this));
        $('#notify_all').on('click', this._notifyAllClicked.bind(this));
        $('#filter_rejected').on('click', this._filterRejectedClicked.bind(this));
    },

    // callback for after matches table is drawn
    _afterProcessTableRegisterReject : function()
    {
        this._$('#matchesTable').find('.reject').on('click', function(event) {
            this._rejectMatch(event.target);
        }.bind(this));
    },

    _findMatches : function()
    {
        var score = this._checkScore('find-matches-score', 'find-matches-messages');
        if (score == undefined) {
            return;
        }
        new Ajax.Request(this._ajaxURL, {
            parameters : {action : 'find-matches',
                          score  : score
            },
            onSuccess : function (response) {
                this._utils.showSuccess('find-matches-messages');
                console.log("find matches result, score = " + score);
                console.log(response.responseJSON);

                this._$('#show-matches-score').val(score);
                this._showMatches();
            }.bind(this),
            onFailure : function (response) {
                this._utils.showFailure('find-matches-messages');
            }.bind(this)
        });
        this._utils.showSent('find-matches-messages');
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
            onSuccess : function (response) {
                this._utils.showSuccess('show-matches-messages');
                console.log("show matches result, score = " + score);
                console.log(response.responseJSON);

                this._matches = response.responseJSON.matches;
                this._matchesTable.update(this._matches);
            }.bind(this),
            onFailure : function (response) {
                this._utils.showFailure('show-matches-messages');
            }.bind(this)
        });
        this._utils.showSent('show-matches-messages');
    },

    _rejectMatch : function(target)
    {
        var matchId = String(this._$(target).data("matchid"));
        var reject = this._$(target).is(':checked');
        var ids = JSON.stringify({ ids: matchId.split(",")});

        new Ajax.Request(this._ajaxURL, {
            parameters : {action : 'reject-matches',
                          ids    : ids,
                          reject : reject
            },
            onSuccess : function (response) {
                this._onSuccessRejectMatch(response.responseJSON.results, reject);
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
        this._notifier.sendNotification();
        this._utils.showSent('send-notifications-messages');
    },

    _onSuccessSendNotification : function(ajaxResponse)
    {
        console.log("onSuccess, received:");
        console.log(ajaxResponse.responseText);
        this._utils.showSuccess('send-notifications-messages');

        var [successfulIds, failedIds] = this._utils.getResults(ajaxResponse.responseJSON.results);

        if (failedIds.length > 0) {
            alert("Sending notification failed for the matches with the following ids: " + failedIds.join());
        }

        // remove notified matches
        var toRemove = this._$.grep(this._matches, this._identifyMatch(successfulIds));
        this._matches = this._$.grep(this._matches,
                                     function(match) {return this._$.inArray(match, toRemove)==-1}.bind(this));

        this._matchesTable.update(this._matches);

        // Mark failed trs
        this._matchesTable.getRowsWithIdsAllInArray(failedIds)
            .each(function (tr)
            {
                this._$(tr).addClass('failed');
                this._$(tr).find('.notify').attr('checked', 'checked');
            }.bind(this));
    },

    _onFailSendNotification : function()
    {
        this._utils.showFailure('send-notifications-messages');
    },

    // When reject is true, request was sent to reject. When false, request was sent to unreject.
    _onSuccessRejectMatch : function(results, reject)
    {
        var [successfulIds, failedIds] = this._utils.getResults(results);

        if (failedIds.length > 0) {
            var operation = reject ? "Rejecting" : "Unrejecting";
            if (failedIds.length == 1) {
                alert(operation + " match failed.");
            } else {
                alert(operation + " matches with the following ids failed: " + failedIds.join());
            }
        }

        // Mark in model
        this._$.grep(this._matches, this._identifyMatch(successfulIds))
            .each(function(match) {match.rejected = reject});

        this._matchesTable.update(this._matches);

        // Mark failed trs
        this._matchesTable.getRowsWithIdsAllInArray(failedIds)
            .each(function (tr)
            {
                this._$(tr).addClass('failed');
            }.bind(this));
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

    _notifyAllClicked : function(event)
    {
        var checked = event.target.checked;
        this._$('#matchesTable').find(".notify").each(function (index, elm) {
            if (!elm.disabled) {
                elm.checked = checked;
            }
        });
    },

    _filterRejectedClicked : function(event)
    {
        var checked = event.target.checked;
        this._matchesTable.setFilter(checked ? this._filterRejected : undefined);
    },

    _filterRejected : function(match) {
        return !match.rejected;
    }

    });
    return PhenoTips;
}(PhenoTips || {}));