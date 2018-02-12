var PhenoTips = (function(PhenoTips) {
  var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
  widgets.MatcherPaginator = Class.create({
    initialize: function(table, domNode, max) {
      var _this = this;
      this.table = table;
      this.max = max;
      this.pagesNode = domNode.select(".xwiki-livetable-pagination-content")[0];
      var prevPagination = domNode.select(".prevPagination")[0];
      var nextPagination = domNode.select(".nextPagination")[0];
      prevPagination && prevPagination.observe("click", function(ev) {
        _this.gotoPrevPage(ev);
      });
      nextPagination && nextPagination.observe("click", function(ev) {
        _this.gotoNextPage(ev);
      });
    },
    refreshPagination: function(maxResults) {
      var _this = this;
      this.pagesNode.innerHTML = "";
      var pages = this.table.totalPages;
      var currentMax = (maxResults) ? maxResults : this.max;
      var currentPage = this.table._page;
      var startPage = Math.floor(currentPage / currentMax) * currentMax - 1;
      
      // always display the first page
      if (startPage>1) {
         this.pagesNode.insert(_this.createPageLink(1, false));
         if (startPage>2) {
            this.pagesNode.insert(" ... ");
         }
      }
      // display pages
      for (var i=(startPage<=0) ? 1 : startPage;i<=Math.min(startPage + currentMax + 1, pages);i++) {
         var selected = (currentPage == i);
         this.pagesNode.insert(_this.createPageLink(i, selected));
         this.pagesNode.insert(" ");
      }
      // always display the last page.
      if (i<pages) {
        if (i+1 <= pages) {
          this.pagesNode.insert(" ... ");
        }
        this.pagesNode.insert(_this.createPageLink(pages, false));
      }

      this._updateArrowsState(currentPage, pages);
    },
    createPageLink: function(page, selected) {
        var pageSpan = new Element("a", {'class':'pagenumber', 'href':'#'}).update(page);
        if (selected) {
           pageSpan.addClassName("selected");
        }
        var _this = this;
        pageSpan.observe("click", function(ev){
            ev.stop();
            _this.gotoPage(ev.element().innerHTML);
        });
        return pageSpan;
    },
    gotoPage: function(page)
    {
      this.table._page = parseInt(page);
      this.table._buildTable();
    },
    gotoPrevPage: function(ev) {
      ev.stop();
      var prevPage = this.table._page - 1;
      if (prevPage > 0) {
        this.table._page--;
        this.table._buildTable();
      }
    },
    gotoNextPage: function(ev) {
      ev.stop();
      var pages = this.table.totalPages;
      var nextPage = this.table._page + 1;
      if (nextPage <= pages) {
        this.table._page++;
        this.table._buildTable();
      }
    },
    _updateArrowsState: function(currentPage, pages) {
      if (currentPage <= 1) {
        this._switchClassName('noPrevPagination', 'prevPagination');
      } else {
        this._switchClassName('prevPagination', 'noPrevPagination');
      }
      if (currentPage >= pages) {
        this._switchClassName('noNextPagination', 'nextPagination');
      } else {
        this._switchClassName('nextPagination', 'noNextPagination');
      }
    },
    _switchClassName: function(addName, removeName) {
      if (!this.pagesNode.up().previous('.controlPagination')) {
        return;
      }
      var page = this.pagesNode.up().previous('.controlPagination').down('.' + removeName);
      page && page.addClassName(addName).removeClassName(removeName);
    }
  });
  return PhenoTips;
}(PhenoTips || {}));
