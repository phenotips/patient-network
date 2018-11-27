var PhenoTips = (function(PhenoTips) {
  var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
  widgets.NotificationDialog = Class.create({
    initialize: function(title) {

        this._GENERAL_ERROR_TITLE = "$escapetool.xml($services.localization.render('phenotips.myMatches.notification.dialog.error.title'))";
        this._GENERAL_SUCCESS_TITLE = "$escapetool.xml($services.localization.render('phenotips.myMatches.notification.dialog.success.title'))";
        this._CLOSE_BUTTON_NAME = "$escapetool.xml($services.localization.render('phenotips.myMatches.notification.dialog.closeButton.name'))";

        this._dialogContainer = this._createDialogContainer();
        this._dialog = new PhenoTips.widgets.ModalPopup(this._dialogContainer, false, {'title': '-'});
    },

    _createDialogContainer : function() {
        var container = new Element('div', {'class' : 'contact-dialog xform'});
        header = new Element("h2");
        container.insert(header);

        message = new Element('div', {'class' : 'notification-dialog-message'});
        container.insert(message);

        var closeButton = new Element('span', {"class" : "buttonwrapper"}).insert(new Element('input', {'name' : 'close', 'value' : this._CLOSE_BUTTON_NAME, 'class' : 'button secondary'}));
        closeButton.on('click', function(event) {
            this._dialog.closeDialog();
        }.bind(this));

        var buttons = new Element('div', {'class' : 'buttons'});
        buttons.insert(closeButton);
        container.insert(buttons);

        return container;
    },

    _show : function(header, message, title) {
        if (header) {
            this._dialogContainer.down('h2').show();
            this._dialogContainer.down('h2').update(header);
        } else {
            this._dialogContainer.down('h2').update("");
            this._dialogContainer.down('h2').hide();
        }
        this._dialogContainer.down('.notification-dialog-message').update(message || '');
        this._dialog.showDialog();
        this._dialogContainer.up().up().down(".msdialog-title").update(title); // has to be done after dialog is shown
    },

    showError: function(header, message, title) {
        this._show(header, message, title || this._GENERAL_ERROR_TITLE);
    },

    showNotification: function(header, message, title) {
        this._show(header, message, title || this._GENERAL_SUCCESS_TITLE);
    }

  });
  return PhenoTips;
}(PhenoTips || {}));
