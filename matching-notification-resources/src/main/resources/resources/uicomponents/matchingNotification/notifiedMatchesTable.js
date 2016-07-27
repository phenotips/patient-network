require(["jquery",
         "matchingNotification/matchesTable",
         "matchingNotification/matchesNotifier",
         "matchingNotification/utils"],
        function($, matchesTable, notifier, utils)
{
    var loadMNM = function($, matchesTable, notifier, utils) {
        new PhenoTips.widgets.NotifiedMatchesTable($, matchesTable, notifier, utils);
    };

    (XWiki.domIsLoaded && loadMNM($, matchesTable, notifier, utils)) || document.observe("xwiki:dom:loaded", loadMNM.bind(this, $, matchesTable, notifier, utils));
});

var PhenoTips = (function (PhenoTips) {
    var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
    widgets.NotifiedMatchesTable = Class.create({

    initialize : function ($, matchesTable, notifier, utils)
    {
        this._ajaxURL = new XWiki.Document('RequestHandler', 'MatchingNotification').getURL('get') + '?outputSyntax=plain';
        this._$ = $;
        this._matches = undefined;

        this._tableElement = this._$('#notifiedMatchesTable');

        this._utils = new utils();
        this._matchesTable = new matchesTable(this._tableElement);
        this._notifier = new notifier({
            ajaxHandler  : this._ajaxURL,
            onSuccess    : this._onSuccessSendNotification.bind(this),
            onFailure    : this._onFailSendNotification.bind(this),
            matchesTable : this._tableElement
        });

        $('#show-matches-button').on('click', this._showMatches.bind(this));
        $('#send-notifications-button').on('click', this._sendNotification.bind(this));

        this._showMatches();
    },

    _showMatches : function()
    {
        var _this = this;
        new Ajax.Request(this._ajaxURL, {
            parameters : {action   : 'show-matches',
                          score    : 0,
                          notified : true
            },
            onSuccess : function (response) {
                this._utils.showSuccess('show-matches-messages');
                console.log("show matches result");
                console.log(response.responseJSON);

                _this._matches = response.responseJSON.matches;
                _this._matchesTable.update(this._matches);
            }.bind(this),
            onFailure : function (response) {
                this._utils.showFailure('show-matches-messages');
            }.bind(this)
        });
        this._utils.showSent('show-matches-messages');
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

        this._matchesTable.getRowsWithIdsAllInArray(failedIds)
            .each(function (tr)
            {
                this._$(tr).addClass('failed');
                this._$(tr).find('.notify').attr('checked', 'checked');
            }.bind(this));

        this._matchesTable.getRowsWithIdsAllInArray(successfulIds)
            .each(function (tr)
            {
                this._$(tr).addClass('succeed');
                this._$(tr).find('.notify').removeAttr('checked');
            }.bind(this));

    },

    _onFailSendNotification : function()
    {
        this._utils.showFailure('send-notifications-messages');
    }

    });
    return PhenoTips;
}(PhenoTips || {}));