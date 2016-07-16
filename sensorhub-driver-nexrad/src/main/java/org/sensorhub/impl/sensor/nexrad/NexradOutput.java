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
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import net.opengis.swe.v20.Count;
import net.opengis.swe.v20.DataArray;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataType;
import net.opengis.swe.v20.Quantity;
import net.opengis.swe.v20.Time;

import org.joda.time.DateTime;
import org.sensorhub.api.sensor.SensorDataEvent;
import org.sensorhub.impl.common.BasicEventHandler;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.sensor.nexrad.aws.AwsNexradUtil;
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

/**
 * 
 * <p>Title: NexradOutput.java</p>
 * <p>Description: </p>
 *
 * @author Tony Cook
 * @date Jul 15, 2016
 * 
 *  TODO - verify that rangeToCenterOfFirstGate and gateSize are constants; how do we specify UOM for a count
 */

public class NexradOutput extends AbstractSensorOutput<NexradSensor>
{
	private static final Logger logger = LoggerFactory.getLogger(NexradOutput.class);
	DataComponent nexradStruct;
	DataBlock latestRecord;
	DataEncoding encoding;
	boolean sendData;
	Timer timer;	
	LdmFilesProvider ldmFilesProvider;
	InputStream is;
	int numListeners;
	NexradSensor nexradSensor;
	//  Listener Check needed to know if anyone is receiving events to know when to delete the AWS queue
	static final long LISTENER_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(1); 


