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
            this._ajaxURL = XWiki.contextPath + "/rest/matches";

            this._utils = new utils();

            this._dialog = this._createWarningDialogue();

            $('find-updated-matches-button').on('click', this._findUpdatedMatches.bind(this));
            $('find-all-matches-button').on('click', this._showWarning.bind(this) );
        },

        _showWarning : function () {
            var servers = this._getListOfServersToUse();
            if (servers.length == 0) {
                return;
            }
            var mmeIncluded = false;
            servers.forEach(function(serverId) {
                if (serverId != "local") {
                    mmeIncluded = true;
                }
            });

            if (mmeIncluded) {
                this._warningBody.update("$services.localization.render('phenotips.findMatches.findAllWarningMME')");
            }
            else {
                this._warningBody.update("$services.localization.render('phenotips.findMatches.findAllWarning')");
            }

            this._dialog.show();
        },

        _createWarningDialogue : function() {
            var warningDiv = new Element('div', {'class': 'find-all-warning-body'});

            this._warningBody = new Element('div', {'class': 'warning-text'});
            warningDiv.insert(this._warningBody);

            this._okButton = new Element('input', {type: 'button', name : 'ok',
                'value': "$services.localization.render('phenotips.findMatches.findAllWarning.findAllButton')", 'class': 'button'});
            this._cancelButton = new Element('input', {type: 'button', name : 'cancel',
                'value': "$services.localization.render('phenotips.findMatches.findAllWarning.cancelButton')", 'class' : 'button secondary'});

            var _this = this;
            this._okButton.observe('click', function(event) {
                _this._hideWarning();
                _this._findAllMatches();
            });

            this._cancelButton.observe('click', function(event) {
                _this._hideWarning();
            });

            var buttons = new Element('div', {'class' : 'buttons import-block-bottom'});
            buttons.insert(this._okButton.wrap('span', {'class' : 'buttonwrapper'}))
                   .insert(this._cancelButton.wrap('span', {'class' : 'buttonwrapper'}));
            warningDiv.insert(buttons);

            return new PhenoTips.widgets.ModalPopup(warningDiv, {close: {method: this._hideWarning.bind(this), keys: ['Esc']}},
                              { extraClassName: "find-all-warning",
                                title: "$services.localization.render('phenotips.findMatches.findAllWarningTitle')",
                                displayCloseButton: false});
        },

        _hideWarning: function() {
            this._dialog.closeDialog();
        },

        _getListOfServersToUse: function() {
            var servers = [];
            $$('.select-for-update input').each( function(elm) {
                if (elm.checked) { servers.push(elm.value); }
            } );
            return servers;
        },

        _findUpdatedMatches : function() {
            this._findMatches(true, "find-matches-messages");
        },

        _findAllMatches : function() {
            this._findMatches(false, "find-matches-messages");
        },

        _findMatches : function(onlyCheckPatientsUpdatedAfterLastRun, messageContainer)
        {
            var servers = this._getListOfServersToUse();
            if (servers.length == 0) {
                return;
            }

            // disable all find matches buttons while matching is running...
            $$('.find-matches-button').each( function(elm) { elm.disable() } );

            new Ajax.Request(this._ajaxURL + "?method=PUT", {
                contentType: 'application/json',
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
