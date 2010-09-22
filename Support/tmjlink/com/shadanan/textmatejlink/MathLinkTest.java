package com.shadanan.textmatejlink;

import java.lang.reflect.Field;

import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLink;
import com.wolfram.jlink.MathLinkException;
import com.wolfram.jlink.MathLinkFactory;
import com.wolfram.jlink.PacketArrivedEvent;
import com.wolfram.jlink.PacketListener;

public class MathLinkTest {
	public static void main(String args[]) {
		System.out.println(String.valueOf(32));
		
		KernelLink ml = null;
		TMJLinkPacketListener pl = null;
		byte[] data = null;
		String[] mlargs = {"-linkmode", "launch", "-linkname", "/Applications/Mathematica.app/Contents/MacOS/MathKernel -mathlink"};

		try {
			ml = MathLinkFactory.createKernelLink(mlargs);
			pl = new TMJLinkPacketListener();
			ml.addPacketListener(pl);
			ml.discardAnswer();
		} catch (MathLinkException e) {
			System.out.println("Fatal error opening link: " + e.getMessage());
			return;
		}

		data = ml.evaluateToImage("<<CorporateAnalysisUtilities`", 0, 0);
		System.out.println(data.length);
		
//		ml.waitForAnswer();
//		result = ml.getExpr();
//		System.out.println(result.toString());
//		ml.newPacket();
		
		data = ml.evaluateToImage("Integrate[x, x]", 0, 0);
		System.out.println(data.length);
		
//		ml.waitForAnswer();
//		result = ml.getExpr();
//		System.out.println(result.toString());
//		ml.newPacket();
		
		data = ml.evaluateToImage("Plot[Sin[x], {x, -4, 4}]", 0, 0);
		System.out.println(data.length);
		
//		ml.waitForAnswer();
//		result = ml.getExpr();
//		System.out.println(result.toString());
//		ml.newPacket();

//		try {
//		} catch (MathLinkException e) {
//			System.out.println("MathLinkException occurred: " + e.getMessage());
//		}
		ml.close();
	}
}

class TMJLinkPacketListener implements PacketListener {
	public boolean packetArrived(PacketArrivedEvent evt) throws MathLinkException {
		KernelLink ml = (KernelLink)evt.getSource();
		
		for (Field field : MathLink.class.getFields()) {
			if (field.getName().endsWith("PKT")) {
				try {
					if (evt.getPktType() == field.getInt(field)) {
						System.err.println("" + evt.getPktType() + " - " + field.getName());
					}
				} catch (IllegalArgumentException e) {
					System.out.println(e.getMessage());
				} catch (IllegalAccessException e) {
					System.out.println(e.getMessage());
				}
			}
		}
		
		if (evt.getPktType() == MathLink.TEXTPKT) {
			System.out.println(ml.getString());
		}
		
		if (evt.getPktType() == MathLink.MESSAGEPKT) {
			System.out.println(ml.getString());
		}
		
		if (evt.getPktType() == MathLink.RETURNPKT) {
			//System.out.println(ml.getString());
		}
		
		return true;
	}
}
