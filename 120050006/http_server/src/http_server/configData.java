package http_server;

class configData {
	static int portNumber = 8999;
	static int timeout = 15000;
	static int maxPackets = 6; //max no of requests per connection
	static int maxNoOfThreads = 5; //max no of Active Connections
	//private static String path = "/home/manik/Documents/hostedFiles/"; Hardcoded path
	private static String path = "./hostedFiles/";//Relative path
    private static String Def = "default";
    //Server Meta-Info
    static String SERVER_PROTOCOL = "HTTP/1.1";
	static String SERVER_SOFTWARE = "Java8";
	static String SERVER_NAME = "MDHD";
	static String GATEWAY_INTERFACE = "CGI/1.1";
	
	static String Path() {
		return path;
	}
	
	static void changePath(String Path) {
		path = Path;
	}
	
	static void changeDefault(String def) {
		Def = def;
	}
	
	static String Default() {
		return Def;
	}
}
