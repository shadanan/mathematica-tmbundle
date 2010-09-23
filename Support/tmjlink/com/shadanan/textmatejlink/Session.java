package com.shadanan.textmatejlink;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;

import com.wolfram.jlink.MathLinkException;

public class Session extends Thread {
	private Socket socket = null;
	private Server server = null;
	private boolean running = false;
	private Resources resources = null;
	
	public Session(Server server, Socket socket) {
		this.server = server;
		this.socket = socket;
		this.running = true;
	}
	
	public void printStatus() {
		System.out.println("Connection: " + socket.getRemoteSocketAddress());
		if (resources != null) {
			System.out.println("  Associated with Session ID: " + resources.getSessionId());
		}
	}
	
	public void close() {
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		server.deleteSession(this);
	}
	
	private void setSessionId(String sessionId) throws MathLinkException, IOException {
		resources = server.getResources(sessionId);
		System.out.println("Associating connection: " + socket.getRemoteSocketAddress() + " with Session ID: " + sessionId);
	}
	
	private String readLine(InputStreamReader in) {
		StringBuilder line = new StringBuilder();
		
		while (running && server.isRunning()) {
			try {
				int val = in.read();
				
				if (val == -1) {
					running = false;
					continue;
				}
				
				if ((char)val == '\r') {
					continue;
				}
				
				if ((char)val == '\n') {
					return line.toString();
				}
				
				line.append((char)val);
				
			} catch (SocketTimeoutException e) {
				continue;
			} catch (IOException e) {
				running = false;
				continue;
			}
		}
		
		return null;
	}
	
	private String readData(InputStreamReader in, int size) {
		StringBuilder line = new StringBuilder();
		
		while (running && server.isRunning()) {
			try {
				int val = in.read();
				
				if (val == -1) {
					running = false;
					continue;
				}
				
				line.append((char)val);
				if (line.length() == size) {
					return line.toString();
				}
				
			} catch (SocketTimeoutException e) {
				continue;
			} catch (IOException e) {
				running = false;
				continue;
			}
		}
		
		return null;
	}
	
	public void run() {
		PrintWriter out = null;
		InputStreamReader in = null;
		
		try {
			in = new InputStreamReader(socket.getInputStream());
			out = new PrintWriter(socket.getOutputStream(), true);
		} catch (IOException e) {
			System.out.println("Socket is shutdown");
			running = false;
		}
		
		int state = 0;
		int readsize = -1;
		out.println("TMJLink Status OK");
		
		while (running && server.isRunning()) {
			String data = null;
			
			String command = null;
			String args = null;
			
			if (readsize == -1) {
				data = readLine(in);
				
				if (data == null) {
					running = false;
					continue;
				}
				
				if (data.indexOf(" ") != -1) {
					command = data.substring(0, data.indexOf(" "));
					args = data.substring(data.indexOf(" ") + 1);
				} else {
					command = data;
				}
			} else {
				data = readData(in, readsize);

				if (data == null) {
					running = false;
					continue;
				}
			}
			
			System.out.println(data);
			
			if (state == 0) {
				if (command.equals("quit")) {
					out.println("TMJLink Okay -- Good Bye");
					running = false;
					continue;
				}
				
				if (command.equals("sessid")) {
					try {
						setSessionId(args);
						out.println("TMJLink Okay -- Session ID set to: " + resources.getSessionId());
						state = 1;
					} catch (IOException e) {
						out.println("TMJLink Exception -- " + e.getMessage());
						e.printStackTrace();
					} catch (MathLinkException e) {
						out.println("TMJLink Exception -- " + e.getMessage());
						e.printStackTrace();
					}
					continue;
				}
				
				out.println("TMJLink InvalidCommand -- State: " + state + ", Command: " + command);
				continue;
			}
			
			if (state == 1) {
				if (command.equals("quit")) {
					out.println("TMJLink Okay -- Good Bye");
					running = false;
					continue;
				}
				
				if (command.equals("evalff")) {
					readsize = Integer.parseInt(args);
					state = 2;
					continue;
				}
				
				if (command.equals("evali")) {
					readsize = Integer.parseInt(args);
					state = 3;
					continue;
				}
				
				if (command.equals("clear")) {
					int resourceSize = resources.getSize();
					resources.release();
					out.println("TMJLink Okay -- Resources released: " + resourceSize);
					continue;
				}
				
				if (command.equals("show")) {
					try {
						File file = resources.render();
						out.println("TMJLink FileSaved " + file.getAbsolutePath());
					} catch (IOException e) {
						out.println("TMJLink Exception -- " + e.getMessage());
						e.printStackTrace();
					}
					continue;
				}
				
				out.println("TMJLink InvalidCommand -- State: " + state + ", Command: " + command);
				continue;
			}
			
			if (state == 2) {
				try {
					resources.evaluate(data);
					out.println("TMJLink Okay");
				} catch (MathLinkException e) {
					out.println("TMJLink Exception -- " + e.getMessage());
					e.printStackTrace();
				}
				
				readsize = -1;
				state = 1;
				continue;
			}
			
			if (state == 3) {
				try {
					resources.evaluateToImage(data);
					out.println("TMJLink Okay");
				} catch (Exception e) {
					out.println("TMJLink Exception -- " + e.getMessage());
					e.printStackTrace();
				}
				
				readsize = -1;
				state = 1;
				continue;
			}
			
			out.println("TMJLink InvalidCommand -- State: " + state + ", Command: " + command);
		}
		
		System.out.println("Closing connection: " + socket.getRemoteSocketAddress());
		
		if (in != null) {
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		if (out != null) {
			out.close();
		}
		
		close();
	}
}
