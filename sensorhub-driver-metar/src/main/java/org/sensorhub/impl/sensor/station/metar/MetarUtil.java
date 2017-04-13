package org.sensorhub.impl.sensor.station.metar;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Title: MetarUtil.java</p>
 * <p>Description: </p>
 *
 * @author T
 * @date Mar 2, 2016
 */
public class MetarUtil
{
	public static List<String> cleanFile(Path p) throws IOException {
		return cleanFile(p, false);
	}
	
	public static List<String> getLinesFromFile(String pathToFile) throws IOException {
		assert Files.exists(Paths.get(pathToFile));
		List<String> lines = Files.readAllLines(Paths.get(pathToFile),
				Charset.defaultCharset());
		return lines;
	}

	public static List<String> cleanFile(Path p, boolean isMarta) throws IOException {
		List<String> lines = getLinesFromFile(p.toString());
		List<String> linesOut = new ArrayList<>();
		boolean multiLine = false;
		StringBuilder multiLineBuffer = new StringBuilder(); 
		int lineCnt = -1;
		for(String line:lines) {
			if(++lineCnt < 7 && isMarta)  continue;  
			if(line.trim().length() == 0)  continue;
			String [] tokens = line.split(" ");
			if(!tokens[0].startsWith("METAR") && !tokens[0].startsWith("SPECI") 
					&& tokens[0].length() != 4 && !multiLine)
				continue;
			line = line.trim();
			if(multiLine) 
				multiLineBuffer.append(" ");
			multiLineBuffer.append(line);
			if(line.endsWith("=")) {
				if(multiLineBuffer.toString().length() > 4 && !line.endsWith("NIL=")) {
					multiLineBuffer.deleteCharAt(multiLineBuffer.length() - 1);  // get rid of the '=' terminating sign 
					linesOut.add(multiLineBuffer.toString());
				}
				multiLineBuffer = new StringBuilder();
				multiLine = false;
			} else {
				multiLine = true;
			}
			
		}
		// check if anything in multilineBuffer- last line in single entry wxMsg files does not end with '='
		// !!!
		if(multiLineBuffer.length() > 5)
			linesOut.add(multiLineBuffer.toString());
			
		
		return linesOut;
	}
	
	public static boolean isAlpha(String name) {
	    return name.matches("[a-zA-Z]+");
	}
	
	public static final double f_to_c(double tempF) {
		return (tempF - 32.0) * 5.0 / 9.0;
	}

	public static final double c_to_f(double tempC) {
		return (tempC*1.8) + 32.0;
	}


	public static void main(String[] args) throws Exception {
//		System.err.println(getWeatherscopePathFromTime(System.currentTimeMillis()));
//		List<String> lines = cleanFile(Paths.get("C:/Data/station/metar/NAMSA_EURSA_621377820000.TXT"), true);
		List<String> lines = cleanFile(Paths.get("C:/Data/station/metar/wxMsg/SAHOURLY.TXT.1286"), false);
//		List<String> lines = cleanFile(Paths.get("C:/Data/station/metar/wxMsg/SAHOURLY.TXT"));
		for(String line:lines)
			System.err.println(line);
//
//		String a = "~~~~";
//		System.err.println(a.startsWith("~~~~"));
	}
}
