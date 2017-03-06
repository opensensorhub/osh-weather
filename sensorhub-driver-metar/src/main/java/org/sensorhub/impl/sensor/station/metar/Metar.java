package org.sensorhub.impl.sensor.station.metar;

import java.util.ArrayList;
import java.util.Date;

public class Metar 
{
	public static final double KILOMETERS_TO_STATUTEMILES = 0.6213712;
	public static final double STATUTEMILES_TO_KILOMETERS = 1.609344;

	public String reportString = null;
	public String dateString = "";
	@Deprecated //  use TimeUtil and long to internally represent date
	public Date date = null;
	public long timeUtc;
	public String stationID = null;
	public Integer windDirection = null;
	public Integer windDirectionMin = null;
	public Integer windDirectionMax = null;
	public boolean windDirectionIsVariable = false;
	private Double windSpeed = null; // (in knots x 1.1508 = MPH) - private to force units (think about this)
	public Double windGust = null; // (in knots x 1.1508 = MPH)
	public Integer windDirectionGust = null; // (in knots x 1.1508 = MPH)
	public boolean isCavok = false;
	private Double visibilityMiles = null; 
	private Double visibilityKilometers = null;
	public boolean visibilityLessThan = false;
	public Double pressure = null;
	public Double altimeter = null;
	public Integer temperatureC = null;
	public Double temperature = null;
	public Double temperaturePrecise = null;
	public Double dewPoint = null;
	public Integer dewPointC = null;
	public Double dewPointPrecise = null;
	public Double hourlyPrecipInches = null;
	public ArrayList<PresentWeather> presentWeathers = new ArrayList<>();
	public ArrayList<SkyCondition> skyConditions = new ArrayList<>();
	public ArrayList<RunwayVisualRange> runwayVisualRanges = new ArrayList<>();
	public ArrayList<String> obscurations = new ArrayList<>();
	public boolean isSpeci = false;
	public boolean isCorrection = false;
//	private boolean isNoSignificantChange = false;
//	private String becoming = null;
	


	public Double getVisibilityMiles() {
		return visibilityMiles;
	}
	
	public void setVisibilityMiles(Double visibilityMiles) {
		this.visibilityMiles = visibilityMiles;
		this.visibilityKilometers = visibilityMiles * STATUTEMILES_TO_KILOMETERS;
	}
	
	public Double getVisibilityKilometers() {
		return visibilityKilometers;
	}
	
	public void setVisibilityKilometers(Double visibilityKilometers) {
		this.visibilityKilometers = visibilityKilometers;
		this.visibilityMiles = visibilityKilometers * KILOMETERS_TO_STATUTEMILES;
	}

	public Double getWindSpeed() {
		return windSpeed;
	}

	public void setWindSpeed(Double windSpeed) {
		this.windSpeed = windSpeed;
	}
	
	public void addRunwayVisualRange(RunwayVisualRange rvr) {
		this.runwayVisualRanges.add(rvr);
	}
	
	public void addPresentWeather(PresentWeather pw) {
		this.presentWeathers.add(pw);
	}

	public void addSkyCondition(SkyCondition sc) {
		this.skyConditions.add(sc);
	}
}

