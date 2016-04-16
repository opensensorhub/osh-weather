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

import java.nio.file.Paths;

import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.aws.nexrad.NexradSqsService;
import org.sensorhub.impl.sensor.AbstractSensorModule;


/**
 * <p>
 * </p>
 *
 * @author Alex Robin <alex.robin@sensiasoftware.com>
 * @since Nov 2, 2014
 */
public class NexradSensor extends AbstractSensorModule<NexradConfig>
{
    NexradOutput dataInterface;
//	ICommProvider<? super CommConfig> commProvider;
    LdmFilesProvider ldmFilesProvider;
	private NexradSqsService nexradSqs;

	@Override
    public void init(NexradConfig config) throws SensorHubException
    {
    	super.init(config);
    	
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
            sensorDescription.setUniqueIdentifier("urn:test:sensors:nexrad");
            sensorDescription.setDescription("Sensor supporting Level II Nexrad data");
        }
    }


    @Override
    public void start() throws SensorHubException
    {
		nexradSqs = new NexradSqsService(config.siteIds.get(0));
    	nexradSqs.start();
        
        // start measurement stream
    	//siteFolder = Paths.get(rootFolder, siteIds.get(0));
    	ldmFilesProvider = new LdmFilesProvider(Paths.get(config.rootFolder, config.siteIds.get(0)));
    	ldmFilesProvider.start();
        dataInterface.start(ldmFilesProvider); 
    }
    

    @Override
    public void stop() throws SensorHubException
    {
    	// stop watching the dir
        dataInterface.stop();
        // delete the Amazaon Queue or it will keep collecting messages
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
}
