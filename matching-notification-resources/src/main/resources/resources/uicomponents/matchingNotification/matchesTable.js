define(["jquery", "dynatable"], function($, dyna)
{
    return Class.create({

        // tableElement - the DOM element for table
        // afterProcessingCallback - a callback for after drawing the table
        initialize : function (tableElement, afterProcessingCallback)
        {
            this._tableElement = tableElement;
            this._afterProcessingCallback = afterProcessingCallback;

            this._tableBuilt = false;

            $('#expand_all').on('click', this._expandAllClicked.bind(this));
        },

        update : function(matches)
        {
            if (matches != undefined) {
                this._matches = JSON.parse(JSON.stringify(matches))
                this._formatMatches();
            }
            this._buildTable();
        },

        // filter is a function that filters out some matches.
        // For example: for filtering rejected matches.
        setFilter : function(filter)
        {
            this._filter = filter;
            this._buildTable();
        },

        getMarkedToNotify : function()
        {
            var ids = [];
            $.each(this._matches, function(index, match) {
                if (match.notify) {
                    $.each(String(match.id).split(","), function(idIndex, id) {
                        ids.push(id);
                    });
                }
            }.bind(this));
            return ids;
        },

        setState : function(matchIds, state)
        {
            var strMatchIds = String(matchIds).split(",");
            var matchesToSet = $.grep(this._matches, function(match) {
                var curIds = String(match.id).split(",");
                return this._listIsSubset(curIds, strMatchIds);
            }.bind(this));

            $.each(matchesToSet, function(index, match) {
                if (state.hasOwnProperty('notified')) {
                    match.notified = state.notified;
                }
                if (state.hasOwnProperty('notify')) {
                    match.notify = state.notify;
                }
                if (state.hasOwnProperty('status')) {
                    match.status = state.status;
                }
                // console.log('Set ' + match.id + ' to ' + JSON.stringify(state, null, 2));
            }.bind(this))
        },

        //////////////////

        // Return true if all elements of the first list are found in the second
        _listIsSubset : function(first, second)
        {
            for (var i = 0; i < first.length; i++) {
                if ($.inArray(first[i], second) == -1) {
                    return false;
                }
            }
            return true;
        },

        _formatMatches : function()
        {
            this._matches.each(function (match, index)
            {
                // add field for match row index
                match.rowIndex = index;

                // to-notify flag
                match.notify = match.notify || false;

                // validation flag
                match.status = match.status || '';

                // scores
                match.score = this._roundScore(match.score);
                match.phenotypicScore = this._roundScore(match.phenotypicScore);
                match.genotypicScore = this._roundScore(match.genotypicScore);

                // emails
                match.reference.emails = this._formatEmails(match.reference.emails);
                match.matched.emails = this._formatEmails(match.matched.emails);
            }.bind(this));
        },

        _formatEmails : function(emails) {
            if (emails.length == 0) {
                return "-";
            } else {
                return emails;
            }
        },

        _roundScore : function(score)
        {
            return Math.round(Number(score) * 100) / 100;
        },

        _rowWriter : function(rowIndex, record, columns, cellWriter)
        {
            var trClass = record.status ? 'status' : '';
            switch(record.status) {
                case 'success':
                    trClass += ' succeed';
                    break;
                case 'failure':
                    trClass += ' failed';
                    break;
            }
            var tr = '<tr id="row-' + record.rowIndex + '" data-matchid="' + record.id + '" class="' + trClass + '">';

            // For each column in table, get record's attribute, or formatted element
            columns.each(function( column, index) {
                switch(column.id) {
                    case 'notification':
                        tr += this._getNotificationTd(record);
                        break;
                    case 'status':
                        tr += this._getStatusTd(record);
                        break;
                    case 'referencePatient':
                        tr += this._getPatientDetailsTd(record.reference, 'referencePatientTd', record.id);
                        break;
                    case 'matchedPatient':
                        tr += this._getPatientDetailsTd(record.matched, 'matchedPatientTd', record.id);
                        break;
                    case 'referenceEmails':
                        tr += this._getEmailsTd(record.reference.emails);
                        break;
                    case 'matchedEmails':
                        tr += this._getEmailsTd(record.matched.emails);
                        break;
                    default:
                        tr += cellWriter(columns[index], record);
                        break;
                }
            }.bind(this));

            tr += '</tr>';

            return tr;
        },

        _getNotificationTd : function(record)
        {
            return '<td><input type="checkbox" class="notify" data-matchid="' + record.id + '" ' + (record.notify ? 'checked ' : '') + '/></td>';
        },

        _getStatusTd : function(record)
        {
            return '<td>'
            + '<select class="status" data-matchid="' + record.id +'">'
            + '<option value="uncategorized" '+ (record.status == "uncategorized" ? ' selected="selected"' : '') + '> </option>'
            + '<option value="saved" '+ (record.status == "saved" ? ' selected="selected"' : '') + '>saved</option>'
            + '<option value="rejected" '+ (record.status == "rejected" ? ' selected="selected"' : '') + '>rejected</option>'
            + '</select></td>';
        },

        _simpleCellWriter : function(value)
        {
            return '<td style="text-align: left">' + value + '</td>';
        },

        _getPatientDetailsTd : function(patient, tdId, matchId)
        {
            var td = '<td id="' + tdId + '">';

            // Patient id and collapsible icon
            td += '<div class="fa fa-minus-square-o patient-div collapse-gp-tool" data-matchid="' + matchId + '">';
            if (!patient.serverId) { // local patient
                var patientHref = new XWiki.Document(patient.patientId, 'data').getURL();
                td += '<a href="' + patientHref + '" target="_blank" class="patient-href">' + patient.patientId + '</a>';
                if (patient.serverId) {
                    td += '<span> (' + patient.serverId + ')</span>';
                }
            } else { // remote patient
                td += '<label class="patient-href">' + patient.patientId + ' (' + patient.serverId + ')</label>';
            }
            td += '</div>';

            // Collapsible div
            td += '<div class="collapse-gp-div" data-matchid="' + matchId + '">';

            td += this._getGenesDiv(patient.genes);
            td += this._getPhenotypesDiv(patient.phenotypes);
            td += this._getAgeOfOnset(patient.age_of_onset);
            td += this._getModeOfInheritance(patient.mode_of_inheritance);

            // End collapsible div
            td += '</div>';

            td += '</td>';
            return td;
        },

        _getGenesDiv : function(genes)
        {
            var td = '<div class="genes-div">';
            var genesTitle = 'Genes';
            if (genes.size() == 0) {
                genesTitle += ': -';
            }
            td += '<p class="subtitle">' + genesTitle + '</p>';
            if (genes.size() != 0) {
                td += '<ul>';
                for (var i = 0 ; i < genes.size() ; i++) {
                    td += '<li>' + genes[i] + '</li>';
                }
                td += '</ul>';
            }
            td += '</div>';
            return td;
        },

        _getPhenotypesDiv : function(phenotypes)
        {
            var empty = (phenotypes.predefined.size() + phenotypes.freeText.size() == 0);

            var td = '<div class="phenotypes-div">';
            var phenotypesTitle = 'Phenotypes';
            if (empty) {
                phenotypesTitle += ': -';
            }
            td += '<p class="subtitle">' + phenotypesTitle + '</p>';
            if (!empty) {
                td += '<ul>';
                td += this._addPhenotypes(phenotypes.predefined, false);
                td += this._addPhenotypes(phenotypes.freeText, true);
                td += '</ul>';
            }
            td += '</div>';
            return td;
        },

        _addPhenotypes : function(phenotypesArray, asFreeText)
        {
            var td = '';
            for (var i = 0 ; i < phenotypesArray.size() ; i++) {
                var observed = phenotypesArray[i].observed != "no";
                td += '<li>'
                if (asFreeText) {
                    td += '<div>';
                    td += '<span class="fa fa-exclamation-triangle" title="$services.localization.render('phenotips.patientSheetCode.termSuggest.nonStandardPhenotype')"/> ';
                }
                td += (!observed ? 'NO ' : '') + phenotypesArray[i].name;
                if (asFreeText) {
                    td += '</div>';
                }
                td += '</li>';
            }
            return td;
        },

        _getAgeOfOnset(age_of_onset)
        {
            var td = '<div class="age-of-onset-div">';

            var aooTitle = 'Age of onset: ';
            if (age_of_onset == '' || age_of_onset == undefined) {
                aooTitle += ': -';
            }
            td += '<p class="subtitle">' + aooTitle + '</p>';
            if (age_of_onset) {
                td += age_of_onset;
            }
            td += '</div>';
            return td;
        },

        _getModeOfInheritance(mode_of_inheritance)
        {
            var td = '<div class="mode-of-inheritance-div">';
            var moiTitle = 'Mode of inheritance: ';
            if (mode_of_inheritance.size() == 0) {
                moiTitle += ': -';
            }
            td += '<p class="subtitle">' + moiTitle + '</p>';
            if (mode_of_inheritance.size() != 0) {
                td += '<ul>';
                for (var i = 0 ; i < mode_of_inheritance.size() ; i++) {
                    td += '<li>' + mode_of_inheritance[i] + '</li>';
                }
                td += '</ul>';
            }
            td += '</div>';
            return td;
        },

        _getEmailsTd : function(emails)
        {
            var td = '<td>';
            for (var i=0; i<emails.length; i++) {
                td += '<div>' + emails[i] + '</div>';
            }
            td += '</td>';
            return td;
        },

        _buildTable : function()
        {
            if (!this._matches) {
                return;
            }
            var matchesToUse = $.grep(this._matches, this._filter || function(match) {return match});

            if (this._tableBuilt) {
                var table = this._tableElement.data('dynatable');
                table.settings.dataset.originalRecords = matchesToUse;
                table.process();
            } else {
                this._tableElement.dynatable({
                    dataset: {
                        records: matchesToUse
                    },
                    writers: {
                        _rowWriter: this._rowWriter.bind(this)
                    },
                    features: {
                        pushState : false
                    }
                }).bind('dynatable:afterProcess', this._afterProcessTable.bind(this));

                // first time needs to be run manually
                this._afterProcessTable();
            }

            this._tableBuilt = true;
        },

        _afterProcessTable : function()
        {
            this._afterProcessTableRegisterCollapisbleDivs();
            this._afterProcessTablePatientsDivs();
            this._expandAllClicked();

            this._afterProcessTableNotifyListeners();

            if (this._afterProcessingCallback != undefined) {
                this._afterProcessingCallback();
            }
        },

        _afterProcessTableRegisterCollapisbleDivs : function()
        {
            this._tableElement.find('.collapse-gp-tool').on('click', function(event) {
                this._expandCollapseGP(event.target);
            }.bind(this));
        },

        _afterProcessTableNotifyListeners : function()
        {
            this._tableElement.find('.notify').on('click', function(event) {
                this._markToNotify(event.target);
            }.bind(this));
        },

        _afterProcessTablePatientsDivs : function()
        {
            var referenceTh = $(this._tableElement.find('[data-dynatable-column="referencePatient"]')[0]);
            var matchedTh = $(this._tableElement.find('[data-dynatable-column="matchedPatient"]')[0]);
            var w = Math.max(referenceTh.width(), matchedTh.width());
            referenceTh.width(w);
            matchedTh.width(w);

            // Makes patient details divs the same height in patient and matched patient columns
            this._tableElement.find('tbody').find('tr').each(function (index, elm)
            {
                var referencePatientTd = $(elm).find('#referencePatientTd');
                var matchedPatientTd = $(elm).find('#matchedPatientTd');

                var divs = ['.genes-div', '.phenotypes-div', '.age-of-onset-div', '.mode-of-inheritance-div'];
                $.each(divs, function(key, div_class) {
                    this._makeSameHeight(referencePatientTd, matchedPatientTd, div_class);
                }.bind(this));
            }.bind(this));
        },

        _makeSameHeight : function(td1, td2, div_class)
        {
            var div1 = $(td1).find(div_class);
            var div2 = $(td2).find(div_class);

            var h = Math.max(div1.height(), div2.height());
            div1.height(h);
            div2.height(h);
        },

        // target is the component that was clicked to expand/collapse (this +/- sign).
        // expand is boolean, when undefined, the value will be understood from target
        _expandCollapseGP : function(target, expand)
        {
            var matchId = $(target).data('matchid');

            // collapse/expand divs
            this._tableElement.find('[data-matchid="' + matchId + '"].collapse-gp-div').each(function (index, elm) {
                if (expand == undefined) {
                    expand = $(elm).hasClass('collapsed');
                }
                if (expand) {
                    $(elm).removeClass("collapsed");
                } else {
                    $(elm).addClass("collapsed");
                }
            }.bind(this));

            // change display of collapse/display component (+/-)
            this._tableElement.find('[data-matchid="' + matchId + '"].collapse-gp-tool').each(function (index, elm) {
                if (expand) {
                    $(elm).removeClass("fa-plus-square-o");
                    $(elm).addClass("fa-minus-square-o");
                } else {
                    $(elm).removeClass("fa-minus-square-o");
                    $(elm).addClass("fa-plus-square-o");
                }
            }.bind(this));
        },

        _expandAllClicked : function()
        {
            var checkbox = $('#expand_all');
            var expand = $(checkbox).is(':checked');
            this._tableElement.find('.collapse-gp-tool').each(function (index, elm) {
                this._expandCollapseGP(elm, expand);
            }.bind(this));
        },

        _markToNotify : function(elm)
        {
            var matchIndex = $(elm).closest('tr').attr('id').split('-')[1];
            this._matches[matchIndex].notify = $(elm).is(':checked');
            console.log("To notify: " + this._matches[matchIndex].id);
            this.update();
        }
    });
});
