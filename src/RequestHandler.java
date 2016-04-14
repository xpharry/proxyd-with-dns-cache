

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.net.InetAddress;

/**
 * RequestHandler processes each client's request through proxy server, 
 * then sends to remote server and write back to client.
 * 
 * @author peng
 *
 */
public class RequestHandler implements Runnable {
	/**
	 * Initialize variables
	 */
	protected DataInputStream clientInputStream; // Client input stream for reading request
//	protected BufferedReader clientInputStream;
	protected OutputStream clientOutputStream; // Client output stream for rendering response
	protected OutputStream remoteOutputStream; // Remote output stream to send in client's request
	protected InputStream remoteInputStream; // Remote input stream to read back response to client
	
	protected Socket clientSocket; // Client socket object
	protected Socket remoteSocket; // Remote socket object 
	
	protected String requestType; // Client request type (Only "GET" or "POST" are handled)
	protected String url; // Client request url (e.g. http://www.google.com) 
	protected String uri; // Client request uri parsed from url (e.g. /index.html) 
	protected String httpVersion; // Client request version (e.g. HTTP/1.1)
	
	protected HashMap<String, String> header; // Data structure to hold all client request handers (e.g. proxy-connection: keep-alive)
	protected DnsCache cache;
	protected HashMap<String, DnsCache> cacheMap;
	
	static double timeout = 30;
	
	static String endOfLine = "\r\n"; // End of line character

 	/** 
 	 * Create a RequestHandler instance with clientSocket object
 	 * 
 	 * @param clientSocket
 	 */
	public RequestHandler(Socket clientSocket) {
		header = new HashMap<String, String>();
		cacheMap = new HashMap<String, DnsCache>();
		this.clientSocket = clientSocket;
	}

