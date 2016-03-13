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
	private String cacheFolder = null;
	private int textMatePid = -1;
	private String[] mlargs = null;
	private boolean running = false;
	private Object sessionsLock = null;
	private ArrayList<Session> sessions = null;
	private HashMap<String, Resources> resourcesMap = null;
	
	public Server(String cacheFolder, int textMatePid, String[] mlargs) {
		this.cacheFolder = cacheFolder;
		this.textMatePid = textMatePid;
		this.mlargs = mlargs;
		this.running = true;
		
		resourcesMap = new HashMap<String, Resources>();
		sessions = new ArrayList<Session>();
		sessionsLock = new Object();
		
		System.out.println("TextMate PID: " + textMatePid);
	}
	
	@Override
  public void run() {
		try {
			ServerSocket ss = new ServerSocket(0);
			ss.setSoTimeout(1000);
			System.out.println("Server started on port: " + ss.getLocalPort());
			
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
		
		// Wait for all connections to end
		try {
			synchronized (sessionsLock) {
				while (sessions.size() > 0) {
					System.out.println("Waiting for " + sessions.size() + " sessions to end...");
					sessionsLock.wait();
				}
				System.out.println("All sessions closed.");
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// Close kernels and remove all resources
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
		return resources;
	}
	
	public Resources newResources(String sessionId) throws MathLinkException, IOException {
		Resources resources = resourcesMap.get(sessionId);
		if (resources != null) {
			System.out.println("Releasing Resources for Session ID: " + sessionId);
			resources.close();
			resourcesMap.remove(sessionId);
		}
		
		System.out.println("Allocating Resources for Session ID: " + sessionId);
		resources = new Resources(sessionId, cacheFolder, mlargs);
		synchronized (sessionsLock) {
			resourcesMap.put(sessionId, resources);
		}
		
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
					"Resource Count: " + entry.getValue().getSize());
		}
		System.out.println("=============================");
	}
}
