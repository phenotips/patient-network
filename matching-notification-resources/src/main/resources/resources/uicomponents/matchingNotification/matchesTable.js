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

        getRowsWithIdsAllInArray : function(ids)
        {
            var allTrs = this._tableElement.find('tbody').find('tr');
            return $.grep(allTrs, this._identifyTr(ids));
        },

        //////////////////

        _identifyTr : function(idsList)
        {
            // TODO in some cases true/false is not enough. For example, row represents matches 1,2. 1 is in the list of 2 is not.
            return function(tr)
            {
                var ids = String($(tr).data('matchid')).split(",").map(function(id) {return Number(id);});
                var allInList = true;
                for (var i=0; i<ids.length; i++) {
                    if ($.inArray(ids[i], idsList)==-1) {
                        allInList = false;
                    }
                }
                return allInList;
            };
        },

        _formatMatches : function()
        {
            this._matches.each(function (match)
            {
                // scores
                match.score = this._roundScore(match.score);
                match.phenotypicScore = this._roundScore(match.phenotypicScore);
                match.genotypicScore = this._roundScore(match.genotypicScore);

                // Phenotypes
                [match.reference.phenotypes, match.matched.phenotypes] =
                    this._formatPhenotypes(match.reference.phenotypes, match.matched.phenotypes);
            }.bind(this));
        },

        _roundScore : function(score)
        {
            return Math.round(Number(score) * 100) / 100;
        },

        // Format two JSON objects containing phenotypes for display
        _formatPhenotypes : function(obj1, obj2)
        {
            // Replace predefined with name field. For example {'id':'myid', 'name':'myname'} -> 'name'.
            var toName = function(phenotype) {return phenotype.name};
            obj1.predefined = $.map(obj1.predefined, toName);
            obj2.predefined = $.map(obj2.predefined, toName);

            // If there are no phenotypes, set empty to true
            obj1.empty = (obj1.predefined.size() + obj1.freeText.size() == 0);
            obj2.empty = (obj2.predefined.size() + obj2.freeText.size() == 0);

            return [obj1, obj2];
        },

        _rowWriter : function(rowIndex, record, columns, cellWriter)
        {
            var trClass = record.rejected ? 'rejected' : '';
            var tr = '<tr data-matchid="' + record.id + '" class="' + trClass + '">';

            // For each column in table, get record's attribute, or formatted element
            columns.each(function( column, index) {
                switch(column.id) {
                    case 'notification':
                        tr += this._getNotificationTd(record);
                        break;
                    case 'rejection':
                        tr += this._getRejectionTd(record);
                        break;
                    case 'referencePatient':
                        tr += this._getPatientDetailsTd(record.reference, 'referencePatientTd', record.id);
                        break;
                    case 'matchedPatient':
                        tr += this._getPatientDetailsTd(record.matched, 'matchedPatientTd', record.id);
                        break;
                    case 'email':
                        tr += this._simpleCellWriter(record.reference.email);
                        break;
                    case 'matchedHref':
                        tr += this._simpleCellWriter(record.matched.email);
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
            return '<td><input type="checkbox" class="notify" data-matchid="' + record.id + '"/></td>';
        },

        _getRejectionTd : function(record)
        {
            return '<td><input type="checkbox" class="reject" data-matchid="' + record.id + '" ' + (record.rejected ? 'checked ' : '') + '/></td>';
        },

        _simpleCellWriter : function(value)
        {
            return '<td style="text-align: left">' + value + '</td>';
        },

        _getPatientDetailsTd : function(patient, tdId, matchId)
        {
            var td = '<td id="' + tdId + '">';

            var patientHref = new XWiki.Document(patient.patientId, 'data').getURL();

            // Patient id and collapsible icon
            td += '<div class="fa fa-minus-square-o patient-div collapse-gp-tool" data-matchid="' + matchId + '">';
            td += '<a href="' + patientHref + '" target="_blank" class="patient-href">' + patient.patientId + '</a>';
            if (patient.serverId) {
                td += '<span class="server-span">(' + patient.serverId + ')</span>';
            }
            td += '</div>';

            // Collapsible div
            td += '<div class="collapse-gp-div" data-matchid="' + matchId + '">';

            // Genes
            var genes = patient.genes;
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
            var phenotypes = patient.phenotypes;
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

        _buildTable : function()
        {
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

        // Makes genes-div the same height in patient and matched patient columns
        _afterProcessTablePatientsDivs : function()
        {
            this._tableElement.find('tbody').find('tr').each(function (index, elm)
            {
                var referencePatientTd = $(elm).find('#referencePatientTd');
                var matchedPatientTd = $(elm).find('#matchedPatientTd');

                var genesDiv = $(referencePatientTd).find('.genes-div');
                var matchedGenesDiv = $(matchedPatientTd).find('.genes-div');

                var h = Math.max(genesDiv.height(), matchedGenesDiv.height());
                genesDiv.height(h);
                matchedGenesDiv.height(h);
            }.bind(this));
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
        }

    });
});
