package info.opensourcejason.services;

import com.palm.luna.LSException;
import com.palm.luna.service.LunaServiceThread;
import com.palm.luna.service.ServiceMessage;
import com.palm.oasis.util.DebugLogger;
import java.io.IOException;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import java.util.logging.Level;
import org.json.JSONException;
import org.json.JSONObject;



public class SocketService extends LunaServiceThread {
 
    /* Synchronizer for messages/buffers */
    Object message_lock = new Object();
    
    /* A lock to prevent blocking operations when 
     * data related to those operations need to be modified.
     */
    Object selectorLock = new Object();
    
    /* Maps IDs to sockets */
    HashMap<Integer, SelectableChannel> sockets = new HashMap<Integer, SelectableChannel>();   
   
    
    Selector selector = Selector.open();
    DebugLogger logger = new DebugLogger("SocketService");
    SocketService ss = this;
    Random RAND = new Random();
    SocketWaitThread swt = new SocketWaitThread();   
    boolean linemode;
    
    /* manage the extra information associated with a SocketChannel */
    /* It is associated with a selector key */
    class SocketData {
        public ServiceMessage message = null;
        public ByteBuffer buffer = null;
        public String application = null;
        boolean subscription = false;      
    }
    
    class SocketWaitThread extends Thread {                
        public void run() {

            while (true) {
                /*
                 * We can't register things if the selector is blocking so the
                 * registration block will grab a selectorLock and then wake the selector
                 * up. This will cause the loop to restart, and then block here until
                 * the register command is done. 
                 */
                synchronized (selectorLock) {
                }

                try {
                    selector.select();
                } catch (IOException e) {
                    logger.severe("Socket Service: IO Exception waiting for select");
                    e.printStackTrace();
                }

                // Get list of selection keys with pending events
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();              
                SelectionKey selKey = null;
                
                logger.severe("IO signal received");
                while (it.hasNext()) {
                    try {
                        // Get the selection key
                        selKey = it.next();

                        // Remove it from the list to indicate that it is being
                        // processed
                        it.remove();

                        if (selKey.isAcceptable()) {  
                            logger.severe("Got connection on server socket with selKey "+selKey);
                            ServerSocketChannel sc = (ServerSocketChannel) selKey.channel();
                            SocketData sd = (SocketData) selKey.attachment();                         
                            logger.severe("SC is: "+sc);
                            logger.severe("SD is: "+sd);
                            logger.severe("Sockets is: "+sockets);
                            logger.severe("SD message is: "+sd.message);
                            SocketChannel client = sc.accept();
                            int id = newId();
                            sockets.put(id, client);
                            
                            JSONObject reply = new JSONObject();
                            reply.put("status", "connection");
                            reply.put("id", id);
                            sd.message.respond(reply.toString());
                            
                        }
                        if (selKey.isReadable()) {
                            logger.severe("Selkey is readable");
                            synchronized (selectorLock) {
                                SocketChannel sc = (SocketChannel) selKey.channel();
                                SocketData sd = (SocketData) selKey.attachment();                         
                                sd.buffer.clear();
                                
                                
                                int count = sc.read(sd.buffer);
                                if (count >= 0) {
                                    sd.buffer.flip();
                                    String data = new String(sd.buffer.array(), 0, count);                                   
                                    JSONObject reply = new JSONObject();
                                    reply.put("data", data);
                                    sd.message.respond(reply.toString());
                                    
                                    
                                    /* If this is a subscribed message, we want to 
                                     * keep the service message handle around so we can
                                     * send more responses. Otherwise, get rid of it.
                                     */
                                    if (!sd.subscription) {
                                        sd.message = null;
                                        selKey.interestOps(0);
                                    }
                                } else {
                                    sd.message.respondError("error", "Socket closed");                                   
                                    selKey.cancel();
                                }
                            }
                        }                    
                    } catch (Exception e) {
                        logger.severe("Main socket loop got an exception" + e);
                        if (selKey != null) {
                            selKey.cancel();
                        }
                    }
                }
            }
        }
    }
 
