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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.sensor.SensorConfig;


public class NexradConfig extends SensorConfig
{
    /**
     * List of station IDs to get data for
     */
    @DisplayInfo(label="Station IDs", desc="List of station IDs to get data for")
    public List<String> siteIds = new ArrayList<String>();
    
	@DisplayInfo(desc="Path to incoming Nexrad Files")
    public String rootFolder;
	public Path siteFolder;

	public NexradSite site;
	
	public void getSite() {
		try {
			site = NexradTable.getInstance().getSite(siteIds.get(0));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
