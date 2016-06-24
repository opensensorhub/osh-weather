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
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import net.opengis.gml.v32.AbstractFeature;
import net.opengis.gml.v32.Point;
import net.opengis.gml.v32.impl.GMLFactory;
import net.opengis.sensorml.v20.AbstractProcess;
import net.opengis.sensorml.v20.PhysicalSystem;

import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.data.IMultiSourceDataProducer;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.sensor.nexrad.aws.NexradSqsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.sensorML.SMLHelper;


/**
 * <p>
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Nov 2, 2014
 */
public class NexradSensor extends AbstractSensorModule<NexradConfig> implements IMultiSourceDataProducer
{
	static final Logger logger = LoggerFactory.getLogger(NexradSensor.class);
	static final String SITE_UID_PREFIX = "urn:test:sensors:weather:nexrad";

	NexradOutput dataInterface;
	//	ICommProvider<? super CommConfig> commProvider;
	LdmFilesProvider ldmFilesProvider;
	private NexradSqsService nexradSqs;

	Set<String> foiIDs;
	Map<String, PhysicalSystem> siteFois;
	Map<String, PhysicalSystem> siteDescs;

	long queueIdleTime;
	boolean queueActive = false;
	static final long QUEUE_IDLE_TIME_THRESHOLD = TimeUnit.MINUTES.toMillis(20);
	static final long QUEUE_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(1);

	public NexradSensor()
	{
		this.foiIDs = new LinkedHashSet<String>();
		this.siteFois = new LinkedHashMap<String, PhysicalSystem>();
		this.siteDescs = new LinkedHashMap<String, PhysicalSystem>();
	}

	public void setQueueActive() {
		if(!queueActive) {
			nexradSqs = new NexradSqsService(config.siteIds, config.rootFolder, config.numThreads);
			nexradSqs.start();
			queueActive = true;
		} 
	}
	
	public void setQueueIdle() {
		if(!queueActive)
			return;
		queueIdleTime = System.currentTimeMillis();
	}

	class CheckQueueStatus extends TimerTask {

		@Override
		public void run() {
			logger.debug("Check queue.  QueueActive = {}" , queueActive);
			if(!queueActive)
				return;
			if(System.currentTimeMillis() - queueIdleTime > QUEUE_IDLE_TIME_THRESHOLD) {
				logger.debug("Check Queue. Stopping unused queue... ");
				nexradSqs.stop();
				queueActive = false;
			}
		}
		
	}

	@Override
	public void init(NexradConfig config) throws SensorHubException
	{
		super.init(config);
		queueIdleTime = System.currentTimeMillis();
		setQueueActive();
		
		Timer queueTimer = new Timer();  //At this line a new Thread will be created
	    queueTimer.scheduleAtFixedRate(new CheckQueueStatus(), 0, QUEUE_CHECK_INTERVAL); //delay in milliseconds

		dataInterface = new NexradOutput(this);
		addOutput(dataInterface, false);
		dataInterface.init();
	}


	@Override
	protected void updateSensorDescription()
	{
		synchronized (sensorDescription)
		{
			super.updateSensorDescription();
			sensorDescription.setId("NEXRAD_SENSOR");
			sensorDescription.setUniqueIdentifier(SITE_UID_PREFIX); // + config.siteIds.get(0));
			sensorDescription.setDescription("Sensor supporting Level II Nexrad data");


			// append href to all stations composing the network
			for (String siteId: config.siteIds)
			{
				String name = "site_" + siteId;
				String href = SITE_UID_PREFIX + "_" + siteId;
				((PhysicalSystem)sensorDescription).getComponentList().add(name, href, null);
			}
		}
	}


	@Override
	public void start() throws SensorHubException
	{
		SMLHelper smlFac = new SMLHelper();
		GMLFactory gmlFac = new GMLFactory(true);

		// generate station FOIs and full descriptions
		for (String siteId: config.siteIds)
		{
			String uid = SITE_UID_PREFIX  + siteId;
			String name = siteId;
			String description = "Nexrad site " + siteId;

			// generate small SensorML for FOI (in this case the system is the FOI)
			PhysicalSystem foi = smlFac.newPhysicalSystem();
			foi.setId(siteId);
			foi.setUniqueIdentifier(uid);
			foi.setName(name);
			foi.setDescription(description);
			Point stationLoc = gmlFac.newPoint();
			NexradSite site = config.getSite(siteId);
			stationLoc.setPos(new double [] {site.lat, site.lon, site.elevation});
			foi.setLocation(stationLoc);
			siteFois.put(uid, foi);
			foiIDs.add(uid);

			// TODO generate full SensorML for sensor description
			PhysicalSystem sensorDesc = smlFac.newPhysicalSystem();
			sensorDesc.setId("SITE_" + siteId);
			sensorDesc.setUniqueIdentifier(uid);
			sensorDesc.setName(name);
			sensorDesc.setDescription(description);
			siteDescs.put(uid, sensorDesc);


		}

		// start receiving files- need to wire this into the requests somehow to turn on and off sites
		try {
			ldmFilesProvider = new LdmFilesProvider(Paths.get(config.rootFolder, config.siteIds.get(0)));
		} catch (IOException e) {
			throw new SensorHubException(e.getMessage(), e);
		}
		ldmFilesProvider.start();
		dataInterface.start(ldmFilesProvider); 



		//		nexradSqs = new NexradSqsService(config.siteIds.get(0));
		//		nexradSqs.start();
		//
		//		// start measurement stream
		//		ldmFilesProvider = new LdmFilesProvider(Paths.get(config.rootFolder, config.siteIds.get(0)));
		//		ldmFilesProvider.start();
		//		dataInterface.start(ldmFilesProvider); 


	}


	@Override
	public void stop() throws SensorHubException
	{
		// stop watching the dir
		dataInterface.stop();
		// delete the Amazaon Queue or it will keep collecting messages
		if(queueActive)
			nexradSqs.stop();  
	}


	@Override
	public void cleanup() throws SensorHubException
	{

	}


	@Override
	public boolean isConnected()
	{
		return true;
	}


	@Override
	public Collection<String> getEntityIDs() {
		return Collections.unmodifiableCollection(siteFois.keySet());
	}


	@Override
	public AbstractProcess getCurrentDescription(String entityID) {
		return siteDescs.get(entityID);
	}


	@Override
	public double getLastDescriptionUpdate(String entityID) {
		return 0;
	}


	@Override
	public AbstractFeature getCurrentFeatureOfInterest(String entityID) {
		return siteFois.get(entityID);
	}


	@Override
	public Collection<? extends AbstractFeature> getFeaturesOfInterest() {
		return Collections.unmodifiableCollection(siteFois.values());
	}


	@Override
	public Collection<String> getFeaturesOfInterestIDs() {
		return Collections.unmodifiableCollection(foiIDs);
	}
}
