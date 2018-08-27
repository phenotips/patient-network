require(["matchingNotification/utils",
         "matchingNotification/matcherPaginator",
         "matchingNotification/matcherPageSizer",
         "matchingNotification/matcherContactDialog"],
        function(utils) {
            var loadMNM = function(utils) {
            new PhenoTips.widgets.MatchesTable(utils);
        };

    (XWiki.domIsLoaded && loadMNM(utils)) || document.observe("xwiki:dom:loaded", loadMNM.bind(this, utils));
});

var PhenoTips = (function (PhenoTips) {
    var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
    widgets.MatchesTable = Class.create({

    initialize : function (utils)
    {
        this._tableElement = $('matchesTable');
        this._ajaxURL = XWiki.contextPath + "/rest/patients/matching-notification/";
        this._tableCollabsed = true;
        $("panels-livetable-ajax-loader").hide();

        this._offset = 1;
        this._maxResults = 50;
        this._minScore = 0;
        this.page = 1;
        this.pagination = $('pagination-matching-notifications');
        this.pagination && this.pagination.hide();
        this.resultsSummary = $('panels-livetable-limits');
        this.paginator = new PhenoTips.widgets.MatcherPaginator(this, this.pagination, this._maxResults);

        this._isAdmin = $('isAdmin');
        this._presentServerIds = [];

        this._utils = new utils(this._tableElement);

        $('show-matches-button').on('click', this._showMatches.bind(this));
        $('send-notifications-button').addClassName("disabled");
        $('send-notifications-button').on('click', this._sendNotification.bind(this));
        $('expand_all').on('click', this._expandAllClicked.bind(this));

        $('show-matches-score').on('input', function() {this._utils.clearHint('score-validation-message');}.bind(this));
        $('show-matches-phen-score').on('input', function() {this._utils.clearHint('score-validation-message');}.bind(this));
        $('show-matches-gen-score').on('input', function() {this._utils.clearHint('score-validation-message');}.bind(this));

        $('show-matches-score').on('change', function() {this.validateScore($('show-matches-score').value, 'show-matches-score', 'score-validation-message', true);}.bind(this));
        $('show-matches-phen-score').on('change', function() {this.validateScore($('show-matches-phen-score').value, 'show-matches-phen-score', 'score-validation-message', true);}.bind(this));
        $('show-matches-gen-score').on('change', function() {this.validateScore($('show-matches-gen-score').value, 'show-matches-gen-score', 'score-validation-message', true);}.bind(this));

        this._PAGE_COUNT_TEMPLATE = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.matchesTable.pagination.footer'))";
        this._AGE_OF_ONSET = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.email.table.ageOfOnset.label'))";
        this._MODE_OF_INHERITANCE = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.modeOfInheritance'))";
        this._NOT_OBSERVED = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.notObserved'))";
        this._GENES = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.email.table.genes.label'))";
        this._PHENOTYPES = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.email.table.phenotypes.label'))";
        this._NOTIFY = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.notify'))";
        this._NONE_STANDART_PHENOTYPE = "$escapetool.javascript($services.localization.render('phenotips.patientSheetCode.termSuggest.nonStandardPhenotype'))";
        this._SAVED = "$escapetool.xml($services.localization.render('phenotips.matchingNotifications.table.saved'))";
        this._REJECTED = "$escapetool.xml($services.localization.render('phenotips.matchingNotifications.table.rejected'))";
        this._HAS_EXOME_DATA = "$escapetool.xml($services.localization.render('phenotips.matchingNotifications.table.hasExome.label'))";
        this._CONTACT_BUTTON_LABEL = "$escapetool.xml($services.localization.render('phenotips.myMatches.contactButton.label'))";
        this._CONTACTED_LABEL = "$escapetool.xml($services.localization.render('phenotips.myMatches.contacted.label'))";
        this._SERVER_ERROR_MESSAGE = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.serverFailed'))";
        this._PUBMED = "$escapetool.javascript($services.localization.render('phenotips.similarCases.pubcase.link'))";
        this._SOLVED_CASE = "$escapetool.javascript($services.localization.render('phenotips.similarCases.solvedCase'))";
        this._CONTACT_ERROR_DIALOG_TITLE = "$escapetool.xml($services.localization.render('phenotips.myMatches.contact.dialog.error.title'))";

        this._initiateFilters();

        this._contactDialog = new PhenoTips.widgets.MatcherContactDialog();
        this._errorDialog = new PhenoTips.widgets.ErrorDialog(this._CONTACT_ERROR_DIALOG_TITLE);

        // memorise filers open/close state
        var filtersButton = $('toggle-filters-button');
        var key = this._utils.getCookieKey(filtersButton.up('.entity-directory').id);
        this._toggleFilters(filtersButton.up('.xwiki-livetable-topfilters-tip'), (XWiki.cookies.read(key) == 'hidden'));
        filtersButton.observe('click', function(event) {
            event.stop();
            this._toggleFilters(event.findElement('.xwiki-livetable-container .xwiki-livetable-topfilters-tip'));
        }.bind(this));

        // set behaviour for hide/show email columns triggers
        this._initiateEmailColumnsBehaviur();

        $('checkbox-server-filters').hide();

        $('contentmenu') && $('contentmenu').hide();
        $('hierarchy') && $('hierarchy').hide();

        document.observe("notified", this._handleNotifiedUpdate.bind(this));

        // set initial scores
        $('show-matches-score').value = 0.5;
        $('show-matches-phen-score').value = 0;
        $('show-matches-gen-score').value = 0.1;

        if (!this._isAdmin) {
            this._showMatches();
        }

        // defines sorting order for UI column sorting elements events listeners,
        // values: 1 if descending, -1 if ascending
        this._scoreSortingOrder = 1;
        this._genotypicScoreSortingOrder = 1;
        this._phenotypicScoreSortingOrder = 1;
        this._foundTimestampSortingOrder = 1;
        $$('th[data-column="score"]')[0] && $$('th[data-column="score"]')[0].on('click', function(event) {this._sortByColumn(event.element(), 'score');}.bind(this));
        $$('th[data-column="genotypicScore"]')[0] && $$('th[data-column="genotypicScore"]')[0].on('click', function(event) {this._sortByColumn(event.element(), 'genotypicScore');}.bind(this));
        $$('th[data-column="phenotypicScore"]')[0] && $$('th[data-column="phenotypicScore"]')[0].on('click', function(event) {this._sortByColumn(event.element(), 'phenotypicScore');}.bind(this));
        $$('th[data-column="foundTimestamp"]')[0] && $$('th[data-column="foundTimestamp"]')[0].on('click', function(event) {this._sortByColumn(event.element(), 'foundTimestamp');}.bind(this));

        Event.observe(window, 'resize', this._buildTable.bind(this));
    },

    _sortByColumn : function(element, propName) {
    	var orderPropName = "_" + propName + "SortingOrder";
    	this[orderPropName] = this[orderPropName] * -1;
    	var el = (element && element.down('.fa')) ? element.down('.fa') : element;
    	if (el) {
    		if (el.hasClassName('fa-sort')) {
    			el.removeClassName('fa-sort');
    			(this[orderPropName] == -1) && el.addClassName('fa-sort-down');
    			(this[orderPropName] == 1) && el.addClassName('fa-sort-up');
    		} else {
    		    el.toggleClassName('fa-sort-up');
    		    el.toggleClassName('fa-sort-down');
    		}
    	}
        this._cachedMatches.sort( function(a, b) {
            if(a[propName] < b[propName]) return -1 * this[orderPropName];
            if(a[propName] > b[propName]) return 1 * this[orderPropName];
            return 0;
        }.bind(this));
        this._update();
    },

    validateScore : function(score, className, messagesFieldName, applyMinScore) {
        // minimum allowed scores: can be less than initial, but no less than some values
        if (!this._isAdmin) {
            var minAllowedValue = { 'show-matches-score': 0.4,
                                    'show-matches-phen-score': 0,
                                    'show-matches-gen-score': 0 };
        } else {
            var minAllowedValue = { 'show-matches-score': 0.1,
                                    'show-matches-phen-score': 0,
                                    'show-matches-gen-score': 0 };
        }

        if (score == undefined || score == "" || Number(score) < minAllowedValue[className]) {
            if (className == 'show-matches-score') {
                this._utils.showHint(messagesFieldName, "For performance reasons currently only matches with overall score of at least " + minAllowedValue[className] + " can be retrieved", "invalid");
            }

            if (applyMinScore) {
                $(className).value = minAllowedValue[className];
                return minAllowedValue[className];
            }
        } else if (isNaN(score) || Number(score) < 0 || Number(score) > 1) {
            this._utils.showHint(messagesFieldName, "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.invalidScore'))", "invalid");
            return undefined;
        }
        return score;
    },

    _initiateFilters : function()
    {
        this._filterValues = {};
        this._filterValues.matchAccess = {"owner" : true, "edit" : true, "manage" : true, "view" : true, "match" : true};
        this._filterValues.matchStatus = {"rejected" : true, "saved" : true, "uncategorized" : true};
        this._filterValues.ownerStatus = {"me" : true, "group" : true, "public" : false, "others": false};
        this._filterValues.notified  = {"notified" : $$('input[name="notified-filter"][value="notified"]')[0].checked,
                                        "unnotified" : $$('input[name="notified-filter"][value="unnotified"]')[0].checked};
        this._filterValues.score  = {"score" : 0, "phenotypicScore" : 0, "genotypicScore" : 0};
        this._filterValues.geneStatus  = "all";
        this._filterValues.externalId  = "";
        this._filterValues.email       = "";
        this._filterValues.geneSymbol  = "";
        this._filterValues.phenotype   = "";

        this._filterValues.serverIds   = [{"local" : true}];
        $$('input[name="checkbox-server-id-filter"]').each(function (checkbox) {
            this._filterValues.serverIds[checkbox.value] = checkbox.checked;
        }.bind(this));

        $$('input[name="status-filter"]').each(function (checkbox) {
            checkbox.on('click', function(event) {
                this._filterValues.matchStatus[event.currentTarget.value] = event.currentTarget.checked;
                this._update();
            }.bind(this));
        }.bind(this));

        $$('input[name="ownership-filter"]').each(function (checkbox) {
            checkbox.on('click', function(event) {
                this._filterValues.ownerStatus[event.currentTarget.value] = event.currentTarget.checked;
                this._update(this._advancedFilter);
            }.bind(this));
        }.bind(this));

        $('gene-status-filter').on('change', function(event) {
            this._filterValues.geneStatus = event.currentTarget.value;
            this._update();
        }.bind(this));

        $$('input[name="access-filter"]').each(function (checkbox) {
            checkbox.on('click', function(event) {
                if (event.currentTarget.value == "owner") {
                    this._filterValues.matchAccess["owner"]  = event.currentTarget.checked;
                } else {
                    this._filterValues.matchAccess["edit"]   = event.currentTarget.checked;
                    this._filterValues.matchAccess["manage"] = event.currentTarget.checked;
                    this._filterValues.matchAccess["view"]   = event.currentTarget.checked;
                }
                this._update();
            }.bind(this));
        }.bind(this));

        $$('input[name="notified-filter"]').each(function (checkbox) {
            checkbox.on('click', function(event) {
                this._filterValues.notified[event.currentTarget.value] = event.currentTarget.checked;
                this._update();
            }.bind(this));
        }.bind(this));

        $$('input[class="score-filter"]').each(function (input) {
            input.on('input', function(event) {
                this._utils.clearHint('score-filter-validation-message');
            }.bind(this));
        }.bind(this));
        $$('input[class="score-filter"]').each(function (input) {
            input.on('change', function(event) {
                var score = this.validateScore(event.currentTarget.value, event.currentTarget.id, 'score-filter-validation-message');
                if (score == undefined) {
                    return;
                }
                this._filterValues.score[event.currentTarget.name] = event.currentTarget.value;
                this._update();
            }.bind(this));
        }.bind(this));

        $$('input[name="checkbox-server-id-filter"]').each(function (checkbox) {
            checkbox.on('click', function(event) {
                this._filterValues.serverIds[checkbox.value] = checkbox.checked;
                this._update();
            }.bind(this));
        }.bind(this));

        $('external-id-filter').on('input', function(event) {
            this._filterValues.externalId = event.currentTarget.value.toLowerCase();
            this._update();
        }.bind(this));

        $('email-filter').on('input', function(event) {
            this._filterValues.email = event.currentTarget.value.toLowerCase();
            this._update();
        }.bind(this));

        $('gene-symbol-filter').on('input', function(event) {
            this._filterValues.geneSymbol = event.currentTarget.value.toLowerCase();
            this._update();
        }.bind(this));

        $('phenotype-filter').on('input', function(event) {
            this._filterValues.phenotype = event.currentTarget.value.toLowerCase();
            this._update();
        }.bind(this));

        $('global-search-input').on('input', function(event) {
            this._filterValues.globalFilter = event.currentTarget.value.toLowerCase();
            this._update(this._globalFilter);
        }.bind(this));

        this._globalFilter = function (match) {
            return ( match.matched.patientId.toLowerCase().includes(this._filterValues.globalFilter) // filter by search input in patient ID, external ID and emails
             || match.reference.patientId.toLowerCase().includes(this._filterValues.globalFilter)
             || match.matched.externalId.toLowerCase().includes(this._filterValues.globalFilter)
             || match.reference.externalId.toLowerCase().includes(this._filterValues.globalFilter)
             || match.matched.emails.toString().toLowerCase().includes(this._filterValues.globalFilter)
             || match.reference.emails.toString().toLowerCase().includes(this._filterValues.globalFilter)
             || match.matched.genes.toString().toLowerCase().includes(this._filterValues.globalFilter)
             || match.reference.genes.toString().toLowerCase().includes(this._filterValues.globalFilter)
             || match.reference.genes.toString().toLowerCase().includes(this._filterValues.globalFilter)
             || match.matched.serverId.toLowerCase().includes(this._filterValues.globalFilter)
             || match.reference.serverId.toLowerCase().includes(this._filterValues.globalFilter)
             || match.phenotypes.toString().toLowerCase().includes(this._filterValues.globalFilter)
             || this._filterValues.serverIds[match.reference.serverId]
             || this._filterValues.serverIds[match.matched.serverId]
             || (match.isLocal && this._filterValues.serverIds["local"]))
             && this._advancedFilter(match);
        }.bind(this);

        this._advancedFilter = function (match) {
            var hasExternalIdMatch = match.matched.patientId.toLowerCase().includes(this._filterValues.externalId) // filter by search input in patient ID, external ID and emails
                || match.reference.patientId.toLowerCase().includes(this._filterValues.externalId)
                || match.matched.externalId.toLowerCase().includes(this._filterValues.externalId)
                || match.reference.externalId.toLowerCase().includes(this._filterValues.externalId);
            var hasEmailMatch = match.matched.emails.toString().toLowerCase().includes(this._filterValues.email)
                || match.reference.emails.toString().toLowerCase().includes(this._filterValues.email);
            var hasGeneSymbolMatch = match.matched.genes.toString().toLowerCase().includes(this._filterValues.geneSymbol)
                || match.reference.genes.toString().toLowerCase().includes(this._filterValues.geneSymbol);
            var hasCheckboxServerIDsMatch = this._filterValues.serverIds[match.reference.serverId]
                || this._filterValues.serverIds[match.matched.serverId]
                || (match.isLocal && this._filterValues.serverIds["local"]);
            // by match access type (owned or shares cases for non admin users)
            var hasAccessTypeMath = this._filterValues.matchAccess[match.matched.access]
                || this._filterValues.matchAccess[match.reference.access];
            // by gene matching statuses (candidate-candidate, solved-candidate, has exome data matches)
            var hasGeneExomeTypeMatch = match.matchingGenesTypes.indexOf(this._filterValues.geneStatus) > -1;
            // by match status (rejected, saved, uncategorized)
            var hasMatchStatusMatch = this._filterValues.matchStatus[match.status];

            var recordOwnershipMatch = function(record) {
                                         return (this._filterValues.ownerStatus["me"] && record.ownership.userIsOwner)
                                             || (this._filterValues.ownerStatus["group"] && record.ownership.userGroupIsOwner)
                                             || (this._filterValues.ownerStatus["public"] && record.ownership.publicRecord)
                                       }.bind(this);
            var recordOwnershipOther = function(record) {
                                         return record.serverId == ""
                                                && !record.ownership.userIsOwner
                                                && !record.ownership.userGroupIsOwner
                                                && !record.ownership.publicRecord
                                       }.bind(this);
            var referenceRecordMatch = match.reference.serverId == "" && recordOwnershipMatch(match.reference);
            var matchedRecordMatch = match.matched.serverId == "" && recordOwnershipMatch(match.matched);
            var hasOwnershipMatch = referenceRecordMatch || matchedRecordMatch
                                    || ((recordOwnershipOther(match.reference) || recordOwnershipOther(match.matched)) && this._filterValues.ownerStatus["others"]);

            var hasPhenotypeMatch = match.phenotypes.toString().toLowerCase().includes(this._filterValues.phenotype);
            var isNotifiedMatch = match.notified && this._filterValues.notified.notified || !match.notified && this._filterValues.notified.unnotified;
            var hasScoreMatch = match.score >= this._filterValues.score.score
                             && match.phenotypicScore >= this._filterValues.score.phenotypicScore
                             && match.genotypicScore >= this._filterValues.score.genotypicScore;
            return hasExternalIdMatch && hasEmailMatch && hasGeneSymbolMatch && hasAccessTypeMath && hasOwnershipMatch
                       && hasMatchStatusMatch && hasGeneExomeTypeMatch && hasPhenotypeMatch && isNotifiedMatch && hasScoreMatch && hasCheckboxServerIDsMatch;
        }.bind(this);
    },

    _toggleFilters : function (filtersElt, forceHide) {
        if (filtersElt) {
            filtersElt.toggleClassName('collapsed', forceHide);
            filtersElt.up('.xwiki-livetable-container').toggleClassName('hidden-filters', forceHide);
            var key = this._utils.getCookieKey(filtersElt.up('.entity-directory').id);
            if (filtersElt.hasClassName('collapsed')) {
                XWiki.cookies.create(key, 'hidden', '');
                $('advanced-filters-header').hide();
            } else {
                XWiki.cookies.erase(key);
                $('advanced-filters-header').show();
            }
        }
    },

    _initiateEmailColumnsBehaviur : function()
    {
        this.collapseEmails = {"referenceEmails" : false, "matchedEmails" : false};
        this._tableElement.select('th .collapse-marker').each(function (elm) {
            elm.on('click', function(event) {
                var trigger = event.element();
                var columnName = trigger.up().dataset.column;
                var hide = trigger.hasClassName('fa-angle-double-left');
                this.collapseEmails[columnName] = hide;
                trigger.toggleClassName('fa-angle-double-left');
                trigger.toggleClassName('fa-angle-double-right');
                this._tableElement.select('td[name="' + columnName + '"] div[name="notification-email-long"]').each(function (email) {
                    hide ? email.hide() : email.show();
                });
                this._tableElement.select('td[name="' + columnName + '"] div[name="notification-email-short"]').each(function (email) {
                    hide ? email.show() : email.hide();
                });
            }.bind(this));
        }.bind(this));
    },

    _showMatches : function()
    {
        this._utils.clearHint('show-matches-messages');
        this._cachedMatches = [];
        this._matches = [];

        var options = this._generateOptions();
        if (!options) return;

        new Ajax.Request(this._ajaxURL + 'show-matches', {
            contentType:'application/json',
            parameters : options,
            onCreate : function () {
                $("panels-livetable-ajax-loader").show();
                this._utils.clearHint('score-validation-message');
                this._utils.clearHint('send-notifications-messages');
                $('send-notifications-button').addClassName("disabled");
            }.bind(this),
            onSuccess : function (response) {
                if (response.responseJSON) {
                    console.log("Show matches response JSON (min scores: " + options.score + "/" + options.phenScore + "/" + options.genScore + "):");
                    console.log(response.responseJSON);

                    if (response.responseJSON.hasOwnProperty("results")) {
                        var matches = response.responseJSON;
                        this._cachedMatches = JSON.parse(JSON.stringify(matches.results));
                        this._formatMatches();
                    }
                } else {
                    this._utils.showFailure('show-matches-messages');
                }
            }.bind(this),
            onFailure : function (response) {
                this._utils.showFailure('show-matches-messages');
            }.bind(this),
            onComplete : function () {
                $("panels-livetable-ajax-loader").hide();
                this._update();
            }.bind(this)
        });
    },

    // Generate options for matches search AJAX request
    _generateOptions : function()
    {
        var score = this.validateScore($('show-matches-score').value, 'show-matches-score', 'show-matches-messages', true);
        var phenScore = this.validateScore($('show-matches-phen-score').value, 'show-matches-phen-score', 'show-matches-messages', true);
        var genScore = this.validateScore($('show-matches-gen-score').value, 'show-matches-gen-score', 'show-matches-messages', true);
        if (score == undefined || phenScore == undefined || genScore == undefined) {
            return;
        }

        var options = { 'score'        : score,
                        'phenScore'    : phenScore,
                        'genScore'     : genScore,
                        'onlyNotified' : false };
        return options;
    },

    // params:
    // filter - Filter function to apply to matches
    _update : function(filter)
    {
        var tableBody = this._tableElement.down('tbody');
        tableBody.update('');

        if (!this._cachedMatches || this._cachedMatches.length == 0) {
            this.pagination.hide();
            this.resultsSummary.hide();
        } else {
            this._matches = this._cachedMatches.filter( (filter) ? filter : this._advancedFilter);
            if (!this._matches || this._matches.length == 0) {
                return;
            }

            this.pagination.show();
            this.resultsSummary.show();

            this.totalResultsCount = this._matches.length;
            this.totalPages = Math.ceil(this.totalResultsCount/this._maxResults);
            this.page = 1;

            this._buildTable();
        }
    },

    changePageSize: function(newLimit) {
        this._maxResults = newLimit;
        this.totalPages = Math.ceil(this.totalResultsCount/this._maxResults);
        this.page = 1;
        this._buildTable();
    },

    _displaySummary : function (message, container, isHeader) {
        var c = container || this._tableElement;
        var summary = new Element("p", {"class" : "summary"});
        if (isHeader && this.page) {
          summary.update(message);
          new PhenoTips.widgets.MatcherPageSizer(this, summary, null, this._maxResults)
        } else {
          summary.update(message);
        }

        c.update(summary);
        return summary;
    },

// FORMATTING MATCHES BEFORE TABLE BUILD

    _formatMatches : function()
    {
        // collect server IDs that are present in the match data
        // to show/hide corresponding filter checkboxes after table processing (see this._afterProcessTableHideApsentServerIdsFromFilter())
        this._presentServerIds = [];

        this._cachedMatches.each( function (match, index) {
            // add field for match row index
            match.rowIndex = index;

            // validation flag
            match.status = match.status || '';

            // for serverId search
            match.matched.serverId = match.matched.serverId || '';
            match.reference.serverId = match.reference.serverId || '';

            match.matched.access = match.matched.access || '';
            match.reference.access = match.reference.access || '';

            match.isLocal = (match.matched.serverId == '' && match.reference.serverId == '');
            match.isLocal && this._presentServerIds.push("local");

            if (match.matched.serverId != '') {
                if (!this._filterValues.serverIds.hasOwnProperty(match.matched.serverId)) {
                    // do not show matches that have server ID that is not pre-generated in velocity,
                    // i.e. are not in the valid (enabled) list of servers
                    this._filterValues.serverIds[match.matched.serverId] = false;
                } else {
                    this._presentServerIds.push(match.matched.serverId);
                }
            }
            if (match.reference.serverId != '') {
                if (!this._filterValues.serverIds.hasOwnProperty(match.reference.serverId)) {
                    this._filterValues.serverIds[match.reference.serverId] = false;
                } else {
                    this._presentServerIds.push(match.reference.serverId);
                }
            }

            // scores
            match.score = this._utils.roundScore(match.score);
            match.phenotypicScore = this._utils.roundScore(match.phenotypicScore);
            match.genotypicScore = this._utils.roundScore(match.genotypicScore);

            // emails
            match.reference.emails = this._formatEmails(match.reference.emails);
            match.matched.emails = this._formatEmails(match.matched.emails);

            // get validated email addressed for notification preview dialog
            match.matched.validatedEmails = this._getValidatedEmails(match.matched.emails);
            match.reference.validatedEmails = this._getValidatedEmails(match.reference.emails);

            // build array of types of genes matches for future faster filtering
            // possible types:  ["solved_solved", "solved_candidate", "candidate_solved", "candidate_candidate"]
            // FUTURE: more possible values, ex. "candidate_exome"
            match.matchingGenesTypes = this._buildMatchingGenesTypes(match);

            // record whether match is local, outgoing, remote for future faster filtering
            match.remoteType = this._getRemoteType(match);

            // aggregate phenotypes from both reference and matched patients for future faster filtering
            match.phenotypes = this._aggregatePhenotypes(match);

            //sort made of inheritance: those that matched to be first in alphabetic order
            this._organiseModeOfInheritance(match);

            this._organiseGenes(match);
        }.bind(this));

        // leave only uniq server ids in the array, remove duplicates
        this._presentServerIds = this._presentServerIds.uniq();

        // sort by match found timestamp in descending order
        this._sortByColumn(false, 'foundTimestamp');
    },

    _organiseModeOfInheritance : function(match) {
        //common modes of inheritance
        if (match.reference.mode_of_inheritance.length == 0 || match.matched.mode_of_inheritance == 0) {
            return;
        }
        var common = match.reference.mode_of_inheritance.intersect(match.matched.mode_of_inheritance).sort();
        match.reference.mode_of_inheritance = common.concat(match.reference.mode_of_inheritance.sort()).uniq();
        match.matched.mode_of_inheritance = common.concat(match.matched.mode_of_inheritance.sort()).uniq();
    },

    _organiseGenes : function(match)
    {
        var matchGenes = match.matched.genes.clone();
        var referenceGenes = match.reference.genes.clone();
        var commonGenes = matchGenes.intersect(referenceGenes);
        match.matched.genes = commonGenes.concat(matchGenes).uniq();
        match.reference.genes = commonGenes.concat(referenceGenes).uniq();
    },

    _aggregatePhenotypes : function(match)
    {
        var matchPhenotypes = [];
        var allPhenotypes = match.matched.phenotypes.predefined.concat(match.reference.phenotypes.predefined)
                                                               .concat(match.reference.phenotypes.freeText)
                                                               .concat(match.matched.phenotypes.freeText);
        allPhenotypes.each(function (elm) {
            matchPhenotypes.push(elm.name);
        });
        return matchPhenotypes;
    },

    _getRemoteType : function(match)
    {
        if (match.matched.serverId == "" && match.reference.serverId == "") {
            return "local";
        }
        if (match.matched.serverId != "" && match.reference.serverId == "") {
            return "outgoing";
        }
        if (match.matched.serverId == "" && match.reference.serverId != "") {
            return "incoming";
        }
    },

    _buildMatchingGenesTypes : function(match) {
        var matchedGenesTypes = [];
        matchedGenesTypes.push('all'); // for showing all types if 'all' selected
        if (match.genotypicScore > 0 && match.matched.genes.size() > 0 && match.reference.genes.size() > 0) {
            var status1 = match.matched.genesStatus ? match.matched.genesStatus : "candidate";
            var status2 = match.reference.genesStatus ? match.reference.genesStatus : "candidate";
            matchedGenesTypes.push(status1 + '_' + status2);
            matchedGenesTypes.push(status2 + '_' + status1);
            if (match.matched.hasExomeData || match.reference.hasExomeData) {
                matchedGenesTypes.push('has_exome');
            }
        }
        // remove possible repetitions
        return matchedGenesTypes.uniq();
    },

    _formatEmails : function(emails) {
        if (emails.length == 0) {
            return [];
        } else {
            var formattedEmails = []
            for (var i=0; i < emails.length; i++) {
                if (emails[i].startsWith('mailto:')) {
                    emails[i] = emails[i].replace('mailto:', '');
                }
                // do not split URLs, but split multiple emails
                var splitted = (emails[i].indexOf("://") > -1) ? [ emails[i] ] : emails[i].split(',');
                formattedEmails = formattedEmails.concat(splitted);
            }
            return formattedEmails;
        }
    },

    _getValidatedEmails : function(emails) {
        if (emails.length == 0) {
            return [];
        } else {
            var validatedEmails = []
            for (var i=0; i < emails.length; i++) {
                if (this._utils.validateEmail(emails[i])) {
                    validatedEmails.push(emails[i]);
                }
            }
            return validatedEmails;
        }
    },

// GENERATE TABLE

    // this is for MatcherPaginator to work
    launchSearchExisting : function()
    {
        this._buildTable();
    },

    _buildTable : function()
    {
        var tableBody = this._tableElement.down('tbody');
        tableBody.update('');

        if (!this._matches) {
            return;
        }

        var begin = this._maxResults*(this.page - 1);
        var end = Math.min(this.page*this._maxResults, this.totalResultsCount);
        var matchesForPage = this._matches.slice(begin, end);
        this.paginator.refreshPagination(this._maxResults);

        var firstItemRangeNo = begin + 1;
        var lastItemRangeNo = end;
        var tableSummary = this._PAGE_COUNT_TEMPLATE.replace(/___caseRange___/g, firstItemRangeNo + "-" + lastItemRangeNo).replace(/___totalCases___/g, this.totalResultsCount).replace(/___numCasesPerPage___/g, "");
        this._displaySummary(tableSummary, $('panels-livetable-limits'), true);

        matchesForPage.each( function (match, index) {
            var tr = this._rowWriter(index, match, this._tableElement.select('.second-header-row th'), this._simpleCellWriter);
            tableBody.insert(tr);
        }.bind(this));

        this._afterProcessTable();
    },

    _rowWriter : function(rowIndex, record, columns, cellWriter)
    {
        var tr = '<tr id="row-' + record.rowIndex + '" data-matchid="' + record.id + '">';

        // For each column in table, get record's attribute, or formatted element
        columns.each(function(column, index) {
            switch(column.dataset.column) {
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
                    tr += this._getEmailsTd(record.reference.emails, record.reference.patientId, record.id[0] ? record.id[0] : record.id, record.reference.serverId, 'referenceEmails', record.reference.isOwner, record.reference.pubmedId);
                    break;
                case 'matchedEmails':
                    tr += this._getEmailsTd(record.matched.emails, record.matched.patientId, record.id[0] ? record.id[0] : record.id, record.matched.serverId, 'matchedEmails', record.matched.isOwner, record.matched.pubmedId);
                    break;
                case 'notified':
                    tr+= this._getNotified(record);
                    break;
                default:
                    tr += cellWriter(record[column.dataset.column]);
                    break;
            }
        }.bind(this));

        tr += '</tr>';

        return tr;
    },

    _getStatusTd : function(record)
    {
        if (this._tableElement.down('th[data-column="status"]').hasClassName("hidden")) {
            return '';
        }
        return '<td>'
        + '<select class="status" data-matchid="' + record.id +'">'
        + '<option value="uncategorized" '+ (record.status == "uncategorized" ? ' selected="selected"' : '') + '> </option>'
        + '<option value="saved" '+ (record.status == "saved" ? ' selected="selected"' : '') + '>' + this._SAVED + '</option>'
        + '<option value="rejected" '+ (record.status == "rejected" ? ' selected="selected"' : '') + '>' + this._REJECTED + '</option>'
        + '</select></td>';
    },

    _simpleCellWriter : function(value)
    {
        return '<td style="text-align: center">' + value + '</td>';
    },

    _getPatientDetailsTd : function(patient, tdId, matchId)
    {
        var td = '<td id="' + tdId + '">';
        var externalId = (!this._utils.isBlank(patient.externalId)) ? " : " + patient.externalId : '';
        // Patient id and collapsible icon
        td += '<div class="fa fa-minus-square-o patient-div collapse-gp-tool" data-matchid="' + matchId + '"></div>';
        if (patient.serverId == '') { // local patient
            var patientHref = new XWiki.Document(patient.patientId, 'data').getURL();
            td += '<a href="' + patientHref + '" target="_blank" class="patient-href">' + patient.patientId + externalId + '</a>';
        } else { // remote patient
            // TODO pass a server name in JSON as well to display a server name instead if server ID in the table
            td += '<label class="patient-href">' + patient.patientId + externalId + ' (' + patient.serverId + ')</label>';
        }

        // Collapsible div
        td += '<div class="collapse-gp-div" data-matchid="' + matchId + '">';

        td += this._getAgeOfOnset(patient.age_of_onset);
        td += this._getModeOfInheritance(patient.mode_of_inheritance);
        td += this._getGenesDiv(patient.genes, patient.hasExomeData, patient.genesStatus);
        td += this._getPhenotypesDiv(patient.phenotypes);

        // End collapsible div
        td += '</div>';

        td += '</td>';
        return td;
    },

    _getGenesDiv : function(genes, hasExomeData, genesStatus)
    {
        var td = '<div class="genes-div">';
        var genesTitle = this._GENES;
        if (genes.size() == 0) {
            genesTitle += ' -';
        }
        td += '<p class="subtitle">' + genesTitle;
        if (genesStatus && genesStatus.length > 0 && genes.size() > 0) {
            td += ' (' + genesStatus + ')';
        }
        if (hasExomeData) {
            td += '<span class="exome-subtitle">' + this._HAS_EXOME_DATA + '</span>';
        }
        td += '</p>';
        if (genes.size() != 0) {
            td += '<ul>';
            for (var i = 0 ; i < genes.size() ; i++) {
                var gene = genes[i].replace(' (candidate)', '').replace(' (solved)', ''); //just in case of cashed/saved status with gene symbol
                td += '<li>' + gene + '</li>';
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
        var phenotypesTitle = this._PHENOTYPES;
        if (empty) {
            phenotypesTitle += ' -';
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
                td += '<span class="fa fa-exclamation-triangle" title="' + this._NONE_STANDART_PHENOTYPE + '"/> ';
            }

            td += (!observed ? this._NOT_OBSERVED + ' ' : '') + phenotypesArray[i].name;
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

        var aooTitle = this._AGE_OF_ONSET;
        if (age_of_onset == '' || age_of_onset == undefined) {
            aooTitle += ' -';
        }
        td += '<p class="subtitle">' + aooTitle + '</p>';
        if (age_of_onset) {
            td += '<ul><li>' + age_of_onset + '</li></ul>';
        }
        td += '</div>';
        return td;
    },

    _getModeOfInheritance(mode_of_inheritance)
    {
        var td = '<div class="mode-of-inheritance-div">';
        var moiTitle = this._MODE_OF_INHERITANCE;
        if (mode_of_inheritance.size() == 0) {
            moiTitle += ' -';
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

    _getEmailsTd : function(emails, patientId, matchId, serverId, cellName, isOwner, pubmedID)
    {
        var td = '<td name="' + cellName + '">';
        // if case is solved and has a Pubmed ID - display a link to it onstead of emails
        if (!this._utils.isBlank(pubmedID)) {
            var href = "http://www.ncbi.nlm.nih.gov/pubmed/?term=" + pubmedID.trim();
            td += '<a href=' + href + ' target="_blank"><span class="fa fa-leanpub" title="' + this._PUBMED + '"></span>PMID: ' + pubmedID + '<span class="metadata">' + this._SOLVED_CASE + '</span></a></td>';
            return td;
        }
        for (var i=0; i < emails.length; i++) {
            var email = emails[i]
            if (email.indexOf("://") > -1) {
                email = email.split('/')[2];
                email = '<a href=' + emails[i] + ' target="_blank">' + email + '</a>';
            } else {
                if (i != emails.length - 1) {
                    email += ", ";
                }
                // insert a 0-width space after the @, so that a long email can be split into two lines
                email = email.replace(/@/g,"@&#8203;");
                // insert a "preferred-no-split <span> around emails, to make sure lines are first split on ",",
                // and only after that on "@"
                email = email.replace(/([^, ]+?@[^ ]+)/g,"<span class='avoidwrap'>$1</span> ")
            }
            td += '<div name="notification-email-long">' + email + '</div>';
        }
        var shortEmail = (emails.length > 0) ? (emails[0].substring(0, 9) + "...") :  "";
        td += '<div name="notification-email-short">' + shortEmail + '</div>';

        //if logged as admin - add notification checkbox for local PC patient email contact but not for self (not for patients owned by admin)
        if (this._isAdmin && serverId == '' && emails.length > 0) {
            td += '<span class="fa fa-envelope" title="' + this._NOTIFY + '"></span> <input type="checkbox" class="notify" data-matchid="' + matchId + '" data-patientid="'+ patientId +'" data-emails="'+ emails.toString() +'">';
        }
        td += '</td>';
        return td;
    },

    _getNotified : function(record)
    {
        var td = '<td style="text-align: center" name="contacted">';
        var accessAboveEdit = record.matched.access == "edit"   || record.reference.access == "edit"
                           || record.matched.access == "owner"  || record.reference.access == "owner"
                           || record.matched.access == "manage" || record.reference.access == "manage";
        if (!this._isAdmin && accessAboveEdit && (this._utils.isBlank(record.matched.pubmedId) || this._utils.isBlank(record.reference.pubmedId))) {
            var matchId = record.id[0] ? record.id[0] : record.id;
            var patientID = (record.matched.isOwner) ? record.reference.patientId : record.matched.patientId;
            var serverId = (record.matched.isOwner) ? record.reference.serverId : record.matched.serverId;
            var validatedEmails = (record.matched.isOwner) ? record.reference.validatedEmails : record.matched.validatedEmails;
            if (validatedEmails.length > 0) {
                if (record.notified == false) {
                    td += '<a class="contact-button" data-matchid="'
                        + matchId + '" data-patientid="'
                        + patientID + '" data-serverid="'
                        + serverId + '"href="#"><span class="fa fa-envelope"></span>'+ this._CONTACT_BUTTON_LABEL +'</a>';
                } else {
                    td += this._CONTACTED_LABEL;
                }
            } else {
                if (record.notified == true) {
                    td += this._CONTACTED_LABEL;
                }
            }
        } else {
            if (record.notified == true) {
                td += this._CONTACTED_LABEL;
            }
        }
        td += '</td>';
        return td;
    },

// -- AFTER PROCESS TABLE

    _afterProcessTable : function()
    {
        this._afterProcessTablePatientsDivs();
        this._afterProcessTableRegisterCollapisbleDivs();
        this._afterProcessTableStatusListeners();
        this._afterProcessTableCollapseEmails();
        this._afterProcessTableInitNotificationEmails();
        this._afterProcessTableHideApsentServerIdsFromFilter();
    },

    _afterProcessTableHideApsentServerIdsFromFilter : function()
    {
        if (this._presentServerIds.length > 1) {
            $('checkbox-server-filters').show();
            $('matching-filters').select('input[type="checkbox"][name="checkbox-server-id-filter"]').each( function(selectEl) {
                (this._presentServerIds.indexOf(selectEl.value) >= 0) ? selectEl.up().show() : selectEl.up().hide();
            }.bind(this));
        } else {
            $('checkbox-server-filters').hide();
        }
    },

    _afterProcessTableStatusListeners : function()
    {
        this._tableElement.select('.status').each( function(selectEl) {
            selectEl.on('change', this._setMatchesStatus.bind(this));
        }.bind(this));
    },

    _afterProcessTableRegisterCollapisbleDivs : function()
    {
        this._tableElement.select('.collapse-gp-tool').each(function (elm) {
            var element = elm;
            elm.on('click', function(event) {
                var expand = elm.hasClassName("fa-plus-square-o");
                this._expandCollapseGP(element, expand);
            }.bind(this));
            this._expandCollapseGP(elm, !this._tableCollabsed);
        }.bind(this));
    },

    _afterProcessTablePatientsDivs : function()
    {
        // Makes patient details divs the same height in patient and matched patient columns
        this._tableElement.select('tbody tr').each( function (elm, index) {
            var referencePatientTd = elm.down('#referencePatientTd');
            var matchedPatientTd = elm.down('#matchedPatientTd');

            var divs = ['.genes-div', '.phenotypes-div', '.age-of-onset-div', '.mode-of-inheritance-div'];
            divs.each(function(div_class, skey) {
                this._makeSameHeight(referencePatientTd, matchedPatientTd, div_class);
            }.bind(this));
        }.bind(this));
    },

    _afterProcessTableCollapseEmails : function()
    {
        ["referenceEmails", "matchedEmails"].each(function (columnName) {
            var hide = this.collapseEmails[columnName];
            this._tableElement.select('td[name="' + columnName + '"] div[name="notification-email-long"]').each(function (email) {
                hide ? email.hide() : email.show();
            });
            this._tableElement.select('td[name="' + columnName + '"] div[name="notification-email-short"]').each(function (email) {
                hide ? email.show() : email.hide();
            });
        }.bind(this));
    },

    _afterProcessTableInitNotificationEmails : function()
    {
        $$('input[type=checkbox][class="notify"]').each(function (elm) {
            elm.on('click', function(event) {
                if (this._getMarkedToNotify().length > 0) {
                    $('send-notifications-button').removeClassName("disabled");
                } else {
                    $('send-notifications-button').addClassName("disabled");
                }
            }.bind(this));
        }.bind(this));

        $$('a[class="contact-button"]').each(function (elm) {
            elm.on('click', function(event) {
                event.stop();
                this._contactDialog.launchContactDialog(elm.dataset.matchid, elm.dataset.patientid, elm.dataset.serverid);
            }.bind(this));
        }.bind(this));
    },

    _makeSameHeight : function(td1, td2, div_class)
    {
        var div1 = td1.down(div_class);
        var div2 = td2.down(div_class);

        var h = Math.max(parseInt(div1.getStyle("height")), parseInt(div2.getStyle("height")));
        div1.setStyle({"height": h + 'px'});
        div2.setStyle({"height": h + 'px'});
    },

    // target is the component that was clicked to expand/collapse (this +/- sign).
    // expand is boolean, when undefined, the value will be understood from target
    _expandCollapseGP : function(target, expand)
    {
        var matchId = target.dataset.matchid;

        // collapse/expand divs
        this._tableElement.select('[data-matchid="' + matchId + '"].collapse-gp-div').each(function (elm, index) {
            (expand) ? elm.show(): elm.hide();
        }.bind(this));

        // change display of collapse/display component (+/-)
        this._tableElement.select('[data-matchid="' + matchId + '"].collapse-gp-tool').each(function (elm) {
            if (expand) {
                elm.removeClassName("fa-plus-square-o");
                elm.addClassName("fa-minus-square-o");
            } else {
                elm.addClassName("fa-plus-square-o");
                elm.removeClassName("fa-minus-square-o");
            }
        }.bind(this));
    },

    _expandAllClicked : function(event)
    {
        var checkbox = event.element();
        if (!checkbox) return;
        this._tableCollabsed = !checkbox.checked;
        this._tableElement.select('.collapse-gp-tool').each(function (elm) {
            this._expandCollapseGP(elm, !this._tableCollabsed);
        }.bind(this));
    },

//-- NOTIFICATION

    _sendNotification : function()
    {
        var ids = this._getMarkedToNotify();
        if (ids.length == 0) {
            this._utils.showFailure('show-matches-messages', "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.notify.noContactSelected'))");
            return;
        }
        this._notifyMatchByIDs(ids);
    },

    _notifyMatchByIDs  : function(matchIDs)
    {
        // console.log("Sending " + idsToNotify);
        var idsToNotify = JSON.stringify({ ids: matchIDs});
        new Ajax.Request(this._ajaxURL + 'send-admin-local-notifications', {
            parameters : {'ids' : idsToNotify},
            onCreate : function (response) {
                // console.log("Notification request sent");
                $('send-notifications-button').addClassName("disabled");
                this._utils.showSent('send-notifications-messages');
            }.bind(this),
            onSuccess : function (response) {
                if (!response.responseJSON || !response.responseJSON.results) {
                    this._errorDialog.showError(this._CONTACT_SEND_ERROR_HEADER, this._SERVER_ERROR_MESSAGE);
                    return;
                }
                this._updateTableState(response.responseJSON.results, true);
            }.bind(this),
            onFailure : function (response) {
                this._errorDialog.showError(this._CONTACT_SEND_ERROR_HEADER, this._SERVER_ERROR_MESSAGE);
            }.bind(this),
            onComplete : function () {
                this._utils.clearHint('send-notifications-messages');
            }.bind(this)
        });
    },

    _getMarkedToNotify : function()
    {
        var ids = [];
        var checkedElms = this._tableElement.select('input[data-patientid]:checked');

        checkedElms.each(function (elm, index) {
            if (elm.dataset.matchid && elm.dataset.patientid) {
                ids.push({'matchId' : elm.dataset.matchid, 'patientId' : elm.dataset.patientid});
            }
        });
        return ids;
    },

//--POST_PROCESS NOTIFICATION
    _handleNotifiedUpdate: function(event)
    {
        if (!event || !event.memo || !event.memo.results) {
            return;
        }
        console.log("Send notification - reply received:");
        console.log(event.memo.results);

        this._utils.showReplyReceived('send-notifications-messages');

        var results = event.memo.results;

        this._updateTableState(results, false);
    },

    _updateTableState : function (results, isAdminNotification) {
        // un-check all notification checkboxes, only admin sees them
        if (isAdminNotification) {
            this._tableElement.select('input[type=checkbox][class="notify"]').each(function (contactCheckbox) {
                contactCheckbox.checked = false;
            });
        }

        if (results.success && results.success.length > 0 ) {
            var properties = {'notified': true, 'state': 'success', 'isAdminNotification': isAdminNotification};
            this._setState(results.success, properties);
        }
        if (results.failed && results.failed.length > 0) {
            var message = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.matchesTable.onFailureAlert')) " + this._getAllEmailsForMatchIds(results.failed);
            this._errorDialog.showError(this._CONTACT_SEND_ERROR_HEADER, message);
            var properties = {'state': 'failure', 'isAdminNotification': isAdminNotification};
            this._setState(results.failed, properties);
        }
    },

    _getAllEmailsForMatchIds : function(matchIds)
    {
        var emails = [];
        matchIds.each(function (matchId) {
            var notifyCheckbox = this._tableElement.down('input[data-matchid="'+matchId+'"');
            var emailsArray = notifyCheckbox.dataset.emails && notifyCheckbox.dataset.emails.split(',');
            emails = emails.concat(emailsArray);
        }.bind(this));
        return emails;
    },

    _setState : function(matchIds, properties)
    {
        var strMatchIds = String(matchIds).split(",");
        var matchesToSet = [];
        this._matches.each( function(match) {
            var curIds = String(match.id).split(",");
            if ( (strMatchIds.length < curIds.length && this._utils.listIsSubset(strMatchIds, curIds))
                || (strMatchIds.length >= curIds.length && this._utils.listIsSubset(curIds, strMatchIds)) ){
                    matchesToSet.push(match);
                }
        }.bind(this));

        matchesToSet.each( function(match, index) {
            if (properties.hasOwnProperty('notified')) {
                // updating Contacted column
                var contactedTd = this._tableElement.down('tr[data-matchid="' + match.id +'"] td[name="contacted"]');
                contactedTd && contactedTd.update(this._CONTACTED_LABEL);

                this._matches[this._matches.indexOf(match)].notified = true;
                this._cachedMatches[this._cachedMatches.indexOf(match)].notified = true;
            }
            if (properties.hasOwnProperty('status')) {
                this._matches[this._matches.indexOf(match)].status = properties.status;
                this._cachedMatches[this._cachedMatches.indexOf(match)].status = properties.status;
            }
            if (properties.hasOwnProperty('state')) {
                this._tableElement.down('[data-matchid="' + match.id +'"]').className = properties.state;
            }
            // console.log('Set ' + match.id + ' to ' + JSON.stringify(state, null, 2));
        }.bind(this));
    },

//--SET MATCH STATUS BLOCK --

    // Send request to change match status
    _setMatchesStatus : function(event)
    {
        var target = event.element();
        if (!target) return;
        var matchId = String(target.dataset.matchid);
        var status = target.value;
        var ids = matchId.split(",");

        new Ajax.Request(this._ajaxURL + 'set-status', {
            contentType : 'application/json',
            parameters : {'matchesIds'    : ids,
                          'status'        : status
            },
            onSuccess : function (response) {
                if (!response.responseJSON || !response.responseJSON.results) {
                    this._utils.showFailure('show-matches-messages');
                    return;
                }
                var results = response.responseJSON.results;
                if (results.success && results.success.length > 0) {
                    this._setState(results.success, { 'status': status, 'state': 'success' });
                } else if (results.failed && results.failed.length > 0) {
                    this._setState(results.failed, { 'state': 'failed' });
                    this._utils.showFailure('show-matches-messages', "Setting status `" + status + "` failed");
                }
            }.bind(this),
            onFailure : function (response) {
                this._utils.showFailure('show-matches-messages');
            }.bind(this)
        });
    }
    });
    return PhenoTips;
}(PhenoTips || {}));