    private int newId() {
        int id = Math.abs(RAND.nextInt());
        while (sockets.containsKey(id)) {
            id = Math.abs(RAND.nextInt());
        }
        return id;
    }

    
    public SocketService() throws IOException {
        logger.setLevel(Level.ALL);
        logger.info("SocketService thread started");
        swt.start();      
    }

    /**
     * 
     * 
     * @param id the id of an existing socket
     * @param size the new chunk size to use for reads
     * @return  true, or an error condition          
     */
    @LunaServiceThread.PublicMethod
    public void setReadChunkSize(ServiceMessage msg) throws JSONException,
            LSException {
              
            int newsize = 0;
            int id;
            
            /* Get socket from JSON Message */
            if (msg.getJSONPayload().has("id")) {
                id = msg.getJSONPayload().getInt("id");
            } else {
                msg.respondError("error", "Specify an id");
                return;
            }
            
            /* Check socket exists */
            if(!sockets.containsKey(id)) {
                msg.respondError("error", "No socket for specified ID");
                return;
            }
            
            /* Get new size from JSON Message */
            if (msg.getJSONPayload().has("size")) {
                newsize = msg.getJSONPayload().getInt("size");
            } else {
                msg.respondError("error", "Specify a size");
                return;
            }
            
            /* Validate size */
            if (newsize <= 0) {
                msg.respondError("error", "Specify a size greater than 0");
                return;
            }
            
            /* Set new size */
            synchronized(message_lock) {
                ((SocketData)(sockets.get(id).keyFor(selector).attachment())).buffer.limit(newsize);
           
            }
            
            msg.respondTrue();
    }   

    @LunaServiceThread.PublicMethod
    public void close(ServiceMessage msg) throws JSONException, LSException {
        int id;
        if (msg.getJSONPayload().has("id")) {
            id = msg.getJSONPayload().getInt("size");
        } else {
            msg.respondError("error", "Specify an id");
            return;
        }
        
        SelectableChannel sc;
        
        if(sockets.containsKey(id)) {
           sc = sockets.get(id);        
        } else {
            msg.respondError("error", "No socket for specified ID");
            return;
        }
        
        
       logger.severe("Closing socket");
        synchronized (selectorLock) {
            if (sc.isOpen()) {
                try {
                    sc.keyFor(selector).cancel();
                    sc.close();
                    sockets.remove(id);

                    JSONObject reply = new JSONObject();
                    reply.put("status", "closed");
                    reply.put("id", id);
                    msg.respond(reply.toString());

                } catch (IOException e) {
                   logger.severe("Got exception while closing "+e);
                }
            } else {
                logger.severe("Socket already closed");
                msg.respondError("error", "Socket already closed");
            }
        }
    }

