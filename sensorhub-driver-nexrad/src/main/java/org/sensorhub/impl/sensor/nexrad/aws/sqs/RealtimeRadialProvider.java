package org.sensorhub.impl.sensor.nexrad.aws.sqs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.sensor.nexrad.NexradConfig;
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

	ChunkPathQueue chunkQueue;
	NexradConfig config;
	static final Logger logger = LoggerFactory.getLogger(RealtimeRadialProvider.class);
	boolean sendRadials = true;
	private NexradSqsService nexradSqs;
	long queueIdleTime;
	boolean queueActive = false;
	static final long QUEUE_CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(1);
	long queueIdleTimeMillis;

	public RealtimeRadialProvider(NexradConfig config) throws SensorHubException {
		this.config = config;
		initChunkPathQueue();
	}

	public void initChunkPathQueue() throws SensorHubException {
		try {
			Path rootPath = Paths.get(config.rootFolder); 
			if(!Files.isDirectory(rootPath))
				throw new SensorHubException("Configured rootFolder does not exist or is not a directory" + config.rootFolder);
			chunkQueue = new ChunkPathQueue(Paths.get(config.rootFolder, config.siteIds.get(0)));
		} catch (IOException e) {
			throw new SensorHubException(e.getMessage(), e);
		}

		logger.debug("QueueIdleTimeMinutes: {}", config.queueIdleTimeMinutes);
		queueIdleTimeMillis = TimeUnit.MINUTES.toMillis(config.queueIdleTimeMinutes);
		queueIdleTime = System.currentTimeMillis();
		try {
			setQueueActive();
		} catch (IOException e) {
			throw new SensorHubException(e.getMessage());
		}

		Timer queueTimer = new Timer();  //At this line a new Thread will be created
		queueTimer.scheduleAtFixedRate(new CheckQueueStatus(), 0, QUEUE_CHECK_INTERVAL); //delay in milliseconds

		//		dataInterface = new NexradOutput(this);
		//		addOutput(dataInterface, false);
		//		dataInterface.init();		
	}

	public void setQueueActive() throws IOException {
		if(!queueActive) {
			nexradSqs = new NexradSqsService(config.queueName, config.siteIds);
			nexradSqs.setNumThreads(config.numThreads);
			// design issue here in that nexradSqs needs chunkQueue and chunkQueue needs s3client.  
			nexradSqs.setChunkQueue(chunkQueue);  // 
			chunkQueue.setS3client(nexradSqs.getS3client());  //
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
			if(System.currentTimeMillis() - queueIdleTime > queueIdleTimeMillis) {
				logger.debug("Check Queue. Stopping unused queue... ");
				nexradSqs.stop();
				queueActive = false;
			}
		}

	}

	public void stop() {
		// delete the Amazaon Queue or it will keep collecting messages
		if(queueActive)
			nexradSqs.stop();  
	}

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
		try {
			Path p = chunkQueue.nextFile();
			logger.debug("Reading {}" , p.toString());
			LdmLevel2Reader reader = new LdmLevel2Reader();
			List<LdmRadial> radials = reader.read(p.toFile());
			return radials;
		} catch (IOException e) {
			e.printStackTrace(System.err);
			logger.error(e.getMessage());
			return null;
		}
	}

}
