var PhenoTips = (function(PhenoTips) {
  var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
  widgets.MatchDetailsView = Class.create({
    initialize: function() {
        this._METADATA_MARKER = "metadata";
        this._NEGATIVE_MARKER = "negative";
    },

    show  : function(match, elm) {
        this._trigger = elm; // trigger is the component that was clicked to expand/collapse match details view ( +/- sign).
        this.close();
        this._matchBreakdown = this._createDialogContainer(match);
        this._trigger.insert({ "after" : this._matchBreakdown });
        this._trigger.removeClassName("fa-plus-square-o");
        this._trigger.addClassName("fa-minus-square-o");
        this._matchBreakdown.down('.hide-tool').on('click', function(event) {
            this.close();
        }.bind(this));
        document.observe('click', this._hideOnOutsideClick.bind(this));
    },

    close : function() {
        $("matchDetails") && $("matchDetails").remove();
        this._trigger.addClassName("fa-plus-square-o");
        this._trigger.removeClassName("fa-minus-square-o");
        document.stopObserving('click', this._hideOnOutsideClick);
    },

    _hideOnOutsideClick : function (event) {
        if (!event.findElement('.match-details') && !event.findElement('.collapse-gp-tool')) {
          this.close();
        }
    },

    _createDialogContainer : function(match)
    {
        var result = new Element("div", {"class" : "xPopup match-details", "id" : "matchDetails"});
        result.insert(new Element("span", {"class" : "hide-tool"}).insert('×'));
        var table = new Element('table');
        this._createTableSectionTitle(table, match);
        this._displayAgeOfOnset(table, match);
        this._displayModeOfInheritance(table, match);
        this._displayFeatureMatches(table, match);
        this._displayGeneMatches(table, match);
        this._displayDisorders(table, match);
        result.insert(table);
        return result;
    },

    _createTableSectionTitle : function(table, r) {
        var row = this._getEmptyTableRow(table);
        var refExternalId = (r.reference.externalId) ? " : " + r.reference.externalId : '';
        var matchedExternalId = (r.matched.externalId) ? " : " + r.matched.externalId : '';
        row.insert(new Element('th', {'class' : 'patient-name'}).update(this._getRemotePatientId(r.reference.patientId, r.reference.serverId) + refExternalId));
        row.insert(new Element('th', {'class' : 'patient-name result'}).update(this._getRemotePatientId(r.matched.patientId, r.matched.serverId) + matchedExternalId));
    },

    _getRemotePatientId : function(patientId, serverId) {
        // Parse remote patient ID because MyGene2 uses URLs in the ID field to ensure that the public MyGene2 profiles are actually delivered to the end user
        if (serverId == "mygene2" && patientId && patientId.startsWith("http")) {
            var matches = patientId.match(/[\d]+/g);
            var id = matches ? matches[matches.length - 1] : patientId;
            return id;
        } else {
            return patientId;
        }
    },

    _createTableCategoryHeader : function(table, cssClass, title) {
        var categoryRow = this._getEmptyTableRow(table);
        categoryRow.insert(new Element('th', {'class' : cssClass, 'colspan' : '2'}).update(title));
    },

    _displayAgeOfOnset : function(table, r) {
        this._createTableCategoryHeader(table, 'match-category-header', "$escapetool.javascript($services.localization.render('phenotips.similarCases.ageOfOnset'))");
        var row = this._getEmptyTableRow(table);
        if (!r.reference.age_of_onset && !r.matched.age_of_onset) {
            row.insert(new Element('td', {'colspan' : '2'}).update(new Element('p', {'class' : 'hint block'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.noAgeOfOnset'))")));
        } else {
            var formatAgeOfOnset = function(age) {return age ? age : "-";};
            row.insert(new Element('td', {'class' : 'table-data query'}).update(formatAgeOfOnset(r.reference.age_of_onset)));
            row.insert(new Element('td', {'class' : 'table-data result'}).update(formatAgeOfOnset(r.matched.age_of_onset)));
         }
     },

     _displayModeOfInheritance : function(table, r) {
         //sort made of inheritance: those that matched to be first in alphabetic order
         if (r.reference.mode_of_inheritance && r.reference.mode_of_inheritance.length > 0 && r.matched.mode_of_inheritance && r.matched.mode_of_inheritance.length > 0) {
             var common = r.reference.mode_of_inheritance.intersect(r.matched.mode_of_inheritance).sort();
             r.reference.mode_of_inheritance = common.concat(r.reference.mode_of_inheritance.sort()).uniq();
             r.matched.mode_of_inheritance = common.concat(r.matched.mode_of_inheritance.sort()).uniq();
         }
         this._createTableCategoryHeader(table, 'match-category-header', "$escapetool.javascript($services.localization.render('phenotips.similarCases.modeOfInheritance'))");
         var row = this._getEmptyTableRow(table);
         if ((!r.reference.mode_of_inheritance || r.reference.mode_of_inheritance.length == 0) && (!r.matched.mode_of_inheritance || r.matched.mode_of_inheritance.length == 0)) {
             row.insert(new Element('td', {'colspan' : '2'}).update(new Element('p', {'class' : 'hint block'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.noModeOfInheritance'))")));
         } else {
             var handleEmptyArray = function(moiArray) {return (moiArray && moiArray.length > 0) ? moiArray : ["-"];};
             var referenceElement = new Element('td', {'class' : 'table-data query'});
             row.insert(referenceElement);
             handleEmptyArray(r.reference.mode_of_inheritance).each(function (item) {
                 referenceElement.insert(new Element('div').update(item));
           });
           var matchedElement = new Element('td', {'class' : 'table-data result'});
           row.insert(matchedElement);
           handleEmptyArray(r.matched.mode_of_inheritance).each(function (item) {
               matchedElement.insert(new Element('div').update(item));
           });
        }
    },

    _displayFeatureMatches : function (table, r) {
        var _this = this;
        _this._createTableCategoryHeader(table, 'match-category-header', "$escapetool.javascript($services.localization.render('phenotips.similarCases.phenotypicFeaturesBreakdown'))");
        if (!r.phenotypesSimilarity) {
            var row = _this._getEmptyTableRow(table);
            row.insert(new Element('td', {'colspan' : '2'}).update(new Element('p', {'class' : 'hint block'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.noPhenotypeInformation'))")));
            return;
        }

        var queryFeatures = _this._createFeatureIndex(r.reference.phenotypes);
        var resultFeatures = _this._createFeatureIndex(r.matched.phenotypes);
        r.phenotypesSimilarity.each(function (featureMatch) {
            var title = featureMatch.category && featureMatch.category.name || "$escapetool.javascript($services.localization.render('phenotips.similarCases.unknownCategory'))";
            _this._createTableCategoryHeader(table, 'phenotype-category', title);

            var phenotypeRow = _this._getEmptyTableRow(table);
            var crtPFeatures = new Element('td', {'class' : 'table-data query'});
            var otherPFeatures = new Element('td', {'class' : 'table-data result'});
            phenotypeRow.insert(crtPFeatures).insert(otherPFeatures);
            if (featureMatch.reference) {
                featureMatch.reference.each(function (fId) {
                    var phenotype = queryFeatures['phenotype'] && queryFeatures['phenotype'][fId] || queryFeatures['prenatal_phenotype'] && queryFeatures['prenatal_phenotype'][fId];
                    crtPFeatures.insert(_this._displayFeature(phenotype) || (fId && ("<div>"+fId+"</div>")) || '');
                });
            } else {
                crtPFeatures.insert("-");
            }
            if (featureMatch.match) {
                featureMatch.match.each(function (fId) {
                    var phenotype = resultFeatures['phenotype'] && resultFeatures['phenotype'][fId] || resultFeatures['prenatal_phenotype'] && resultFeatures['prenatal_phenotype'][fId];
                    otherPFeatures.insert(_this._displayFeature(phenotype) || (fId && ("<div>"+fId+"</div>")) || '');
                });
            } else {
                otherPFeatures.insert("-");
            }
        });
    },

    _displayFeature : function(f) {
        if (!f) { return ''; }
        var label = f.label || "";
        var prefix = "", cssModifier = !label && this._UNKNOWN_INFO_MARKER || "";
        if (f.observed === false) {
            prefix = "NO ";
            cssModifier += " " + this._NEGATIVE_MARKER;
        }
        var container = new Element("div");
        var name = new Element("span", {
             "title" : (f.id || "") + " " + (label || ""),
             "class" : cssModifier
            }).insert(label && prefix + label);
        if (!f.id) {
            name.insert(new Element('span', {'class' : 'fa fa-exclamation-triangle', 'title' : "$escapetool.javascript($services.localization.render('phenotips.patientSheetCode.termSuggest.nonStandardPhenotype'))"}));
        }
        container.insert(name);
        if (f.qualifiers && f.qualifiers instanceof Array && f.qualifiers.length > 0) {
            var metadata = new Element ("ul", {"class" : this._METADATA_MARKER});
            container.insert(metadata);
            f.qualifiers.each(function(m) {
                if (m.label) {
                    var entry = new Element("li", {"title" : (m.id || "") + " " + (m.label || ""), "class" : f.type || ""}).update(m.label || "");
                    metadata.insert(entry);
                }
            });
        }
        return container;
    },

    _displayGeneMatches : function(table, r) {
        this._createTableCategoryHeader(table, 'match-category-header', "$escapetool.javascript($services.localization.render('phenotips.similarCases.geneMatchingBreakdown'))");
        var _this = this;
        var allRefGenes = r.reference.genes.slice();
        var allMatchGenes = r.matched.genes.slice();
        if (!r.genotypeSimilarity && allRefGenes.length == 0 && allMatchGenes.length == 0) {
            // The 'genes' field is absent when there is no genetic information in one of the two patients
            var row = _this._getEmptyTableRow(table);
            row.insert(new Element('td', {'colspan' : '2'}).update(new Element('p', {'class' : 'hint block'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.noGeneticInfo'))")));
        } else if (!r.genotypeSimilarity || r.genotypeSimilarity.length == 0) {
            // The 'genes' field is present but empty when both patients have genetic information available but no matches were found
            var row = _this._getEmptyTableRow(table);
            row.insert(new Element('td', {'colspan' : '2'}).update(new Element('p', {'class' : 'hint block'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.noGenotypeMatches'))")));
        } else {
            var referenceP = r.reference;
            var matchedP = r.matched;
            // Display genotype matches
            r.genotypeSimilarity.each(function (geneInfo) {
                var geneName = geneInfo.symbol || geneInfo.gene;
                allRefGenes.indexOf(geneName) > -1 && allRefGenes.splice(allRefGenes.indexOf(geneName), 1);
                allMatchGenes.indexOf(geneName) > -1 && allMatchGenes.splice(allMatchGenes.indexOf(geneName), 1);
                var titleRow = _this._getEmptyTableRow(table, 'gene-row');
                titleRow.insert(new Element('th', {'class' : 'gene', 'colspan' : 2})
                                  .insert(geneName)
                                  .insert(new Element('span', {'class' : 'variants-toggle'}))
                                   /* The genename service should populate this with links if the input is present*/);
                if (geneInfo.reference || geneInfo.match) {
                    if (geneInfo.reference && geneInfo.reference.variants || geneInfo.match && geneInfo.match.variants) {
                        var showVariants = new Element("a", {"href" : "#", "class" : "button secondary tool show"}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.showVariants'))");
                        var hideVariants = new Element("a", {"href" : "#", "class" : "button secondary tool hide"}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.hideVariants'))");
                        var variantToggle = new Element('span', {"class" : "buttonwrapper"}).insert(showVariants).insert(hideVariants);

                        titleRow.down('span.variants-toggle').insert(variantToggle);
                        var variantsRow = _this._getEmptyTableRow(table, 'variants-row');
                        variantsRow.insert(new Element('td', {'class' : 'query table-data'}).insert(_this._displayVariants(geneInfo.reference)));
                        variantsRow.insert(new Element('td', {'class' : 'result table-data'}).insert(_this._displayVariants(geneInfo.match)));

                        // Add behavior for showing/hiding the variants
                        variantsRow.hide();
                        hideVariants.hide();
                        showVariants.observe('click', function(event) {
                            event.stop();
                            showVariants.hide();
                            hideVariants.show();
                            variantsRow.show();
                        });
                        hideVariants.observe('click', function(event) {
                            event.stop();
                            showVariants.show();
                            hideVariants.hide();
                            variantsRow.hide();
                        });
                    }
                }
            });
        }

        // Display unmatched genes
        if (allRefGenes && allRefGenes.length > 0 || allMatchGenes && allMatchGenes.length > 0) {
            var title = "$escapetool.javascript($services.localization.render('phenotips.similarCases.unmatched'))";
            _this._createTableCategoryHeader(table, 'phenotype-category', title);
            var unmatchedGenesRow = _this._getEmptyTableRow(table);
            var refPGenesTd = new Element('td', {'class' : 'table-data query'});
            var matchedPGenesTd = new Element('td', {'class' : 'table-data result'});
            unmatchedGenesRow.insert(refPGenesTd).insert(matchedPGenesTd);

            if (allRefGenes && allRefGenes.length > 0) {
                allRefGenes.each(function (geneSymbol) {
                    refPGenesTd.insert((geneSymbol && ("<div>"+geneSymbol+"</div>")) || '');
                });
            } else {
                refPGenesTd.insert("-");
            }
            if (allMatchGenes && allMatchGenes.length > 0) {
                allMatchGenes.each(function (geneSymbol) {
                    matchedPGenesTd.insert((geneSymbol && ("<div>"+geneSymbol+"</div>")) || '');
                });
            } else {
                matchedPGenesTd.insert("-");
            }
        }
    },

    _displayVariants : function (geneInfo) {
        
        if (!geneInfo || !geneInfo.variants || geneInfo.variants.length == 0) {
            return new Element('span', {'class' : 'hint'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.noVariantInformation'))");
        }
        var result = new Element('table', {'class' : 'variants-data'});
        var _this = this;
        geneInfo.variants.each(function (v) {
            var vRow = _this._getEmptyTableRow(result);
            var position = new Element('span', {'class' : 'hint'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.undisclosedPosition'))");
            var change = "";
            if (v.chrom && (v.position || (v.start_position && v.end_position))) {
                var start = v.position || v.start_position;
                var end = v.position && (v.position + (v.ref && (v.ref.length - 1) || 0)) || v.end_position;
                position = "chr" + v.chrom.toUpperCase() + ": " + start + " - " + end;
                var positionURLFragment = "chr" + v.chrom.toUpperCase() + ":" + start + "-" + end;
                var assembly = v.assembly || "hg19";
                position = new Element('a', {
                    'href' : 'http://genome.ucsc.edu/cgi-bin/hgTracks?db=' + assembly + '&amp;position=' + encodeURIComponent(positionURLFragment),
                    'class' : 'button secondary',
                    'target' : '__blank',
                    'title' : "$escapetool.javascript($services.localization.render('phenotips.similarCases.UCSCGenomeBrowser'))"
                }).update(position).wrap('span', {'class' : 'buttonwrapper'});
                if (v.ref && v.alt) {
                    var displayedRef = v.ref.length <= 15 ? v.ref : (v.ref.substring(0, 9) + "..." + v.ref[v.ref.length - 1]);
                    var displayedAlt = v.alt.length <= 15 ? v.alt : (v.alt.substring(0, 9) + "..." + v.alt[v.alt.length - 1]);
                    change = "<span class='dna-fragment ref'>" + displayedRef + "</span> → <span class='dna-fragment alt'>" + displayedAlt + "</span>";
                }
            }
            vRow.insert(new Element('td').insert(position)
                                         .insert(new Element('div')
                                                        .insert(new Element('strong').update(change))
                                                        .insert(v.type && new Element('span', {'class' : 'hint'}).update(' (' + v.type + ')') || '')
                                                        .insert(v.isHomozygous && new Element('span', {'class' : 'hint'}).update(' (' + "$escapetool.javascript($services.localization.render('phenotips.similarCases.homozygous'))" + ')') || '')
                                          )
            );
        });
        return result;
    },

    _displayDisorders : function(table, r) {
        //sort made of inheritance: those that matched to be first in alphabetic order
        if (r.matched.disorders && r.matched.disorders.length > 0 && r.reference.disorders && r.reference.disorders.length > 0) {
            var common = r.matched.disorders.intersect(r.reference.disorders).sort();
            r.reference.disorders = common.concat(r.reference.disorders.sort()).uniq();
            r.matched.disorders = common.concat(r.matched.disorders.sort()).uniq();
        }

        this._createTableCategoryHeader(table, 'match-category-header', "$escapetool.javascript($services.localization.render('phenotips.similarCases.diagnosis'))");
        var row = this._getEmptyTableRow(table);
        if ((!r.matched.disorders || r.matched.disorders.length == 0) && (!r.reference.disorders || r.reference.disorders.length == 0)) {
            row.insert(new Element('td', {'colspan' : '2'}).update(new Element('p', {'class' : 'hint block'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.undiagnosed'))")));
        } else {
            var handleEmptyArray = function(moiArray) {return (moiArray && moiArray.length > 0) ? moiArray : ["-"];};
            var referenceElement = new Element('td', {'class' : 'table-data query'});
            row.insert(referenceElement);
            handleEmptyArray(r.reference.disorders).each(function (item) {
                referenceElement.insert(new Element('div').update(item.label || item.name));
          });
          var matchedElement = new Element('td', {'class' : 'table-data result'});
          row.insert(matchedElement);
          handleEmptyArray(r.matched.disorders).each(function (item) {
              matchedElement.insert(new Element('div').update(item.label || item.name));
          });
       }
    },

    _getEmptyTableRow : function(table, cssClass) {
        var row = new Element('tr', {'class' : cssClass || ''});
        table.insert(row);
        return row;
    },

    _createFeatureIndex : function(features) {
        var featureIndex = {};
        if (features) {
          features.each(function (f) {
            if (f.type && (f.id || f.label)) {
              featureIndex[f.type] = featureIndex[f.type] || {};
              var key = f.id || f.label;
              featureIndex[f.type][key] = f;
            }
          });
        }
        return featureIndex;
      }

  });
  return PhenoTips;
}(PhenoTips || {}));
