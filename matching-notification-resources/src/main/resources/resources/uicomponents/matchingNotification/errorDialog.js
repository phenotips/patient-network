var PhenoTips = (function(PhenoTips) {
  var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
  widgets.ErrorDialog = Class.create({
    initialize: function(title) {

        this._GENERAL_ERROR_TITLE = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.error.title'))";
        this._CLOSE_BUTTON_NAME = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.error.closeButton.name'))";

        this._errorDialogContainer = this._createErrorDialogContainer();
        this._errorDialog = new PhenoTips.widgets.ModalPopup(this._errorDialogContainer, false, {'title': title || this._GENERAL_ERROR_TITLE});
    },

    _createErrorDialogContainer : function() {
        var container = new Element('div', {'class' : 'contact-dialog xform'});
        header = new Element("h2");
        container.insert(header);

        message = new Element('div', {'class' : 'dialog-error-message'});
        container.insert(message);

        var closeButton = new Element('span', {"class" : "buttonwrapper"}).insert(new Element('input', {'name' : 'close', 'value' : this._CLOSE_BUTTON_NAME, 'class' : 'button secondary'}));
        closeButton.on('click', function(event) {
            this._errorDialog.closeDialog();
        }.bind(this));

        var buttons = new Element('div', {'class' : 'buttons'});
        buttons.insert(closeButton);
        container.insert(buttons);

        return container;
    },

    showError : function(header, message) {
        this._errorDialogContainer.down('h2').update(header || '');
        this._errorDialogContainer.down('.dialog-error-message').update(message || '');
        this._errorDialog.showDialog();
    }

  });
  return PhenoTips;
}(PhenoTips || {}));