	/** 
	 * When instance is created, open client/remote streams then 
	 * proceed with the following 3 tasks:<br>
	 * 
	 * 1) get request from client<br>
	 * 2) forward request to remote host<br>
	 * 3) read response from remote back to client<br>
	 * 
	 * Close client/remote streams when finished.<br>
	 * 
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		try {
			clientInputStream = new DataInputStream(clientSocket.getInputStream());
//			clientInputStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			clientOutputStream = clientSocket.getOutputStream();

			// step 1: get request from client
			clientToProxy();
			
			// step 2: resolve dns
			dnsResolve();
			
			// step 3: forward request to remote host
			proxyToRemote();

			// step 4: read response from remote back to client
			remoteToClient();

			System.out.println();

			if(remoteOutputStream != null) remoteOutputStream.close();
			if(remoteInputStream != null) remoteInputStream.close();
			if(remoteSocket != null) remoteSocket.close();

			if(clientOutputStream != null) clientOutputStream.close();
			if(clientInputStream != null) clientInputStream.close();
			if(clientSocket != null) clientSocket.close();
		} catch (IOException e) { }
	}

	/**
	 * Receive and pre-process client's request headers before redirecting to remote server
	 * 
	 */
	@SuppressWarnings("deprecation")
	private void clientToProxy() {
		String line, key, value;
		StringTokenizer tokens;

		try {
			// HTTP Command
			if(( line = clientInputStream.readLine()) != null) {
				System.out.println("******* line: " + line);
				tokens = new StringTokenizer(line);
				requestType = tokens.nextToken();
				url = tokens.nextToken();
				httpVersion = tokens.nextToken();
			}

			// Header Info
			while((line = clientInputStream.readLine()) != null) {
				System.out.println("******* line: " + line);
				// check for empty line
				if(line.trim().length() == 0) break;

				// tokenize every header as key and value pair
				tokens = new StringTokenizer(line);
				key = tokens.nextToken(":");
				value = line.replaceAll(key, "").replace(": ", "");
				header.put(key.toLowerCase(), value);
				System.out.println("======= key: " + key);
				System.out.println("======= value: " + value);
			}

			stripUnwantedHeaders();
			getUri();
		} 
		catch (UnknownHostException e) { return; } 
		catch (SocketException e){ return; } 
		catch (IOException e) { return;} 
	}

	
	/**
	 * 2. dns resolve
	 * 
	 */
	private void dnsResolve() {
		try {
			String hostname = header.get("host");
			
			if(cacheMap.get(hostname) != null) {
				
				cache = cacheMap.get(hostname);
				
			} else {

				System.out.println("******** resolving " + hostname + " start!");
				InetAddress inetAddress = InetAddress.getLocalHost();
				System.out.println(inetAddress.getHostAddress()); 
				
				inetAddress = InetAddress.getByName(hostname);
				System.out.println(inetAddress.getHostAddress()); 
				
				InetAddress[] inetAddressArray = InetAddress.getAllByName(hostname);
				for (int i = 0; i < inetAddressArray.length; i++) {
					System.out.println(inetAddressArray[i].getHostAddress()); 
				}
				System.out.println("******** resolving " + hostname + " ends!");
				
				String[] ips = new String[1 + inetAddressArray.length];
				ips[0] = inetAddress.getHostAddress();
				for (int i = 0; i < inetAddressArray.length; i++) {
					ips[i+1] = inetAddressArray[i].getHostAddress();
				}
				
		        cache = new DnsCache(hostname, ips, new Date());
			}

			if(cacheMap.get(hostname) != null) {
				if( (new Date()).getTime()/1000 - cacheMap.get(hostname).getExpiration().getTime()/1000 >= timeout) {
					cacheMap.remove(hostname);
				}
			} else {
				cacheMap.put(header.get("host"), cache);
			}
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
	}

	
	/**
	 * 3. Sending pre-processed client request to remote server
	 * 
	 */
	private void proxyToRemote() {
		try{
			if(header.get("host") == null) return;
			if(!requestType.startsWith("GET") && !requestType.startsWith("POST")) 
				return;

			InetAddress IP = InetAddress.getByName(cache.getIp());
			remoteSocket = new Socket(IP, 80);
			remoteOutputStream = remoteSocket.getOutputStream();

			// make sure streams are still open
			checkRemoteStreams();
			checkClientStreams();

			// make request from client to remote server
			String request = requestType + " " + uri + " HTTP/1.0";
			remoteOutputStream.write(request.getBytes());
			remoteOutputStream.write(endOfLine.getBytes());
			System.out.println(request);

			// send hostname
			String command = "host: "+ header.get("host");
			remoteOutputStream.write(command.getBytes());
			remoteOutputStream.write(endOfLine.getBytes());
			System.out.println(command);

			// send rest of the headers
			for( String key : header.keySet()) {
				if(!key.equals("host")){
					command = key + ": "+ header.get(key);
					remoteOutputStream.write(command.getBytes());
					remoteOutputStream.write(endOfLine.getBytes());
					System.out.println(command);
				}
			}

			remoteOutputStream.write(endOfLine.getBytes());
			remoteOutputStream.flush();

			// send client request data if its a POST request
			if(requestType.startsWith("POST")) {

				int contentLength = Integer.parseInt(header.get("content-length"));
				for (int i = 0; i < contentLength; i++)
				{
					remoteOutputStream.write(clientInputStream.read());
				}
			}

			// complete remote server request
			remoteOutputStream.write(endOfLine.getBytes());
			remoteOutputStream.flush();
		}
		catch (UnknownHostException e) { return; } 
		catch (SocketException e){ return; } 
		catch (IOException e) { return;} 
	}

	/**
	 * 4. Sending buffered remote server response back to client with minor header processing
	 * 
	 */
	@SuppressWarnings("deprecation")
	private void remoteToClient() {

		try {

			// If socket is closed, return
			if(remoteSocket == null) return;

			String line;
			DataInputStream remoteOutHeader = new DataInputStream(remoteSocket.getInputStream());
//			BufferedReader remoteOutHeader
//	          = new BufferedReader(new InputStreamReader(remoteSocket.getInputStream()));
			
			// get remote response header
			while((line = remoteOutHeader.readLine()) != null) {

				// check for end of header blank line
				if(line.trim().length() == 0) break;

				// check for proxy-connection: keep-alive
				if(line.toLowerCase().startsWith("proxy")) continue;
				if(line.contains("keep-alive")) continue;

				// write remote response to client
				System.out.println(line);
				clientOutputStream.write(line.getBytes());
				clientOutputStream.write(endOfLine.getBytes());
			}

			// complete remote header response
			clientOutputStream.write(endOfLine.getBytes());
			clientOutputStream.flush();

			// get remote response body
			remoteInputStream = remoteSocket.getInputStream();
			byte[] buffer = new byte[1024];

			// buffer remote response then write it back to client
			for(int i; (i = remoteInputStream.read(buffer)) != -1;) 
			{
				clientOutputStream.write(buffer, 0, i);
				clientOutputStream.flush();
			}
		} 
		catch (UnknownHostException e) { return; } 
		catch (SocketException e){ return; } 
		catch (IOException e) { return;} 
	}

	/**
	 * Helper function to strip out unwanted request header from client
	 * 
	 */
	private void stripUnwantedHeaders() {
		if(header.containsKey("user-agent")) header.remove("user-agent");
		if(header.containsKey("referer")) header.remove("referer");
		if(header.containsKey("proxy-connection")) header.remove("proxy-connection");
		if(header.containsKey("connection") && header.get("connection").equalsIgnoreCase("keep-alive")) {
			header.remove("connection");
		}
	}

	/**
	 * Helper function to check for client input and output stream, reconnect if closed
	 * 
	 */
	private void checkClientStreams() {
		try {
			if(clientSocket.isOutputShutdown())	clientOutputStream = clientSocket.getOutputStream();
			if(clientSocket.isInputShutdown())	clientInputStream = new DataInputStream(clientSocket.getInputStream());
//			if(clientSocket.isInputShutdown())	clientInputStream 
//				= new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		}
		catch (UnknownHostException e) { return; } 
		catch (SocketException e){ return; } 
		catch (IOException e) { return;} 
	}

	/**
	 * Helper function to check for remote input and output stream, reconnect if closed
	 * 
	 */
	private void checkRemoteStreams() {
		try {
			if(remoteSocket.isOutputShutdown())	remoteOutputStream = remoteSocket.getOutputStream();
			if(remoteSocket.isInputShutdown())	remoteInputStream = new DataInputStream(remoteSocket.getInputStream());
		} 
		catch (UnknownHostException e) { return; } 
		catch (SocketException e){ return; } 
		catch (IOException e) { return;} 
	}

	/**
	 * Helper function to parse URI from full URL
	 * 
	 * replace the full absolute url with the relative one
	 * 
	 */
	private void getUri() {
		if(header.containsKey("host")) 
		{
			int temp = url.indexOf(header.get("host"));
			temp += header.get("host").length();

			if(temp < 0) { 
				// prevent index out of bound, use entire url instead
				uri = url;
			} else {
				// get uri from part of the url
				uri = url.substring(temp);
				System.out.println("******* url = " + url);
				System.out.println("******* uri = " + uri);
			}
		}
	}

	private void saveCache(String hostname, String[] ips) {
        Date expiration = new Date();
        cache = new DnsCache(hostname, ips, expiration);
	}
}

