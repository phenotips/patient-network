require.config({
  paths: {
    dynatable: ["$!services.webjars.url('jquery-dynatable', 'jquery.dynatable.js')"]
  }
});

require(['jquery'], function($) {
   require(['dynatable'], function(dyna) {
      var loadMNM = function($) {
         new PhenoTips.widgets.MatchingNotificationManager($);
      };

      (XWiki.domIsLoaded && loadMNM($)) || document.observe("xwiki:dom:loaded", loadMNM.bind(this, $));
   });
});

var PhenoTips = (function (PhenoTips) {
  var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
  widgets.MatchingNotificationManager = Class.create({

    initialize : function ($)
    {
      this._ajaxURL = new XWiki.Document('WebHome', 'MatchingNotification').getURL('get') + '?outputSyntax=plain';
      this._$ = $;
      this._tableBuilt = false;

      this._matches = undefined;
      this._notificationResults = undefined;
      this._filteredNotified = false;

      $('#find-matches-button').on('click', this._findMatches.bind(this));
      $('#show-matches-button').on('click', this._showMatches.bind(this));
      $('#send-notifications-button').on('click', this._sendNotification.bind(this));
      $('#notify_all').on('click', this._notifyAllClicked.bind(this));
      $('#checkbox_filter_notified').on('click', this._filterNotifiedClicked.bind(this));
      $('#expand_all').on('click', this._expandAllClicked.bind(this));
    },

    _findMatches : function()
    {
        var _this = this;

        var score = this._checkScore('find-matches-score', 'find-matches-messages');
        if (score == undefined) {
          return;
        }
        new Ajax.Request(_this._ajaxURL,
          {  parameters : {action : 'find-matches',
                           score  : score
             },
             onSuccess : function (response) {
                _this._showSuccess('find-matches-messages');
                console.log("find matches result, score = " + score);
                console.log(response.responseJSON);
             },
             onFailure : function (response) {
                _this._showFailure('find-matches-messages');
             }
          }
        );
        this._showSent('find-matches-messages');

        this._$('#show-matches-score').val(score);
        this._showMatches();
    },

    _showMatches : function()
    {
      var _this = this;
      var score = _this._checkScore('show-matches-score', 'show-matches-messages');
      if (score == undefined) {
        return;
      }
      new Ajax.Request(this._ajaxURL,
        {  parameters : {action   : 'show-matches',
                         score    : score,
                         notified : false
           },
           onSuccess : function (response) {
              _this._showSuccess('show-matches-messages');
              console.log("show matches result, score = " + score);
              console.log(response.responseJSON);
              _this._matches = _this._formatMatches(response.responseJSON.matches);
              _this._buildTable();
           },
           onFailure : function (response) {
              _this._showFailure('show-matches-messages');
           }
        }
      );
      _this._showSent('show-matches-messages');
    },

    _sendNotification : function()
    {
       var _this = this;
       var ids = this._readMatchesToNotify();
       var idsJson = JSON.stringify({ ids: ids});
       console.log("Sending " + idsJson);
       new Ajax.Request(_this._ajaxURL,
          { parameters : {action : 'send-notifications',
                          idsForNotification : idsJson
            },
            onSuccess : function (response) {
               console.log("onSuccess, received:");
               console.log(response.responseText);
               _this._showSuccess('send-notifications-messages');
              _this._notificationResults = response.responseJSON;
              _this._showMatches();
            },
            onFailure : function (response) {
               _this._showFailure('send-notifications-messages');
            }
          }
       );
       this._showSent('send-notifications-messages');
    },

    _checkScore : function(scoreFieldName, messagesFieldName) {
      var score = this._$('#' + scoreFieldName).val();
      if (score == undefined || score == "") {
         this._showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.emptyScore')");
         return;
      } else if (isNaN(score)) {
         this._showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.invalidScore')");
         return;
      };
      scoreNumber = Number(score);
      if (scoreNumber < 0 || scoreNumber > 1) {
         this._showHint(messagesFieldName, "$services.localization.render('phenotips.matchingNotifications.invalidScore')");
         return;
      }
      return score;
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
    },

    _formatMatches : function(matches)
    {
       for (var i = 0 ; i < matches.length ; i++) {
          // scores
          matches[i].score = this._roundScore(matches[i].score);
          matches[i].phenotypicScore = this._roundScore(matches[i].phenotypicScore);
          matches[i].genotypicScore = this._roundScore(matches[i].genotypicScore);

          // remote id
          var remoteId = matches[i].remoteId;
          if (remoteId == undefined || remoteId == 'undefined') {
             matches[i].remoteId = '-';
          }

          // Phenotypes
          [matches[i].phenotypes, matches[i].matchedPhenotypes] = this._formatPhenotypes(matches[i].phenotypes, matches[i].matchedPhenotypes);
       }
       return matches;
    },

    // Format two JSON objects containing phenotypes for display (compare to formatGenesArrays)
    _formatPhenotypes : function(obj1, obj2)
    {
       // Replace predefined {key,value} phenotypes with value-s.
       for (var i=0; i<obj1.predefined.size(); i++) {
          obj1.predefined[i] = obj1.predefined[i].name;
       }
       for (var i=0; i<obj2.predefined.size(); i++) {
          obj2.predefined[i] = obj2.predefined[i].name;
       }

       // If there are no phenotypes, push emptiness sign to predefined
       obj1.empty = (obj1.predefined.size() + obj1.freeText.size() == 0);
       obj2.empty = (obj2.predefined.size() + obj2.freeText.size() == 0);

       return [obj1, obj2];
    },

    _roundScore : function(score)
    {
        return Math.round(Number(score) * 100) / 100;
    },

    _buildTable : function()
    {
      var _this = this;

      var matchesToUse = this._matches;

      if (this._filteredNotified) {
          var unnotifiedMatches = [];
          for (var i = 0 ; i < this._matches.length ; i++) {
              var current = this._matches[i];
              if (!current.notified) {
                 unnotifiedMatches.push(current);
              }
          }
          matchesToUse = unnotifiedMatches;
      }

      if (this._tableBuilt) {
         var table = this._$('#matchesTable').data('dynatable');
         table.settings.dataset.originalRecords = matchesToUse;
         table.process();
      } else {
         this._$('#matchesTable').dynatable({
            dataset: {
               records: matchesToUse
            },
            writers: {
               _rowWriter: this._rowWriter.bind(this)
            },
            features: {
               pushState : false
            }
         }).bind('dynatable:afterProcess', this._processingComplete.bind(this));
         this._processingComplete();
      }

      // Mark failed notifications
      if (this._notificationResults != undefined) {
         var results = this._notificationResults.results;
         if (results != undefined) {
             var failed = this._$.grep(results, function(item) {return item.success == false;});
             failed.each(function (item) {
                 var tr = _this._$('#matchesTable').find("#tr_" + item.id);
                 tr.attr('class', 'failed');
                 tr.find('[id^=notify_]').attr('checked', 'checked');
             });
         }
      }

       this._tableBuilt = true;
    },

    _rowWriter : function(rowIndex, record, columns, cellWriter)
    {
       var tr = '';

       // notification checkbox
       var notification = '<td><input type="checkbox" id="notify_' + record.id + '" ' + (record.notified ? 'checked disabled ' : '') + '/></td>';

       // rejection checkbox
       var rejection = '<td><input type="checkbox" class="reject" data-matchid="' + record.id + '"/></td>';

       // reference patient and matched patient, their genes and phenotypes
       patientTd  = this._getPatientDetailsTd(record.patientId, record.genes, record.phenotypes, 'patientTd', record.id);
       matchedPatientTd = this._getPatientDetailsTd(record.matchedPatientId, record.matchedGenes, record.matchedPhenotypes, 'matchedPatientTd', record.id);

       // grab the record's attribute for each column.
       // skip first column (index 0), it's for checkbox
       for (var i = 0, len = columns.length; i < len; i++) {
         var id = columns[i].id;
         if (id == 'notification') {
            tr += notification;
         } else if (id == 'rejection') {
            tr += rejection;
         } else if (id == 'patient') {
            tr += patientTd;
         } else if (id == 'matchedPatient') {
            tr += matchedPatientTd;
         } else {
             tr += cellWriter(columns[i], record);
         }
       }

       return '<tr id="tr_' + record.id + '">' + tr + '</tr>';
    },

    _getPatientDetailsTd : function(patientId, genes, phenotypes, tdId, matchId)
    {
       var td = '<td id="' + tdId + '">';

       var patientHref = new XWiki.Document(patientId, 'data').getURL();

       // Patient id and collapsible icon
       td += '<div class="fa fa-minus-square-o patient-div collapse-gp-tool" data-matchid="' + matchId + '">';
       td += '<a href="' + patientHref + '" target="_blank" class="patient-href">' + patientId + '</a>';
       td += '</div>';

       // Collapsible div
       td += '<div class="collapse-gp-div" data-matchid="' + matchId + '">';

       // Genes
       td += '<div class="genes-div">';
       var genesTitle = 'Genes';
       if (genes.size() == 0) {
           genesTitle += ': -';
       }
       td += '<div class="subtitle">' + genesTitle + '</div>';
       if (genes.size() != 0) {
          td += '<ul>';
          for (var i = 0 ; i < genes.size() ; i++) {
              td += '<li>' + genes[i] + '</li>';
          }
          td += '</ul>';
       }
       td += '</div>';

       // Phenotypes
       td += '<div class="phenotypes-div">';
       var phenotypesTitle = 'Phenotypes';
       if (phenotypes.empty) {
           phenotypesTitle += ': -';
       }
       td += '<div class="subtitle">' + phenotypesTitle + '</div>';
       if (!phenotypes.empty) {
          td += '<ul>';
          for (var i = 0 ; i < phenotypes.predefined.size() ; i++) {
              td += '<li>' + phenotypes.predefined[i] + '</li>';
          }
          for (var i = 0 ; i < phenotypes.freeText.size() ; i++) {
             td += '<li>'
             td += '<div>';
             td += '<span class="fa fa-exclamation-triangle" title="$services.localization.render('phenotips.patientSheetCode.termSuggest.nonStandardPhenotype')"/> ';
             td += phenotypes.freeText[i];
             td += '</div>';
             td += '</li>';
          }
          td += '</ul>';
       }
       td += '</div>';

       // End collapsible div
       td += '</div>';

       td += '</td>';
       return td;
    },

    _processingComplete : function(specificTd)
    {
       // Makes genes-div the same height in patient and matched patient columns
       this._$('#matchesTable > tbody').find('tr').each(function (index, elm)
       {
          var patientTd = this._$(elm).find('#patientTd');
          var matchedPatientTd = this._$(elm).find('#matchedPatientTd');

          var genesDiv = this._$(patientTd).find('.genes-div');
          var matchedGenesDiv = this._$(matchedPatientTd).find('.genes-div');

          var h = Math.max(genesDiv.height(), matchedGenesDiv.height());
          genesDiv.height(h);
          matchedGenesDiv.height(h);
       }.bind(this));

       // Register listeners to components in table
       this._$('#matchesTable').find('[class*="collapse-gp-tool"]').on('click', function(event) {
          this._expandCollapseGP(event.target);
       }.bind(this));

       this._expandAllClicked();
    },

    _notifyAllClicked : function(event)
    {
       var checked = event.target.checked;
       this._$('#matchesTable').find("[id^=notify_]").each(function (index, elm) {
          if (!elm.disabled) {
             elm.checked = checked;
          }
       });
    },

    _filterNotifiedClicked : function(event)
    {
       var checked = event.target.checked;
       this._filteredNotified = checked;
       this._buildTable();
    },

    _readMatchesToNotify : function()
    {
       var ids = [];
       this._$('#matchesTable').find("[id^=notify_]").each(function (index, elm) {
          if (elm.checked && !elm.disabled) {
             var id = Number(elm.id.substr(elm.id.indexOf('_')+1));
             ids.push(String(id));
          }
       });
       return ids;
    },

    // target is the component that was clicked to expand/collapse (this +/- sign).
    // expand is boolean, when undefined, the value will be understood from target
    _expandCollapseGP : function(target, expand)
    {
        var matchId = this._$(target).data('matchid');

       // collapse/expand divs
       this._$('#matchesTable').find('[data-matchid=' + matchId + '].collapse-gp-div').each(function (index, elm) {

          if (expand == undefined) {
             expand = this._$(elm).hasClass('collapsed');
          }

          if (expand) {
             this._$(elm).removeClass("collapsed");
          } else {
             this._$(elm).addClass("collapsed");
          }

       }.bind(this));

       // change display of collapse/display component (+/-)
       this._$('#matchesTable').find('[data-matchid=' + matchId + '].collapse-gp-tool').each(function (index, elm) {

          if (expand) {
             this._$(elm).removeClass("fa-plus-square-o");
             this._$(elm).addClass("fa-minus-square-o");
          } else {
             this._$(elm).removeClass("fa-minus-square-o");
             this._$(elm).addClass("fa-plus-square-o");
          }

       }.bind(this));

    },

    _expandAllClicked : function()
    {
       var checkbox = this._$('#expand_all');
       var expand = this._$(checkbox).is(':checked');
       this._$('#matchesTable').find('[class*="collapse-gp-tool"]').each(function (index, elm) {
          this._expandCollapseGP(elm, expand);
       }.bind(this));
    }

  });
  return PhenoTips;
}(PhenoTips || {}));