    /**
     * Open a new socket
     * @param host The IP address of a host
     * @param port the port
     * @param chunkSize the read chunk size to use
     * 
     * @return id the id to serve as a handle to the socket for future ops
     * @throws IOException 
     *         
     */
    @LunaServiceThread.PublicMethod
    public void open(ServiceMessage msg) throws JSONException, LSException,
            InterruptedException, IOException {

        logger.severe("Doing an open requested by application: "+ msg.getApplicationID());
        
        String host;
        int port;
        int chunkSize=1;

        if (msg.getJSONPayload().has("host")) {
            host = msg.getJSONPayload().getString("host");
        } else {
            host = "127.0.0.1";
        }

        if (msg.getJSONPayload().has("port")) {
            port = msg.getJSONPayload().getInt("port");
        } else {
            port = 23;
        }

        if (msg.getJSONPayload().has("chunkSize")) {
            chunkSize = msg.getJSONPayload().getInt("chunkSize");
        } else {
            chunkSize = 1024*100; //Default 100kB chunk
        }
        if (chunkSize <= 0) {
            chunkSize = 1;
        }
        
        boolean server;
        if (msg.getJSONPayload().has("server")) {
            server = msg.getJSONPayload().getBoolean("server");
        } else {
            server = false;
        }
        
        logger.severe("Creating INetSocketAddress for "+host+":"+port);
        
        InetSocketAddress addr = new InetSocketAddress(host, port);

        
        logger.severe("Created INetSocketAddr");
        int id = newId();
        
        
        SelectableChannel sc;
        if(server) {
            logger.severe("Opening server socket");
            sc = ServerSocketChannel.open();
            ((ServerSocketChannel) sc).socket().bind(addr);
            sockets.put(id, (ServerSocketChannel) sc);
            sc.configureBlocking(false);
        } else {
            logger.severe("Opening client socket");
            sc = SocketChannel.open();
            sockets.put(id, (SocketChannel) sc);
            ((SocketChannel)sc).connect(addr);
            sc.configureBlocking(false);
        }
        
        logger.severe("Created socket");
        
        SocketData sd = new SocketData();
        sd.buffer = ByteBuffer.allocate(chunkSize);
        sd.application = msg.getApplicationID();
        sd.message = msg;
        
        synchronized (selectorLock) {
            // Can't register on a blocking selector... it will also block.
            selector.wakeup();
            SelectionKey skey;
            if(server) {
                skey = sc.register(selector, SelectionKey.OP_ACCEPT);
            } else {
                skey = sc.register(selector, SelectionKey.OP_READ);          
                skey.interestOps(0);
            }
            skey.attach(sd);
        }
        JSONObject reply = new JSONObject();
        reply.put("status", "created");
        reply.put("id", id);
        msg.respond(reply.toString());
        
    }

    

    /*
     * Queues a read in the reader thread Doesn't return anything... it's the
     * thread's job to read the data and return it via the ServiceMessage handle
     * You can set subscribe=true to keep getting data.
     * 
     * @param id the socket to read
     * @param subscribe whether or not to subscribe to the event
     */
    @LunaServiceThread.PublicMethod
    public void read(ServiceMessage msg) throws JSONException, LSException,
            InterruptedException, IOException {
        
        int id;
        if (msg.getJSONPayload().has("id")) {
            id = msg.getJSONPayload().getInt("id");
        } else {
            msg.respondError("error", "Specify an id");
            return;
        }       
        if(!sockets.containsKey(id)) {
            msg.respondError("error", "No socket for specified ID: "+id);
            return;
        }
        
        synchronized(selectorLock) {
            selector.wakeup();
            SocketChannel sc = (SocketChannel) sockets.get(id);
            SelectionKey skey = sc.keyFor(selector);
            SocketData sd = (SocketData) skey.attachment();
            sd.message = msg;
            Boolean isSub = isSubscription(msg);
            if(isSub != null && isSub == true) {
               sd.subscription = true; 
            } else {
                sd.subscription = false;
            }
            skey.interestOps(SelectionKey.OP_READ);
        }
        
       
    }

    @LunaServiceThread.PublicMethod
    public void write(ServiceMessage msg) throws JSONException, LSException,
            InterruptedException, IOException {

        String data;
        int id;
        if (msg.getJSONPayload().has("id")) {
            id = msg.getJSONPayload().getInt("id");
        } else {
            msg.respondError("error", "Specify an id");
            return;
        }
        
        if(!sockets.containsKey(id)) {
            msg.respondError("error", "No socket for specified ID: "+id);
            return;
        }
  
        if (msg.getJSONPayload().has("data")) {
            data = msg.getJSONPayload().getString("data");
        } else {
            data = "";
        }
        
        logger.severe("Doing write with data: "+data);
        
        SocketChannel sc = (SocketChannel)sockets.get(id);
        ByteBuffer buf = ByteBuffer.wrap(data.getBytes());
        try {
            sc.write(buf);
            logger.severe("Wrote");
        } catch (Exception e) {
            msg.respondError("exception", "Exception while writing" + e);
        }
        JSONObject reply = new JSONObject();
        reply.put("status", "wrote " + data);
        logger.severe("sending response");
        msg.respond(reply.toString());
        logger.severe("sent response");

    }

}