	public NexradOutput(NexradSensor parentSensor)
	{
		super(parentSensor);
		nexradSensor = parentSensor;
		Timer queueTimer = new Timer();  
		queueTimer.scheduleAtFixedRate(new CheckNumListeners(), 0, LISTENER_CHECK_INTERVAL); //delay in milliseconds
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
		nexradStruct = new DataRecordImpl(8);  // was 6 before I added siteId and it still worked?
		nexradStruct.setName(getName());
		nexradStruct.setDefinition("http://sensorml.com/ont/swe/propertyx/NexradRadial");

		//  0
		Time time = new TimeImpl();
		time.getUom().setHref(Time.ISO_TIME_UNIT);
		time.setDefinition(SWEConstants.DEF_SAMPLING_TIME);
		nexradStruct.addComponent("time", time);

		// 88D site identifier (1)
		nexradStruct.addComponent("siteId", fac.newText());

		// 2
		Quantity el = new QuantityImpl();
		el.getUom().setCode("deg");
		el.setDefinition("http://sensorml.com/ont/swe/property/ElevationAngle");
		nexradStruct.addComponent("elevation",el);

		// 3
		Quantity az = new QuantityImpl();
		az.getUom().setCode("deg");
		az.setDefinition("http://sensorml.com/ont/swe/property/AzimuthAngle");
		nexradStruct.addComponent("azimuth",az);

		// 4
		Quantity rangeToCenterOfFirstRefGate = new QuantityImpl(DataType.SHORT);
		rangeToCenterOfFirstRefGate.setDefinition("http://sensorml.com/ont/swe/property/Range.html");
		rangeToCenterOfFirstRefGate.getUom().setCode("m");
		nexradStruct.addComponent("rangeToCenterOfFirstRefGate", rangeToCenterOfFirstRefGate);
		
		// 5
		Quantity refGateSize = new QuantityImpl(DataType.SHORT);
		refGateSize.setDefinition("http://sensorml.com/ont/swe/property/RangeSampleSpacing.html"); 
		refGateSize.getUom().setCode("m");
		nexradStruct.addComponent("refGateSize", refGateSize);

		// 6
		Count numRefGates = fac.newCount(DataType.INT);
		numRefGates.setDefinition("http://sensorml.com/ont/swe/property/NumberOfSamples"); 
		numRefGates.setId("NUM_REF_GATES");
		nexradStruct.addComponent("numRefGates",numRefGates);

		// 7
		Quantity rangeToCenterOfFirstVelGate = new QuantityImpl(DataType.SHORT);
		rangeToCenterOfFirstVelGate.setDefinition("http://sensorml.com/ont/swe/property/Range.html"); 
		rangeToCenterOfFirstVelGate.getUom().setCode("m");
		nexradStruct.addComponent("rangeToCenterOfFirstVelGate", rangeToCenterOfFirstVelGate);
		
		// 8
		Quantity velGateSize = new QuantityImpl(DataType.SHORT);
		velGateSize.setDefinition("http://sensorml.com/ont/swe/property/RangeSampleSpacing.html"); 
		velGateSize.getUom().setCode("m");
		nexradStruct.addComponent("velGateSize", velGateSize);

		// 9
		Count numVelGates = fac.newCount(DataType.INT);
		numVelGates.setDefinition("http://sensorml.com/ont/swe/property/NumberOfSamples"); 
		numVelGates.setId("NUM_VEL_GATES");
		nexradStruct.addComponent("numVelGates",numVelGates);

		// 10
		Quantity rangeToCenterOfFirstSwGate = new QuantityImpl(DataType.SHORT);
		rangeToCenterOfFirstSwGate.setDefinition("http://sensorml.com/ont/swe/property/Range.html"); 
		rangeToCenterOfFirstSwGate.getUom().setCode("m");
		nexradStruct.addComponent("rangeToCenterOfFirstSwGate", rangeToCenterOfFirstSwGate);
		
		// 11
		Quantity swGateSize = new QuantityImpl(DataType.SHORT);
		swGateSize.setDefinition("http://sensorml.com/ont/swe/property/RangeSampleSpacing.html"); 
		swGateSize.getUom().setCode("m");
		nexradStruct.addComponent("swGateSize", swGateSize);

		// 12
		Count numSwGates = fac.newCount(DataType.INT);
		numSwGates.setDefinition("http://sensorml.com/ont/swe/property/NumberOfSamples"); 
		numSwGates.setId("NUM_SW_GATES");
		nexradStruct.addComponent("numSwGates",numSwGates);

		// 13
		Quantity reflQuant = fac.newQuantity(DataType.FLOAT);
		reflQuant.setDefinition("http://sensorml.com/ont/swe/propertyx/Reflectivity");  // does not exist- will be reflectivity,velocity,or spectrumWidth- choice here?
		reflQuant.getUom().setCode("db");
		DataArray reflData = fac.newDataArray();
		reflData.setElementType("Reflectivity", reflQuant);
		reflData.setElementCount(numRefGates); //  alex adding support for this
		nexradStruct.addComponent("Reflectivity", reflData);

		// 14
		Quantity velQuant = fac.newQuantity(DataType.FLOAT);
		velQuant.setDefinition("http://sensorml.com/ont/swe/propertyx/Velocity");  // does not exist- will be reflectivity,velocity,or spectrumWidth- choice here?
		velQuant.getUom().setCode("m/s");
		DataArray velData = fac.newDataArray();
		velData.setElementType("Velocity", velQuant);
		//		velData.setElementCount(numVelBins); 
		velData.setElementCount(numVelGates); 
		nexradStruct.addComponent("Velocity", velData);

		// 15
		Quantity swQuant = fac.newQuantity(DataType.FLOAT);
		swQuant.setDefinition("http://sensorml.com/ont/swe/propertyx/SpectrumWidth");  // does not exist- will be reflectivity,velocity,or spectrumWidth- choice here?
		swQuant.getUom().setCode("1"); // ? or db
		DataArray swData = fac.newDataArray();
		swData.setElementType("SpectrumWidth", swQuant);
		swData.setElementCount(numSwGates); 
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
						logger.debug("Reading {}" , p.toString());
						LdmLevel2Reader reader = new LdmLevel2Reader();
						List<LdmRadial> radials = reader.read(p.toFile());
						if(radials == null) { 
							continue;
						}
						sendRadials(radials);
					} catch (IOException e) {
						e.printStackTrace(System.err);
						logger.error(e.getMessage());
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
			DataArray refArr = (DataArray)nexradStruct.getComponent(13);
			DataArray velArr = (DataArray)nexradStruct.getComponent(14);
			DataArray swArr = (DataArray)nexradStruct.getComponent(15);
			//
			//			
			MomentDataBlock refMomentData = radial.momentData.get("REF");
			MomentDataBlock velMomentData = radial.momentData.get("VEL");
			MomentDataBlock swMomentData = radial.momentData.get("SW");
			
			if(refMomentData == null) {
				refArr.updateSize(1);
			} else {
				refArr.updateSize(refMomentData.numGates);
			}

			if(velMomentData == null) {
				velArr.updateSize(1);
			} else {
				velArr.updateSize(velMomentData.numGates);
			}
			
			if(swMomentData == null) {
				swArr.updateSize(1);
			} else {
				swArr.updateSize(swMomentData.numGates);
			}
			DataBlock nexradBlock = nexradStruct.createDataBlock();

			long days = radial.dataHeader.daysSince1970;
			long ms = radial.dataHeader.msSinceMidnight;
			double utcTime = (double)(AwsNexradUtil.toJulianTime(days, ms)/1000.);
			nexradBlock.setDoubleValue(0, utcTime);
			nexradBlock.setStringValue(1, radial.dataHeader.siteId);
			nexradBlock.setDoubleValue(2, radial.dataHeader.elevationAngle);
			nexradBlock.setDoubleValue(3, radial.dataHeader.azimuthAngle);

			float [] f = new float[1];
			if(refMomentData != null) {
				nexradBlock.setShortValue(4, refMomentData.rangeToCenterOfFirstGate);
				nexradBlock.setShortValue(5, refMomentData.rangeSampleInterval);
				nexradBlock.setIntValue(6, refMomentData.numGates);
			} else {
				nexradBlock.setShortValue(4, (short)0);
				nexradBlock.setShortValue(5, (short)0);
				nexradBlock.setIntValue(6, 1);
			}
			if(velMomentData != null) {
				nexradBlock.setShortValue(7, velMomentData.rangeToCenterOfFirstGate);
				nexradBlock.setShortValue(8, velMomentData.rangeSampleInterval);
				nexradBlock.setIntValue(9, velMomentData.numGates);
			} else {
				nexradBlock.setShortValue(7, (short)0);
				nexradBlock.setShortValue(8, (short)0);
				nexradBlock.setIntValue(9, 1);
			}
			if(swMomentData != null) {
				nexradBlock.setShortValue(10, swMomentData.rangeToCenterOfFirstGate);
				nexradBlock.setShortValue(11, swMomentData.rangeSampleInterval);
				nexradBlock.setIntValue(12, swMomentData.numGates);
			} else {
				nexradBlock.setShortValue(10, (short)0);
				nexradBlock.setShortValue(11, (short)0);
				nexradBlock.setIntValue(12, 1);
			}
			
			if(refMomentData != null) {
				((DataBlockMixed)nexradBlock).getUnderlyingObject()[13].setUnderlyingObject(refMomentData.getData());
			} else {
				((DataBlockMixed)nexradBlock).getUnderlyingObject()[13].setUnderlyingObject(f);
			}
			if(velMomentData != null) {
				((DataBlockMixed)nexradBlock).getUnderlyingObject()[14].setUnderlyingObject(velMomentData.getData());
			} else {
				((DataBlockMixed)nexradBlock).getUnderlyingObject()[14].setUnderlyingObject(f);
			}

			if(swMomentData != null) {
				((DataBlockMixed)nexradBlock).getUnderlyingObject()[15].setUnderlyingObject(swMomentData.getData());
			} else {
				((DataBlockMixed)nexradBlock).getUnderlyingObject()[15].setUnderlyingObject(f);
			}

			//System.out.printf("r,v,s: %d,%d,%d\n", refMomentData.numGates, velMomentData.numGates, swMomentData.numGates);

			//			int blockCnt = 0;
			//			for(MomentDataBlock data: radial.momentData) {
			//				int blockIdx;
			//				System.out.println(data.blockName + ": " + data.getData().length);
			//				switch(data.blockName) {
			//				case "REF":
			//					((DataBlockMixed)nexradBlock).getUnderlyingObject()[7].setUnderlyingObject(data.getData());
			//					blockCnt++;
			//					break;
			//				case "VEL":
			//					((DataBlockMixed)nexradBlock).getUnderlyingObject()[8].setUnderlyingObject(data.getData());
			//					blockCnt++;
			//					break;
			//				case "SW":
			//					((DataBlockMixed)nexradBlock).getUnderlyingObject()[9].setUnderlyingObject(data.getData());
			//					blockCnt++;
			//					break;
			//				default:
			//					// PHI/RHO/ZDR - may support these later
			//					break;
			//				}
			//				if(blockCnt == 3)  break;
			//			}
			//			if(blockCnt < 3) {
			//				//  we're missing a product, but shouldn't break the publishing at least
			//			}

			latestRecord = nexradBlock;
			eventHandler.publishEvent(new SensorDataEvent((long)utcTime * 1000L, NexradOutput.this, nexradBlock));
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
		return latestRecord;
	}


	@Override
	public long getLatestRecordTime()
	{
		if (latestRecord != null) {
			long t = latestRecord.getLongValue(0);
			DateTime dt = new DateTime(t*1000);
			return latestRecord.getLongValue(0) * 1000;
		}

		return 0;
	}

	boolean noListeners = false;
	class CheckNumListeners extends TimerTask {
		@Override
		public void run() {
			int numListeners = ((BasicEventHandler)eventHandler).getNumListeners();
			logger.debug("CheckNumListeners = {}",numListeners);
			if (numListeners > 0) { 
				nexradSensor.setQueueActive();
				noListeners = true;
			}else {
				if(!noListeners) { 
					nexradSensor.setQueueIdle();
					noListeners = true;
				}

			}

		}

	}

}
