package org.sensorhub.impl.sensor.nexrad.aws.sqs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.sensor.nexrad.NexradConfig;
import org.sensorhub.impl.sensor.nexrad.NexradSensor;
import org.sensorhub.impl.sensor.nexrad.RadialProvider;
import org.sensorhub.impl.sensor.nexrad.aws.LdmLevel2Reader;
import org.sensorhub.impl.sensor.nexrad.aws.LdmRadial;
import org.sensorhub.impl.sensor.nexrad.aws.NexradSqsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Title: RealtimeRadialProvider.java</p>
 * <p>Description: </p>
 *
 * @author tcook
 * @date Sep 20, 2016
 */
public class RealtimeRadialProvider implements RadialProvider {
	Map<String, ChunkPathQueue> queueMap;
	NexradSensor sensor;
	NexradSqsService nexradSqsService;
	NexradConfig config;
	static final Logger logger = LoggerFactory.getLogger(RealtimeRadialProvider.class);
//	boolean sendRadials = true;
//	private NexradSqsService nexradSqs;
//	long queueIdleTime;
//	static final long QUEUE_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(1);
//	long queueIdleTimeMillis;
//	boolean queueActive = false;

	public RealtimeRadialProvider(NexradSqsService nexradSqsService, NexradConfig config) throws SensorHubException {
		this.config = config;
		this.nexradSqsService = nexradSqsService;
		initQueueMap();
	}

	public void initQueueMap() throws SensorHubException {
		try {
			queueMap = new HashMap<>();
			Path rootPath = Paths.get(config.rootFolder); 
			if(!Files.isDirectory(rootPath))
				throw new SensorHubException("Configured rootFolder does not exist or is not a directory" + config.rootFolder);
			
			for(String site: config.siteIds) {
				ChunkPathQueue queue = new ChunkPathQueue(Paths.get(config.rootFolder, site));
				queueMap.put(site, queue);
				nexradSqsService.setChunkQueue(queue);  // 
				queue.setS3client(nexradSqsService.getS3client());  //

//				queue.setQueueIdleTimeMillis(TimeUnit.MINUTES.toMillis(config.queueIdleTimeMinutes));
//				queue.setQueueIdleTime(System.currentTimeMillis());

//				try {
//					setQueueActive();
//				} catch (IOException e) {
//					throw new SensorHubException(e.getMessage());
//				}
//
			}
		} catch (IOException e) {
			throw new SensorHubException(e.getMessage(), e);
		}

//		Timer queueTimer = new Timer();  //
//		queueTimer.scheduleAtFixedRate(new CheckQueueStatus(), 0, QUEUE_CHECK_INTERVAL); //delay in milliseconds
	}

	
//	public void initChunkPathQueue() throws SensorHubException {
//		try {
//			Path rootPath = Paths.get(config.rootFolder); 
//			if(!Files.isDirectory(rootPath))
//				throw new SensorHubException("Configured rootFolder does not exist or is not a directory" + config.rootFolder);
//			chunkQueue = new ChunkPathQueue(Paths.get(config.rootFolder, config.siteIds.get(0)));
////			chunkQueue = new ChunkPathQueue(config.rootFolder, config.siteIds);
//		} catch (IOException e) {\
//			throw new SensorHubException(e.getMessage(), e);
//		}
//
//		logger.debug("QueueIdleTimeMinutes: {}", config.queueIdleTimeMinutes);
//		queueIdleTimeMillis = TimeUnit.MINUTES.toMillis(config.queueIdleTimeMinutes);
//		queueIdleTime = System.currentTimeMillis();
//		try {
//			setQueueActive();
//		} catch (IOException e) {
//			throw new SensorHubException(e.getMessage());
//		}
//
//		Timer queueTimer = new Timer();  //At this line a new Thread will be created
//		queueTimer.scheduleAtFixedRate(new CheckQueueStatus(), 0, QUEUE_CHECK_INTERVAL); //delay in milliseconds
//	}

//	public void setQueueActive() throws IOException {
//		if(!queueActive) {
//			nexradSqs = new NexradSqsService(config.queueName, config.siteIds);
//			nexradSqs.setNumThreads(config.numThreads);
//			// design issue here in that nexradSqs needs chunkQueue and chunkQueue needs s3client.  
//			nexradSqs.setChunkQueue(chunkQueue);  // 
//			chunkQueue.setS3client(nexradSqs.getS3client());  //
//			nexradSqs.start();
//			queueActive = true;
//		} 
//	}
//
//	public void setQueueIdle() {
//		if(!queueActive)
//			return;
//		queueIdleTime = System.currentTimeMillis();
//	}
//
//	class CheckQueueStatus extends TimerTask {
//
//		@Override
//		public void run() {
//			logger.debug("Check queue.  QueueActive = {}" , queueActive);
//			if(!queueActive)
//				return;
//			if(System.currentTimeMillis() - queueIdleTime > queueIdleTimeMillis) {
//				logger.debug("Check Queue. Stopping unused queue... ");
//				nexradSqs.stop();
//				queueActive = false;
//			}
//		}
//
//	}
//
//	public void stop() {
//		// delete the Amazaon Queue or it will keep collecting messages
//		if(queueActive)
//			nexradSqs.stop();  
//	}

	/* (non-Javadoc)
	 * @see org.sensorhub.impl.sensor.nexrad.RadialProvider#getNextRadial()
	 */
	@Override
	public LdmRadial getNextRadial() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.sensorhub.impl.sensor.nexrad.RadialProvider#getNextRadials()
	 */
	@Override
	public List<LdmRadial> getNextRadials() throws IOException {
		ChunkPathQueue chunkQueue = queueMap.get(config.siteIds.get(0));
		try {
			Path p = chunkQueue.nextFile();
			logger.debug("Reading File {}" , p.toString());
			LdmLevel2Reader reader = new LdmLevel2Reader();
			List<LdmRadial> radials = reader.read(p.toFile());
//			List<LdmRadial> radials = new ArrayList<>();
			return radials;
		} catch (IOException e) {
			e.printStackTrace(System.err);
			logger.error(e.getMessage());
			return null;
		}
	}

}
