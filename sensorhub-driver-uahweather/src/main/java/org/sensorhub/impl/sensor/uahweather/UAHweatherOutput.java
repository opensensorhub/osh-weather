package org.sensorhub.impl.sensor.uahweather;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
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
        dataStruct = fac.newDataRecord(2);
        dataStruct.setName(getName());
        
        // build SWE Common record structure
        dataStruct.setDefinition("http://sensorml.com/ont/swe/property/Weather");
        dataStruct.setDescription("Weather Station Data");
        
        /************************* Add appropriate data fields *******************************************************************************/
        // add time, average, and instantaneous radiation exposure levels
        dataStruct.addComponent("time", fac.newTimeStampIsoUTC());
        dataStruct.addComponent("pressure", fac.newQuantity(SWEHelper.getPropertyUri("AtmosphericPressure"), "Air Pressure", null, "inHg"));
        //dataStruct.addComponent("DoseRateInst", fac.newQuantity(SWEHelper.getPropertyUri("DoseRate"), "Dose Instant", null, "uR"));
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
    
    
    protected void sendOutput(long msgTime, double airPres)
    {
    	DataBlock dataBlock = dataStruct.createDataBlock();
    	dataBlock.setLongValue(0, msgTime);
    	dataBlock.setDoubleValue(1, airPres);
    	//dataBlock.setDoubleValue(2, temp);
    	
    	// update latest record and send event
        latestRecord = dataBlock;
        latestRecordTime = System.currentTimeMillis();
        eventHandler.publishEvent(new SensorDataEvent(latestRecordTime, UAHweatherOutput.this, dataBlock));
    	
    }
}
