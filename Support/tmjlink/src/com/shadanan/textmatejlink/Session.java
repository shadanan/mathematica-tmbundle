package com.shadanan.textmatejlink;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;

import com.wolfram.jlink.ExprFormatException;
import com.wolfram.jlink.MathLinkException;

public class Session extends Thread {
	private Socket socket = null;
	private PrintWriter out = null;
	private InputStreamReader in = null;
	
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
	
	private void resetResources() throws MathLinkException, IOException {
		server.newResources(resources.getSessionId());
		System.out.println("Resetting Resources with Session ID: " + resources.getSessionId());
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
	
	public void sendInline(String data) {
		send("inline " + (data.length() + 1));
		send(data);
	}
	
	private void send(String reply) {
		System.out.println("To " + socket.getRemoteSocketAddress() + ": " + reply);
		out.println(reply);
	}
	
	@Override
  public void run() {
		try {
			in = new InputStreamReader(socket.getInputStream());
			out = new PrintWriter(socket.getOutputStream(), true);
		} catch (IOException e) {
			System.out.println("Socket is shutdown");
			running = false;
		}
		
		int state = 0;
		int readsize = -1;
		send("okay");
		
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
			
			System.out.println("From " + socket.getRemoteSocketAddress() + ": " + data);
			
			if (state == 0) {
				if (command.equals("quit")) {
					send("okay -- Good Bye");
					running = false;
					continue;
				}
				
				if (command.equals("sessid")) {
					try {
						setSessionId(args);
						send("okay -- Session ID set to: " + resources.getSessionId());
						state = 1;
					} catch (IOException e) {
						send("exception -- " + e.getMessage());
						e.printStackTrace();
					} catch (MathLinkException e) {
						send("exception -- " + e.getMessage());
						e.printStackTrace();
					}
					continue;
				}
				
				send("exception -- Invalid command (" + state + "): " + command);
				continue;
			}
			
			if (state == 1) {
				if (command.equals("quit")) {
					send("okay -- Good Bye");
					running = false;
					continue;
				}
				
				if (command.equals("execute")) {
					readsize = Integer.parseInt(args);
					state = 2;
					continue;
				}
				
				if (command.equals("image")) {
					readsize = Integer.parseInt(args);
					state = 3;
					continue;
				}
				
				if (command.equals("header")) {
					try {
						String renderedHtml = resources.render();
						send("inline " + (renderedHtml.length() + 1));
						send(renderedHtml);
						send("okay");
					} catch (Exception e) {
						send("exception -- " + e.getMessage());
						e.printStackTrace();
					}
					continue;
				}
				
				if (command.equals("clear")) {
					int resourceSize = resources.getSize();
					resources.release();
					send("okay -- Resources released: " + resourceSize);
					continue;
				}
				
				if (command.equals("reset")) {
					try {
						resetResources();
						send("okay -- All resources reset");
					} catch (MathLinkException e) {
						send("exception -- " + e.getMessage());
						e.printStackTrace();
					} catch (IOException e) {
						send("exception -- " + e.getMessage());
						e.printStackTrace();
					}
					continue;
				}
				
				if (command.equals("suggest")) {
					try {
						String suggestions = resources.getSuggestions();
						send("suggestions " + suggestions);
					} catch (MathLinkException e) {
						send("exception -- " + e.getMessage());
						e.printStackTrace();
					} catch (ExprFormatException e) {
						send("exception -- " + e.getMessage());
						e.printStackTrace();
					}
					continue;
				}
				
				if (command.equals("intexec")) {
					readsize = Integer.parseInt(args);
					state = 4;
					continue;
				}
				
				send("exception -- Invalid command (" + state + "): " + command);
				continue;
			}
			
			if (state == 2) {
				try {
					resources.evaluate(data, false, this);
					send("okay");
				} catch (Exception e) {
					send("exception -- " + e.getMessage());
					e.printStackTrace();
				}
				
				readsize = -1;
				state = 1;
				continue;
			}
			
			if (state == 3) {
				try {
					resources.evaluate(data, true, this);
					send("okay");
				} catch (Exception e) {
					send("exception -- " + e.getMessage());
					e.printStackTrace();
				}
				
				readsize = -1;
				state = 1;
				continue;
			}
			
			if (state == 4) {
				try {
					String result = resources.evaluate(data);
					if (result != null) sendInline(result);
					send("okay");
				} catch (Exception e) {
					send("exception -- " + e.getMessage());
					e.printStackTrace();
				}
				
				readsize = -1;
				state = 1;
				continue;
			}
			
			send("exception -- Invalid command (" + state + "): " + command);
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
