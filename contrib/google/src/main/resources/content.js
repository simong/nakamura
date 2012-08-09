var page = require('webpage').create();

if (phantom.args.length != 1) {
    console.log('Usage: run.js URL');
    phantom.exit();
}
else {
    // Make a request to the page.
    var url = phantom.args[0];
    page.open(url, function (status) {
    	if (status === "success") {
	        // Retrieves all the html in the page and write it to standard out.
	        var check = function() {
	            var content = page.evaluate(function () {
	                return $("html").html();
	            });
	            console.log("<html>" + content + "</html>");
	
	            // No need to keep running in interactive mode.
	            phantom.exit();
	        };
	
	        // Give it some time
	        window.setTimeout(check, 5000);
	    }
	    else {
	    	// For some reason, we couldn't open the page.
	    	// Exit with a status code 1, so the caller knows something went wrong.
	    	phantom.exit(1);
	    }
    });
}