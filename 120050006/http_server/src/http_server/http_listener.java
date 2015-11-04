package http_server;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class http_listener {
	ServerSocket serverSocket; //this object will listen for new connections
	
	public http_listener() {
		try {
			serverSocket = new ServerSocket(configData.portNumber); 
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private class multithreader implements Runnable {
		Socket clientSocket;
		ServerSemaphore threadCounter;
		
		public multithreader(ServerSemaphore threadCounter) {
			try {
				this.threadCounter = threadCounter;
				threadCounter.take(); //This adds 1 to thread counter
				clientSocket = serverSocket.accept(); //listen for a new connection at an appropriate port
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		public void run() {
			InputStream inP = null;
			responseHandler gen = null;
			try {
			    inP = clientSocket.getInputStream();
			    //All writing will happen in responseHandler. Hence, we pass all writers to it
			    gen = new responseHandler(new PrintWriter(clientSocket.getOutputStream(), true), new BufferedOutputStream(clientSocket.getOutputStream()), clientSocket);
			    http_request res;//an object to store request data
			    int count = 0; //counts number of requests processed
			    do{
			    count++;
			    clientSocket.setSoTimeout(configData.timeout);//Sets timeout on connection
			    res = new http_request(inP);//Read the request
			    clientSocket.setSoTimeout(0);//set timeout to infinity
			    if(count >= configData.maxPackets) res.closeConnection(); //we close at maxPackets
			    gen.handleRequest(res);//Send request to be handled
			    }while(!(res.isClosePacket()) && res.isPersistent()); 
			    //we continue if the connection is not closed and the connection is persistent
			    inP.close(); //close reader
			    gen.stop(); //close writer
			    clientSocket.close(); //close socket
			    threadCounter.release(); //reduce the thread counter
			}catch(SocketTimeoutException e) {
				try {
			    	inP.close();
			    	gen.stop();
					clientSocket.close();
					threadCounter.release();
				} catch (Exception e1) {
					//Do Nothing
				}
			}
			catch(IOException e) {
				try {
					gen.stop();
			    	inP.close();
					clientSocket.close();
					threadCounter.release();
				} catch (Exception e1) {
					//Do Nothing
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	//This class helps in counting the no of active connections
	private class ServerSemaphore {
		private int noOfThreads = 0;
		private int maxNo = 0;
		
		public ServerSemaphore(int maxNo) {
			this.maxNo = maxNo; //set max no of connections
		}
		
		public synchronized void take() throws InterruptedException{ //adds one more connection
			while(this.noOfThreads == maxNo) wait();//wait if at max capacity
			this.noOfThreads++;
			this.notify();
		}
		
		public synchronized void release() throws InterruptedException{ //releases 1 connection
			while(this.noOfThreads == 0) wait();
			this.noOfThreads--;
			this.notify();
		}
	}
	
	private void start() {
		ServerSemaphore threadCounter = new ServerSemaphore(configData.maxNoOfThreads);
		while(true) {
			(new Thread((new multithreader(threadCounter)))).start();//Create and start a new thread.
		}		
	}
 	
	public static void main(String[] args) {
		if(args.length == 2) {
			configData.changeDefault(args[1]);
			configData.changePath(args[0]);
		}
		http_listener obj = new http_listener();//Create a new server
		obj.start();
	}
}
