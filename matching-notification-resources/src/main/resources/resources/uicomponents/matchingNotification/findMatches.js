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

            $('#find-updated-matches-button').on('click', this._findUpdatedMatches.bind(this));
            $('#find-all-matches-button').on('click', this._findAllMatches.bind(this));
        },

        _findUpdatedMatches : function() {
            this._findMatches("find-updated-matches", "find-matches-messages");
        },

        _findAllMatches : function() {
            this._findMatches("find-all-matches", "find-matches-messages");
        },

        _findMatches : function(action, messageContainer)
        {
            // disable all find matches buttons while matching is running...
            this._$('.find-matches-button').each( function() { this.disable() } );

            var servers = [];
            this._$('.select-for-update input').each( function(index, elm) {
                if (elm.checked) { servers.push(elm.value); }
            } );

            if (servers.length == 0) {
                this._$('.find-matches-button').each( function() { this.enable() } );
                return;
            }

            new Ajax.Request(this._ajaxURL, {
                parameters : { "action" : action, "servers" : servers },
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
