package com.shadanan.textmatejlink;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

import com.wolfram.jlink.Expr;
import com.wolfram.jlink.ExprFormatException;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.jlink.MathLinkFactory;
import com.wolfram.jlink.PacketArrivedEvent;
import com.wolfram.jlink.PacketListener;

public class Resources implements PacketListener {
	public static final Expr NULLEXPR = new Expr(Expr.SYMBOL, "Null");
	
	private String sessionId = null;
	private String cacheFolder = null;
	private KernelLink kernelLink = null;
	private int currentCount = 0;
	private String[] mlargs = null;
	private ArrayList<Resources.Resource> resources = null;
	
	public Resources(String sessionId, String cacheFolder, String[] mlargs) throws MathLinkException, IOException {
		this.sessionId = sessionId;
		this.cacheFolder = cacheFolder;
		this.mlargs = mlargs;
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
	
	public File getSessionFolder() {
		return new File(cacheFolder + "/" + sessionId);
	}
	
	public File getNamedFile(String filename) {
		File file = new File(cacheFolder + "/" + sessionId + "/" + filename);
		return file;
	}
	
	public void reconnect() throws MathLinkException {
		kernelLink.removePacketListener(this);
		kernelLink.close();
		
		kernelLink = MathLinkFactory.createKernelLink(mlargs);
		kernelLink.addPacketListener(this);
		kernelLink.discardAnswer();
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
		// Delete resources allocated
		Iterator<Resource> iterator = resources.iterator();
		while (iterator.hasNext()) {
			Resource resource = iterator.next();
			resource.release();
			iterator.remove();
		}
	}
	
	public String getSuggestions() throws MathLinkException, ExprFormatException {
		StringBuilder result = new StringBuilder();
		result.append("[");
		
		kernelLink.evaluate("$ContextPath");
		kernelLink.waitForAnswer();
		Expr contexts = kernelLink.getExpr();
		
		for (int j = 1; j <= contexts.length(); j++) {
			String context = contexts.part(j).asString();
			kernelLink.evaluate("Names[\"" + context + "*\"]");
			kernelLink.waitForAnswer();
			Expr symbols = kernelLink.getExpr();
			
			for (int i = 1; i <= symbols.length(); i++) {
				result.append('"');
				result.append(symbols.part(i).asString());
				result.append('"');
				result.append(",");
			}
		}
		
		result.append("]");
		return result.toString();
	}
	
	public String evaluate(String query, boolean evalToImage) throws MathLinkException, IOException {
		int currentResource = -1;
		
		StringBuilder cellgroup = new StringBuilder();
		cellgroup.append("<div id='resource_" + currentCount + "' class='cellgroup'>");
		
		// Log the input
		Resource input = new Resource(query); 
		resources.add(input);
		currentResource = resources.size();
		cellgroup.append(input.render(true));
		
		kernelLink.evaluate(query);
		kernelLink.waitForAnswer();
		Expr result = kernelLink.getExpr();
		
		// Append intermediate packets received by listener callback
		while (currentResource < resources.size()) {
			cellgroup.append(resources.get(currentResource).render(true));
			currentResource++;
		}
		
		if (!result.equals(NULLEXPR)) {
			// Log the output as fullform text
			Resource textResource = new Resource(MathLink.RETURNPKT, result);
			byte[] data = null;
			
			if (evalToImage || textResource.isGraphics())
				data = kernelLink.evaluateToImage(result, 0, 0);
			
			if (data != null) {
				Resource graphicsResource = new Resource(MathLink.DISPLAYPKT, data);
				resources.add(graphicsResource);
				cellgroup.append(graphicsResource.render(true));
				textResource.subdue();
				cellgroup.append(textResource.render(false));
			} else {
				cellgroup.append(textResource.render(true));
			}
			
			resources.add(textResource);
		}
		
		// Done with this. Move on...
		kernelLink.newPacket();
		currentCount++;
		
		cellgroup.append("</div>");
		return cellgroup.toString();
	}
	
	public String render() {
		boolean renderedDisplay = false;
		int currentCount = -1;
		StringBuilder content = new StringBuilder();
		
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
		
		return content.toString();
	}
	
	class Resource {
		private int type;
		private String value;
		private int count;
		private boolean subdue;
		private Expr expr;
		
		public Resource(String value) {
			this.type = -1;
			this.value = value;
			this.count = currentCount;
			this.subdue = false;
			this.expr = null;
		}
		
		public Resource(int type, Expr expr) {
			this.type = type;
			this.value = null;
			this.count = currentCount;
			this.subdue = false;
			this.expr = expr;
		}
		
		public Resource(int type, String value) {
			this.type = type;
			this.value = value;
			this.count = currentCount;
			this.subdue = false;
			this.expr = null;
		}
		
		public Resource(int type, byte[] data) throws IOException {
			this.type = type;
			this.value = UUID.randomUUID().toString() + ".gif";
			this.count = currentCount;
			this.subdue = false;
			this.expr = null;
			
			FileOutputStream fp = new FileOutputStream(getFilePointer());
			fp.write(data);
			fp.close();
		}
		
		public void subdue() {
			subdue = true;
		}
		
		public int getType() {
			return type;
		}
		
		public String getHtmlEscapedValue() {
			StringBuilder sb = new StringBuilder();
			String base = value == null ? expr.toString() : value;
			
			for (int i = 0; i < base.length(); i++) {
				char c = base.charAt(i);
				switch (c) {
					case '<': sb.append("&lt;"); break;
					case '>': sb.append("&gt;"); break;
					case '&': sb.append("&amp;"); break;
					case '"': sb.append("&quot;"); break;
					case 'à': sb.append("&agrave;"); break;
					case 'À': sb.append("&Agrave;"); break;
					case 'â': sb.append("&acirc;"); break;
					case 'Â': sb.append("&Acirc;"); break;
					case 'ä': sb.append("&auml;"); break;
					case 'Ä': sb.append("&Auml;"); break;
					case 'å': sb.append("&aring;"); break;
					case 'Å': sb.append("&Aring;"); break;
					case 'æ': sb.append("&aelig;"); break;
					case 'Æ': sb.append("&AElig;"); break;
					case 'ç': sb.append("&ccedil;"); break;
					case 'Ç': sb.append("&Ccedil;"); break;
					case 'é': sb.append("&eacute;"); break;
					case 'É': sb.append("&Eacute;"); break;
					case 'è': sb.append("&egrave;"); break;
					case 'È': sb.append("&Egrave;"); break;
					case 'ê': sb.append("&ecirc;"); break;
					case 'Ê': sb.append("&Ecirc;"); break;
					case 'ë': sb.append("&euml;"); break;
					case 'Ë': sb.append("&Euml;"); break;
					case 'ï': sb.append("&iuml;"); break;
					case 'Ï': sb.append("&Iuml;"); break;
					case 'ô': sb.append("&ocirc;"); break;
					case 'Ô': sb.append("&Ocirc;"); break;
					case 'ö': sb.append("&ouml;"); break;
					case 'Ö': sb.append("&Ouml;"); break;
					case 'ø': sb.append("&oslash;"); break;
					case 'Ø': sb.append("&Oslash;"); break;
					case 'ß': sb.append("&szlig;"); break;
					case 'ù': sb.append("&ugrave;"); break;
					case 'Ù': sb.append("&Ugrave;"); break;         
					case 'û': sb.append("&ucirc;"); break;         
					case 'Û': sb.append("&Ucirc;"); break;
					case 'ü': sb.append("&uuml;"); break;
					case 'Ü': sb.append("&Uuml;"); break;
					case '®': sb.append("&reg;"); break;         
					case '©': sb.append("&copy;"); break;   
					case '€': sb.append("&euro;"); break;
					// be carefull with this one (non-breaking whitee space)
					// case ' ': sb.append("&nbsp;"); break;
					// case '\n': sb.append("<br />"); break;
					default:  sb.append(c); break;
				}
			}
			
			return sb.toString();
		}
		
		public int getCount() {
			return count;
		}
		
		public File getFilePointer() {
			return getNamedFile(value);
		}
		
		public boolean isGraphics() {
			if (type != MathLink.RETURNPKT) {
				throw new RuntimeException("This method can only be called on RETURNPKT resource.");
			}
			
			Expr head = expr.head();
			if (head.toString().equals("InputForm"))
				return false;
			
			if (head.toString().equals("Graphics"))
				return true;
			
			if (head.toString().equals("Graphics3D"))
				return true;
			
			if (head.toString().endsWith("Form"))
				return true;
			
			if (head.toString().equals("List")) {
				if (expr.length() == 0)
					return false;
				
				Expr subhead = expr.part(1).head();
				
				if (subhead.toString().equals("InputForm"))
					return false;
				
				if (subhead.toString().equals("Graphics"))
					return true;
				
				if (subhead.toString().equals("Graphics3D"))
					return true;
				
				if (subhead.toString().endsWith("Form"))
					return true;
			}
			
			return false;
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
				result.append("  <div class='content'>" + getHtmlEscapedValue() + "</div>");
				result.append("</div>");
			}
			
			if (type == MathLink.TEXTPKT) {
				result.append("<div class='cell text'" + style + ">");
				result.append("  <div class='margin'>Msg[" + count + "] := </div>");
				result.append("  <div class='content'>" + getHtmlEscapedValue() + "</div>");
				result.append("</div>");
			}
			
			if (type == MathLink.MESSAGEPKT) {
				result.append("<div class='cell message'" + style + ">");
				result.append("  <div class='margin'>Msg[" + count + "] := </div>");
				result.append("  <div class='content'>" + getHtmlEscapedValue() + "</div>");
				result.append("</div>");
			}
			
			if (type == MathLink.DISPLAYPKT) {
				result.append("<div class='cell display'" + style + ">");
				result.append("  <div class='margin'>Out[" + count + "] := </div>");
				result.append("  <div class='content'>");
				result.append("    <img src='file://" + getFilePointer() + "' onclick='toggle(" + count + ")' />");
				result.append("  </div>");
				result.append("</div>");
			}
			
			if (type == MathLink.RETURNPKT) {
				String cls = "";
				if (subdue)
					cls = " subdue";
				
				result.append("<div class='cell return" + cls + "'" + style + ">");
				result.append("  <div class='margin'>Out[" + count + "] := </div>");
				result.append("  <div class='content'>" + getHtmlEscapedValue() + "</div>");
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
		
		for (Field field : MathLink.class.getFields()) {
			if (field.getName().endsWith("PKT")) {
				try {
					if (evt.getPktType() == field.getInt(field)) {
						System.out.println("Received Mathematica Packet: " + field.getName() + " (" + evt.getPktType() + ")");
					}
				} catch (IllegalArgumentException e) {
					System.out.println(e.getMessage());
				} catch (IllegalAccessException e) {
					System.out.println(e.getMessage());
				}
			}
		}
		
		return true;
	}
}
