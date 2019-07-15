var PhenoTips = (function(PhenoTips) {
  var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
  widgets.MatcherContactDialog = Class.create({
    initialize: function() {
        this._ajaxURL = XWiki.contextPath + "/rest/matches/";

        this._CONTACT_DIALOG_TITLE = "$escapetool.xml($services.localization.render('phenotips.similarCases.contactDialogTitle'))";
        this._CONTACT_DIALOG_HEADER = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.header'))";
        this._SEND = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.send'))";
        this._CANCEL = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.cancel'))";

        this._EMAIL_CC = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.cc.label'))";
        this._EMAIL_TO = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.to.label'))";
        this._EMAIL_FROM = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.from.label'))";
        this._EMAIL_SUBJECT = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.subject.label'))";
        this._EMAIL_MESSAGE = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.message.label'))";
        this._CONTACT_PREVIEW_ERROR_HEADER = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.send.error.header'))";

        this._CONTACT_ERROR_DIALOG_TITLE = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.error.title'))";
        this._CONTACT_SEND_ERROR_HEADER = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.send.error.header'))";
        this._SERVER_ERROR_MESSAGE = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.serverFailed'))";
        this._CONTACT_SEND_FAILED_MESSAGE = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.sendFailed'))";

        this._SUCCESS_NOTIFICATION_TITLE = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.emailsent.title'))";
        this._SUCCESS_NOTIFICATION_MESSAGE = "$escapetool.xml($services.localization.render('phenotips.similarCases.emailSent'))";

        this.matchId = '';
        this.subjectPatientId = '';
        this.subjectServerId = '';

        this._errorDialog = new PhenoTips.widgets.NotificationDialog(this._CONTACT_ERROR_DIALOG_TITLE);

        this._contactContainer = this._createContactDialogContainer();
        this._contactDialog = new PhenoTips.widgets.ModalPopup(this._contactContainer, false, {'title': this._CONTACT_DIALOG_TITLE, 'verticalPosition': 'top'});
    },

    show  : function() {
        this._contactDialog.showDialog();
    },

    close : function() {
        this._contactDialog.closeDialog();
    },

    _createContactDialogContainer : function()
    {
        var container = new Element('div', {'class' : 'contact-dialog xform'});
        container.insert(new Element("h2").update(this._CONTACT_DIALOG_HEADER));

        container.insert(new Element('dt').insert(new Element('label').update(this._EMAIL_FROM)));
        container.insert(new Element('dd').insert(new Element('input', {'name' : 'from', 'type' : 'text', 'disabled' : true})));

        container.insert(new Element('dt').insert(new Element('label').update(this._EMAIL_TO)));
        container.insert(new Element('dd').insert(new Element('input', {'name' : 'to', 'type' : 'text', 'disabled' : true})));

        container.insert(new Element('dt').insert(new Element('label').update(this._EMAIL_CC)));
        container.insert(new Element('dd').insert(new Element('input', {'name' : 'cc', 'type' : 'text', 'disabled' : true})));

        container.insert(new Element('dt').insert(new Element('label').update(this._EMAIL_SUBJECT)));
        container.insert(new Element('dd').insert(new Element('input', {'name' : 'subject', 'type' : 'text'})));

        container.insert(new Element('dt').insert(new Element('label').update(this._EMAIL_MESSAGE)));
        var text = new Element('textarea', {'name' : 'message'});
        container.insert(new Element('dd').insert(text));

        var sendButton = new Element('span', {"class" : "buttonwrapper"}).insert(new Element('input', {'name' : 'send', 'value' : this._SEND, 'class' : 'button'}));
        sendButton.on('click', function(event) {
            this.close();
            this._notifyUserMatch(this.matchId, this.subjectPatientId, this.subjectServerId,
                    this._contactContainer.down('textarea[name="message"]').value,
                    this._contactContainer.down('input[name="subject"]').value);
        }.bind(this));

        var cancelButton = new Element('span', {"class" : "buttonwrapper"}).insert(new Element('input', {'name' : 'cancel', 'value' : this._CANCEL, 'class' : 'button secondary'}));
        cancelButton.on('click', function(event) {
            this.close();
        }.bind(this));

        var buttons = new Element('div', {'class' : 'buttons'});
        buttons.insert(sendButton);
        buttons.insert(cancelButton);
        container.insert(buttons);

        return container;
    },

    _populateContactDialogContainer : function(content, subject, to, cc, from)
    {
         this._contactContainer.down('textarea[name="message"]').update(content);
         this._contactContainer.down('input[name="subject"]').value = subject;
         this._contactContainer.down('input[name="to"]').value = to.toString();
         this._contactContainer.down('input[name="cc"]').value = cc.toString();
         this._contactContainer.down('input[name="from"]').value = from.toString();
    },

    launchContactDialog  : function(matchId, subjectPatientId, subjectServerId)
    {
        new Ajax.Request(this._ajaxURL + matchId + "/email?method=GET", {
            contentType:'application/json',
            parameters : {
                'subjectPatientId' : subjectPatientId,
                'subjectServerId' : subjectServerId
            },
            onCreate : function() {
                this.matchId = matchId;
                this.subjectPatientId = subjectPatientId;
                this.subjectServerId = subjectServerId;
            }.bind(this),
            onSuccess : function(response) {
                if (!(response && response.responseJSON)) {
                    return;
                }

                var content = response.responseJSON.emailContent || '';
                var subject = response.responseJSON.subject || '';
                var to = response.responseJSON.recipients && response.responseJSON.recipients.to || '';
                var cc = response.responseJSON.recipients && response.responseJSON.recipients.cc || '';
                var from = response.responseJSON.recipients && response.responseJSON.recipients.from || '';

                this._populateContactDialogContainer(content, subject, to, cc, from);
                this.show();
            }.bind(this),
            onFailure : function(response) {
                var failureReason = response.statusText || response.responseText;
                if (response.statusText == '' /* No response */ || response.status == 12031 /* In IE */) {
                   failureReason = this._SERVER_ERROR_MESSAGE;
                }
                this._errorDialog.showError(this._CONTACT_PREVIEW_ERROR_HEADER, failureReason);
                this.close();
            }.bind(this),
            on0 : function (response) {
                this._errorDialog.showError(this._CONTACT_PREVIEW_ERROR_HEADER, this._SERVER_ERROR_MESSAGE);
                this.close();
            }.bind(this)
        });
    },

    _notifyUserMatch: function(matchID, subjectPatientId, subjectServerId, emailText, emailSubject)
    {
        new Ajax.Request(this._ajaxURL + matchID + "/email", {
            contentType : 'application/json',
            parameters : {'subjectPatientId' : subjectPatientId,
                          'subjectServerId' : subjectServerId,
                          'emailText': emailText,
                          'emailSubject': emailSubject},
            onSuccess : function (response) {
                if (!response.responseJSON || !response.responseJSON.results) {
                    this._errorDialog.showError(this._CONTACT_SEND_ERROR_HEADER, this._SERVER_ERROR_MESSAGE);
                    return;
                }
                if (!response.responseJSON.results.failed || response.responseJSON.results.failed.length == 0) {
                    this._errorDialog.showNotification('', this._SUCCESS_NOTIFICATION_MESSAGE, this._SUCCESS_NOTIFICATION_TITLE);

                    var notificationResult = response.responseJSON.results;
                    notificationResult.notifiedPatients = {};
                    notificationResult.notifiedPatients[matchID] = [ subjectPatientId ];
                    notificationResult.successNotificationHistories = response.responseJSON.successNotificationHistories || '';

                    var event = { 'notificationResult' : notificationResult };
                    document.fire("match:contacted:byuser", event);
                } else {
                    this._errorDialog.showError(this._CONTACT_SEND_ERROR_HEADER, this._CONTACT_SEND_FAILED_MESSAGE);
                }
            }.bind(this),
            onFailure : function (response) {
               this._errorDialog.showError(this._CONTACT_SEND_ERROR_HEADER, this._SERVER_ERROR_MESSAGE);
            }.bind(this)
        });
    }

  });
  return PhenoTips;
}(PhenoTips || {}));
