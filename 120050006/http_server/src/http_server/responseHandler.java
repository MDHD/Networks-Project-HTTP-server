package http_server;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class responseHandler {
	
    private PrintWriter outP; //Writes text data
    private BufferedOutputStream oos; //Writes binary data
    private Socket clientSocket; //used to get client meta information
    private BlockingQueue<http_request> requestList; //Queue requests to be processed
    private BlockingQueue<responseData> responseList; //Queue responses to be written
    private Thread dispatcher; //handles writing responses
    private Thread processor; //handles request processing
    
    private class responseProcessor implements Runnable {
		@Override
		public void run() {
			try {
				while(true) {
					http_request request = requestList.take(); //take blocks the queue till we get a new object
					String temp = request.getItem("Method"); //We process the request according to the method
					if(temp.equals("HEAD")) responseList.put(HEAD(request));
					else if(temp.equals("GET")) responseList.put(GET(request));
					else responseList.put((new responseData(request)).flagEx(501, request));
				}
			} catch(InterruptedException e) {
				//Do nothing
			}
		}
    }
    
    private class responseWriter implements Runnable {
		@Override
		public void run() {
			try {
				while(true) {
					responseData rd = responseList.take();
					dispatchResponse(rd);//We write the response
				}
			} catch(InterruptedException e) {
				return;//Do nothing
			}
		}
    }
    
    public responseHandler(PrintWriter outP, BufferedOutputStream oos, Socket clientSocket) {
    	this.outP = outP;
		this.oos = oos;
		this.clientSocket = clientSocket;
		responseList = (BlockingQueue<responseData>) new LinkedBlockingQueue<responseData>();
		requestList = (BlockingQueue<http_request>) new LinkedBlockingQueue<http_request>();
		processor = new Thread(new responseProcessor());
		dispatcher = new Thread(new responseWriter());
		processor.start();
		dispatcher.start();
    }//initializes the class
    
    void stop() throws IOException {//stops the threads and closes the writers
    	dispatcher.interrupt();
    	processor.interrupt();
    	outP.close();
    	oos.close();
    }
    
    private boolean dispatchResponse(responseData rd) { //writes the response
		outP.print(rd.dumpHead());
		outP.flush();
		if(!(rd.checkEx())) {
			try {
				oos.write(rd.dumpBuffer());
				oos.flush();
			} catch(IOException e) {
				return false;
			}
		}
		return true;
	}
    
    private void blockingProcess(responseData rd) {//used if we want to block reading requests till pipes are empty
    	while(requestList.peek() != null); 
    	while(responseList.peek() != null);
    	dispatchResponse(rd);
    }
    
    //this function handles individual request
    void handleRequest(http_request request) throws InterruptedException {
    	String temp = request.getItem("Method");
		if(temp.equals("POST")) { //as post is not idempotent we don't pipeline it 
			blockingProcess(POST(request));
			return;
		}
		if(request.isClosePacket()) { //if closing packet no need to pipeline we process it when the queue becomes empty
			if(temp.equals("GET")) blockingProcess(GET(request));
			else if(temp.equals("HEAD")) blockingProcess(HEAD(request));
			else blockingProcess((new responseData(request)).flagEx(501, request));
		}
		else requestList.put(request); //by default we pipeline it
		return;
    }
    
	private File getFileObj(responseData rd, http_request request) { //using request we get a file ptr for the object requested
		String tempUrl = rd.getUrl();
		if(!(tempUrl.startsWith("/"))) { //check if it start correctly
			rd.flagEx(404, request);
			return null;
		}
		if(tempUrl.startsWith("/~")) { //check if for user
			int slash2 = tempUrl.indexOf('/', 1);
			if(!(slash2 >= 0)) {
				if((new File(configData.Path() + tempUrl.substring(2))).isDirectory()) {
					rd.modURL(rd.getUrl() + "/");
					rd.flagEx(301, request);
				}//return a path with / at end if the client didn't add it
				else rd.flagEx(404, request);
				return null;
			}
			tempUrl =  configData.Path()  + tempUrl.substring(2,slash2) + "/public_html" + tempUrl.substring(slash2);
		}
		else tempUrl = configData.Path() + configData.Default() + "/public_html" + tempUrl;
		if(tempUrl.endsWith("/")) tempUrl = tempUrl + "index.html";
		rd.initPath(tempUrl);//we store the complete path in the response
		File fr = new File(tempUrl); //get file ptr
		if(fr.isDirectory()) {
			rd.modURL(rd.getUrl() + "/");
			rd.flagEx(301, request);
			return null;
		}//if directory we ask to add /
		if(!(fr.exists())) {
			rd.flagEx(404, request);
			return null;
		}//if non existent send 404
		return fr;
	}
	
	private responseData HEAD(http_request request) { //generates HEAD response
		responseData rd = new responseData(request);//create response object
		File fr = getFileObj(rd, request);
		if(fr == null) {
			return rd;
		}
		if(!(fr.canRead())) {
			rd.flagEx(403 , request);
			return rd;
		}
		return rd.genHEADER(request, fr.length(), "");//we generate header and return
	}
	
	private responseData GET(http_request request) {//generates GET response
		InputStream is;
		long BytesRead = 0;
		String temp = request.getItem("urlPath");
		int indT = temp.indexOf('?');
		if(indT != -1) {
			request.setItem("urlPath", temp.substring(0, indT));
			request.setData((temp.substring(indT)).getBytes());
			return POST(request);
		}//if ? in path then we send it to post as this GET is meant to be run with a script
		
		responseData rd = new responseData(request);
		File fr = getFileObj(rd, request);
		if(fr == null) {
			return rd;
		}
		if(!(fr.canRead())) {
			rd.flagEx(403 , request);
			return rd;
		}
		BytesRead = fr.length();
		try {
			is = new FileInputStream(fr);
		} 
		catch(FileNotFoundException e) {
			rd.flagEx(404, request);
			return rd;
		}//create a stream to read the file

		try{
			rd.streamToBuffer(is, (int) BytesRead);//add file to response
			String CompressionType = rd.compress(request);//compress file
			rd.genHEADER(request, rd.getContentSize(), CompressionType);//generate header
			return rd;
		}
		catch(IOException e) {
			rd.flagEx(500, request);
			return rd;
		}
	}
	
	private void genPostHead(http_request request, responseData rd) throws IOException { //post head generation is a 2 step process
		boolean lineNonZero = false;
		byte out[] = rd.dumpBuffer();
		int i = 0;
		int x = 1;//the output of the script will have some headers which we extract first
		StringBuilder bdr = new StringBuilder();
		while(i < out.length - 1) {
			char temp = ((char)out[i]);
			if(temp == '\n') {
				if(lineNonZero) lineNonZero = false;
				else break;
			}
			else if(temp != '\r') lineNonZero = true;
			else if(!lineNonZero){
				if(((char) out[i + 1]) == '\n') {
					x++;
					break;
				}
			}
			bdr.append(temp);
			i++;
		}//The logic here is similar (or the same as when we read the actual request)
		//We have written it here in a single loop ensuring compatibility with both \n and \r\n
		//then we truncate the other data
		byte truncLoad[] = new byte[out.length - i - x];
		for(int j = 0; j < truncLoad.length; j++) truncLoad[j] = out[i + x + j];
		rd.modBuffer(truncLoad);//we change the response data
		bdr.append(rd.compress(request));//we compress the data
		rd.genHEADER(request, rd.getContentSize(), bdr.toString()); //we generate the header
		return;
	}
	
	private responseData POST(http_request request) {//generates POST response
		responseData rd = new responseData(request);
		File fr = getFileObj(rd, request);
		if(fr == null) {
			return rd;
		}
		if(!(fr.canExecute())) {
			rd.flagEx(403 , request);
			return rd;
		}
		//processBuilder is used to set the environment variables as required by post
		ProcessBuilder prB = new ProcessBuilder(fr.getAbsolutePath());
		Map<String, String> envCon = prB.environment();
		
		//here we set the post environment variables
		//The list is obtained from the net http://www.ietf.org/rfc/rfc3875
		//For the test cases we have considered these kind and presence of these variables is not essential
		//Configuration information
		envCon.put("GATEWAY_INTERFACE", configData.GATEWAY_INTERFACE);
		envCon.put("SERVER_NAME", configData.SERVER_NAME);
		envCon.put("SERVER_SOFTWARE", configData.SERVER_SOFTWARE);
		//information about the request
		envCon.put("SERVER_PROTOCOL", request.getItem("VERSION"));
		envCon.put("SERVER_PORT", Integer.toString(configData.portNumber));
		envCon.put("REQUEST_METHOD", request.getItem("Method"));
		envCon.put("SCRIPT_FILENAME", fr.getAbsolutePath());
		//Only added if present
		if (request.getItem("User-Agent") != null) envCon.put("HTTP_USER_AGENT", request.getItem("User-Agent"));
		if (request.getItem("Referer") != null) envCon.put("HTTP_REFERER", request.getItem("Referer"));
		//Client info obtained from the client socket
		envCon.put("REMOTE_ADDR", (clientSocket.getRemoteSocketAddress()).toString());
		envCon.put("REMOTE_HOST", (clientSocket.getRemoteSocketAddress()).toString());
		envCon.put("REMOTE_PORT", Integer.toString(clientSocket.getPort()));
		//The section below is loosely based on http://stackoverflow.com/questions/3643939/java-process-with-input-output-stream
		Process pr = null;
		try {
			pr = prB.start();
			//we run the process
			OutputStream os = pr.getOutputStream();//This stream writes into the process's input
			if (request.getData() != null) os.write(request.getData());//we give the data we have as input to the POST request
			os.flush();
			os.close();
			rd.streamToBuffer(pr.getInputStream(), 1600);//the output of the process is put in the response
			genPostHead(request, rd);//we generate the POST header
		} catch (IOException e) {
			rd.flagEx(500 , request);
			return rd;
		}
		return rd;//return the response
	}
}
