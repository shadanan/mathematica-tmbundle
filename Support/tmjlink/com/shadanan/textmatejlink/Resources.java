package com.shadanan.textmatejlink;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.jlink.MathLinkFactory;
import com.wolfram.jlink.PacketArrivedEvent;
import com.wolfram.jlink.PacketListener;

public class Resources implements PacketListener {
	private String sessionId = null;
	private String cacheFolder = null;
	private KernelLink kernelLink = null;
	private int currentCount = 0;
	
	private ArrayList<Resources.Resource> resources = null;
	private String resourceView = null;
	
	public Resources(String sessionId, String cacheFolder, String[] mlargs) throws MathLinkException, IOException {
		this.sessionId = sessionId;
		this.cacheFolder = cacheFolder;
		this.resources = new ArrayList<Resources.Resource>();
		
		// Allocate the kernel link and register packet listener
		kernelLink = MathLinkFactory.createKernelLink(mlargs);
		kernelLink.addPacketListener(this);
		kernelLink.discardAnswer();
		
		// Create cache folder
		File sessionFolderPointer = getSessionFolder();
		if (sessionFolderPointer.exists())
			delete(sessionFolderPointer);
		sessionFolderPointer.mkdir();
	}
	
	public static boolean delete(File file) {
		if (file.isDirectory()) {
			for (File child : file.listFiles()) {
				boolean success = delete(child);
				if (!success) return false;
			}
		}
		
		return file.delete();
	}
	
	public String getSessionId() {
		return sessionId;
	}
	
	public int getSize() {
		return resources.size();
	}
	
	public String getResourceView() {
		return resourceView;
	}
	
	public File getSessionFolder() {
		return new File(cacheFolder + "/" + sessionId);
	}
	
	public File getNamedFile(String filename) {
		File file = new File(cacheFolder + "/" + sessionId + "/" + filename);
		return file;
	}
	
	public void refresh() throws MathLinkException {
		kernelLink.connect();
	}
	
	public void close() {
		// Close the kernel link
		kernelLink.close();
		
		// Release all allocated resources
		release();
		
		// Delete the cache folder (it should be empty now)
		File cacheFp = getSessionFolder();
		if (cacheFp.exists()) cacheFp.delete();
	}
	
	public void release() {
		// Delete the resource view
		File resourceViewFp = getNamedFile(resourceView);
		if (resourceViewFp.exists()) resourceViewFp.delete();
		
		// Delete resources allocated
		Iterator<Resource> iterator = resources.iterator();
		while (iterator.hasNext()) {
			Resource resource = iterator.next();
			resource.release();
			iterator.remove();
		}
	}
	
	public void evaluate(String query) throws MathLinkException, IOException {
		// Log the input
		resources.add(new Resource(query));
		
		if (query.trim().charAt(query.trim().length()-1) == ';') {
			// Evaluate and don't log the answer.
			kernelLink.evaluate(query);
			kernelLink.discardAnswer();
		} else {
			// Log the output as an image
			byte[] data = kernelLink.evaluateToImage(query, 0, 0);
			if (data != null)
				resources.add(new Resource(MathLink.DISPLAYPKT, data));
			
			// Log the output as fullform text
			String result = kernelLink.evaluateToInputForm(query, 120);
			if (result != null)
				resources.add(new Resource(MathLink.RETURNPKT, result));
		}
		
		// Done with this. Move on...
		kernelLink.newPacket();
		currentCount++;
	}
	
	private String applyLayout(String content) throws IOException {
		StringBuilder result = new StringBuilder();
		FileReader layout = new FileReader(cacheFolder + "/layout.html.erb");
		
		int charsRead;
		char[] buff = new char[1024];
		do {
			charsRead = layout.read(buff);
			if (charsRead != -1) result.append(buff);
		} while (charsRead != -1);
		
		String yieldToken = "<%= yield %>";
		int start = result.indexOf(yieldToken);
		result.replace(start, start + yieldToken.length(), content);
		return result.toString();
	}
	
