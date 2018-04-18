var PhenoTips = (function(PhenoTips) {
  var widgets = PhenoTips.widgets = PhenoTips.widgets || {};
  widgets.MatcherPageSizer = Class.create({
    initialize: function(table, domNode, pageSizeBounds, currentPageSize) {
      this.table = table;
      this.currentValue = currentPageSize;
      var bounds = pageSizeBounds || [10, 100, 10];
      this.startValue = bounds[0];
      this.step = bounds[2];
      this.maxValue = bounds[1];
      domNode.insert(this.createPageSizeSelectControl());
    },
    /**
     * Create the page size control using a select node and returns it
     * @return an Element containing the select
     **/
    createPageSizeSelectControl: function() {
      var select = new Element('select', {'class':'pagesizeselect'});
      for (var i=this.startValue; i<=this.maxValue; i += this.step) {
        var attrs = {'value':i, 'text':i};
        if (i == this.currentValue) {
          attrs.selected = true;
        } else {
          var prevStep = i - this.step;
          if (this.currentValue > prevStep && this.currentValue < i) {
            select.appendChild(new Element('option', {'value':this.currentValue, 'text':this.currentValue, selected:true}).update(this.currentValue));
          }
        }
        select.appendChild(new Element('option', attrs).update(i));
      }
      select.observe("change", this.changePageSize.bind(this));
      return select;
    },

    /**
     * Change the page size of the table
     **/
    changePageSize: function(event) {
      event.stop();
      var newLimit =  parseInt($F(Event.element(event)));
      this.table.changePageSize(newLimit);
    }
  });
  return PhenoTips;
}(PhenoTips || {}));
