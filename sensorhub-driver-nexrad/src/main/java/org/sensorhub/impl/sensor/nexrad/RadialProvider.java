package org.sensorhub.impl.sensor.nexrad;

import org.sensorhub.impl.sensor.nexrad.aws.LdmRadial;

/**
 * <p>Title: RadialProvider.java</p>
 * <p>Description: </p>
 *
 * @author tcook
 * @date Sep 14, 2016
 * 
 * Probably won't go this route
 * 
 */

public interface RadialProvider {
	
	public LdmRadial getNextRadial();
	
}
