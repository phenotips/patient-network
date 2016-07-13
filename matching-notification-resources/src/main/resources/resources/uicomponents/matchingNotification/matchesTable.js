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
        this._filterRejected = false;

        $('#find-matches-button').on('click', this._findMatches.bind(this));
        $('#show-matches-button').on('click', this._showMatches.bind(this));
        $('#send-notifications-button').on('click', this._sendNotification.bind(this));
        $('#notify_all').on('click', this._notifyAllClicked.bind(this));
        $('#filter_rejected').on('click', this._filterRejectedClicked.bind(this));
        $('#expand_all').on('click', this._expandAllClicked.bind(this));
    },

    _findMatches : function()
    {
        var _this = this;

        var score = this._checkScore('find-matches-score', 'find-matches-messages');
        if (score == undefined) {
            return;
        }
        new Ajax.Request(_this._ajaxURL, {
            parameters : {action : 'find-matches',
                          score  : score
            },
            onSuccess : function (response) {
                _this._showSuccess('find-matches-messages');
                console.log("find matches result, score = " + score);
                console.log(response.responseJSON);

                _this._$('#show-matches-score').val(score);
                _this._showMatches();
            },
            onFailure : function (response) {
                _this._showFailure('find-matches-messages');
            }
        });
        this._showSent('find-matches-messages');
    },

    _showMatches : function()
    {
        var _this = this;
        var score = _this._checkScore('show-matches-score', 'show-matches-messages');
        if (score == undefined) {
            return;
        }
        new Ajax.Request(this._ajaxURL, {
            parameters : {action   : 'show-matches',
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
        });
        _this._showSent('show-matches-messages');
    },

    _sendNotification : function()
    {
        var _this = this;
        var ids = this._readMatchesToNotify();
        var idsJson = JSON.stringify({ ids: ids});
        console.log("Sending " + idsJson);
        new Ajax.Request(_this._ajaxURL, {
            parameters : {action : 'send-notifications',
                          ids    : idsJson
            },
            onSuccess : function (response) {
                console.log("onSuccess, received:");
                console.log(response.responseText);
                _this._showSuccess('send-notifications-messages');

               _this._onSuccessSendNotification(response.responseJSON.results);
            },
            onFailure : function (response) {
                _this._showFailure('send-notifications-messages');
            }
        });
        this._showSent('send-notifications-messages');
    },

    _rejectMatch : function(target)
    {
        var _this = this;

        var matchId = this._$(target).data("matchid");
        var reject = this._$(target).is(':checked');
        var ids = JSON.stringify({ ids: [matchId]});

        new Ajax.Request(this._ajaxURL, {
            parameters : {action : 'reject-matches',
                          ids    : ids,
                          reject : reject
            },
            onSuccess : function (response) {
                _this._onSuccessRejectMatch(response.responseJSON.results, reject);
            },
            onFailure : function (response) {
                console.log(response);
            }
        });
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

    _onSuccessSendNotification : function(results)
    {
        var _this = this;

        var successfulIds = this._$.grep(results, function(item) {return item.success} ).map(function(item) {return item.id});
        var failedIds     = this._$.grep(results, function(item) {return !item.success}).map(function(item) {return item.id});

        // remove notified matches
        this._matches = this._$.grep(this._matches, function(item) {return _this._$.inArray(item.id, successfulIds)==-1});

        this._buildTable();

        // update table
        failedIds.each(function (item) {
            var tr = _this._$('#matchesTable').find("#tr_" + item);
            tr.addClass('failed');
            tr.find('.notify').attr('checked', 'checked');
        });
    },

    // when reject is true, request was sent to reject. When false, request was sent to unreject.
    _onSuccessRejectMatch : function(results, reject)
    {
        var _this = this;

        var successfulIds = this._$.grep(results, function(item) {return item.success} ).map(function(item) {return item.id});
        var failedIds     = this._$.grep(results, function(item) {return !item.success}).map(function(item) {return item.id});

        this._buildTable();

        // mark un/rejected in model
        this._$.grep(this._matches, function(match) {return _this._$.inArray(match.id, successfulIds)>-1})
            .each(function(match) {match.rejected = reject});

        // update table
        successfulIds.each(function (id) {
            var tr = _this._$('#matchesTable').find("#tr_" + id);
            if (reject) {
                tr.addClass('rejected');
                tr.find('.reject').attr('checked', 'checked');
            } else {
                tr.removeClass('rejected');
                tr.find('.reject').removeAttr('checked');
            }
        });

        failedIds.each(function (id) {
            var tr = _this._$('#matchesTable').find("#tr_" + id);
            tr.addClass('failed');
            if (reject) {
                tr.find('.reject').removeAttr('checked');
            } else {
                tr.find('.reject').attr('checked', 'checked');
            }
        });
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

        if (this._filterRejected) {
            matchesToUse = this._$.grep(this._matches, function(item) {return !item.rejected;});
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

            // first time needs to be run manually
            this._processingComplete();
        }

        this._tableBuilt = true;
    },

    _rowWriter : function(rowIndex, record, columns, cellWriter)
    {
        var tr = '';

        // notification checkbox
        var notification = '<td><input type="checkbox" class="notify" data-matchid="' + record.id + '" ' + (record.notified ? 'checked disabled ' : '') + '/></td>';

        // rejection checkbox
        var rejection = '<td><input type="checkbox" class="reject" data-matchid="' + record.id + '" ' + (record.rejected ? 'checked ' : '') + '/></td>';

        // reference patient and matched patient, their genes and phenotypes
        patientTd  = this._getPatientDetailsTd(record.patientId, record.genes, record.phenotypes, 'patientTd', record.id);
        matchedPatientTd = this._getPatientDetailsTd(record.matchedPatientId, record.matchedGenes, record.matchedPhenotypes, 'matchedPatientTd', record.id);

        // grab the record's attribute for each column.
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

        var trClass = record.rejected ? 'rejected' : '';

        return '<tr id="tr_' + record.id + '" class="' + trClass + '">' + tr + '</tr>';
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

        this._$('#matchesTable').find('[class*="reject"]').on('click', function(event) {
            this._rejectMatch(event.target);
        }.bind(this));

        this._expandAllClicked();
    },

    _notifyAllClicked : function(event)
    {
        var checked = event.target.checked;
        this._$('#matchesTable').find(".notify").each(function (index, elm) {
            if (!elm.disabled) {
                elm.checked = checked;
            }
        });
    },

    _filterRejectedClicked : function(event)
    {
        var checked = event.target.checked;
        this._filterRejected = checked;
        this._buildTable();
    },

    _readMatchesToNotify : function()
    {
        var ids = [];
        this._$('#matchesTable').find(".notify").each(function (index, elm) {
            if (elm.checked && !elm.disabled) {
                var id = Number(this._$(elm).data('matchid'));
                ids.push(String(id));
            }
        }.bind(this));
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