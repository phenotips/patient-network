require.config({
  paths : {
    matchingNotification : "$stringtool.substringBefore($xwiki.getSkinFile('uicomponents/matchingNotification/unnotifiedMatchesTable.js', true), 'unnotifiedMatchesTable.js')",
    dynatable: ["$!services.webjars.url('jquery-dynatable', 'jquery.dynatable.js')"]
  },
  shim : {
    dynatable: {
      deps: ['jquery'],
      exports: 'dynatable'
    }
  }
});