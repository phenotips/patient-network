require(["jquery",
         "matchingNotification/utils"],
        function($, utils)
{
    var loadFM = function($, utils) {
        new PhenoTips.widgets.FindMatches($, utils);
    };

    (XWiki.domIsLoaded && loadFM($, utils)) || document.observe("xwiki:dom:loaded", loadFM.bind(this, $, utils));
});

var PhenoTips = (function (PhenoTips) {
    var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
    widgets.FindMatches = Class.create({

        initialize : function ($, utils)
        {
            this._ajaxURL = new XWiki.Document('RequestHandler', 'MatchingNotification').getURL('get') + '?outputSyntax=plain';
            this._$ = $;

            this._utils = new utils();

            $('#find-updated-local-matches-button').on('click', this._findUpdatedLocalMatches.bind(this));
            $('#find-all-local-matches-button').on('click', this._findAllLocalMatches.bind(this));

            $('#find-updated-other-matches-button').on('click', this._findUpdatedOtherMatches.bind(this));
            $('#find-all-other-matches-button').on('click', this._findAllOtherMatches.bind(this));
        },

        _findUpdatedLocalMatches : function() {
            this._findMatches("find-local-updated-matches", "find-local-matches-messages");
        },

        _findAllLocalMatches : function() {
            this._findMatches("find-local-all-matches", "find-local-matches-messages");
        },

        _findUpdatedOtherMatches : function() {
            this._findMatches("find-other-updated-matches", "find-other-matches-messages");
        },

        _findAllOtherMatches : function() {
            this._findMatches("find-other-all-matches", "find-other-matches-messages");
        },
        _findMatches : function(action, messageContainer)
        {
            // disable all find matches buttons while matching is running...
            this._$('.find-matches-button').each( function() { this.disable() } );

            new Ajax.Request(this._ajaxURL, {
                parameters : { "action" : action },
                onSuccess : function(response) {
                        this._utils.showHint(messageContainer, "$services.localization.render('phenotips.findMatches.refreshMatches.afterUpdate')");
                    }.bind(this),
                onFailure : function(response) {
                        this._utils.showFailure(messageContainer);
                    }.bind(this),
                onComplete: function() {
                    // re-enable buttons
                    this._$('.find-matches-button').each( function() { this.enable() } );
                }.bind(this)
            });
            this._utils.showSent(messageContainer);
        }
    });
    return PhenoTips;
}(PhenoTips || {}));
