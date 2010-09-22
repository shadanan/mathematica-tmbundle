package com.shadanan.textmatejlink;


public class TextMateJLink {
	public static void main(String args[]) throws InterruptedException {
		int port = Integer.parseInt(args[0]);
		System.out.println("Server starting up on: " + port);
		String cacheFolder = args[1];
		int textMatePid = Integer.parseInt(args[2]);
		
		String[] mlargs = new String[args.length-3];
		for (int i = 3; i < args.length; i++) {
			mlargs[i-3] = args[i];
		}
		
		Server server = new Server(port, cacheFolder, textMatePid, mlargs);
		Runtime.getRuntime().addShutdownHook(new Shutdown(server));
		server.start();
		server.join();
	}
}

class Shutdown extends Thread {
	private Server server = null;
	
	public Shutdown(Server server) {
		super();
		this.server = server;
	}
	
	public void run() {
		try {
			server.shutdown();
			server.join();
		} catch (InterruptedException e) {}
	}
}
