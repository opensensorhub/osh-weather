/***************************** BEGIN LICENSE BLOCK ***************************

The contents of this file are subject to the Mozilla Public License, v. 2.0.
If a copy of the MPL was not distributed with this file, You can obtain one
at http://mozilla.org/MPL/2.0/.

Software distributed under the License is distributed on an "AS IS" basis,
WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
for the specific language governing rights and limitations under the License.

Copyright (C) 2012-2015 Sensia Software LLC. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.impl.sensor.nexrad;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Timer;

import net.opengis.swe.v20.Count;
import net.opengis.swe.v20.DataArray;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataType;
import net.opengis.swe.v20.Quantity;
import net.opengis.swe.v20.Time;

import org.sensorhub.api.common.IEventListener;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.impl.common.BasicEventHandler;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.nexrad.aws.LdmLevel2Reader;
import org.sensorhub.impl.sensor.nexrad.aws.LdmRadial;
import org.sensorhub.impl.sensor.nexrad.aws.MomentDataBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.DataBlockMixed;
import org.vast.data.DataRecordImpl;
import org.vast.data.QuantityImpl;
import org.vast.data.SWEFactory;
import org.vast.data.TimeImpl;
import org.vast.swe.SWEConstants;
import org.vast.swe.SWEHelper;


public class NexradOutput extends AbstractSensorOutput<NexradSensor>
{
	private static final Logger log = LoggerFactory.getLogger(NexradOutput.class);
	DataComponent nexradStruct;
	DataBlock latestRecord;
	DataEncoding encoding;
	boolean sendData;
	Timer timer;	
//	static int NUM_BINS = 720;  // this should be fixed at construction time as part of the config
	LdmFilesProvider ldmFilesProvider;
	InputStream is;
	int numListeners;
	NexradSensor nexradSensor;
	
	public NexradOutput(NexradSensor parentSensor)
	{
		super(parentSensor);
		nexradSensor = parentSensor;
	}


	@Override
	public String getName()
	{
		return "NexradData";
	}


	protected void init()
	{
		//  Add Location only as ouptut- Alex is adding support for this
//		SweHelper.newLocationVectorLLa(...);
		SWEFactory fac = new SWEFactory();
		SWEHelper helper = new SWEHelper();

		// SWE Common data structure
		nexradStruct = new DataRecordImpl(5);
		nexradStruct.setName(getName());
		nexradStruct.setDefinition("http://sensorml.com/ont/swe/propertyx/NexradRadial");

		// stationName
//		nexradStruct.addField("site", helper.newText("http://sensorml.com/ont/swe/property/StationID", "Site ID", null));
//		nexradStruct.addComponent("siteId", fac.newText());
		
		//  Time,el,az,data[]
		Time time = new TimeImpl();
		time.getUom().setHref(Time.ISO_TIME_UNIT);
		time.setDefinition(SWEConstants.DEF_SAMPLING_TIME);
		nexradStruct.addComponent("time", time);

		Quantity el;
		el = new QuantityImpl();
		el.getUom().setCode("deg");
		el.setDefinition("http://sensorml.com/ont/swe/property/ElevationAngle");
		nexradStruct.addComponent("elevation",el);

		Quantity az = new QuantityImpl();
		az.getUom().setCode("deg");
		az.setDefinition("http://sensorml.com/ont/swe/property/AzimuthAngle");
		nexradStruct.addComponent("azimuth",az);

		Count numBins = fac.newCount(DataType.INT);
		numBins.setDefinition("http://sensorml.com/ont/swe/property/NumberOfSamples"); 
		numBins.setId("NUM_BINS");
//		numBins.setValue(NUM_BINS);  
		nexradStruct.addComponent("count",numBins);

		Quantity reflQuant = fac.newQuantity(DataType.FLOAT);
		reflQuant.setDefinition("http://sensorml.com/ont/swe/propertyx/Reflectivity");  // does not exist- will be reflectivity,velocity,or spectrumWidth- choice here?
		reflQuant.getUom().setCode("db");
		DataArray reflData = fac.newDataArray();
		reflData.setElementType("Reflectivity", reflQuant);
		reflData.setElementCount(numBins); //  alex adding support for this
		nexradStruct.addComponent("Reflectivity", reflData);

		Quantity velQuant = fac.newQuantity(DataType.FLOAT);
		velQuant.setDefinition("http://sensorml.com/ont/swe/propertyx/Velocity");  // does not exist- will be reflectivity,velocity,or spectrumWidth- choice here?
		velQuant.getUom().setCode("m/s");
		DataArray velData = fac.newDataArray();
		velData.setElementType("Velocity", velQuant);
		velData.setElementCount(numBins); 
		nexradStruct.addComponent("Velocity", velData);

		Quantity swQuant = fac.newQuantity(DataType.FLOAT);
		swQuant.setDefinition("http://sensorml.com/ont/swe/propertyx/SpectrumWidth");  // does not exist- will be reflectivity,velocity,or spectrumWidth- choice here?
		swQuant.getUom().setCode("1"); // ? or db
		DataArray swData = fac.newDataArray();
		swData.setElementType("SpectrumWidth", swQuant);
		swData.setElementCount(numBins); 
		nexradStruct.addComponent("SpectrumWidth", swData);

//		encoding = SWEHelper.getDefaultBinaryEncoding(nexradStruct);
		encoding = fac.newTextEncoding();
	}
	

    protected void start(LdmFilesProvider provider)
    {
        if (sendData)
            return;

        sendData = true;

        ldmFilesProvider = provider;
        
        // start main measurement thread
        Thread t = new Thread(new Runnable()
        {
            public void run()
            {
                while (sendData)
                {
                    try {
                    	Path p = ldmFilesProvider.nextFile();
                    	log.debug("Reading {}" , p.toString());
                    	System.err.println("Reading " + p.toString());
                    	LdmLevel2Reader reader = new LdmLevel2Reader();
                    	List<LdmRadial> radials = reader.read(p.toFile());
                    	if(radials == null) {
                    		continue;
                    	}
                    	sendRadials(radials);
					} catch (IOException e) {
						e.printStackTrace(System.err);
						log.error(e.getMessage());
						continue;
					}
                }
            }
        });
        t.start();
    }

	private void sendRadials(List<LdmRadial> radials) throws IOException
	{
		for(LdmRadial radial: radials) {
//			// build and publish datablock
			DataArray refArr = (DataArray)nexradStruct.getComponent(4);
			DataArray velArr = (DataArray)nexradStruct.getComponent(5);
			DataArray swArr = (DataArray)nexradStruct.getComponent(6);
//
//			
			MomentDataBlock momentData = radial.momentData.get(0);
			refArr.updateSize(momentData.numGates);
			velArr.updateSize(momentData.numGates);
			swArr.updateSize(momentData.numGates);
			DataBlock nexradBlock = nexradStruct.createDataBlock();
			
			long days = radial.dataHeader.daysSince1970;
			long ms = radial.dataHeader.msSinceMidnight;
			double utcTime = (double)(radial.toJulianTime(days, ms)/1000.);

			
//			nexradBlock.setStringValue(0, radial.dataHeader.siteId);
			nexradBlock.setDoubleValue(0, utcTime);
			nexradBlock.setDoubleValue(1, radial.dataHeader.elevationAngle);
			nexradBlock.setDoubleValue(2, radial.dataHeader.azimuthAngle);
			
			nexradBlock.setIntValue(3, momentData.numGates);
			
			int blockCnt = 0;
			for(MomentDataBlock data: radial.momentData) {
				int blockIdx;
				switch(data.blockName) {
				case "REF":
					((DataBlockMixed)nexradBlock).getUnderlyingObject()[4].setUnderlyingObject(data.getData());
					blockCnt++;
					break;
				case "VEL":
					((DataBlockMixed)nexradBlock).getUnderlyingObject()[5].setUnderlyingObject(data.getData());
					blockCnt++;
					break;
				case "SW":
					((DataBlockMixed)nexradBlock).getUnderlyingObject()[6].setUnderlyingObject(data.getData());
					blockCnt++;
					break;
				default:
					// PHI/RHO/ZDR - may support these later
					break;
				}
				if(blockCnt == 3)  break;
			}
			if(blockCnt < 3) {
				//  we're missing a product, but doesn't break the publishing at least
			}

			latestRecord = nexradBlock;
			eventHandler.publishEvent(new SensorDataEvent(1, NexradOutput.this, nexradBlock));
		}
	}
    
//	private void sendRadial() throws IOException
//	{
//		//  What will really be happening. We will be getting one full sweep every 5 to 6 minutes, and then a pause
//		//  So need to sim this somehow
//		String testFile = "C:/Data/sensorhub/Level2/HTX/KHTX20110427_205716_V03";
//		Level2Reader reader = new Level2Reader();
//		Sweep sweep = reader.readSweep(testFile, Level2Product.REFLECTIVITY);
//
//		// build and publish datablock
//		DataBlock dataBlock = nexradStruct.createDataBlock();
//		Radial first = sweep.getRadials().get(0);
//		long time = (long)first.radialStartTime / 1000;
//		dataBlock.setLongValue(0, time);
//		dataBlock.setDoubleValue(1, first.elevation);
//		dataBlock.setDoubleValue(2, first.azimuth);
//		dataBlock.setIntValue(first.numGates);
//		dataBlock.setUnderlyingObject(first.dataFloat);
//
//		//        latestRecord = dataBlock;
//		eventHandler.publishEvent(new SensorDataEvent(1, NexradOutput.this, dataBlock));
//	}

//
//	protected void startFile()
//	{
//		if (timer != null)
//			return;
//		timer = new Timer();
//
//		// start main measurement generation thread
//		TimerTask task = new TimerTask() {
//			public void run()
//			{
//				try {
//					sendRadial();
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			}            
//		};
//
//		timer.scheduleAtFixedRate(task, 0, (long)(getAverageSamplingPeriod()*1000));        
//	}


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
		return 1.0;
	}


	@Override
	public DataComponent getRecordDescription()
	{
		return nexradStruct;
	}


	@Override
	public DataEncoding getRecommendedEncoding()
	{
		return encoding;
	}


	@Override
	public DataBlock getLatestRecord()
	{
		((BasicEventHandler)eventHandler).getNumListeners();
		nexradSensor.latestRecordRequested();
		//  if queue was not active and has to be created, we cannot return the record yet!
		return latestRecord;
	}


	@Override
	public long getLatestRecordTime()
	{
		if (latestRecord != null)
			return latestRecord.getLongValue(0);

		return 0;
	}

    @Override
    public void registerListener(IEventListener listener)
    {
        super.registerListener(listener);
        numListeners++;
        System.err.println("Reg:NumListeners is " + numListeners);
        if(numListeners == 1)
    		nexradSensor.activateQueue();
    }


    @Override
    public void unregisterListener(IEventListener listener)
    {
        super.unregisterListener(listener);
        numListeners--;
        System.err.println("UnReg:NumListeners is " + numListeners);
        if(numListeners == 0)
        	nexradSensor.setQueueIdle();
    }
}
