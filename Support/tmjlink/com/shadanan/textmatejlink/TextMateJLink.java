package com.shadanan.textmatejlink;


public class TextMateJLink {
	public static void main(String args[]) throws InterruptedException {
		int port = Integer.parseInt(args[0]);
		String cacheFolder = args[1];
		
		String[] mlargs = new String[args.length-2];
		for (int i = 2; i < args.length; i++) {
			mlargs[i-2] = args[i];
		}
		
		Server server = new Server(port, cacheFolder, mlargs);
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
