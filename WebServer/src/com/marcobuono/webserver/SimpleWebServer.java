package com.marcobuono.webserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

/**
 * The main() program in this class is designed to read requests from
 * a Web browser and display the requests on standard output.  The
 * program sets up a listener on port 50505.  It can be contacted
 * by a Web browser running on the same machine using a URL of the
 * form  http://localhost:505050/path/to/resource.html  This method
 * does not return any data to the web browser.  It simply reads the
 * request, writes it to standard output, and then closes the connection.
 * The program continues to run, and the server continues to listen
 * for new connections, until the program is terminated (by clicking the
 * red "stop" square in Eclipse or by Control-C on the command line).
 *  
 * @author marcobuono
 *
 */
public class SimpleWebServer {
	
	/**
	 * The server listens on this port.  Note that the port number must
	 * be greater than 1024 and lest than 65535.
	 */
	private final static int LISTENING_PORT = 50505;
	/* ****************************************************************************
	 * *************** Please modify with path of your 'www' folder ***************
	/* ****************************************************************************/
	//private final static String rootDirectory = 	System.getProperty("user.dir");
	private final static String rootDirectory = "/Users/marcobuono/Desktop/CS 1103 - Programming 2/www";
	
	/**
	 * Main program opens a server socket and listens for connection
	 * requests.  It calls the handleConnection() method to respond
	 * to connection requests.  The program runs in an infinite loop,
	 * unless an error occurs.
	 * @param args ignored
	 */
	public static void main(String[] args) {
		ServerSocket serverSocket;
		try {
			serverSocket = new ServerSocket(LISTENING_PORT);
		}
		catch (Exception e) {
			System.out.println("Failed to create listening socket.");
			return;
		}
		System.out.println("Listening on port " + LISTENING_PORT);
		try {
			while (true) {
				Socket connection = serverSocket.accept();
				System.out.println("\nConnection from " 
						+ connection.getRemoteSocketAddress());
				ConnectionThread thread = new ConnectionThread(connection);
				thread.start();
			}
		}
		catch (Exception e) {
			System.out.println("Server socket shut down unexpectedly!");
			System.out.println("Error: " + e);
			System.out.println("Exiting.");
		}
	}

	/**
	 * Handle commuincation with one client connection.  This method reads
	 * lines of text from the client and prints them to standard output.
	 * It continues to read until the client closes the connection or
	 * until an error occurs or until a blank line is read.  In a connection
	 * from a Web browser, the first blank line marks the end of the request.
	 * This method can run indefinitely,  waiting for the client to send a
	 * blank line.
	 * NOTE:  This method does not throw any exceptions.  Exceptions are
	 * caught and handled in the method, so that they will not shut down
	 * the server.
	 * @param connection the connected socket that will be used to
	 *    communicate with the client.
	 */
	private static void handleConnection(Socket connection) {
		try {
			Scanner in = new Scanner(connection.getInputStream());
			OutputStream out = connection.getOutputStream();
			String pathToFile = ""; // the requested file
			File file;
			// First time set to true
			boolean isFirstLine = true;
			while (true) {
				if (! in.hasNextLine())
					break;
				String line = in.nextLine();
				if (isFirstLine) { // Checks if it is the first line to retrieve the tokens
					String[] tokenize = line.split(" ");
					// check if first line contains the three tokens:
					// GET, Path file, and Protocol (HTTP)
					if(! tokenize[0].equalsIgnoreCase("GET")) {
						sendErrorResponse(501, out);
						in.close();
						return;
					} else if (! tokenize[2].equalsIgnoreCase("HTTP/1.1")
							&& ! tokenize[2].equalsIgnoreCase("HTTP/1.0")) {
						sendErrorResponse(400, out);
						in.close();
						return;
					// For the path I used a regular expression retrieved from
					// https://stackoverflow.com/questions/9363145/regex-for-extracting-filename-from-path/46008067
					// First block "(.+\)*" matches directory path.
					// Second block "(.+)" matches file name without extension.
					// Third block "(.+)$" matches extension.
					} else if (tokenize[1].matches("^\\\\(.+\\\\)*(.+)\\.(.+)$")) {
						sendErrorResponse( 400, out );
						in.close();
						return;
					}
					pathToFile = tokenize[1].replace("%20", " ");
					isFirstLine = false; // all ok, sets to false isFirstLine
				}
				if (line.trim().length() == 0)
					break;
				System.out.println("   " + line);
			}
			file = new File(rootDirectory + pathToFile); // pathToFile is the requested file
			int statusCode = checkFile(file);
			if(statusCode != 200) { // something went wrong
				sendErrorResponse(statusCode, out); // Returns the answer based on the received code
				return;
			} else {
				try {
					PrintWriter pw = new PrintWriter(out);
			    	pw.print("HTTP/1.1 200 OK \r\n");
					pw.print("Connection: close \r\n");
					pw.print("Content-Length: " + file.length() + "\r\n");
					pw.print("Content-Type: " + getMimeType(pathToFile) + " \r\n");
					pw.print("\r\n");
					pw.flush();
					sendFile(file, out);
				} catch (IOException e) {
					System.out.println("An error occurred while sending the requested file!");
					System.out.println("Error: " + e);
					System.out.println("Exiting.");
				}
			}
		}
		catch (Exception e) {
			try(OutputStream socketOut = connection.getOutputStream();) {
				sendErrorResponse(500, socketOut); // try to sent an error to the client
			} catch (IOException err) {
				System.out.println("Error while I/O operation: " + err);
			}
			System.out.println("Error while communicating with client: " + e);
		}
		finally {  // make SURE connection is closed before returning!
			try {
				connection.close();
			}
			catch (Exception e) {
			}
			System.out.println("Connection closed.");
		}
	}
	
