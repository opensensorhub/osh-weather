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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.api.data.IMultiSourceDataProducer;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.sensor.nexrad.aws.sqs.RealtimeRadialProvider;
import org.sensorhub.impl.sensor.nexrad.ucar.ArchiveRadialProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.sensorML.SMLHelper;

import net.opengis.gml.v32.AbstractFeature;
import net.opengis.gml.v32.Point;
import net.opengis.gml.v32.impl.GMLFactory;
import net.opengis.sensorml.v20.AbstractProcess;
import net.opengis.sensorml.v20.PhysicalSystem;


/**
 * <p>
 * </p>
 *
 * @author Tony Cook <tony.coook@opensensorhub.org>
 */
public class NexradSensor extends AbstractSensorModule<NexradConfig> implements IMultiSourceDataProducer
{
	static final Logger logger = LoggerFactory.getLogger(NexradSensor.class);
	static final String SITE_UID_PREFIX = "urn:osh:sensor:nexrad:";

	NexradOutput dataInterface;
	RadialProvider radialProvider;  // either Realtime or archive AWS source
	boolean isRealtime;
	
	Set<String> foiIDs;
	Map<String, PhysicalSystem> siteFois;
	Map<String, PhysicalSystem> siteDescs;


	public NexradSensor() throws SensorHubException
	{
		this.foiIDs = new LinkedHashSet<String>();
		this.siteFois = new LinkedHashMap<String, PhysicalSystem>();
		this.siteDescs = new LinkedHashMap<String, PhysicalSystem>();
	}

	public void setQueueActive() throws IOException {
		if(isRealtime)
			((RealtimeRadialProvider)radialProvider).setQueueActive();
	}
	
	public void setQueueIdle() {
		if(isRealtime)
			((RealtimeRadialProvider)radialProvider).setQueueIdle();
	}

	
	@Override
	public void init() throws SensorHubException
	{
	    super.init();
        
        // generate IDs
        this.uniqueID = SITE_UID_PREFIX + "network";
        this.xmlID = "NEXRAD_NETWORK";
        
	    if(config.archiveStartTime != null && config.archiveStopTime != null) {
			radialProvider = new ArchiveRadialProvider(config);
			isRealtime = false;
		} else {
			radialProvider = new RealtimeRadialProvider(config);
			isRealtime = true;
		}
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
			sensorDescription.setDescription("Sensor supporting Level II Nexrad data");

			// append href to all stations composing the network
			for (String siteId: config.siteIds)
			{
				String name = "site_" + siteId;
				String href = SITE_UID_PREFIX + siteId;
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
			String uid = SITE_UID_PREFIX + siteId;
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

 		dataInterface.start(radialProvider); 
	}


	@Override
	public void stop() throws SensorHubException
	{
		dataInterface.stop();
		if(isRealtime)
			((RealtimeRadialProvider)radialProvider).stop();
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

    @Override
    public Collection<String> getEntitiesWithFoi(String foiID)
    {
        // FOI ID same as entity ID
        return Arrays.asList(foiID);
    }
}
