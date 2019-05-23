use(function(){
  var query = resolver.findResources("select * from [lfs:dataEntry] as n", "JCR-SQL2");

  // Dump our iterator into a list of strings
  var nodeNames = [];
  while (query.hasNext()) {
    var resource = query.next();
    var stringOut = resource.getPath() + "|";

    // Create a comma-delimited list of entries for each property
    var propIterator = resource.properties.keySet().iterator();
    while (propIterator.hasNext()) {
      var key = propIterator.next();
      stringOut += key + ' ' + resource.properties[key]+"\n";
    }
    nodeNames.push(stringOut);
  }

  return {
    results: nodeNames
  };
});