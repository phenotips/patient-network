require(["jquery", "matchingNotification/matchesTable"], function($, matchesTable) 
{
    var loadMNM = function($, matchesTable) {
        new PhenoTips.widgets.UnnotifiedMatchesTable($, matchesTable);
    };

    (XWiki.domIsLoaded && loadMNM($, matchesTable)) || document.observe("xwiki:dom:loaded", loadMNM.bind(this, $, matchesTable));
});

var PhenoTips = (function (PhenoTips) {
    var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
    widgets.UnnotifiedMatchesTable = Class.create({

    initialize : function ($, matchesTable)
    {
        this._ajaxURL = new XWiki.Document('WebHome', 'MatchingNotification').getURL('get') + '?outputSyntax=plain';
        this._$ = $;

        this._matches = undefined;
        this._matchesTable = new matchesTable(this._$('#matchesTable'), this._afterProcessTableRegisterReject.bind(this));

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
        var _this = this;

        var score = this._checkScore('find-matches-score', 'find-matches-messages');
        if (score == undefined) {
            return;
        }
        new Ajax.Request(_this._ajaxURL, {
            parameters : {action : 'find-matches',
                          score  : score
            },
            onSuccess : function (response) {
                _this._showSuccess('find-matches-messages');
                console.log("find matches result, score = " + score);
                console.log(response.responseJSON);

                _this._$('#show-matches-score').val(score);
                _this._showMatches();
            },
            onFailure : function (response) {
                _this._showFailure('find-matches-messages');
            }
        });
        this._showSent('find-matches-messages');
    },

    _showMatches : function()
    {
        var _this = this;
        var score = _this._checkScore('show-matches-score', 'show-matches-messages');
        if (score == undefined) {
            return;
        }
        new Ajax.Request(this._ajaxURL, {
            parameters : {action   : 'show-matches',
                          score    : score,
                          notified : false
            },
            onSuccess : function (response) {
                _this._showSuccess('show-matches-messages');
                console.log("show matches result, score = " + score);
                console.log(response.responseJSON);

                _this._matches = response.responseJSON.matches;
                _this._matchesTable.update(_this._matches);
            },
            onFailure : function (response) {
                _this._showFailure('show-matches-messages');
            }
        });
        _this._showSent('show-matches-messages');
    },

    _sendNotification : function()
    {
        var _this = this;

        var ids = this._readMatchesToNotify();
        var idsJson = JSON.stringify({ ids: ids});
        console.log("Sending " + idsJson);

        new Ajax.Request(_this._ajaxURL, {
            parameters : {action : 'send-notifications',
                          ids    : idsJson
            },
            onSuccess : function (response) {
                console.log("onSuccess, received:");
                console.log(response.responseText);
                _this._showSuccess('send-notifications-messages');

               _this._onSuccessSendNotification(response.responseJSON.results);
            },
            onFailure : function (response) {
                _this._showFailure('send-notifications-messages');
            }
        });
        this._showSent('send-notifications-messages');
    },

    _rejectMatch : function(target)
    {
        var _this = this;

        var matchId = this._$(target).data("matchid");
        var reject = this._$(target).is(':checked');
        var ids = JSON.stringify({ ids: [matchId]});

        new Ajax.Request(this._ajaxURL, {
            parameters : {action : 'reject-matches',
                          ids    : ids,
                          reject : reject
            },
            onSuccess : function (response) {
                _this._onSuccessRejectMatch(response.responseJSON.results, reject);
            },
            onFailure : function (response) {
                console.log(response);
            }
        });
    },

    _checkScore : function(scoreFieldName, messagesFieldName) {
        var score = this._$('#' + scoreFieldName).val();
        if (score == undefined || score == "") {
            this._showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.emptyScore')");
            return;
        } else if (isNaN(score)) {
            this._showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.invalidScore')");
            return;
        };
        scoreNumber = Number(score);
        if (scoreNumber < 0 || scoreNumber > 1) {
            this._showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.invalidScore')");
            return;
        }
        return score;
    },

    _showSent : function(messagesFieldName) {
        this._showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.requestSent')");
    },

    _showSuccess : function(messagesFieldName, value) {
        this._showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.success')");
    },

    _showFailure : function(messagesFieldName) {
        this._showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.failure')");
    },

    _showHint : function(messagesFieldName, message) {
        var messages = this._$('#' + messagesFieldName);
        messages.empty();
        messages.append(new Element('div', {'class' : 'xHint'}).update(message));
    },

    _onSuccessSendNotification : function(results)
    {
        var _this = this;

        var successfulIds = this._$.grep(results, function(item) {return item.success} ).map(function(item) {return item.id});
        var failedIds     = this._$.grep(results, function(item) {return !item.success}).map(function(item) {return item.id});

        // remove notified matches
        this._matches = this._$.grep(this._matches, function(item) {return _this._$.inArray(item.id, successfulIds)==-1});

        this._matchesTable.update(this._matches);

        failedIds.each(function (item) {
            var tr = this._$('#matchesTable').find("#tr_" + item);
            tr.addClass('failed');
            tr.find('.notify').attr('checked', 'checked');
        }.bind(this));
    },

    // When reject is true, request was sent to reject. When false, request was sent to unreject.
    _onSuccessRejectMatch : function(results, reject)
    {
        var _this = this;

        var successfulIds = this._$.grep(results, function(item) {return item.success} ).map(function(item) {return item.id});
        var failedIds     = this._$.grep(results, function(item) {return !item.success}).map(function(item) {return item.id});

        // mark un/rejected in model
        this._$.grep(this._matches, function(match) {return _this._$.inArray(match.id, successfulIds)>-1})
            .each(function(match) {match.rejected = reject});

        this._matchesTable.update(this._matches);

        successfulIds.each(function (id) {
            var tr =  this._$('#matchesTable').find("#tr_" + id);
            if (reject) {
                tr.addClass('rejected');
                tr.find('.reject').attr('checked', 'checked');
            } else {
                tr.removeClass('rejected');
                tr.find('.reject').removeAttr('checked');
            }
        }.bind(this));

        failedIds.each(function (id) {
            var tr = this._$('#matchesTable').find("#tr_" + id);
            tr.addClass('failed');
            if (reject) {
                tr.find('.reject').removeAttr('checked');
            } else {
                tr.find('.reject').attr('checked', 'checked');
            }
        }.bind(this));
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
    },

    _readMatchesToNotify : function()
    {
        var ids = [];
        this._$('#matchesTable').find(".notify").each(function (index, elm) {
            if (elm.checked && !elm.disabled) {
                var id = Number(this._$(elm).data('matchid'));
                ids.push(String(id));
            }
        }.bind(this));
        return ids;
    }

    });
    return PhenoTips;
}(PhenoTips || {}));