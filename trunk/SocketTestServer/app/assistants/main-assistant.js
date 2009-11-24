function MainAssistant(){
    
    
}

MainAssistant.prototype.setup = function(){
    /* this function is for setup tasks that have to happen when the scene is first created */
    
    /* use Mojo.View.render to render view templates and add them to the scene, if needed. */
    
    /* setup widgets here */
    this.output = this.controller.get('output');
	
   this.s = new Socket();
    this.s.startserver('127.0.0.1', '8932', {onConnect:this.handleConnection.bind(this)}, this.serverActivity.bind(this));
	
	    
    Mojo.Log.info("done setup");    
}

MainAssistant.prototype.serverActivity = function(r) {
	Mojo.Log.info("App maybe started server:"+Object.toJSON(r));
	if (r.status === "created") {
		this.output.appendChild(document.createTextNode("App started server: " + Object.toJSON(r)));
	}
	
}

MainAssistant.prototype.handleConnection = function(s) {
    Mojo.Log.info("Got a connection:"+Object.toJSON(s));
   
    s.write("Yo");
    Mojo.Log.info("done writing");    
}


MainAssistant.prototype.cleanup = function(event){
	Mojo.Log.info("closing socket");
    this.s.close();
}
     
    
    
     
    
    
     
    
    
     
    
    
     
    
    
     
    
    
     
    
    
     
    
    
    