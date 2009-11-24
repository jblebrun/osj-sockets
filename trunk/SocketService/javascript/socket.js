var Socket = function() {
	Mojo.Log.info("Creating new socket");
	this.socketURI = "palm://info.opensourcejason.services.socket/";	
	this.id = null;
	this.state = "unconnected";
	this.clients = [];
	this.params = {};
	Mojo.Log.info("Controller for this socket:");
	Mojo.Log.info(Object.toJSON(this.controller));
}

Socket.prototype.close = function() {
	new Mojo.Service.Request(this.socketURI, {
		method: "close",
		parameters: {
			id: this.id
		},
		onSuccess: function() {
			Mojo.Log.info("Closed socket "+this.id);
		},
		onFailure: function() {
		  Mojo.Log.info("Didn't close socket");
		 }
		
	});
}
Socket.prototype.startserver = function(host, port, params, callback) {
	  var maxConnections = params.maxConnections || 1;
      new Mojo.Service.Request(this.socketURI, {
        method: "open",
        parameters: {
            host: host,
            port: port, 
            server: true,
            maxConnections: maxConnections
        },
        onSuccess: function(r) {
			Mojo.Log.info("startserver: "+Object.toJSON(r));
			if(r.status==="created") {
			     Mojo.Log.info("startserver success");
				 callback(r);
			     this.state = "listening";
                 this.id = r.id;
			}
			if(r.status==="connection") {
                if(typeof params.onConnect === "function") {
					s = new Socket();
					s.state = "connected";
					s.id = r.id;
					this.clients.push(s);
                    params.onConnect(s);
                }
		     }
        }.bind(this),
        onFailure:  function(r) {
			Mojo.Log.info("startserver failure: "+Object.toJSON(r));
            this.state = "error";
            if(typeof callback === "function") {
                callback(r);
            }
			
        }
    }); 
	Mojo.Log.info("Submitted startserver request");
};

Socket.prototype.connect = function(host, port, params, callback) {
	
	var chunkSize = params.chunkSize || 1024*100;
	var maxConnections = params.maxConnections || null;
	
	new Mojo.Service.Request(this.socketURI, {
		method: "open",
		parameters: {
			host: host,
			port: port, 
			server: false,
			chunkSize: chunkSize, 
			maxConnections: maxConnections
		},
		onSuccess: function(r) {
			this.state = "connected";
			this.id = r.id;
			if(typeof callback === "function") {
				callback(r);
			}
		}.bind(this),
		onFailure:  function(r) {
			this.state = "error";
			if(typeof callback === "function") {
                callback(r);
            }
		}.bind(this)
	});	
};

Socket.prototype.startReading = function(handler) {
    if(typeof handler !== "function") {
		Mojo.Log.info("read handler not a function ");
		throw "Invalid socket read handler";
	}
	
	if(this.state !== "connected") {
		Mojo.Log.info("socket not connected ");

		throw "Socket not connected";
	}
	
	
	new Mojo.Service.Request(this.socketURI, {
		method: "read",
		parameters: {
			id: this.id,
			subscribe: true
		},
		onSuccess: function(r) {
			Mojo.Log.info("read success");
			if(r.data !== undefined) {
			    handler(r.data);
			} else {
				this.stopReading();
			}
			
			
		}.bind(this),
		onFailure: function(r) {
			Mojo.Log.info("failed to start read handler");
		}.bind(this)
	});
	
}

Socket.prototype.stopReading = function() {
    new Mojo.Server.Request(this.socketURI, {
        method: "read",
        parameters: {
            id: this.id,
            subscribe: false
        },
        onSuccess: function(r) {
            if(r.data !== undefined) {
                
            }
            
        },
        onFailure: function(r) {
            
        }
    });
}


Socket.prototype.write = function(data, callback) {
    if(this.id == null || this.state !== "connected") {
		throw "Socket not connected";		
	}
	
	new Mojo.Service.Request(this.socketURI, {
        method: "write",
        parameters: {
            id: this.id,
            data: data
        },
        onSuccess: function(r) {
			Mojo.Log.info("wrote "+Object.toJSON(r));
            if (typeof callback === "function") {
				callback(r);
			}
        },
        onFailure: function(r) {
			Mojo.Log.info("failed wrote "+Object.toJSON(r));
            
        }
    });
	
	
}
