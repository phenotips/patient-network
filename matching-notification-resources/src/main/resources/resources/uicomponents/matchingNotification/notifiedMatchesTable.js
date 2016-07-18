require(["jquery", "matchingNotification/matchesTable"], function($, matchesTable)
{
    var loadMNM = function($, matchesTable) {
        new PhenoTips.widgets.NotifiedMatchesTable($, matchesTable);
    };

    (XWiki.domIsLoaded && loadMNM($, matchesTable)) || document.observe("xwiki:dom:loaded", loadMNM.bind(this, $, matchesTable));
});

var PhenoTips = (function (PhenoTips) {
    var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
    widgets.NotifiedMatchesTable = Class.create({

    initialize : function ($, matchesTable)
    {
        this._ajaxURL = new XWiki.Document('RequestHandler', 'MatchingNotification').getURL('get') + '?outputSyntax=plain';
        this._$ = $;

        this._matches = undefined;
        this._matchesTable = new matchesTable(this._$('#matchesTable'));

        $('#show-matches-button').on('click', this._showMatches.bind(this));
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
                _this._showSuccess('show-matches-messages');
                console.log("show matches result");
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
    }

    });
    return PhenoTips;
}(PhenoTips || {}));