	/**
	 * It performs some checks on the file passed by the Client 
	 * and returns a code based on the result obtained
	 * 
	 * @param file, the requested file
	 * @return the code of operation
	 */
	private static int checkFile(File file) {
    	if (! file.exists()) {
    		System.out.println("Ops, The specified file does not exist!");
    		return 404;
    	} else if (file.isDirectory()) {
    		System.out.println("Please indicate a file, not a directory!!");
    		return 404;
    	} else if (! file.canRead()) {
    		System.out.println("Ops, You do not have permission to read the file!");
    		return 403;
    	}
    	return 200; //all ok
    }
    
    /**
     * The method checks the file's extension and returns the
     * equivalentMime Type or unknown value if it did not
     * find the extension of that file.
     * 
     * @param fileName, the requested file
     * @return the Mime Type of the File
     */
	private static String getMimeType(String fileName) {
    	int pos = fileName.lastIndexOf('.');
        if (pos < 0)  // no file extension in name
            return "x-application/x-unknown";
        String ext = fileName.substring(pos+1).toLowerCase();
        if (ext.equals("txt")) return "text/plain";
        else if (ext.equals("html")) return "text/html";
        else if (ext.equals("htm")) return "text/html";
        else if (ext.equals("css")) return "text/css";
        else if (ext.equals("js")) return "text/javascript";
        else if (ext.equals("java")) return "text/x-java";
        else if (ext.equals("jpeg")) return "image/jpeg";
        else if (ext.equals("jpg")) return "image/jpeg";
        else if (ext.equals("png")) return "image/png";
        else if (ext.equals("gif")) return "image/gif";
        else if (ext.equals("ico")) return "image/x-icon";
        else if (ext.equals("class")) return "application/java-vm";
        else if (ext.equals("jar")) return "application/java-archive";
        else if (ext.equals("zip")) return "application/zip";
        else if (ext.equals("xml")) return "application/xml";
        else if (ext.equals("xhtml")) return"application/xhtml+xml";
        else return "x-application/x-unknown";
        // Note:  x-application/x-unknown  is something made up;
        // it will probably make the browser offer to save the file.
    }
    
    /**
     * The method reads the file byte by byte and sends it to the Client.
     * 
     * @param file, the requested file
     * @param socketOut, the OutputStream
     * @throws IOException, if cannot read/write
     */
	private static void sendFile(File file, OutputStream socketOut) throws IOException {
    	InputStream in = new BufferedInputStream(new FileInputStream(file));
    	OutputStream out = new BufferedOutputStream(socketOut);
    	while (true) {
    		int x = in.read(); // read one byte from file
    		if (x < 0)
    			break; // end of file reached
    		out.write(x);  // write the byte to the socket
    	}
    	out.flush();
    	in.close();
    }
    
    /**
     * The method returns a response to the client and optionally the requested file.
     * 
     * @param statusCode
     * @param socketOut
     */
	private static void sendErrorResponse(int statusCode, OutputStream socketOut) {
    	String response;
    	String mimeType;
    	String message = "";
    	switch (statusCode) { // prepare the token for each status code
	    	case 400:
				response = "400 Bad Request";
				mimeType = "text/html";
				message = "Bad syntax of request, protocol is not in the format expected.";
	    		break;
	    	case 403:
	    		response = "403 Forbidden";
	    		mimeType = "text/html";
	    		message = "You do not have permission to read the requested  file.";
    			break;
	    	case 404:
	    		response = "404 Not Found";
	    		mimeType = "text/html";
	    		message = "The resource you requested does not exist on this server.";
				break;
	    	case 500:
	    		response = "500 Internal Server Error";
	    		mimeType = "text/html";
	    		message = "Ops, An unexpected error has occurred!";
    			break;
	    	case 501:
	    		response = "501 Not Implemented";
	    		mimeType = "text/html";
	    		message = "The command received is not yet implemented.";
    			break;
    		default:
    			response = "500 Internal Server Error";
	    		mimeType = "text/html";
	    		message = "Ops, An unexpected error has occurred!";
    			break;
    	}
    	PrintWriter pw = new PrintWriter(socketOut);
    	pw.print("HTTP/1.1 " + response + " \r\n");
		pw.print("Connection: close \r\n");
		pw.print("Content-Type: text/html \r\n");
		pw.print("\r\n");
		pw.print("<html><head><title>Error</title></head><body>\r\n"
				+ "<h2>Error: " + response + "</h2>\r\n"
				+ "<p>" + message + "</p>\r\n"
				+ "</body></html>");
		pw.flush();
    }
    
    /**
     * 
     * To make your server into a multi-threaded server, you will need
     * a subclass of Thread. The class needs a run() method to specify
     * the task that the thread will perform. In this case, it should
     * handle one connection request. We can pass the socket for that
     * connection to the constuctor of the class. Here's the class
     * 
     */
	private static class ConnectionThread extends Thread {
    	Socket connection;
        ConnectionThread(Socket connection) {
           this.connection = connection;
        }
        public void run() {
           handleConnection(connection);
        }
     }
}
