define([], function()
{
    return Class.create({

        initialize : function (tableElement)
        {
            this._CONTACT_ERROR_DIALOG_TITLE = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.error.title'))";
            this._CLOSE_BUTTON_NAME = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.error.closeButton.name'))";

            this._tableElement = tableElement;

            this._errorContactDialogContainer = this._createErrorDialogContainer();
            this._errorContactDialog = new PhenoTips.widgets.ModalPopup(this._errorContactDialogContainer, false, {'title': this._CONTACT_ERROR_DIALOG_TITLE});
        },

        _createErrorDialogContainer : function() {
            var container = new Element('div', {'class' : 'contact-dialog xform'});
            header = new Element("h2");
            container.insert(header);

            message = new Element('div', {'class' : 'contact-dialog-error-message'});
            container.insert(message);

            var closeButton = new Element('span', {"class" : "buttonwrapper"}).insert(new Element('input', {'name' : 'close', 'value' : this._CLOSE_BUTTON_NAME, 'class' : 'button secondary'}));
            closeButton.on('click', function(event) {
                this._errorContactDialog.closeDialog();
            }.bind(this));

            var buttons = new Element('div', {'class' : 'buttons'});
            buttons.insert(closeButton);
            container.insert(buttons);

            return container;
        },

        showContactError : function(header, message) {
            this._errorContactDialogContainer.down('h2').update(header || '');
            this._errorContactDialogContainer.down('.contact-dialog-error-message').update(message || '');
            this._errorContactDialog.showDialog();
        },

        showSent : function(messagesFieldName) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matching.ajaxutils.requestSent')");
        },

        showReplyReceived : function(messagesFieldName, value) {
            this.showHint(messagesFieldName, "$services.localization.render('phenotips.matching.ajaxutils.done')");
        },

        showFailure : function(messagesFieldName, message) {
            var message = message || "$services.localization.render('phenotips.matching.ajaxutils.failure')";
            this.showHint(messagesFieldName, message, 'failure');
        },

        clearHint : function(messagesFieldName) {
            $(messagesFieldName).update('');
        },

        showHint : function(messagesFieldName, message, cssClass)
        {
            var messages = $(messagesFieldName);
            messages.update(new Element('div', {'class' : cssClass}).update(message));
        },

        _roundScore : function(score)
        {
            return Math.floor(Number(score) * 100) / 100;
        },

        _getCookieKey : function (tableId) {
            var userHash = $$('#' + tableId + ' .toggle-filters input[name="user-hash"]')[0];
            userHash = userHash && userHash.value;
            return userHash + '_' + tableId + '_filters_state';
        },

        // checking if a string is blank or contains only white-space
        _isBlank : function(str)
        {
            return (!str || !str.trim());
        },

        // Return true if all elements of the first list are found in the second
        _listIsSubset : function(first, second)
        {
            for (var i = 0; i < first.length; i++) {
                if (second.indexOf(first[i]) === -1) {
                    return false;
                }
            }
            return true;
        },

        _validateEmail: function (email)
        {
            var re = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
            return re.test(String(email).toLowerCase());
        }
    });
});
