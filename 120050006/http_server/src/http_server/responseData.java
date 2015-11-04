package http_server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

class responseData {//This class holds the response data and some meta info. Many functions to put the response in are present
	private boolean ex;
	private StringBuilder header;//header info put in here
	private ByteArrayOutputStream buffer;//other data put in here
	private String url;//url put in here
	private String dataPath;//actual path in here
	
	public responseData(http_request request) {
		ex = false;
		header = new StringBuilder();
		buffer = new ByteArrayOutputStream();
		url = request.getItem("urlPath");
		dataPath = url;
	}
	
	void modBuffer(byte[] newLoad) throws IOException {
		buffer = new ByteArrayOutputStream();
		buffer.write(newLoad);
	}//changes response data
	
	boolean checkEx() {
		return ex;
	}
	
	void modURL(String url) {
		this.url = url;
	}//if we need to change the url
	
	void initPath(String path) {
		dataPath = path;
	}
	
	String getURL(String url) {
		return url;
	}
	
	int getContentSize() {
		return buffer.size();
	}
	
	private void addConnectionData(http_request request) {
		if(request.isClosePacket()) {
			header.append("\r\nConnection: Close");
		}
		else if(request.isPersistent()) {
			header.append("\r\nKeep-Alive: timeout=");
			header.append((configData.timeout)/1000);
			header.append(",max=");
		    header.append(configData.maxPackets);
		    header.append("\r\nConnection: Keep-Alive");
		}
	}//Meta data about the connection is added using this function depending on the request
	
	responseData genHEADER(http_request request, long len, String extraHeaders) {
		header.append((request.getItem("VERSION")));
		header.append(" 200 ");
		header.append("OK\r\nContent-Type: ");
		header.append(this.findContentType());
		header.append("\r\n");
		header.append("Content-Length: ");
		addToHead(Long.toString(len));
		addConnectionData(request);
		header.append("\r\n");
		addToHead(extraHeaders);
		header.append("\r\n");
		return this;
	}//this function generates the response header
	
	String compress(http_request request) throws IOException {//this function compresses the data
		String temp = request.getItem("Accept-Encoding");
		if(temp == null) return null;//We check if an encoding is accepted
		String opts[] = temp.split(",");
		int i;
		for(i = 0; i < opts.length; i++) if(((opts[i]).toLowerCase()).equals("gzip")) break;//We break if gzip works
		if(i == opts.length) {
			for(i = 0; i < opts.length; i++) if(((opts[i]).toLowerCase()).equals("deflate")) break;//We break if deflate works
			if(i == opts.length) return null;//We return if bot don't
			//The usage of DeflaterOutputStream was read from the internet
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			DeflaterOutputStream df = new DeflaterOutputStream(buf);
			df.write(buffer.toByteArray(),0,buffer.size());
			df.finish();
			df.close();
			return "Content-Encoding: deflate\r\n";
		}
		//The usage of GZIPOutputStream was read from the internet
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		GZIPOutputStream gzos = new GZIPOutputStream(buf);
		gzos.write(buffer.toByteArray(),0,buffer.size());
		gzos.finish();
		gzos.close();
		buffer = buf;
		return "Content-Encoding: gzip\r\n";
	}
	
	responseData flagEx(int code, http_request request) {//this function changes the response if an error is flagged to an appropriate exception
		ex = true;
		header = new StringBuilder();
		StringBuilder payload = new StringBuilder();
		buffer = new ByteArrayOutputStream();
		if(code == 301) {
			header.append(request.getItem("VERSION"));
			header.append(" 301 ");
			header.append("Moved Permanently\r\n");
			header.append("Location: http://");
			header.append(request.getItem("Host"));
			header.append(url);
			addConnectionData(request);
			payload.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n<html>\n<head>\n<title>Moved Permanently</title>\n</head>\n<body>\n<h1>Moved Permanently</h1>\n<p>The page you requested has been moved to ");
			payload.append(url);
			payload.append(". Please use this URL in the future.</p>\n</body>\n</html>");
		}
		else if(code == 404) {
			header.append(request.getItem("VERSION"));
			header.append(" 404 ");
			header.append("Not Found");
			addConnectionData(request);
			payload.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n<html><head>\n<title>404 Not Found</title>\n</head><body>\n<h1>Not Found</h1>\n<p>The requested location ");
			payload.append(url);
			payload.append(" is absent.</p>\n</body></html>\n");
		}
		else if(code == 403) {
			header.append(request.getItem("VERSION"));
			header.append(" 403 ");
			header.append("FORBIDDEN");
			addConnectionData(request);
			payload.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n<html><head>\n<title>403 Forbidden</title>\n</head><body>\n<h1>Not Found</h1>\n<p>The requested action is not allowed.</p>\n</body></html>\n");
		}
		else if(code == 501) {
			header.append(request.getItem("VERSION"));
			header.append(" 501 ");
			header.append("Not Implemented");
			addConnectionData(request);
			payload.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n<html>\n<title>\n500 Internal Server Error\n</title>\n<body>\n<h1>Server Error</h1>\n<p>\nThe required method has not been implemented in the server.\n</p>\n</body>\n</html>\n");
		}
		else {
			header.append(request.getItem("VERSION"));
			header.append(" 500 ");
			header.append("Server Error");
			addConnectionData(request);
			payload.append("<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n<html>\n<title>\n500 Internal Server Error\n</title>\n<body>\n<h1>Server Error</h1>\n<p>\nException encountered\n</p>\n</body>\n</html>\n");
		}
		header.append("\r\nContent-Length: ");
		header.append(payload.length());
		header.append("\r\n\r\n");
		header.append(payload);
		return this;
	}
	
	private boolean addToHead(String aux) {
		if(aux.equals("") || aux == null) return false;
		header.append(aux);
		return true;
	}//if we need to add to the head safely (avoid nulls)
	
	boolean addToBuffer(int nRead, byte[] aux) {
		if(nRead == 0) return false;
		buffer.write(aux, 0, nRead);
		return true;
	}//add data to the buffer
	
	void streamToBuffer(InputStream bis, int size) throws IOException{
		int count;
		byte[] bytes = new byte[size];
		while ((count = bis.read(bytes)) > 0) {
			this.addToBuffer(count, bytes);
		}
		bis.close();
	}//add data to buffer from stream
	
	String dumpHead() {
		return header.toString();
	}//return header info
	
	byte[] dumpBuffer() {
		return buffer.toByteArray();
	}//dump data 
	
	String getUrl() {
		return url;
	}
	
	private String findContentType() {//this function returns the content type
		if(dataPath.endsWith(".txt")) {
			return "text";
		}
		if(dataPath.endsWith(".html")) {
			return "text/html";
		}
		if(dataPath.endsWith(".css")) {
			return "text/css";
		}
		if(dataPath.endsWith(".jpg")) {
			return "img/jpg";
		}
		if(dataPath.endsWith(".png")) {
			return "img/png";
		}
		return "binary";
	}
}