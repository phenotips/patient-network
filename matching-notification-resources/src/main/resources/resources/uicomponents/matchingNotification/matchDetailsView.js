var PhenoTips = (function(PhenoTips) {
  var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
  widgets.MatchDetailsView = Class.create({
    initialize: function() {
        this._UNKNOWN_INFO_MARKER = "unknown";
        this._METADATA_MARKER = "metadata";
        this._NEGATIVE_MARKER = "negative";
    },

    show  : function(match, elm) {
        this.close(elm);
        this._matchBreakdown = this._createDialogContainer(match);
        elm.insert({ "after" : this._matchBreakdown });
        this._matchBreakdown.down('.hide-tool').on('click', function(event) {
            this.close();
            elm.addClassName("fa-plus-square-o");
            elm.removeClassName("fa-minus-square-o");
        }.bind(this));
    },

    close : function(elm) {
        $("matchDetails") && $("matchDetails").remove();
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
        row.insert(new Element('th', {'class' : 'hint query'}).update(r.reference.patientId + refExternalId));
        row.insert(new Element('th', {'class' : 'hint result'}).update(r.matched.patientId + matchedExternalId));
    },

    _createTableCategoryHeader : function(table, cssClass, title) {
        var categoryRow = this._getEmptyTableRow(table);
        categoryRow.insert(new Element('th', {'class' : cssClass, 'colspan' : '2'}).update(title));
    },

    _displayAgeOfOnset : function(table, r) {
        this._createTableCategoryHeader(table, 'match-category-header', "$escapetool.javascript($services.localization.render('phenotips.similarCases.ageOfOnset'))");
        var row = this._getEmptyTableRow(table);
        if (!r.reference.age_of_onset && !r.matched.age_of_onset) {
            row.insert(new Element('p', {'class' : 'hint block'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.noAgeOfOnset'))"));
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
             row.insert(new Element('p', {'class' : 'hint block'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.noModeOfInheritance'))"));
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
            row.insert(new Element('p', {'class' : 'hint block'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.noPhenotypeInformation'))"));
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
                    crtPFeatures.insert(_this._displayFeature(queryFeatures['phenotype'] && queryFeatures['phenotype'][fId] || queryFeatures['prenatal_phenotype'] && queryFeatures['prenatal_phenotype'][fId]) || (fId && ("<div>"+fId+"</div>")) || '');
                });
            } else {
                crtPFeatures.insert("-");
            }
            if (featureMatch.match) {
                var undisclosedCount = 0;
                featureMatch.match.each(function (fId) {
                  if (fId) {
                      otherPFeatures.insert(_this._displayFeature(resultFeatures['phenotype'] && resultFeatures['phenotype'][fId] || resultFeatures['prenatal_phenotype'] && resultFeatures['prenatal_phenotype'][fId]) || (fId && ("<div>"+fId+"</div>")) || '');
                  } else {
                      undisclosedCount++;
                  }
              });
              if (undisclosedCount > 0) {
                  otherPFeatures.insert(new Element('div', {'class' : _this._UNKNOWN_INFO_MARKER})
                                                   .update(undisclosedCount
                                                   + " $escapetool.javascript($services.localization.render('phenotips.similarCases.undisclosedFeature'))"));
              }
          } else {
              otherPFeatures.insert("-");
          }
       });
    },

    _displayFeature : function(f) {
        if (!f) { return ''; }
        var prefix = "", cssModifier = !f.name && this._UNKNOWN_INFO_MARKER || "";
        if (f.present === false) {
            prefix = "NO ";
            cssModifier += " " + this._NEGATIVE_MARKER;
        }
        var container = new Element("div");
        var name = new Element("span", {
             "title" : (f.id || "") + " " + (f.name || ""),
             "class" : cssModifier
        }).insert(f.name && prefix + f.name || "$escapetool.javascript($services.localization.render('phenotips.similarCases.undisclosedInfo'))");
            //.insert(f.type && new Element("span", {"class" : this._METADATA_MARKER}).insert(" (" + f.type + ")") || "");
        container.insert(name);
        if (f.metadata && f.metadata instanceof Array && f.metadata.length > 0) {
            var metadata = new Element ("ul", {"class" : this._METADATA_MARKER});
            container.insert(metadata);
            f.metadata.each(function(m) {
                if (m.name) {
                    var entry = new Element("li", {"title" : (m.id || "") + " " + (m.name || ""), "class" : f.type || ""}).update(m.name || "$escapetool.javascript($services.localization.render('phenotips.similarCases.undisclosedInfo'))");
                    metadata.insert(entry);
                }
            });
        }
        return container;
    },

    _displayGeneMatches : function(table, r) {
        this._createTableCategoryHeader(table, 'match-category-header', "$escapetool.javascript($services.localization.render('phenotips.similarCases.geneMatchingBreakdown'))");
        var _this = this;
        if (!r.genotypeSimilarity) {
            // The 'genes' field is absent when there is no genetic information in one of the two patients
            var row = _this._getEmptyTableRow(table);
            row.insert(new Element('p', {'class' : 'hint block'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.noGeneticInfo'))"));
        } else if (r.genotypeSimilarity.length == 0) {
            // The 'genes' field is present but empty when both patients have genetic information available but no matches were found
            var row = _this._getEmptyTableRow(table);
            row.insert(new Element('p', {'class' : 'hint block'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.noGenotypeMatches'))"));
        } else {
            // Display genotype matches
            r.genotypeSimilarity.each(function (geneInfo) {
                var titleRow = _this._getEmptyTableRow(table, 'gene-row');
                titleRow.insert(new Element('th', {'class' : 'gene', 'colspan' : 2})
                                  .insert(geneInfo.symbol || geneInfo.gene)
                                  .insert(new Element('input', {'type':'hidden', 'name': 'gene_name', 'class' : 'gene-name', 'value' : geneInfo.gene}))
                                  .insert(new Element('span', {'class' : 'variants-toggle'}))
                                   /* The genename service should populate this with links if the input is present*/);
                if (geneInfo.reference || geneInfo.match) {
                    if (geneInfo.reference && geneInfo.reference.variants || geneInfo.match && geneInfo.match.variants) {
                        var showVariants = new Element("a", {"href" : "#", "class" : "button secondary tool show"}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.showVariants'))");
                        var hideVariants = new Element("a", {"href" : "#", "class" : "button secondary tool hide"}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.hideVariants'))");
                        var variantToggle = new Element('span', {"class" : "buttonwrapper"}).insert(showVariants).insert(hideVariants);

                        titleRow.down('span.variants-toggle').insert(variantToggle);
                        var variantsRow = _this._getEmptyTableRow(table, 'variants-row');
                        variantsRow.insert(new Element('td', {'class' : 'query'})
                                            .insert(geneInfo.reference
                                                    && geneInfo.reference.variants
                                                    && _this._displayVariants(geneInfo.reference.variants)
                                                    || "$escapetool.javascript($services.localization.render('phenotips.similarCases.noVariantInformation'))"));
                        variantsRow.insert(new Element('td', {'class' : 'result'})
                                            .insert(geneInfo.match
                                                    && geneInfo.match.variants
                                                    && _this._displayVariants(geneInfo.match.variants)
                                                    || "$escapetool.javascript($services.localization.render('phenotips.similarCases.noVariantInformation'))"));

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
    },

    _displayVariants : function (variants) {
        if (!variants || variants.length == 0) {
            return new Element('span', {'class' : 'hint'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.noVariantInformation'))");
        }
        var result = new Element('table', {'class' : 'variants-data'});
        var _this = this;
        var hRow = _this._getEmptyTableRow(result);
        variants.each(function (v) {
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
            row.insert(new Element('p', {'class' : 'hint block'}).update("$escapetool.javascript($services.localization.render('phenotips.similarCases.undiagnosed'))"));
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
            if (f.type && f.id) {
              featureIndex[f.type] = featureIndex[f.type] || {};
              featureIndex[f.type][f.id] = f;
            }
          });
        }
        return featureIndex;
      }

  });
  return PhenoTips;
}(PhenoTips || {}));