var PhenoTips = (function(PhenoTips) {
  var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
  widgets.MacthContactSelectDialog = Class.create({
    initialize: function(title) {
        this._SELECT_MATCH_TITLE = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.selectPatientToContactTitle'))";
        this._SELECT_MATCH_TEXT  = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.selectPatientToContactText'))";
        this._CANCEL_BUTTON_NAME = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.cancelContact'))";
        this._dialogContainer = this._createDialogContainer();
        this._dialog = new PhenoTips.widgets.ModalPopup(this._dialogContainer, false, {'title': this._SELECT_MATCH_TITLE});
        this._contactData = null;
    },

    _createDialogContainer : function() {
        var container = new Element('div', {'class' : 'contact-dialog xform select-patient-to-contact'});
        //header = new Element("h2");
        //container.insert(header);

        message = new Element('div', {'class' : 'select-patient-to-contact-content'}).insert(this._SELECT_MATCH_TEXT);
        container.insert(message);

        this.rightPatientButton = new Element('span', {"class" : "buttonwrapper contact_right_patient_in_match"}).insert(new Element('input', {'name' : 'contactRight', 'value' : 'right', 'class' : 'button'}));
        this.leftPatientButton = new Element('span', {"class" : "buttonwrapper contact_left_patient_in_match"}).insert(new Element('input', {'name' : 'contactLeft', 'value' : 'left', 'class' : 'button'}));
        this.cancelButton = new Element('span', {"class" : "buttonwrapper cancel_contact"}).insert(new Element('input', {'name' : 'close', 'value' : this._CANCEL_BUTTON_NAME, 'class' : 'button secondary cancel_contact_button'}));

        this.rightPatientButton.on('click', function(event) {
            this._dialog.closeDialog();
            this._contactData.function(this._contactData.matchData.matchid,
                                       this._contactData.matchData.rightpatientid,
                                       this._contactData.matchData.rightserverid);
        }.bind(this));

        this.leftPatientButton.on('click', function(event) {
            this._dialog.closeDialog();
            this._contactData.function(this._contactData.matchData.matchid,
                                       this._contactData.matchData.leftpatientid,
                                       this._contactData.matchData.leftserverid);
        }.bind(this));

        this.cancelButton.on('click', function(event) {
            this._dialog.closeDialog();
        }.bind(this));

        var buttons = new Element('div', {'class' : 'buttons'});
        buttons.insert(this.leftPatientButton);
        buttons.insert(this.rightPatientButton);
        buttons.insert(this.cancelButton);

        container.insert(buttons);

        return container;
    },

    show : function(matchData, contactFunction) {

        this._contactData = { "function": contactFunction,
                              "matchData": matchData.dataset };

        this.rightPatientButton.down(".button").value = "Contact " + matchData.dataset.rightlabel.substring(0, 15) + " about " + this._contactData.matchData.rightpatientid;
        this.leftPatientButton.down(".button").value = "Contact " + matchData.dataset.leftlabel.substring(0, 15) + " about " + this._contactData.matchData.leftpatientid;

        this._dialog.showDialog();
    },
  });
  return PhenoTips;
}(PhenoTips || {}));
