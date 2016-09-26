package org.sensorhub.impl.sensor.uahweather;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.Quantity;

import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.vast.swe.SWEHelper;

public class UAHweatherOutput extends AbstractSensorOutput<UAHweatherSensor>
{
    DataComponent dataStruct;
    DataEncoding dataEnc;
    
    public UAHweatherOutput(UAHweatherSensor parentSensor)
    {
        super(parentSensor);
    }


    @Override
    public String getName()
    {
        return "UAH Weather";
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
        dataStruct.addComponent("windSpeed", fac.newQuantity(SWEHelper.getPropertyUri("WindSpeed"), "Wind Speed", null, "m/s"));
        Quantity q = fac.newQuantity(SWEHelper.getPropertyUri("WindDirection"), "Wind Direction", null, "adc");
        q.setReferenceFrame("http://sensorml.com/ont/swe/property/NED");
        q.setAxisID("z");
        dataStruct.addComponent("windDir", q);
        dataStruct.addComponent("rainAccum", fac.newQuantity(SWEHelper.getPropertyUri("RainAccumulation"), "Rain Accumulation", null, "mm"));
        /*************************************************************************************************************************************/
        
        // also generate encoding definition
        dataEnc = fac.newTextEncoding(",", "\n");
    }


    @Override
    public double getAverageSamplingPeriod()
    {
    	// sample every 1 second
        return 1.0;
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
    
    
    protected void sendOutput(long msgTime, double airPres, float airTemp, float humid, float windSpeed, int windDir, byte rainCnt)
    {
    	DataBlock dataBlock = dataStruct.createDataBlock();
    	dataBlock.setLongValue(0, msgTime);
    	dataBlock.setDoubleValue(1, airPres);
    	dataBlock.setFloatValue(2, airTemp);
    	dataBlock.setFloatValue(3, humid);
    	dataBlock.setFloatValue(4, windSpeed);
    	dataBlock.setIntValue(5, windDir);
    	dataBlock.setByteValue(6, rainCnt);
    	
    	
    	// update latest record and send event
        latestRecord = dataBlock;
        latestRecordTime = msgTime;
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, UAHweatherOutput.this, dataBlock));
    	
    }
}
