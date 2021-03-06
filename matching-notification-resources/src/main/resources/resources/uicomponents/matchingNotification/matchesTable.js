require(["matchingNotification/utils",
         "matchingNotification/matcherPaginator",
         "matchingNotification/matcherPageSizer",
         "matchingNotification/notificationDialog",
         "matchingNotification/matcherContactDialog",
         "matchingNotification/matchContactSelectDialog",
         "matchingNotification/matchDetailsView"],
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
        this._ajaxURL = XWiki.contextPath + "/rest/matches";

        this._loadMatchesURL = this._ajaxURL;
        this._onSimilarCasesPage = $('similar-cases-container');
        if (this._onSimilarCasesPage) {
            this._loadMatchesURL = XWiki.contextPath + "/rest/matches/patients/" + XWiki.currentDocument.page;
        }

        this._tableCollabsed = true;
        $("panels-livetable-ajax-loader").hide();

        this._offset = 1;
        this._maxResults = 50;
        this._maxPagesShown = 10;
        this._minScore = 0;
        this.page = 1;
        this.paginations = $$('.pagination-matching-notifications');
        this.paginations.invoke("hide");
        this.resultsSummary = $('panels-livetable-limits');
        this.paginator = new PhenoTips.widgets.MatcherPaginator(this, this.paginations, this._maxPagesShown);

        this._isAdmin = $('isAdmin');
        this._isAdminOfGroup = $('isAdminOfGroup');

        this._utils = new utils(this._tableElement);

        $('show-matches-button') && $('show-matches-button').on('click', this._showMatches.bind(this));
        this._notificationsButton = $('send-notifications-button');
        this._notificationsButton.disabled = true;
        this._notificationsButton.hide();
        this._notificationsButton.on('click', this._sendNotification.bind(this));

        if (!this._onSimilarCasesPage) {
            this._overallScoreSlider = this._initializeScoreSlider('show-matches-score', (!this._isAdmin) ? 0.4 : 0.1, 0.5);
            this._phenScoreSlider = this._initializeScoreSlider('show-matches-phen-score', 0, 0);
            this._genScoreSlider = this._initializeScoreSlider('show-matches-gen-score', 0, 0.1);
        }

        this._PAGE_COUNT_TEMPLATE = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.matchesTable.pagination.footer'))";
        this._NOTIFY = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.notify'))";
        this._NONE_STANDART_PHENOTYPE = "$escapetool.javascript($services.localization.render('phenotips.patientSheetCode.termSuggest.nonStandardPhenotype'))";
        this._SAVED = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.saved'))";
        this._REJECTED = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.rejected'))";
        this._MARK_USER_CONTACTED_BUTTON_LABEL = "$escapetool.javascript($services.localization.render('phenotips.myMatches.markUserContactedButton.label'))";
        this._MARK_USER_CONTACTED_BUTTON_TITLE = "$escapetool.javascript($services.localization.render('phenotips.myMatches.markUserContactedButton.title'))";
        this._MARK_USER_UNCONTACTED_BUTTON_TITLE = "$escapetool.javascript($services.localization.render('phenotips.myMatches.markUserUncontactedButton.title'))";
        this._SAVE_COMMENT_BUTTON_LABEL = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.comment.save'))";
        this._ADD_COMMENT_TITLE = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.addComment'))";
        this._COMMENTS_TITLE = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.comments.title'))";
        this._COMMENTS_HINT = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.comments.hint'))";
        this._SERVER_ERROR_MESSAGE = "$escapetool.javascript($services.localization.render('phenotips.myMatches.contact.dialog.serverFailed'))";
        this._PUBMED = "$escapetool.javascript($services.localization.render('phenotips.similarCases.pubcase.link'))";
        this._SOLVED_CASE = "$escapetool.javascript($services.localization.render('phenotips.similarCases.solvedCase'))";
        this._CONTACT_ERROR_DIALOG_TITLE = "$escapetool.javascript($services.localization.render('phenotips.myMatches.contact.dialog.error.title'))";
        this._ADMIN_NOTIFICATION_FAILED_MESSAGE = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.matchesTable.onFailureAlert'))";
        this._NOTIFICATION_HISTORY_TITLE = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.notificationHistory.title'))";
        this._NOTIFICATION_HISTORY_TO = "$escapetool.javascript($services.localization.render('phenotips.myMatches.contact.dialog.to.label'))";
        this._NOTIFICATION_HISTORY_FROM = "$escapetool.javascript($services.localization.render('phenotips.myMatches.contact.dialog.from.label'))";
        this._NOTIFICATION_HISTORY_DATE = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.notificationHistory.date'))";
        this._NOTIFICATION_HISTORY_CONTACT_TITLE = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.notificationHistory.contact.title'))";
        this._NOTIFICATION_HISTORY_NOTIFICATION_TITLE = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.notificationHistory.notification.title'))";
        this._NOTES_TITLE = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.notes.title'))";
        this._NOTES_SAVE = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.notes.save'))";
        this._NOTES_HINT = "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.table.notes.hint'))";
        this._MATCHES_DISCLAIMER_TITLE = "$escapetool.javascript($services.localization.render('phenotips.myMatches.disclaimer.disclaimerTitle'))";

        this._PUBMED_URL = "http://www.ncbi.nlm.nih.gov/pubmed/";

        this._initiateFilters();

        this._contactDialog = new PhenoTips.widgets.MatcherContactDialog();
        this._errorDialog = new PhenoTips.widgets.NotificationDialog(this._CONTACT_ERROR_DIALOG_TITLE);
        this._matchSelectDialog = new PhenoTips.widgets.MacthContactSelectDialog();
        this._matchDetailsView = new PhenoTips.widgets.MatchDetailsView();

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

        $('contentmenu') && $('contentmenu').hide();
        $('hierarchy') && $('hierarchy').hide();

        document.observe("match:contacted:byuser", this._handleUserNotifiedUpdate.bind(this));

        if (this._onSimilarCasesPage) {
            this._updateAndShowMatches();
        } else if (!this._isAdmin) {
            this._showMatches();
        }

        this._resetSortingPreferences();

        // event listeners for sorting icon clicks
        $$('th[data-column="score"]')[0] && $$('th[data-column="score"]')[0].on('click', function(event) {this._sortByColumn('score', true);}.bind(this));
        $$('th[data-column="genotypicScore"]')[0] && $$('th[data-column="genotypicScore"]')[0].on('click', function(event) {this._sortByColumn('genotypicScore', true);}.bind(this));
        $$('th[data-column="phenotypicScore"]')[0] && $$('th[data-column="phenotypicScore"]')[0].on('click', function(event) {this._sortByColumn('phenotypicScore', true);}.bind(this));
        $$('th[data-column="foundTimestamp"]')[0] && $$('th[data-column="foundTimestamp"]')[0].on('click', function(event) {this._sortByColumn('foundTimestamp', true);}.bind(this));

        Event.observe(window, 'resize', this._buildTable.bind(this));

        Event.observe(document, "matches:refreshed", this._showMatches.bind(this));

        this._initializeDisclaimers();
    },

    _initializeScoreSlider: function(id, minScore, initialScore) {
        var filterEl = $(id);
        if (!filterEl) { return; };

        var progressEl = filterEl.down('.progress');
        var handleEl = filterEl.down('.handle');
        var gridEl = filterEl.down('.grid');
        var gridWidth = parseInt(window.getComputedStyle(gridEl, null).getPropertyValue("width"));
        var disabledRange = filterEl.down('.disabled-range');
        var disabledRangeWidth = gridWidth * minScore;
        disabledRange.setStyle({ width: disabledRangeWidth + "px"});

        var sliderValues = [minScore];
        var i = minScore;
        while (i <= 1) {
            i += 0.05;
            sliderValues.push(parseFloat(i.toFixed(2)));
        }

        var slider = new Control.Slider(handleEl, filterEl, {
            range: $R(0, 1),
            values: sliderValues,
            sliderValue: initialScore,
            onSlide: function(value) {
                progressEl.setStyle({ width: parseInt(window.getComputedStyle(handleEl, null).getPropertyValue("left")) + 2 + "px"});
                // round a float to the nearest 0.05
                handleEl.update(value);
            }
        });

        handleEl.update(initialScore);
        progressEl.setStyle({ width: parseInt(window.getComputedStyle(handleEl, null).getPropertyValue("left")) + 2 + "px"});

        return slider;
    },

    _resetSortingPreferences: function() {
        // defines current/default sorting order for various columns
        this._sortingOrder = { "score": "descending",
                               "genotypicScore": "descending",
                               "phenotypicScore": "descending",
                               "foundTimestamp": "descending" };
        // current sorting order
        this._currentSortingOrder = "none";
    },

    _initializeDisclaimers: function() {
        $$('#checkbox-server-filters .mme-disclaimer-help').each(function(trigger) {
            var disclaimerTextInput = trigger.up('label').down('.disclaimer');
            if (disclaimerTextInput && disclaimerTextInput.value) {

                var disclaimerContainer = new Element('span', {'class' : 'mme-disclaimer xTooltip hidden'});

                var hideAllHelpOnOutsideClick = function (event) {
                    if (!event.findElement('.xTooltip') && !event.findElement('.mme-disclaimer-help')) {
                      disclaimerContainer.addClassName('hidden');
                      document.stopObserving('click', hideAllHelpOnOutsideClick);
                    }
                }

                var closeButton = new Element('span', {'class': 'hide-tool', 'title': 'Hide'}).update('×');
                closeButton.observe('click', function(event) {
                    if (event) {event.stop();}
                    disclaimerContainer.addClassName('hidden');
                    document.stopObserving('click', hideAllHelpOnOutsideClick);
                });
                disclaimerContainer.insert(closeButton);

                var serverName = trigger.up('label').down('.serverName').value;
                disclaimerContainer.insert(new Element('div', {'class' : 'server-name'}).insert(serverName));

                if (!this._utils.isBlank(disclaimerTextInput.value)) {
                    disclaimerContainer.insert(new Element('div', {'class' : 'title'}).insert(this._MATCHES_DISCLAIMER_TITLE));
                    disclaimerContainer.insert(new Element('div', {'class' : 'disclaimer-text'}).insert(disclaimerTextInput.value));
                }

                trigger.insert({"after" : disclaimerContainer});

                trigger.observe("click", function(event) {
                    if (event) {event.stop();}
                    if (disclaimerContainer.hasClassName('hidden')) {
                        $$('.xTooltip:not(.hidden)', '.xPopup:not(.hidden)').each(function(el) { el.addClassName('hidden');});
                        document.observe('click', hideAllHelpOnOutsideClick);
                    } else {
                        document.stopObserving('click', hideAllHelpOnOutsideClick);
                    }
                    disclaimerContainer.toggleClassName('hidden');
                });

                disclaimerContainer.observe("click", function(event) { if (event) {event.stop();} });
            }
        }.bind(this));
    },

    _sortByColumn : function(propName, doUpdate) {
        if (!this._sortingOrder.hasOwnProperty(propName)) {
            return;
        }

        if (this._currentSortingOrder == propName) {
            // reverse sorting order if already sorting by this parameter...
            this._sortingOrder[propName] = (this._sortingOrder[propName] == "ascending") ? "descending" : "ascending";
        } else {
            // ...but use current/default sorting order if currently sorting by other column
            this._currentSortingOrder = propName;
        }

        var getIconElementForProperty = function(propertyName) {
            var element = $$('th[data-column="' + propertyName + '"]')[0];
            return element.down('.fa') ? element.down('.fa') : element;
        }

        // update sorting icons for all columns
        for (var column in this._sortingOrder) {
            if (this._sortingOrder.hasOwnProperty(column)) {
                var faElement = getIconElementForProperty(column);
                if (faElement) {
                    faElement.removeClassName('fa-sort-down');
                    faElement.removeClassName('fa-sort-up');
                    if (column == this._currentSortingOrder) {
                        // for current column
                        faElement.removeClassName('fa-sort');
                        (this._sortingOrder[column] == "descending") ? faElement.addClassName('fa-sort-down') : faElement.addClassName('fa-sort-up');
                    } else {
                        // for all other columns
                        faElement.addClassName('fa-sort');
                    }
                }
            }
        }

        this._cachedMatches.sort( function(a, b) {
            if(a[propName] < b[propName]) return (this._sortingOrder[propName] == "ascending") ? -1 : +1;
            if(a[propName] > b[propName]) return (this._sortingOrder[propName] == "ascending") ? +1 : -1;
            return 0;
        }.bind(this));

        doUpdate && this._update();
    },

    validateScore : function(score, messagesFieldName) {
        if (isNaN(score) || Number(score) < 0 || Number(score) > 1) {
            this._utils.showHint(messagesFieldName, "$escapetool.javascript($services.localization.render('phenotips.matchingNotifications.invalidScore'))", "invalid");
            return undefined;
        }
        return score;
    },

    _initiateFilters : function()
    {
        this._filterValues = {};
        this._filterValues.matchStatus = {"rejected"      : $$('input[name="status-filter"][value="rejected"]')[0].checked,
                                          "saved"         : $$('input[name="status-filter"][value="saved"]')[0].checked,
                                          "uncategorized" : $$('input[name="status-filter"][value="uncategorized"]')[0].checked};
        this._filterValues.ownerStatus = {"me"     : $$('input[name="ownership-filter"][value="me"]')[0].checked,
                                          "group"  : $$('input[name="ownership-filter"][value="group"]')[0].checked,
                                          "others" : $$('input[name="ownership-filter"][value="others"]')[0].checked,
                                          "public" : $$('input[name="ownership-filter"][value="public"]')[0].checked};
        this._filterValues.notified  = {"notified" : $$('input[name="notified-filter"][value="notified"]')[0].checked,
                                        "unnotified" : $$('input[name="notified-filter"][value="unnotified"]')[0].checked};
        this._filterValues.contacted  = {"contacted" : $$('input[name="contacted-filter"][value="contacted"]')[0].checked,
                                        "uncontacted" : $$('input[name="contacted-filter"][value="uncontacted"]')[0].checked};
        this._filterValues.score  = {"score" : 0, "phenotypicScore" : 0, "genotypicScore" : 0};
        this._filterValues.geneStatus  = "all";
        this._filterValues.hasExome  = {"hasExome" : $$('input[name="exome-filter"][value="hasExome"]')[0].checked,
                                        "hasNoExome" : $$('input[name="exome-filter"][value="hasNoExome"]')[0].checked};
        this._filterValues.externalId  = "";
        this._filterValues.email       = "";
        this._filterValues.geneSymbol  = "";
        this._filterValues.phenotype   = "";
        this._filterValues.solved      = $$('input[name="solved-filter"][value="hide"]')[0].checked;
        this._filterValues.ownCases    = $$('input[name="own-filter"][value="hide"]')[0].checked;

        this._filterValues.serverIds   = [{"local" : true}];
        $$('input[name="checkbox-server-id-filter"]').each(function (checkbox) {
            this._filterValues.serverIds[checkbox.value] = checkbox.checked;
        }.bind(this));

        var mmeFilters = $$('#checkbox-server-filters input[name="checkbox-server-id-filter"].mme');
        $('mme-filter') && $('mme-filter').on('click', function(event) {
            var checked = event.currentTarget.checked;
            mmeFilters.each(function (checkbox) {
                this._filterValues.serverIds[checkbox.value] = checked;
                checkbox.checked = checked;
            }.bind(this));
            this._update();
        }.bind(this));

        $$('input[name="status-filter"]').each(function (checkbox) {
            // initialize filter to the value set in the form
            this._filterValues.matchStatus[checkbox.value] = checkbox.checked;
            // click handler
            checkbox.on('click', function(event) {
                this._filterValues.matchStatus[event.currentTarget.value] = event.currentTarget.checked;
                this._update();
            }.bind(this));
        }.bind(this));

        $$('input[name="ownership-filter"]').each(function (checkbox) {
            // initialize filter to the value set in the form
            this._filterValues.ownerStatus[checkbox.value] = checkbox.checked;
            // click handler
            checkbox.on('click', function(event) {
                this._filterValues.ownerStatus[event.currentTarget.value] = event.currentTarget.checked;
                this._update();
            }.bind(this));
        }.bind(this));

        $('gene-status-filter').on('change', function(event) {
            this._filterValues.geneStatus = event.currentTarget.value;
            this._update();
        }.bind(this));

        $$('input[name="exome-filter"]').each(function (checkbox) {
            checkbox.on('click', function(event) {
                this._filterValues.hasExome[event.currentTarget.value] = event.currentTarget.checked;
                this._update();
            }.bind(this));
        }.bind(this));

        $$('input[name="solved-filter"]').each(function (checkbox) {
            checkbox.on('click', function(event) {
                this._filterValues.solved = event.currentTarget.checked;
                this._update();
            }.bind(this));
        }.bind(this));

        $$('input[name="own-filter"]').each(function (checkbox) {
            checkbox.on('click', function(event) {
                this._filterValues.ownCases = event.currentTarget.checked;
                this._update();
            }.bind(this));
        }.bind(this));

        $$('input[name="notified-filter"]').each(function (checkbox) {
            checkbox.on('click', function(event) {
                this._filterValues.notified[event.currentTarget.value] = event.currentTarget.checked;
                this._update();
            }.bind(this));
        }.bind(this));

        $$('input[name="contacted-filter"]').each(function (checkbox) {
            checkbox.on('click', function(event) {
                this._filterValues.contacted[event.currentTarget.value] = event.currentTarget.checked;
                this._update();
            }.bind(this));
        }.bind(this));

        $$('input[class="score-filter"]').each(function (input) {
            input.on('input', function(event) {
                this._utils.clearHint('score-filter-validation-message');
            }.bind(this));

            input.on('change', function(event) {
                var score = this.validateScore(event.currentTarget.value, 'score-filter-validation-message');
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

        this._advancedFilter = function (match) {
            // filter by search input in patient ID, external ID and emails
            var hasExternalIdMatch = match.matched.patientId.toLowerCase().includes(this._filterValues.externalId)
                || match.matched.externalId.toLowerCase().includes(this._filterValues.externalId);
            if (!this._onSimilarCasesPage) {
                hasExternalIdMatch = hasExternalIdMatch || match.reference.patientId.toLowerCase().includes(this._filterValues.externalId)
                                                        || match.reference.externalId.toLowerCase().includes(this._filterValues.externalId);
            }

            var hasEmailMatch = match.matched.emails.toString().toLowerCase().includes(this._filterValues.email);
            if (!this._onSimilarCasesPage) {
                hasEmailMatch = hasEmailMatch || match.reference.emails.toString().toLowerCase().includes(this._filterValues.email);
            }

            var hasGeneSymbolMatch = match.matched.genes.toString().toLowerCase().includes(this._filterValues.geneSymbol);
            if (!this._onSimilarCasesPage) {
                hasGeneSymbolMatch = hasGeneSymbolMatch || match.reference.genes.toString().toLowerCase().includes(this._filterValues.geneSymbol);
            }

            var hasPhenotypeMatch = match.matched.aggregatedPhenotypes.toString().toLowerCase().includes(this._filterValues.phenotype);
            if (!this._onSimilarCasesPage) {
                hasPhenotypeMatch = hasPhenotypeMatch || match.reference.aggregatedPhenotypes.toString().toLowerCase().includes(this._filterValues.phenotype);
            }

            var hasCheckboxServerIDsMatch = this._filterValues.serverIds[match.reference.serverId]
                || this._filterValues.serverIds[match.matched.serverId]
                || (match.isLocal && this._filterValues.serverIds["local"]);
            // by gene matching statuses (candidate-candidate, solved-candidate)
            var hasGeneTypeMatch = match.matchingGenesTypes.indexOf(this._filterValues.geneStatus) > -1;
            // by uploaded VCF file to ane of the patients
            var hasExomeMatch = (match.matched.hasExomeData || match.reference.hasExomeData) && this._filterValues.hasExome.hasExome
                             || (!match.matched.hasExomeData && !match.reference.hasExomeData) && this._filterValues.hasExome.hasNoExome;
            // by match status (rejected, saved, uncategorized)
            var hasMatchStatusMatch = this._filterValues.matchStatus[match.status];

            // returns true iff any of the two records in the match is local and its ownership field "ownershipParameter" is true
            var matchHasRecordWithOwnership = function(match, ownershipParameter) {
                                         if (match.reference.serverId == "" && match.reference.ownership[ownershipParameter]) {
                                            return true;
                                         }
                                         if (match.matched.serverId == "" && match.matched.ownership[ownershipParameter]) {
                                            return true;
                                         }
                                         return false;
                                     };

            var matchOwnedByMe     = matchHasRecordWithOwnership(match, "userIsOwner");
            var matchOwnedByGroup  = matchHasRecordWithOwnership(match, "userGroupIsOwner");
            var matchOwnedByPublic = matchHasRecordWithOwnership(match, "publicRecord");
            var matchOwnedByOthers = !matchOwnedByMe && !matchOwnedByGroup && !matchOwnedByPublic;

            var hasOwnershipMatch = (this._filterValues.ownerStatus["me"] && matchOwnedByMe)
                                    || (this._filterValues.ownerStatus["group"] && matchOwnedByGroup)
                                    || (this._filterValues.ownerStatus["public"] && matchOwnedByPublic)
                                    || (this._filterValues.ownerStatus["others"] && matchOwnedByOthers);

            var isNotifiedMatch = match.adminNotified && this._filterValues.notified.notified || !match.adminNotified && this._filterValues.notified.unnotified;
            var isContactedMatch = (match.contacted || match.userContacted) && this._filterValues.contacted.contacted
                                || (!match.contacted && !match.userContacted) && this._filterValues.contacted.uncontacted;
            var hasScoreMatch = match.score >= this._filterValues.score.score
                             && match.phenotypicScore >= this._filterValues.score.phenotypicScore
                             && match.genotypicScore >= this._filterValues.score.genotypicScore;

            // returns true if at least one of the patients in a match is owned by the user (or user's group) and is unsolved
            var matchHasOwnUnSolvedCase = function(match) {
                    // both patients in the match are solved - so "exclude my solved" filter should exclude this match, no matter which patient is "mine"
                    if (match.reference.solved && match.matched.solved) {
                        return false;
                    }
                    // no solved cases in the match => there is own unsolved case, since at least one patient
                    // must be "own", and both are unsolved
                    if (!match.reference.solved && !match.matched.solved) {
                        return true;
                    }
                    // by this point it is guaranteed that only one of the patients is solved
                    var unsolvedCase = match.reference.solved ? match.matched : match.reference;
                    // check the unsolved patient. If it is editable by the user => keep the match
                    if (this._accessAtLeastEdit(unsolvedCase)) {
                        return true;
                    }
                    // unsolved case is not "mine", thus there are no unsolved cases which are "mine"
                    return false;
                }.bind(this);
            var keepOwnSolvedCases = !this._filterValues.solved || matchHasOwnUnSolvedCase(match);


            var matchBetweenOwnCases = function(match) {
                if (match.reference.serverId == "" && (match.reference.ownership["userIsOwner"] || match.reference.ownership["userGroupIsOwner"]) &&
                    match.matched.serverId == ""   && (match.matched.ownership["userIsOwner"] || match.matched.ownership["userGroupIsOwner"])) {
                    return true;
                }
                return false;
            }

            var keepOwnMatchedOwnCases = !this._filterValues.ownCases || !matchBetweenOwnCases(match);

            return hasExternalIdMatch && hasEmailMatch && hasGeneSymbolMatch && hasOwnershipMatch && keepOwnSolvedCases && hasExomeMatch && keepOwnMatchedOwnCases
                       && hasMatchStatusMatch && hasGeneTypeMatch && hasPhenotypeMatch && isNotifiedMatch && isContactedMatch && hasScoreMatch && hasCheckboxServerIDsMatch;
        }.bind(this);
    },

    _toggleFilters : function (filtersElt, forceHide) {
        if (filtersElt) {
            filtersElt.toggleClassName('collapsed', forceHide);
            filtersElt.up('.xwiki-livetable-container').toggleClassName('hidden-filters', forceHide);
            var key = this._utils.getCookieKey(filtersElt.up('.entity-directory').id);
            if (filtersElt.hasClassName('collapsed')) {
                XWiki.cookies.create(key, 'hidden', '');
                if (!this._onSimilarCasesPage) {
                    $('advanced-filters-header').hide();
                }
            } else {
                XWiki.cookies.erase(key);
                if (!this._onSimilarCasesPage) {
                    $('advanced-filters-header').show();
                }
            }
        }
    },

    _initiateEmailColumnsBehaviur : function()
    {
        this.collapseEmails = {"referenceEmails" : true, "matchedEmails" : true};
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

        new Ajax.Request(this._loadMatchesURL + "?method=GET", {
            contentType: 'application/json',
            parameters : options,
            onCreate : function () {
                $("panels-livetable-ajax-loader").show();
                this._utils.clearHint('send-notifications-messages');
                this._notificationsButton.hide();
            }.bind(this),
            onSuccess : function (response) {
                if (response.responseJSON) {

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

    _updateAndShowMatches : function()
    {
        new Ajax.Request(this._loadMatchesURL + "?method=PUT", {
            contentType : 'application/json',
            parameters : {'serverId' : 'local'},
            onComplete : function () {
                this._showMatches();
            }.bind(this)
        });
    },

    // Generate options for matches search AJAX request
    _generateOptions : function()
    {
        var score     = this._overallScoreSlider ? (Math.ceil(this._overallScoreSlider.value*20)/20).toFixed(2) : 0.3;
        var phenScore = this._phenScoreSlider ? (Math.ceil(this._phenScoreSlider.value*20)/20).toFixed(2) : 0;
        var genScore  = this._genScoreSlider ? (Math.ceil(this._genScoreSlider.value*20)/20).toFixed(2) : 0;

        var options = { 'minScore'        : score,
                        'minPhenScore'    : phenScore,
                        'minGenScore'     : genScore};
        return options;
    },

    // params:
    // filter - Filter function to apply to matches
    _update : function(filter)
    {
        var tableBody = this._tableElement.down('tbody');
        tableBody.update('');

        if (!this._cachedMatches || this._cachedMatches.length == 0) {
            this.paginations.invoke("hide");
            this.resultsSummary.hide();
        } else {
            this._matches = this._cachedMatches.filter(this._advancedFilter);
            this._updateServerFilterMatchesCount();

            this.paginations.invoke("show");
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

            if (match.matched.serverId != '') {
                if (!this._filterValues.serverIds.hasOwnProperty(match.matched.serverId)) {
                    // do not show matches that have server ID that is not pre-generated in velocity,
                    // i.e. are not in the valid (enabled) list of servers
                    this._filterValues.serverIds[match.matched.serverId] = false;
                }
            }
            if (match.reference.serverId != '') {
                if (!this._filterValues.serverIds.hasOwnProperty(match.reference.serverId)) {
                    this._filterValues.serverIds[match.reference.serverId] = false;
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
            match.matched.aggregatedPhenotypes = this._aggregatePhenotypes(match.matched.phenotypes);
            match.reference.aggregatedPhenotypes = this._aggregatePhenotypes(match.reference.phenotypes);

            //sort made of inheritance: those that matched to be first in alphabetic order
            this._organiseModeOfInheritance(match);

            this._organiseGenes(match);

            // For user only: out of the two patients in a match ("reference" and "matched") returns the one that is "not mine"
            match.notMyCase = this._getNotMyCase(match);

            // swap "reference" and "matched" if they are not in right place based on "notMyCase" if determined
            if (match.notMyCase != null && match.reference == match.notMyCase) {
                match.reference = match.matched;
                match.matched = match.notMyCase

                // also need to swap "reference" and "matched" in all similarity views
                match.phenotypesSimilarity && this._swapSimilaritiesIfNotMyCase(match.phenotypesSimilarity);
                match.genotypeSimilarity && this._swapSimilaritiesIfNotMyCase(match.genotypeSimilarity);
            }

            this._organiseNotificationHistory(match);
        }.bind(this));

        // new data - forget current sorting preferences
        this._resetSortingPreferences();

        // sort by match found timestamp in descending order
        this._sortByColumn('foundTimestamp', false);
    },

    _swapSimilaritiesIfNotMyCase : function(similarities) {
        similarities.each( function (similarity) {
            var temp = similarity.match;
            similarity.match = similarity.reference;
            similarity.reference = temp;
        }.bind(this));
    },

    _organiseNotificationHistory : function(match)
    {
        if (!match.notificationHistory) {
            return;
        }

        // whether match was contacted by user outside of the PhenomeCentral
        match.userContacted = !!match.notificationHistory["user-contacted"];

        var records = [];
        if (match.notificationHistory.interactions && match.notificationHistory.interactions.size() > 0) {
            // we go from bottom of array to top to put most recent notifications first
            for (var i=match.notificationHistory.interactions.length-1; i >= 0; i--) {
                var record = match.notificationHistory.interactions[i];
                if (record.to && record.to.emails && record.to.emails.size() > 0) {

                    if (record.type == "notification") {
                        match.adminNotified = true;
                    }
                    if (record.type == "contact") {
                        match.contacted = true;
                    }

                    records.push(record);
                }
            }
        }

        match.notificationHistory = records;
    },

    _updateServerFilterMatchesCount : function()
    {
        $('matching-filters').select('input[type="checkbox"][name="checkbox-server-id-filter"]').each( function(selectEl) {
            //count matches with this server id
            var serverID = selectEl.value;
            var matchesForServer = [];
            if (selectEl.value == "local") {
                // local matches
                matchesForServer = this._matches.filter(function(match) { return (match.reference.serverId === "" && match.matched.serverId === ""); });
            } else {
                // remote matches
                matchesForServer = this._matches.filter(function(match) { return (match.reference.serverId === serverID || match.matched.serverId === serverID); });
            }
            var countEl = selectEl.up().down('.matches-count').update(' (' + matchesForServer.length + ')');
        }.bind(this));
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
        if (match.matched.matchedExomeGenes) {
            matchGenes = matchGenes.concat(match.matched.matchedExomeGenes);
        }

        var referenceGenes = match.reference.genes.clone();
        if (match.reference.matchedExomeGenes) {
            referenceGenes = referenceGenes.concat(match.reference.matchedExomeGenes);
        }

        var commonGenes = matchGenes.intersect(referenceGenes);
        match.matched.genes = commonGenes.concat(matchGenes).uniq();
        match.reference.genes = commonGenes.concat(referenceGenes).uniq();
    },

    _aggregatePhenotypes : function(phenotypes)
    {
        var allPhenotypes = [];
        phenotypes.each(function (elm) {
            elm.label && allPhenotypes.push(elm.label);
        });

        return allPhenotypes;
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
        this.paginator.refreshPagination();

        var firstItemRangeNo = (end == 0) ? 0 : begin + 1;
        var lastItemRangeNo = end;
        var tableSummary = this._PAGE_COUNT_TEMPLATE.replace(/___caseRange___/g, firstItemRangeNo + "-" + lastItemRangeNo).replace(/___totalCases___/g, this.totalResultsCount).replace(/___numCasesPerPage___/g, "");
        this._displaySummary(tableSummary, $('panels-livetable-limits'), true);

        matchesForPage.each( function (match, index) {
            var tr = this._rowWriter(match, this._tableElement.select('.second-header-row th'), this._simpleCellWriter);
            tableBody.insert(tr);
        }.bind(this));

        this._afterProcessTable(matchesForPage);
    },

    _rowWriter : function(match, columns, cellWriter)
    {
        var tr = '<tr id="row-' + match.rowIndex + '" data-matchid="' + match.id + '">';

        // For each column in table, get match's attribute, or formatted element
        columns.each(function(column, index) {
            switch(column.dataset.column) {
                case 'status':
                    tr += this._getStatusTd(match);
                    break;
                case 'notes':
                    tr += this._getNotesTd(match);
                    break;
                case 'referencePatient':
                    tr += this._getPatientDetailsTd(match.reference, 'referencePatientTd', match.id, match.matched, match.rowIndex);
                    break;
                case 'matchedPatient':
                    tr += this._getPatientDetailsTd(match.matched, 'matchedPatientTd', match.id, match.reference, match.rowIndex);
                    break;
                case 'referenceEmails':
                    tr += this._getEmailsTd(match.reference, match, 'referenceEmails');
                    break;
                case 'matchedEmails':
                    tr += this._getEmailsTd(match.matched, match, 'matchedEmails');
                    break;
                case 'contact':
                    tr+= this._getContact(match);
                    break;
                default:
                    tr += cellWriter(match[column.dataset.column], column.dataset.column);
                    break;
            }
        }.bind(this));

        tr += '</tr>';

        return tr;
    },

    _getStatusTd : function(match)
    {
        var td = '<td class="status-column">';
        td += '<select class="status" data-matchid="' + match.id +'">'
            + '<option value="uncategorized" '+ (match.status == "uncategorized" ? ' selected="selected"' : '') + '> </option>'
            + '<option value="saved" '+ (match.status == "saved" ? ' selected="selected"' : '') + '>' + this._SAVED + '</option>'
            + '<option value="rejected" '+ (match.status == "rejected" ? ' selected="selected"' : '') + '>' + this._REJECTED + '</option>'
            + '</select>';
        td += '</td>';
        return td;
    },

    _getNotesTd : function(match)
    {
        var td = '<td class="notes-column">';
        if (this._isAdmin || this._isAdminOfGroup) {
            // comments
            var icon = (match.comments) ? "fa fa-comments" : "fa fa-comments-o";
            td += '<span class="comment ' + icon + '" title="' + this._ADD_COMMENT_TITLE + '"> </span>';
            var commentsTable = '';
            if (match.comments) {
                commentsTable = this._generateCommentsTable(match.comments);
            }
            td += '<div class="xPopup comment-container"><span class="hide-tool" title="Hide">×</span>'
                + '<div class="nhdialog-title">' + this._COMMENTS_TITLE
                + '<span class="fa fa-info-circle xHelpButton" title="' + this._COMMENTS_HINT + '"></span></div>'
                + '<div><textarea rows="3" cols="20"></textarea></div>';
            td +='<span class="buttonwrapper"><button class="save-comment" data-matchid="' + match.id + '"><span class="fa fa-save"> </span>'
                + this._SAVE_COMMENT_BUTTON_LABEL + '</button></span>' + commentsTable + '</div>';
        }
        // notes icon
        var icon = (match.notes) ? "fa fa-file" : "fa fa-file-o";
        td += '<span class="notes ' + icon + '" title="' + this._NOTES_TITLE + '"> </span>';
        td += '<div class="xPopup notes-container"><span class="hide-tool" title="Hide">×</span>'
            + '<div class="nhdialog-title">' + this._NOTES_TITLE + '</div>'
            + '<p class="xHint">' + this._NOTES_HINT + '</p>'
            + '<div><textarea rows="5" cols="20"></textarea></div>'
            + '<span class="buttonwrapper"><button class="save-notes" data-matchid="' + match.id + '"><span class="fa fa-save"> </span>'
            + this._NOTES_SAVE + '</button></span></div>';
        td += '</td>';
        return td;
    },

    _generateCommentsTable : function(records)
    {
        var tableBody = '';

        for (var i = 0 ; i < records.length ; i++) {
            var record = records[i];

            // if no comment text, continue to next comment record
            if (!record.comment) {
                continue;
            }

            // date and user name row
            var row = '<tr>';
            var date = '<td class="comment-info">';
            if (record.date && ("Invalid Date" != new Date(record.date))) {
                var time = new Date(record.date);
                time.setMinutes(time.getMinutes() - time.getTimezoneOffset());
                date += '<span class="date">' + time.toISOString().split('T')[0] + '</span>';
            }
            if (record.userinfo) {
                // admin comments should have something generic e.g. "PhenomeCentral" (no link)
                if (record.userinfo.id == "xwiki:XWiki.Admin") {
                    date += '<span>PhenomeCentral</span>';
                } else {
                    if (record.userinfo.name) {
                        if (record.userinfo.url) {
                            date += '<a href="' + record.userinfo.url + '" target="_blank">' + record.userinfo.name + '</a>';
                        } else {
                            date += record.userinfo.name;
                        }
                    }
                }
            }
            date += '</td>';
            row += date;
            row += '</tr>';
            tableBody += row;

            // comment row
            row = '<tr>';
            var comment = '<td class="comment-text">' + record.comment + '</td>';
            row += comment;
            row += '</tr>';

            tableBody += row;
        }

        if (!tableBody) {
            return '';
        }

        var table = '<table class="comments-table"><tbody>';
        table += tableBody;
        table += '</tbody></table>';

        return table;
    },

    _simpleCellWriter : function(value, name)
    {
        if (name == "score" || name == "genotypicScore" || name == "phenotypicScore") {
            var score = parseFloat(value).toFixed(2);
            var loColor = Math.max(0, 248 - Number.parseInt(score*96));
            return '<td class="smaller" style="background-color: rgb(' + loColor + ', 248,' + loColor + ');"  >' + score + '</td>';
        }
        return '<td class="smaller">' + value + '</td>';
    },

    _getPatientDetailsTd : function(patient, tdClass, matchId, otherPatient, index)
    {
        var td = '<td class="' + tdClass;
        if (patient.solved) {
            td += ' solved';
        }
        td += '">';
        var externalId = (!this._utils.isBlank(patient.externalId)) ? " : " + patient.externalId : '';
        // Patient id and collapsible icon
        if (this._onSimilarCasesPage || tdClass != 'matchedPatientTd') {
            td += '<div class="fa fa-plus-square-o patient-div collapse-gp-tool" data-matchid="' + matchId + '" data-matchindex="' + index + '"></div>';
        }
        if (patient.serverId == '') { // local patient
            var patientHref = new XWiki.Document(patient.patientId, 'data').getURL();
            td += '<a href="' + patientHref + '" target="_blank" class="patient-href">' + patient.patientId + externalId + '</a>';
        } else { // remote patient
            // TODO pass a server name in JSON as well to display a server name instead if server ID in the table
            // Parse remote patient ID because MyGene2 uses URLs in the ID field to ensure that the public MyGene2 profiles are actually delivered to the end user
            if (patient.serverId == "mygene2" && patient.patientId && patient.patientId.startsWith("http")) {
                var matches = patient.patientId.match(/[\d]+/g);
                var id = matches ? matches[matches.length - 1] : "$escapetool.javascript($services.localization.render('phenotips.similarCases.profile'))";
                td += '<a href="' + patient.patientId + '" target="_blank" class="patient-href">' + id + externalId  + ' (' + patient.serverId + ') ' + '</a>';
            } else {
                td += '<label class="patient-href">' + patient.patientId + externalId + ' (' + patient.serverId + ')</label>';
            }
        }

        if (patient.solved) {
            td += '<div class="metadata">' + this._SOLVED_CASE + '</div>';
        }

        td += '</td>';
        return td;
    },

    _getEmailsTd : function(patient, match, cellName)
    {
        var matchId = match.id[0] ? match.id[0] : match.id;
        var td = '<td name="' + cellName + '">';
        // if case is solved and has at least one Pubmed ID - display a link to it instead of emails
        if (patient.solved) {
            if (patient.pubmedIds && patient.pubmedIds.size() > 0) {
                for (var i=0; i < patient.pubmedIds.length; i++) {
                    var href = this._PUBMED_URL + patient.pubmedIds[i].trim();
                    td += '<div><a href=' + href + ' target="_blank"><span class="fa fa-leanpub" title="' + this._PUBMED + '"></span>PMID: ' + patient.pubmedIds[i] + '</a></div>';
                }
                return td;
            }
        }

        var contactInfo = patient.contact_info;
        if (contactInfo && (contactInfo.name || contactInfo.institution)) {
            if (contactInfo.name) {
                td += '<div name="owner-info">' + contactInfo.name + '</div>';
            }
            if (contactInfo.institution) {
                td += '<div name="owner-info" class="metadata">' + contactInfo.institution + '</div>';
            }
        } else {
            var shortEmail = (patient.emails.length > 0) ? (patient.emails[0].substring(0, 9) + "...") :  "";
            td += '<div name="notification-email-short" class="code">' + shortEmail + '</div>';
        }

        for (var i=0; i < patient.emails.length; i++) {
            var email = patient.emails[i]
            if (email.indexOf("://") > -1) {
                email = email.split('/')[2];
                email = '<a href=' + patient.emails[i] + ' target="_blank">' + email + '</a>';
            } else {
                // insert a 0-width space after the @, so that a long email can be split into two lines
                email = email.replace(/@/g,"@&#8203;");
                // insert a "preferred-no-split <span> around emails, to make sure lines are first split on ",",
                // and only after that on "@"
                email = email.replace(/([^, ]+?@[^ ]+)/g,"<span class='avoidwrap'>$1</span> ")
            }
            td += '<div name="notification-email-long" class="code">' + email + '</div>';
        }

        //if logged as admin - add notification checkbox for local PC patient email contact but not for self (not for patients owned by admin)
        if (this._isAdmin && patient.serverId == '' && patient.emails.length > 0) {
            td += '<div><span class="fa fa-volume-up" title="' + this._NOTIFY + '"></span> <input type="checkbox" class="notify" data-matchid="' + matchId + '" data-patientid="'+ patient.patientId +'" data-emails="'+ patient.emails.toString() +'"></div>';
        }
        td += '</td>';
        return td;
    },

    _generateNotificationHistoryTable : function(records)
    {
        var table = '<table class="notification-history-table"><tbody>';

        var header = '<tr>';
        header += '<th scope="col">' + this._NOTIFICATION_HISTORY_DATE + '</th>';
        header += '<th scope="col">' + this._NOTIFICATION_HISTORY_FROM + '</th>';
        header += '<th scope="col">' + this._NOTIFICATION_HISTORY_TO + '</th>';
        header += '</tr>';

        table += header;

        for (var i=0; i < records.length; i++) {
            var record = records[i];
            var row = '<tr class="' + record.type + '">';

            var title = (record.type == 'contact') ? this._NOTIFICATION_HISTORY_CONTACT_TITLE : this._NOTIFICATION_HISTORY_NOTIFICATION_TITLE;
            var date = '<td title="' + title + '">';
            if (record.date) {
                var dateIconName = (record.type == 'contact') ? 'fa fa-envelope-o' : 'fa fa-volume-up' ;
                dateString = record.date;
                if ("Invalid Date" != new Date(record.date)) {
                    var time = new Date(record.date);
                    time.setMinutes(time.getMinutes() - time.getTimezoneOffset());
                    dateString = time.toISOString().split('T')[0]
                }
                date += '<div class="date">' + dateString + '</div><span class="'+ dateIconName + '"> </span></td>';
            }
            date += '</td>';

            var from = '<td>';
            if (record.from) {
                // The `From` field should be " PhenomeCentral" for all `notification`s, regardless of which admin sent them
                from += (record.type == "notification") ? "PhenomeCentral" : this._generateUserInfoCell(record.from);
            }
            from += '</td>';

            var to = '<td>';
            if (record.to) {
                to += this._generateUserInfoCell(record.to);
            }
            to += '</td>';

            row += date;
            row += from;
            row += to;
            row += '</tr>';

            table += row;
        }

        table += '</tbody></table>';

        return table;
    },

    _generateUserInfoCell : function(info)
    {
        var cell = '';
        // user name and institution
        if (info.userinfo) {
            if (info.userinfo.name) {
                if (info.userinfo.url) {
                    cell += '<div><a href="' + info.userinfo.url + '" target="_blank">' + info.userinfo.name + '</a></div>';
                } else {
                    cell += '<div>' + info.userinfo.name + '</div>';
                }
            }
            if (info.userinfo.institution) {
                cell += '<span class="metadata">' + info.userinfo.institution + '</span>';
            }
        }
        // emails
        if (info.emails && info.emails.size() > 0) {
            for (var i=0; i < info.emails.length; i++) {
                cell += '<span class="code">' + info.emails[i] + '</span>';
            }
        }
        return cell;
    },

    _getContact : function(match)
    {
        var td = '<td name="contact">';
        td += this._getContactButtonHTML(match);
        var hasHistory = match.notificationHistory && match.notificationHistory.size() > 0;
        //add notification history icon
        td += '<span class="fa fa-history notification-history ' + ((!hasHistory && !match.userContacted) ? 'secondary' : '')
            + '" title="' + this._NOTIFICATION_HISTORY_TITLE + '"> </span>'
            + '<div class="xPopup notification-history-container"><span class="hide-tool" title="Hide">×</span>'
            + '<div class="nhdialog-title">' + this._NOTIFICATION_HISTORY_TITLE + '</div>';
        // add "mark as contacted outside PC" button
        td += '<div class="mark-user-contacted-button-container">' + this._getMarkUserContactedHTML(match) + '</div>';
        // add notification history table if there is any history
        if (hasHistory) {
            td += this._generateNotificationHistoryTable(match.notificationHistory);
        }
        td += '</div>';

        td += '</td>';
        return td;
    },

    _accessAtLeastEdit : function(patient) {
        return (patient.access == "edit" || patient.access == "owner"  || patient.access == "manage");
    },

    // out of the two patients in a match ("reference" and "matched") returns the one that is "not mine"
    _getNotMyCase : function(match)
    {
        if (this._onSimilarCasesPage) {
            if (match.matched.patientId == XWiki.currentDocument.page && match.matched.serverId == '') {
                return match.reference;
            }
            if (match.reference.patientId == XWiki.currentDocument.page && match.reference.serverId == '') {
                return match.matched;
            }
            return null;
        }

        if (match.reference.serverId == '' && match.matched.serverId != '') {
            return match.matched;
        }
        if (match.reference.serverId != '' && match.matched.serverId == '') {
            return match.reference;
        }

        // determine which of the two patients in match is "my case" and which is "matched case" which should be contacted
        if (match.reference.ownership["userIsOwner"]) {
            // user directly owns "match.reference" => "matched" is match.matched (we know user never owns both patients in a match from this table)
            return match.matched;
        } else if (match.matched.ownership["userIsOwner"]) {
            // user directly owns "match.matched" => "matched" is match.reference
            return match.reference;
        } else if (this._accessAtLeastEdit(match.reference) && !this._accessAtLeastEdit(match.matched)) {
            // user has edit access to "match.reference" and no edit access to the "match.matched": assume that the user wants to contact match.matched
            return match.matched;
        } else if (this._accessAtLeastEdit(match.matched) && !this._accessAtLeastEdit(match.reference)) {
            // user has edit access to "match.matched" and no edit access to the "match.reference": assume that the user wants to contact match.reference
            return match.reference;
        } else if (match.reference.ownership["userGroupIsOwner"] && !match.matched.ownership["userGroupIsOwner"]) {
            // user does not own any of the cases, but user's group owns "match.reference" => "matched" is match.matched
            return match.matched;
        } else if (match.matched.ownership["userGroupIsOwner"] && !match.reference.ownership["userGroupIsOwner"]) {
            // user does not own any of the cases, but user's group owns "match.matched" => "matched" is match.reference
            return match.reference;
        }
        // else: user has "equal" (*) access level to both patients: we don't know which one of the two the user wants to contact
        //       (*) e.g. both are owned by someone else and user has equal edit access to both
        return null;
    },


    _canCaseBeContacted : function(matchedCase)
    {
        if (!matchedCase) {
            return false;
        }

        // 1) check if the case has a PubmedID:
        //    it it does, no need to have a contact button, user should use PubmedID link instead
        var caseHasPubmedID = matchedCase.pubmedIds && matchedCase.pubmedIds.size() > 0;
        if (caseHasPubmedID) {
            return false;
        }

        // 2) check if there is an email to be used: no emails => no way to contact, no contact button
        if (matchedCase.validatedEmails.length == 0) {
            return false;
        }

        return true;
    },

    // returns null if match should not be contacted, caseID if it is clear whom to contact, and undefined if it is not
    _getPatientToBeContactedByCurrentNonAdminUser : function(match)
    {
        if (this._isAdmin) {
            return null;
        }

        if (!this._accessAtLeastEdit(match.reference) && !this._accessAtLeastEdit(match.matched)) {
            // user does not have at least edit access to any of the patients in the match: do not allow to contact or mark as user-contacted
            return null;
        }

        if (match.notMyCase == null) {
            // not clear which of the two cases should be contacted: only allow contact if both cases are "cotactable"
            if (!this._canCaseBeContacted(match.matched) || !this._canCaseBeContacted(match.reference))
            {
                return null;
            }
            return undefined;
        } else {
            // match.notMyCase is the case to be contacted: check that ic can be contacted (e.g. no pubmedid, has email)
            if (!this._canCaseBeContacted(match.notMyCase)) {
                return null;
            }
            return match.notMyCase;
        }
    },

    _getContactButtonHTML : function(match)
    {
        var matchedCase = this._getPatientToBeContactedByCurrentNonAdminUser(match);

        if (matchedCase === null) {
            // a match should not be contacted (because it has a pubmedID or not enough permissions for current user to contact)
            // OR unable to determine which of the two cases should be contacted => do not show contact button
            return '';
        }

        var matchId = match.id[0] ? match.id[0] : match.id;

        var className = "button";
        if (match.contacted || match.userContacted) {
            className += " secondary";
        }

        if (matchedCase === undefined) {
            // not sure which of the two patients to contact
            className += " pick-contact-button";

            var leftName  = match.reference.contact_info.name ? match.reference.contact_info.name :
                              (match.reference.contact_info.institution ? match.reference.contact_info.institution : match.emails[0]);
            var rightName = match.matched.contact_info.name ? match.matched.contact_info.name :
                              (match.matched.contact_info.institution ? match.matched.contact_info.institution : match.emails[0]);

            var buttonHTML = '<a class="' + className
            + '" data-matchid="' + matchId
            + '" data-leftpatientid="' + match.reference.patientId
            + '" data-leftserverid="' + match.reference.serverId
            + '" data-leftlabel="' + leftName
            + '" data-rightpatientid="' + match.matched.patientId
            + '" data-rightserverid="' + match.matched.serverId
            + '" data-rightlabel="' + rightName
            + '" href="#"><span class="fa fa-envelope"></span></a>';
        } else {
            className += " contact-button";
            var buttonHTML = '<a class="' + className
            + '" data-matchid="' + matchId
            + '" data-patientid="' + matchedCase.patientId
            + '" data-serverid="' + matchedCase.serverId
            + '" href="#"><span class="fa fa-envelope"></span></a>';
        }

        return buttonHTML;
    },

    _getMarkUserContactedHTML : function(match)
    {
        // the case user/user's group is NOT owner of is unsolved
        var matchId = match.id[0] || match.id;
        var label = this._MARK_USER_CONTACTED_BUTTON_LABEL;
        var icon = (!match.userContacted) ? "fa fa-square-o" : "fa fa-check-square-o";
        var title = (match.userContacted) ? this._MARK_USER_UNCONTACTED_BUTTON_TITLE : this._MARK_USER_CONTACTED_BUTTON_TITLE;

        var buttonHTML = '<span class="buttonwrapper"><a class="button secondary mark-user-contacted-button" title="' + title + '" data-matchid="'
                         + matchId + '" data-userContacted="' + !!match.userContacted + '" href="#"><span class="' + icon + '"></span> '+ label +'</a></span>';
        return buttonHTML;
    },

// -- AFTER PROCESS TABLE

    _afterProcessTable : function(matchesForPage)
    {
        this._afterProcessTableRegisterCollapisbleDivs();
        this._afterProcessTableStatusListeners();
        this._afterProcessTableCollapseEmails();
        this._afterProcessTableInitNotificationEmails(matchesForPage);
        this._afterProcessTableComments();
        this._afterProcessTableNotes();
        this._afterProcessTableNotificationHistory();
    },

    _hideAllNotificationHistoryDialogs: function(item)
    {
        this._tableElement.select('.notification-history-container').each(function (elm) {
            if (item && item === elm) {
                return;
            }
            elm.addClassName('hidden');
        });
    },

    _afterProcessSingleNotificationHistory : function(elm)
    {
        var history_container = elm.up('td').down('.notification-history-container');

        // hide history container on table update
        history_container.addClassName('hidden');

        elm.on('click', function(event) {
            this._hideAllNotificationHistoryDialogs(history_container);
            history_container.toggleClassName('hidden');
        }.bind(this));

        var hideTool = elm.up('td').down('.hide-tool');
        hideTool.on('click', function(event) {
            history_container.addClassName('hidden');
        });
    },

    _afterProcessTableNotificationHistory : function()
    {
        this._tableElement.select('.notification-history').each(function (elm) {
            this._afterProcessSingleNotificationHistory(elm);
        }.bind(this));
    },

    _hideAllCommentsDialogs: function(item)
    {
        this._tableElement.select('.comment-container').each(function (elm) {
            if (item && elm === item) {
                return;
            }
            elm.addClassName('hidden');
        });
    },

    _afterProcessTableComments : function()
    {
        this._tableElement.select('.fa.comment').each(function (elm) {
            var comment_container = elm.up('td').down('.comment-container');
            var textarea = comment_container.down('textarea');
            var saveButton = elm.up('td').down('.save-comment');
            saveButton.disable();
            var commentMatch = this._matches.filter( function(match) { return String(match.id) === saveButton.dataset.matchid; } );
            var records = commentMatch && commentMatch[0] && commentMatch[0].comments || '';

            // hide comment container on table update
            comment_container.addClassName('hidden');

            elm.on('click', function(event) {
                event.stop();
                comment_container.toggleClassName('hidden');
                this._hideAllNotesDialogs();
                this._hideAllCommentsDialogs(comment_container);
                !comment_container.hasClassName('hidden') && textarea.focus();
            }.bind(this));

            var hideTool = elm.up('td').down('.comment-container .hide-tool');
            hideTool.on('click', function(event) {
                comment_container.addClassName('hidden');
            });

            textarea.on('input', function(event) {
                if (textarea.value.trim()){
                    saveButton.enable();
                } else {
                    saveButton.disable();
                }
            });

            saveButton.on('click', function(event) {
                saveButton.disable();
                this._saveComment(event);
            }.bind(this));

        }.bind(this));

        //initiate HelpButton widgets
        Event.fire(document, "xwiki:dom:updated", {'elements' : [this._tableElement]});
    },

    _hideAllNotesDialogs: function(item)
    {
        this._tableElement.select('.notes-container').each(function (elm) {
            if (item && elm === item) {
                return;
            }
            var textarea = elm.down('textarea');
            elm.up('td').down('span.fa.notes').className = (textarea.value) ? "notes fa fa-file" : "notes fa fa-file-o";
            elm.addClassName('hidden');
        });
    },

    _afterProcessTableNotes : function()
    {
        this._tableElement.select('.fa.notes').each(function (elm) {
            var notes_container = elm.up('td').down('.notes-container');
            var textarea = notes_container.down('textarea');

            // hide notes container on table update
            notes_container.addClassName('hidden');

            elm.on('click', function(event) {
                event.stop();
                this._hideAllNotesDialogs(notes_container);
                this._hideAllCommentsDialogs();
                notes_container.toggleClassName('hidden');
                !notes_container.hasClassName('hidden') && textarea.focus();
            }.bind(this));

            var hideTool = elm.up('td').down('.notes-container .hide-tool');
            hideTool.on('click', function(event) {
                elm.className = (textarea.value) ? "notes fa fa-file" : "notes fa fa-file-o";
                notes_container.addClassName('hidden');
            });

            textarea.on('input', function(event) {
                if (textarea.value.trim()){
                    saveButton.enable();
                } else {
                    saveButton.disable();
                }
            });

            var saveButton = elm.up('td').down('.save-notes');
            saveButton.disable();
            saveButton.on('click', function(event) {
                saveButton.disable();
                elm.className = (textarea.value) ? "notes fa fa-file" : "notes fa fa-file-o";
                notes_container.addClassName('hidden');
                this._saveNote(event);
            }.bind(this));

            var notesMatch = this._matches.filter(function(match) { return String(match.id) === saveButton.dataset.matchid; });
            textarea.value = (notesMatch && notesMatch[0] && notesMatch[0].notes) ? notesMatch[0].notes : '';
        }.bind(this));
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

    _afterProcessSingleContactButton : function(elm)
    {
        elm.on('click', function(event) {
            event.stop();
            this._contactDialog.launchContactDialog(elm.dataset.matchid, elm.dataset.patientid, elm.dataset.serverid);
        }.bind(this));
    },

    _afterProcessPickMatchToContactButton : function(elm)
    {
        elm.on('click', function(event) {
            event.stop();
            this._matchSelectDialog.show(elm, this._contactDialog.launchContactDialog.bind(this._contactDialog));
        }.bind(this));
    },

    _afterProcessSingleUserContactedButton : function(elm)
    {
        elm.on('click', function(event) {
            event.stop();
            this._markUserContacted(event);
        }.bind(this));
    },

    _afterProcessTableInitNotificationEmails : function(matchesForPage)
    {
        if (matchesForPage.length > 0 ) {
            this._notificationsButton.show();
        } else {
            this._notificationsButton.hide();
        }

        $$('input[type=checkbox][class="notify"]').each(function (elm) {
            elm.on('click', function(event) {
                if (this._getMarkedToNotify().length > 0) {
                    this._notificationsButton.disabled = false;
                } else {
                    this._notificationsButton.disabled = true;
                }
            }.bind(this));
        }.bind(this));

        $$('.contact-button').each(function (elm) {
            this._afterProcessSingleContactButton(elm);
        }.bind(this));

        $$('.pick-contact-button').each(function (elm) {
            this._afterProcessPickMatchToContactButton(elm);
        }.bind(this));

        $$('.mark-user-contacted-button').each(function (elm) {
            this._afterProcessSingleUserContactedButton(elm);
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
        var matchesById = this._cachedMatches.filter(function(match) { return String(match.id) === matchId; });
        var match = matchesById[0];

        // show/hide match details pop-up
        expand ? this._matchDetailsView.show(match, target) : this._matchDetailsView.close();
    },

//-- NOTIFICATION

    _sendNotification : function(event)
    {
        var matchIDs = this._getMarkedToNotify();
        if (matchIDs.length == 0) {
            return;
        }
        var idsToNotify = JSON.stringify({ ids: matchIDs});
        new Ajax.Request(this._ajaxURL + "/email", {
            parameters : {'matchesToNotify' : idsToNotify},
            onCreate : function (response) {
                this._notificationsButton.disabled = true;
                this._utils.showSent('send-notifications-messages');
            }.bind(this),
            onSuccess : function (response) {
                if (!response.responseJSON || !response.responseJSON.results) {
                    this._errorDialog.showError(this._CONTACT_SEND_ERROR_HEADER, this._SERVER_ERROR_MESSAGE);
                    return;
                }

                var failedIDs = response.responseJSON.results.failed;
                if (failedIDs && failedIDs.length > 0) {
                    var message = this._ADMIN_NOTIFICATION_FAILED_MESSAGE + " " + this._getAllEmailsForMatchIds(failedIDs);
                    this._errorDialog.showError(this._CONTACT_SEND_ERROR_HEADER, message);
                }

                // add data about patients to notification response (which currently only has matchIDs, but not patient IDs
                // involved - which may include only one of the two patients in a match, or both)
                var notificationResult = this._addPatientDataToResults(response.responseJSON.results, matchIDs);
                notificationResult["successNotificationHistories"] = response.responseJSON.successNotificationHistories || '';

                // highlight table cells/rows as notified/failed to notify
                this._updateTableAfterNotification(notificationResult, true);
            }.bind(this),
            onFailure : function (response) {
                this._errorDialog.showError(this._CONTACT_SEND_ERROR_HEADER, this._SERVER_ERROR_MESSAGE);
                this._uncheckNotifyCheckboxes();
            }.bind(this),
            onComplete : function () {
                this._utils.clearHint('send-notifications-messages');
            }.bind(this)
        });
    },

    _uncheckNotifyCheckboxes : function(isAdminNotification)
    {
        // un-check all notification checkboxes, only admin sees them
        if (isAdminNotification) {
            this._tableElement.select('input[type=checkbox][class="notify"]').each(function (contactCheckbox) {
                contactCheckbox.checked = false;
            });
        }
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
    _handleUserNotifiedUpdate: function(event)
    {
        if (!event || !event.memo || !event.memo.notificationResult) {
            return;
        }

        this._utils.showReplyReceived('send-notifications-messages');

        // highlight table cells/rows as notified/failed to notify
        this._updateTableAfterNotification(event.memo.notificationResult, false);
    },

    _addPatientDataToResults: function(restResponse, matchIDs) {
        // convert matchIDs to a map { matchID -> [ list of patients (for which their owners were contacted) ] }
        var matchPatients = {};
        matchIDs.each(function(match) {
                    if (!matchPatients.hasOwnProperty(match.matchId)) {
                        matchPatients[match.matchId] = [];
                    }
                    matchPatients[match.matchId].push(match.patientId);
                });

        restResponse["notifiedPatients"] = matchPatients;
        return restResponse;
    },

    // updates the table after a match (or matches) have been notified by either the user (isAdminNotification == false) or admin (isAdminNotification == true)
    // notifiedPatients, failedNotifications: a map of { matchId -> [ list of notified patient IDs ] }
    _updateTableAfterNotification : function (notificationResult, isAdminNotification) {
        this._uncheckNotifyCheckboxes(isAdminNotification);

        if (notificationResult.success && notificationResult.success.length > 0 ) {
            var properties = {'notified': true, 'state': 'success', 'isAdminNotification': isAdminNotification};
            properties.successNotificationHistories = notificationResult.successNotificationHistories || '';
            this._setState(notificationResult.success, properties, notificationResult.notifiedPatients);
        }
        if (notificationResult.failed && notificationResult.failed.length > 0) {
            var properties = {'state': 'failure', 'isAdminNotification': isAdminNotification};
            this._setState(notificationResult.failed, properties, notificationResult.notifiedPatients);
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

    _setState : function(matchIds, properties, notifiedPatients)
    {
        if (!matchIds || !properties) {
            return;
        }

        var strMatchIds = String(matchIds).split(",");
        var matchesToSet = [];
        this._matches.each( function(match) {
            // a match can potentially have more than one ID (e.g. P1 matchs P2 and P2 matches P1 may be two different matches, but they are equivalent and grouped into a single match with two IDs)
            var curIds = String(match.id).split(",");
            // if at least one of the match IDs matches an ID that changed its state => include the match in the list of affected matches
            if (this._utils.listsIntersect(strMatchIds, curIds)) {
                matchesToSet.push(match);
            }
        }.bind(this));

        matchesToSet.each( function(match, index) {
            var matchIndex = this._matches.indexOf(match);
            var cachedIndex = this._cachedMatches.indexOf(match);

            if (properties.hasOwnProperty('notified')) {
                if (properties.successNotificationHistories && Object.keys(properties.successNotificationHistories).indexOf(match.id.toString()) > -1) {
                    this._matches[matchIndex].notificationHistory = properties.successNotificationHistories[match.id];
                    this._organiseNotificationHistory(this._matches[matchIndex]);
                    this._cachedMatches[cachedIndex].notificationHistory = properties.successNotificationHistories[match.id];
                    this._organiseNotificationHistory(this._cachedMatches[cachedIndex]);
                }

                if (properties.hasOwnProperty('userContacted')) {
                    this._matches[matchIndex].userContacted = properties.userContacted;
                    this._cachedMatches[cachedIndex].userContacted = properties.userContacted;
                }

                // re-generate whole contact <td>
                var contactTd = this._tableElement.down('tr[data-matchid="' + match.id +'"] td[name="contact"]');
                contactTd && contactTd.remove();
                var newContactTd = this._getContact(this._matches[matchIndex]);
                this._tableElement.down('tr[data-matchid="' + match.id +'"]').insert(newContactTd);
                contactTd = this._tableElement.down('tr[data-matchid="' + match.id +'"] td[name="contact"]');
                if (contactTd) {
                    var history = contactTd.down('.notification-history');
                    history && this._afterProcessSingleNotificationHistory(history);
                    var contactButton = contactTd.down('.contact-button');
                    contactButton && this._afterProcessSingleContactButton(contactButton);
                    var userContactedButton = contactTd.down('.mark-user-contacted-button');
                    userContactedButton && this._afterProcessSingleUserContactedButton(userContactedButton);
                }
            }

            if (properties.hasOwnProperty('status')) {
                this._matches[matchIndex].status = properties.status;
                this._cachedMatches[cachedIndex].status = properties.status;
            }

            if (properties.hasOwnProperty('state')) {
                // FIXME: "match.reference.patientId != match.matched.patientId"
                //        when notifying patients, we know matchID and patientID. But in theory both reference and match
                //        patient may have the same ID (when one is local, another is remote). We actually know which one
                //        we are notifying, so we can pass this information around, but it requires more refactoring, so
                //        since this is an extremely inlikely corner case it is left "as is" for now - just both will
                //        be highlighted, so no real functionality loss happens
                //
                // TODO: simplify logic related to match.id once the duality of match.id is resolved on the back-end
                var matchIdArray = String(match.id).split(",");
                if (notifiedPatients && this._utils.listsIntersect(Object.keys(notifiedPatients), matchIdArray) && match.reference.patientId != match.matched.patientId) {
                    // highlight only cells with contacted emails
                    // use custom highligh css class, since the color used to highlight the enitre row is too bleak to notice within a single cell
                    var highllightCSSClass = (properties.state == "failure") ? "failure" : "notified";
                    matchIdArray.each(function(matchId) {
                        if (notifiedPatients.hasOwnProperty(matchId)) {
                            notifiedPatients[matchId].each(function(patientId) {
                                if (match.reference.patientId == patientId) {
                                    this._tableElement.down('[data-matchid="' + match.id +'"]').down("[name=referenceEmails]").className = highllightCSSClass;
                                } else {
                                    this._tableElement.down('[data-matchid="' + match.id +'"]').down("[name=matchedEmails]").className = highllightCSSClass;
                                }
                            }.bind(this));
                        }
                    }.bind(this));
                } else {
                    // highlight entire row
                    this._tableElement.down('[data-matchid="' + match.id +'"]').className = properties.state;
                }
            }

            if (properties.hasOwnProperty('comments')) {
                this._matches[matchIndex].comments = properties.comments;
                this._cachedMatches[cachedIndex].comments = properties.comments;
                // update comments table in pop-up
                var commentsContainer = this._tableElement.down('tr[data-matchid="' + match.id +'"] .comment-container');
                if (commentsContainer) {
                    var commentsTable = commentsContainer.down('.comments-table');
                    commentsTable && commentsTable.remove();
                    var newCommentsTable = this._generateCommentsTable(this._matches[matchIndex].comments);
                    commentsContainer.insert(newCommentsTable);
                    var commentsIconEl = commentsContainer.up('tr').down('span.fa.comment');
                    if (commentsIconEl) { commentsIconEl.className = "comment fa fa-comments"; }
                    var textarea = commentsContainer.down('textarea');
                    if (textarea) { textarea.value = ''; }
                }
            }

            if (properties.hasOwnProperty('notes')) {
                this._matches[this._matches.indexOf(match)].notes = properties.notes;
                this._cachedMatches[this._cachedMatches.indexOf(match)].notes = properties.notes;
            }
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

        new Ajax.Request(this._ajaxURL + '/' + matchId + "?method=PATCH", {
            contentType: 'application/json',
            parameters : {'status' : status},
            onSuccess  : function (response) {
                if (!response.responseJSON) {
                    this._setState([matchId], { 'state': 'failed' });
                    this._utils.showFailure('show-matches-messages', "Setting status `" + status + "` failed");
                    return;
                }

                this._setState([matchId], { 'status': status, 'state': 'success' });
            }.bind(this),
            onFailure : function (response) {
                this._utils.showFailure('show-matches-messages');
            }.bind(this)
        });
    },

//--MARK NOTIFIED --

    // Send request to add a user-notified attrubute to the notification history
    _markUserContacted : function(event)
    {
        var target = event.element();
        if (!target) return;
        var button = target.up('.buttonwrapper').down('.mark-user-contacted-button');
        if (!button) return;
        var matchId = String(button.dataset.matchid);
        // new user-contacted status to set to is a negation of the current one
        var newUserContactedStatus = !JSON.parse(button.dataset.usercontacted);

        new Ajax.Request(this._ajaxURL + '/' + matchId + "?method=PATCH", {
            contentType : 'application/json',
            parameters  : {'isUserContacted' : newUserContactedStatus},
            onSuccess   : function (response) {
                if (!response.responseJSON) {
                    this._setState([matchId], { 'state': 'failed' });
                    this._utils.showFailure('show-matches-messages', "Mark matches user-contacted status to " + newUserContactedStatus + " failed");
                    return;
                }

                this._setState([matchId], {'notified': true, 'userContacted': newUserContactedStatus, 'state': 'success'});
            }.bind(this),
            onFailure : function (response) {
                this._utils.showFailure('show-matches-messages');
            }.bind(this)
        });
    },

// SAVE COMMENT

    // Send request to save match comment
    _saveComment : function(event)
    {
        var target = event.element();
        if (!target) return;
        var comment = target.up('.comment-container').down('textarea').value;
        if (!comment) return;
        var matchId = String(target.dataset.matchid);

        new Ajax.Request(this._ajaxURL + '/' + matchId + "?method=PATCH", {
            contentType : 'application/json',
            parameters  : { 'comment': encodeURI(comment) },
            onSuccess   : function (response) {
                if (!response.responseJSON) {
                    this._setState([matchId], { 'state': 'failed' });
                    this._utils.showFailure('show-matches-messages', "Saving comment failed");
                    return;
                }

                var updatedMatch = response.responseJSON;
                this._setState([matchId], {'state': 'success', 'comments' : updatedMatch.comments});
            }.bind(this),
            onFailure : function (response) {
                this._utils.showFailure('show-matches-messages');
            }.bind(this)
        });
    },

 // SAVE Notes

    // Send request to save match notes
    _saveNote : function(event)
    {
        var target = event.element();
        if (!target) return;
        var matchId = String(target.dataset.matchid);
        var note = target.up('.notes-container').down('textarea').value;

        new Ajax.Request(this._ajaxURL + '/' + matchId + "?method=PATCH", {
            contentType : 'application/json',
            parameters  : {'note' : encodeURI(note) },
            onSuccess   : function (response) {
                if (!response.responseJSON) {
                    this._setState([matchId], { 'state': 'failed' });
                    this._utils.showFailure('show-matches-messages', "Saving notes failed");
                    return;
                }

                var updatedMatch = response.responseJSON;
                this._setState([matchId], {'state': 'success', 'notes' : updatedMatch.notes});
            }.bind(this),
            onFailure : function (response) {
                this._utils.showFailure('show-matches-messages');
            }.bind(this)
        });
    }

    });
    return PhenoTips;
}(PhenoTips || {}));
