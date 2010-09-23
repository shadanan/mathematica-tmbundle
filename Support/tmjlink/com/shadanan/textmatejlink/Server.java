package com.shadanan.textmatejlink;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import com.wolfram.jlink.MathLinkException;

public class Server extends Thread {
	private int serverPort = -1;
	private String cacheFolder = null;
	private int textMatePid = -1;
	private String[] mlargs = null;
	private boolean running = false;
	private Object sessionsLock = null;
	private ArrayList<Session> sessions = null;
	private HashMap<String, Resources> resourcesMap = null;
	
	public Server(int serverPort, String cacheFolder, int textMatePid, String[] mlargs) {
		this.serverPort = serverPort;
		this.cacheFolder = cacheFolder;
		this.textMatePid = textMatePid;
		this.mlargs = mlargs;
		this.running = true;
		
		resourcesMap = new HashMap<String, Resources>();
		sessions = new ArrayList<Session>();
		sessionsLock = new Object();
	}
	
	public void run() {
		try {
			ServerSocket ss = new ServerSocket(serverPort);
			ss.setSoTimeout(1000);
			System.out.println("Server started.");
			
			while (running) {
				try {
					Process p = Runtime.getRuntime().exec("kill -0 " + this.textMatePid);
					if (p.waitFor() != 0) {
						System.out.println("TextMate's PID is gone: " + this.textMatePid);
						running = false;
						continue;
					}
					
					Socket socket = ss.accept();
					socket.setSoTimeout(1000);
					
					System.out.println("Opening connection: " + socket.getRemoteSocketAddress());
					Session session = new Session(this, socket);
					session.start();
					
					synchronized (sessionsLock) {
						sessions.add(session);
					}
				} catch (SocketTimeoutException te) {
					continue;
				} catch (InterruptedException e) {
					e.printStackTrace();
					running = false;
					continue;
				}
			}
			
			ss.close();
		} catch (IOException e) {
			e.printStackTrace();
			running = false;
		}
		
		// Shutdown the kernels
		try {
			synchronized (sessionsLock) {
				while (sessions.size() > 0) {
					sessionsLock.wait();
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// Remove all resources
		Iterator<Entry<String, Resources>> iterator = resourcesMap.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<String, Resources> entry = iterator.next();
			System.out.println("Releasing Resources for Session ID: " + entry.getKey());
			entry.getValue().close();
			iterator.remove();
		}
		
		System.out.println("Server shut down.");
	}
	
	public boolean isRunning() {
		return running;
	}
	
	public void shutdown() {
		System.out.println("Server shutting down...");
		running = false;
	}
	
	public void deleteSession(Session session) {
		synchronized (sessionsLock) {
			sessions.remove(session);
			sessionsLock.notifyAll();
		}
	}
	
	public Resources getResources(String sessionId) throws MathLinkException, IOException {
		if (resourcesMap.get(sessionId) == null) {
			System.out.println("Allocating Resources for Session ID: " + sessionId);
			Resources resources = new Resources(sessionId, cacheFolder, mlargs);
			synchronized (sessionsLock) {
				resourcesMap.put(sessionId, resources);
			}
		}
		
		Resources resources = resourcesMap.get(sessionId);
		resources.refresh();
		return resources;
	}
	
	public void printStatus() {
		System.out.println("==== Current Connections ====");
		for (Session session : sessions) {
			session.printStatus();
		}
		System.out.println("==== Allocated Resources ====");
		for (Entry<String, Resources> entry : resourcesMap.entrySet()) {
			System.out.println("Session ID: " + entry.getKey() + ", " + 
					"Resource Count: " + entry.getValue().getSize() + ", " +
					"Current View: " + entry.getValue().getResourceView());
		}
		System.out.println("=============================");
	}
}
