package http_server;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

class http_request {
	private HashMap<String,String> info;//Stores request info
	private byte[] DATA; //Stores Data with request
	
	http_request(InputStream inP) throws IOException {
		info = new HashMap<String, String>();
		this.init(inP);
	}
	
	//If we want to change request data
	void setData(byte[] DATA) {
		this.DATA = DATA;
	}
	
	//get some request information
	String getItem(String key) {
		return info.get(key);
	}
	
	//set some request information
	void setItem(String key, String val) {
		info.put(key, val);
	}
	
	//get request DATA
	byte[] getData() {
		return DATA;
	}
	
	//Tells us if it is a close packet
	boolean isClosePacket() {
		return info.containsKey("Connection") && ((info.get("Connection")).toLowerCase()).equals("close");
	}
	
	//change packet to close connection
	void closeConnection() {
		info.put("Connection", "close");
		return;
	}
	
	//checks if persistent
	boolean isPersistent() {
		if(info.containsKey("Connection") && ((info.get("Connection")).toLowerCase()).equals("keep-alive")) {
			return true;
		}
		return (info.get("VERSION")).equals("HTTP/1.1");
	}
	
	//readline from inputStream char by char
	private String readline(InputStream inp) throws IOException {
		int curr;
		StringBuilder bdr = new StringBuilder();
		do{
			curr = inp.read();
			if(curr == -1) {
				throw new IOException();
			}
			if(((char) curr) == '\n') break;
			bdr.append(((char) curr));
		}while(true);
		return bdr.toString();
	}
	
	//initializes the request by reading from the stream
	private void init(InputStream inP) throws IOException {
		String curr;
		curr = readline(inP);
		String header[] = (curr.replaceAll("\r", "")).trim().split(" ");
		//Split first line into method, path and version
	    info.put("Method", header[0]);
	    info.put("urlPath", header[1]);
	    info.put("VERSION", header[2]);
	    //get other header info
	    while((!(curr.equals(""))) && curr != null) {
	    	int i = curr.indexOf(":");
	    	if(i != -1) info.put(curr.substring(0,i),(curr.substring(i+1)).trim());		    	
	    	curr = ((readline(inP)).replaceAll("\r", "")).trim();
	    }
	    //read data with packet if any
	    if(info.containsKey("Content-Length")) {
	    	int size = Integer.parseInt(info.get("Content-Length"));
	    	DATA = new byte[size];
	    	int off = 0;
	    	while(off < size) {
	    		int amtRead = inP.read(DATA, off, (size - off));
	    		if(amtRead == -1) throw new IOException();
	    		off += amtRead;
	    	}
	    }
	    else DATA = null;
	    return;
	}
}
