function MainAssistant(){
    
    
}

MainAssistant.prototype.setup = function(){
    this.output = this.controller.get('output');
	
    this.s = new Socket();
    this.s.connect('127.0.0.1', '8932', {}, function(r) {
        this.output.appendChild(document.createTextNode("App started client: "+Object.toJSON(r)));
		this.s.startReading(function(d) {
            Mojo.Log.info("reading callback");
            this.output.appendChild(document.createTextNode("data: "+d));
        }.bind(this));
    }.bind(this));
	
	
	this.controller.setupWidget("input", {}, {});
	this.controller.listen(this.controller.get("input"), Mojo.Event.change, this.handleInput.bind(this));
	   
    Mojo.Log.info("done setup");    
}

MainAssistant.prototype.handleInput = function(event){
	Mojo.Log.info("Writing "+event.target.value+" to socket")
	this.s.write(event.target.value);
}
MainAssistant.prototype.activate = function(event){
    /* put in event handlers here that should only be in effect when this scene is active. For
    
    
     
    
    
     
    
    
     
    
    
     
    
    
     
    
    
     
    
    
     
    
    
     
    
    
     example, key handlers that are observing the document */
    
    
}


MainAssistant.prototype.deactivate = function(event){
    /* remove any event handlers you added in activate and do any other cleanup that should happen before
    
     
    
     
    
     
    
     this scene is popped or another scene is pushed on top */
    
}

MainAssistant.prototype.cleanup = function(event){
    /* this function should do any cleanup needed before the scene is destroyed as 
     a result of being popped off the scene stack */
}
