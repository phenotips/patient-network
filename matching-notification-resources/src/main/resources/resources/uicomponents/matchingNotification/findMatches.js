require(["matchingNotification/utils"],
        function(utils)
{
    var loadFM = function(utils) {
        new PhenoTips.widgets.FindMatches(utils);
    };

    (XWiki.domIsLoaded && loadFM(utils)) || document.observe("xwiki:dom:loaded", loadFM.bind(this, utils));
});

var PhenoTips = (function (PhenoTips) {
    var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
    widgets.FindMatches = Class.create({

        initialize : function (utils)
        {
            this._ajaxURL = XWiki.contextPath + "/rest/patients/matching-notification/find-matches";

            this._utils = new utils();

            $('find-updated-matches-button').on('click', this._findUpdatedMatches.bind(this));
            $('find-all-matches-button').on('click', this._findAllMatches.bind(this));
        },

        _findUpdatedMatches : function() {
            this._findMatches(true, "find-matches-messages");
        },

        _findAllMatches : function() {
            this._findMatches(false, "find-matches-messages");
        },

        _findMatches : function(onlyCheckPatientsUpdatedAfterLastRun, messageContainer)
        {
            // disable all find matches buttons while matching is running...
            $$('.find-matches-button').each( function(elm) { elm.disable() } );

            var servers = [];
            $$('.select-for-update input').each( function(elm) {
                if (elm.checked) { servers.push(elm.value); }
            } );

            if (servers.length == 0) {
                $$('.find-matches-button').each( function(elm) { elm.enable() } );
                return;
            }

            new Ajax.Request(this._ajaxURL, {
            	contentType:'application/json',
                parameters : { "onlyCheckPatientsUpdatedAfterLastRun" : onlyCheckPatientsUpdatedAfterLastRun,
                               "serverIds" : servers 
                },
                onCreate : function() {
                    this._utils.showHint(messageContainer, "$services.localization.render('phenotips.matching.ajaxutils.requestSent')");
                    }.bind(this),
                onSuccess : function(response) {
                        this._utils.showHint(messageContainer, "$services.localization.render('phenotips.findMatches.refreshMatches.afterUpdate')");
                    }.bind(this),
                onFailure : function(response) {
                        this._utils.showFailure(messageContainer);
                    }.bind(this),
                onComplete: function() {
                    // re-enable buttons
                    $$('.find-matches-button').each( function(elm) { elm.enable() } );
                }.bind(this)
            });

            this._utils.showSent(messageContainer);
        }
    });
    return PhenoTips;
}(PhenoTips || {}));
