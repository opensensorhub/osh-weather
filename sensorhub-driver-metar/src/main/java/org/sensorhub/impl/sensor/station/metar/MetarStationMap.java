package org.sensorhub.impl.sensor.station.metar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

import org.sensorhub.impl.sensor.station.Station;

import com.opencsv.CSVReader;

/**
 * <p>Title: MetarStationMap.java</p>
 * <p>Description: </p>
 *
 * @author tcook
 * @date Sep 26, 2016
 */
public class MetarStationMap {
	private static final String MAP_FILE_PATH = "metarStations.csv";
	private HashMap<String, Station> map;
	private static MetarStationMap instance = null;
	
	private MetarStationMap() throws IOException {
		loadMap();
	}
	
	public static MetarStationMap getInstance() throws IOException {
		if(instance == null)
			instance = new MetarStationMap();
		
		return instance;
	}
	
	private void loadMap() throws IOException {
		ClassLoader classLoader = getClass().getClassLoader();
		File file = new File(classLoader.getResource(MAP_FILE_PATH).getFile());
		map = new HashMap<>();
		
		CSVReader reader = new CSVReader(new FileReader(file));
		try {
			String [] line;
			reader.readNext(); // skip hdr line
			while ((line = reader.readNext()) != null ) {
				String id = line[1];
				String name = line[2];
				double lat = 0.0, lon = 0.0, el = 0.0;
				if(!line[3].equals(""))
					lat = Double.parseDouble(line[3]);
				if(!line[4].equals(""))
					lon = Double.parseDouble(line[4]);
				if(!line[5].equals(""))
					el = Double.parseDouble(line[5]);

				Station s = new Station();
				s.setId(id.toUpperCase());
				s.setName(name);
				s.setLat(lat);
				s.setLon(lon);
				s.setElevation(el);
				map.put(id, s);

			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			reader.close();
		}
	}

	public Station getStation(String id) {
		return map.get(id.toUpperCase());
	}
	
	public static void main(String[] args) throws IOException {
		MetarStationMap metarMap = MetarStationMap.getInstance();

		System.err.println(metarMap.getStation("KEVW"));
	}
}
