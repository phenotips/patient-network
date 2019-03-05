var PhenoTips = (function(PhenoTips) {
  var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
  widgets.MatcherPaginator = Class.create({
    initialize: function(table, domNodes, max) {
      var _this = this;
      this.table = table;
      this.max = max;
      this.pagesNodes = [];
      domNodes.each(function(elem){
          _this.pagesNodes.push(elem.down(".xwiki-livetable-pagination-content"));
      });

      this.pagesNodes.each(function(elem){
          elem.down(".prevPagination") && elem.down(".prevPagination").observe("click", function(ev) {
              _this.gotoPrevPage(ev);
          });
          elem.down(".nextPagination") && elem.down(".nextPagination").observe("click", function(ev) {
              _this.gotoNextPage(ev);
        });
      });

    },
    refreshPagination: function() {
      var _this = this;
      this.pagesNodes.each(function(elem){
          elem.innerHTML = "";
      });
      var pages = this.table.totalPages;
      var currentMax = (!this.max) ? pages : this.max;
      var currentPage = this.table.page;
      var startPage = Math.floor(currentPage / currentMax) * currentMax - 1;

      // always display the first page
      if (startPage>1) {
          this.pagesNodes.each(function(elem){
              elem.insert(_this.createPageLink(1, false));
          });
          if (startPage>2) {
             this.pagesNodes.invoke("insert", " ... ");
          }
      }
      // display pages
      for (var i=(startPage<=0) ? 1 : startPage;i<=Math.min(startPage + currentMax + 1, pages);i++) {
         var selected = (currentPage == i);
         this.pagesNodes.each(function(elem){
             elem.insert(_this.createPageLink(i, selected));
         });
         this.pagesNodes.invoke("insert", " ");
      }
      // always display the last page.
      if (i<pages) {
        if (i+1 <= pages) {
            this.pagesNodes.invoke("insert", " ... ");
        }
        this.pagesNodes.each(function(elem){
            elem.insert(_this.createPageLink(pages, false));
        });
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
      this.table.page = parseInt(page);
      this.table.launchSearchExisting();
    },
    gotoPrevPage: function(ev) {
      ev.stop();
      var prevPage = this.table.page - 1;
      if (prevPage > 0) {
        this.table.page--;
        this.table.launchSearchExisting();
      }
    },
    gotoNextPage: function(ev) {
      ev.stop();
      var pages = this.table.totalPages;
      var nextPage = this.table.page + 1;
      if (nextPage <= pages) {
        this.table.page++;
        this.table.launchSearchExisting();
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
      this.pagesNodes.each(function(item) {
          if (!item.up().previous('.controlPagination')) {
            return;
          }
          var page = item.up().previous('.controlPagination').down('.' + removeName);
          page && page.addClassName(addName).removeClassName(removeName);
      });
    }
  });
  return PhenoTips;
}(PhenoTips || {}));