	public File render() throws IOException {
		boolean renderedDisplay = false;
		int currentCount = -1;
		StringBuilder content = new StringBuilder();
		
		if (resourceView != null) {
			File resourceViewFile = getNamedFile(resourceView);
			if (resourceViewFile.exists()) 
				resourceViewFile.delete();
		}
		
		// Render session id div
		content.append("<div class='session_id'>");
		content.append("<span class='label'>Session ID:</span> ");
		content.append("<span class='value'>" + sessionId + "</span>");
		content.append("</div>");
		
		for (Resource resource : resources) {
			if (currentCount == -1) {
				currentCount = resource.getCount();
				content.append("<div id='resource_" + currentCount + "' class='cellgroup'>");
			}
			
			if (resource.getCount() != currentCount) {
				content.append("</div>");
				currentCount = resource.getCount();
				content.append("<div id='resource_" + currentCount + "' class='cellgroup'>");
				renderedDisplay = false;
			}
			
			if (resource.type == MathLink.DISPLAYPKT) {
				renderedDisplay = true;
				content.append(resource.render(true));
			} else if (resource.type == MathLink.RETURNPKT) {
				if (renderedDisplay)
					content.append(resource.render(false));
				else
					content.append(resource.render(true));
			} else {
				content.append(resource.render(true));
			}
		}
		
		if (currentCount != -1) {
			content.append("</div>");
		}
		
		resourceView = UUID.randomUUID().toString() + ".html";
		FileWriter fp = new FileWriter(getNamedFile(resourceView));
		fp.write(applyLayout(content.toString()));
		fp.close();
		
		return getNamedFile(resourceView);
	}
	
	class Resource {
		private int type;
		private String value;
		private int count;
		
		public Resource(String value) {
			this.type = -1;
			this.value = value;
			this.count = currentCount;
		}
		
		public Resource(int type, String value) {
			this.type = type;
			this.value = value;
			this.count = currentCount;
		}
		
		public Resource(int type, byte[] data) throws IOException {
			this.type = type;
			this.value = UUID.randomUUID().toString() + ".gif";
			this.count = currentCount;
			
			FileOutputStream fp = new FileOutputStream(getFilePointer());
			fp.write(data);
			fp.close();
		}
		
		public int getType() {
			return type;
		}
		
		public String getValue() {
			return value;
		}
		
		public int getCount() {
			return count;
		}
		
		public File getFilePointer() {
			return getNamedFile(value);
		}
		
		public void release() {
			if (type == MathLink.DISPLAYPKT) {
				File file = getFilePointer();
				if (file.exists()) file.delete();
			}
		}
		
		public String render(boolean visible) {
			StringBuilder result = new StringBuilder();
			
			String style = "";
			if (!visible)
				style = " style='display:none;'";
			
			if (type == -1) {
				result.append("<div class='cell input'" + style + ">");
				result.append("  <div class='margin'>In[" + count + "] := </div>");
				result.append("  <div class='content'>" + value + "</div>");
				result.append("</div>");
			}
			
			// TODO: May need to convert \n to <br />
			if (type == MathLink.TEXTPKT) {
				result.append("<div class='cell text'" + style + ">");
				result.append("  <div class='margin'>Msg[" + count + "] := </div>");
				result.append("  <div class='content'>" + value + "</div>");
				result.append("</div>");
			}
			
			// TODO: May need to convert \n to <br />
			if (type == MathLink.MESSAGEPKT) {
				result.append("<div class='cell message'" + style + ">");
				result.append("  <div class='margin'>Msg[" + count + "] := </div>");
				result.append("  <div class='content'>" + value + "</div>");
				result.append("</div>");
			}
			
			if (type == MathLink.DISPLAYPKT) {
				result.append("<div class='cell display'" + style + ">");
				result.append("  <div class='margin'>Out[" + count + "] := </div>");
				result.append("  <div class='content'>");
				result.append("    <img src='" + getFilePointer() + "' onclick='toggle(" + count + ")' />");
				result.append("  </div>");
				result.append("</div>");
			}
			
			if (type == MathLink.RETURNPKT) {
				result.append("<div class='cell return'" + style + ">");
				result.append("  <div class='margin'>Out[" + count + "] := </div>");
				result.append("  <div class='content'>" + value + "</div>");
				result.append("</div>");
			}
			
			return result.toString();
		}
	}

	public boolean packetArrived(PacketArrivedEvent evt) throws MathLinkException {
		KernelLink ml = (KernelLink)evt.getSource();
		
		if (evt.getPktType() == MathLink.TEXTPKT) {
			resources.add(new Resource(evt.getPktType(), ml.getString()));
			
		}
		
		if (evt.getPktType() == MathLink.MESSAGEPKT) {
			resources.add(new Resource(evt.getPktType(), ml.getString()));
		}
		
		System.out.println("Received Packet: " + evt.getPktType());
		
		return true;
	}
}
