/**
 * 
 */


import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * @author Peng
 *
 */
public class proxyd {

	protected ServerSocket server; // Proxy server instance
	
	/**
	 * Executors.newCachedThreadPool() launches new thread as needed and usage idle threads
	 * idle threads will die after being unused for 60 seconds
	 */
	protected ExecutorService executor;
	
	protected static int LISTEN_PORT = 5500+15; // Default HTTP request port

	/**
	 * Create new ProxyServer instance and listen to a port
	 * 
	 * @param port	ProxyServer listening port
	 */
	public proxyd (int port) {	
		executor = Executors.newCachedThreadPool();
		try { server = new ServerSocket(port); }
		catch (IOException e) {	}
	}

	/**
	 * Create new socket and request handler object on each request
	 * 
	 */
	public void accept() {
		while (true) {
			try { executor.execute(new RequestHandler(server.accept())); }
			catch (IOException e) {	}			
		}
	}
	
	/**
	 * main function
	 * 
	 */
	public static void main(String[] args) {
		int port = LISTEN_PORT;
		if(args.length == 2) {
			port = Integer.parseInt(args[1]);
		}
		System.out.println("ProxyServer is listening to port "+port);
		proxyd proxy = new proxyd(port);
		proxy.accept();
	}

}
