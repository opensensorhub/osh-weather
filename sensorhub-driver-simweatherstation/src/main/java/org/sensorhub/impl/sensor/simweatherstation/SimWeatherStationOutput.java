package org.sensorhub.impl.sensor.simweatherstation;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.Quantity;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.SWEHelper;

public class SimWeatherStationOutput extends AbstractSensorOutput<SimWeatherStationSensor>
{
    DataComponent dataStruct;
    DataEncoding dataEnc;
    Timer timer;
    Random rand = new Random();
    
    // reference values around which actual values vary
    double pressRef = 987.5;
    double tempRef = 30.0;
    double humidRef = 35.0;
    double rainRef = 2.0;
    double windSpeedRef = 1.0;
    double directionRef = 0.0;
    
    // initialize then keep new values for each measurement
    double press = pressRef;
    double temp = tempRef;
    double humid = humidRef;
    double rain = rainRef;
    double windSpeed = windSpeedRef;
    double windDir = directionRef;
    
    public SimWeatherStationOutput(SimWeatherStationSensor parentSensor)
    {
        super(parentSensor);
    }


    @Override
    public String getName()
    {
        return "Sim Weather";
    }


    protected void init()
    {
        SWEHelper fac = new SWEHelper();
        dataStruct = fac.newDataRecord(7);
        dataStruct.setName(getName());
        
        // build SWE Common record structure
        dataStruct.setDefinition("http://sensorml.com/ont/swe/property/Weather");
        dataStruct.setDescription("Weather Station Data");
        
        /************************* Add appropriate data fields *******************************************************************************/
        // add time, average, and instantaneous radiation exposure levels
        dataStruct.addComponent("time", fac.newTimeStampIsoUTC());
        dataStruct.addComponent("pressure", fac.newQuantity(SWEHelper.getPropertyUri("BarometricPressure"), "Barometric Pressure", null, "mbar"));
        dataStruct.addComponent("temperature", fac.newQuantity(SWEHelper.getPropertyUri("Temperature"), "Air Temperature", null, "degC"));
        dataStruct.addComponent("relHumidity", fac.newQuantity(SWEHelper.getPropertyUri("RelativeHumidity"), " Relative Humidity", null, "%"));
        dataStruct.addComponent("rainAccum", fac.newQuantity(SWEHelper.getPropertyUri("RainAccumulation"), "Rain Accumulation", null, "tips"));
        dataStruct.addComponent("windSpeed", fac.newQuantity(SWEHelper.getPropertyUri("WindSpeed"), "Wind Speed", null, "m/s"));
        Quantity q = fac.newQuantity(SWEHelper.getPropertyUri("WindDirection"), "Wind Direction", null, "deg");
        q.setReferenceFrame("http://sensorml.com/ont/swe/property/NED");
        q.setAxisID("z");
        dataStruct.addComponent("windDir", q);
        
        /*************************************************************************************************************************************/
        
        // also generate encoding definition
        dataEnc = fac.newTextEncoding(",", "\n");
    }
    
    private void sendMeasurement()
    {	
    	// generate new weather values
    	long time = System.currentTimeMillis();
        
        // pressure; value will increase or decrease by less than 20 hPa
        press += variation(press, pressRef, -0.001, 0.1);
        
        // temperature; value will increase or decrease by less than 0.1 deg
        temp += variation(temp, tempRef, -0.001, 0.1);
        
        // humidity; value will increase or decrease by less than 0.5%
        humid += variation(humid, humidRef, -0.001, 0.5);
        
        // rain; value will increase or decrease by less than 1 mm
        rain += variation(rain, rainRef, -0.001, 1.0);

        // wind speed; keep positive
        // vary value between +/- 10 m/s
        windSpeed += variation(windSpeed, windSpeedRef, -0.001, 0.1);
        windSpeed = windSpeed < 0.0 ? 0.0 : windSpeed; 
        
        // wind direction; keep between 0 and 360 degrees
        windDir += 1.0 * (2.0 * Math.random() - 1.0);
        windDir = windDir < 0.0 ? windDir+360.0 : windDir;
        windDir = windDir > 360.0 ? windDir-360.0 : windDir;
        
        parentSensor.getLogger().trace(String.format("press=%4.2f, temp=%5.2f, humid=%5.2f, wind speed=%5.2f, wind dir=%3.1f, rain=%5.1f", press, temp, humid, windSpeed, windDir, rain));
        
        DataBlock dataBlock;
    	if (latestRecord == null)
    	    dataBlock = dataStruct.createDataBlock();
    	else
    	    dataBlock = latestRecord.renew();
    	
    	dataBlock.setDoubleValue(0, time/1000);
    	dataBlock.setDoubleValue(1, Math.round(press*100.0)/100.0);
    	dataBlock.setDoubleValue(2, Math.round(temp*100.0)/100.0);
    	dataBlock.setDoubleValue(3, Math.round(humid*100.0)/100.0);
    	dataBlock.setDoubleValue(4, Math.round(rain));
    	dataBlock.setDoubleValue(5, Math.round(windSpeed*100.0)/100.0);
    	dataBlock.setDoubleValue(6, Math.round(windDir*100.0)/100.0);

    	// update latest record and send event
        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, SimWeatherStationOutput.this, dataBlock));
    }
    
    private double variation(double val, double ref, double dampingCoef, double noiseSigma)
    {
        return -dampingCoef*(val - ref) + noiseSigma*rand.nextGaussian();
    }
    
    protected void start()
    {
        if (timer != null)
            return;
        timer = new Timer();
        
        // start main measurement generation thread
        TimerTask task = new TimerTask() {
            public void run()
            {
                sendMeasurement();
            }            
        };
        
        timer.scheduleAtFixedRate(task, 0, (long)(getAverageSamplingPeriod()*1000));        
    }


    protected void stop()
    {
        if (timer != null)
        {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public double getAverageSamplingPeriod()
    {
    	// sample every 5 seconds
        return 15.0;
    }


    @Override
    public DataComponent getRecordDescription()
    {
        return dataStruct;
    }


    @Override
    public DataEncoding getRecommendedEncoding()
    {
        return dataEnc;
    }
}