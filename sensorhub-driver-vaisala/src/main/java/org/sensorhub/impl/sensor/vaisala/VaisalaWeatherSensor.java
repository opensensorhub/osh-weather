package org.sensorhub.impl.sensor.vaisala;

import java.io.IOException;

import net.opengis.sensorml.v20.IdentifierList;
import net.opengis.sensorml.v20.Term;

import org.sensorhub.api.comm.ICommProvider;
import org.sensorhub.api.common.SensorHubException;
import org.sensorhub.impl.sensor.AbstractSensorModule;
import org.sensorhub.impl.sensor.vaisala.VaisalaWeatherConfig;
import org.sensorhub.impl.sensor.vaisala.VaisalaWeatherOutput;
import org.vast.sensorML.SMLFactory;
import org.vast.swe.SWEHelper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class VaisalaWeatherSensor extends AbstractSensorModule<VaisalaWeatherConfig>
{ 
    ICommProvider<?> commProvider;
    BufferedReader dataIn;
    BufferedWriter dataOut;
    VaisalaWeatherOutput dataInterface;
    String modelNumber;
    String serialNumber = null;
    String inputLine = null;
    public final static char CR = (char) 0x0D;
    public final static char LF = (char) 0x0A;
    public final static String CRLF = "" + CR + LF;
    
    /***** Constants used for issuing commands - might use later *****/
//    private static final String queryDeviceAddress = "?";
//    private static final String resetCommand = "XZ";
//    
//    private static final String queryCompositeRecord = "R0";
//    private static final String queryWindRecord = "R1";
//    private static final String queryPTURecord = "R2";
//    private static final String queryPrecipRecord = "R3";
//    private static final String querySupervisorRecord = "R5";
//    
//    private static final String queryCommsSettings = "XU";
//    private static final String querySupervisorSettings = "SU";
//    private static final String queryWindSettings = "WU";
//    private static final String queryPtuSettings = "TU";
//    private static final String queryPrecipSettings = "RU";
    /*****************************************************************/
    
    /******************** Settings Messages **************************/
    private String commsSettingsInit = "A=0,M=P,T=0,C=2,I=0,B=19200";
    private String commsSettingsAutoASCII = "A=0,M=A,I=1";
    
    private String supervisorSettings1 = "R=0000000011100000";
    private String supervisorSettings2 = "I=15,S=N,H=Y";
    private String windSettings1 = "R=0000000011111100";
    private String windSettings2 = "I=1,A=12,U=S,D=0,N=W,F=2";
    private String ptuSettings1 = "R=0000000011110000";
    private String ptuSettings2 = "I=60,P=I,T=F";
    private String precipSettings1 = "R=0000000010110111";
    private String precipSettings2 = "I=60,U=I,S=I,M=T,Z=A";
    /*****************************************************************/
    
    public VaisalaWeatherSensor()
    {        
    }
    
    
    @Override
    public void init() throws SensorHubException
    {
        super.init();
        
        // create main data interface
        dataInterface = new VaisalaWeatherOutput(this);
        addOutput(dataInterface, false);
        dataInterface.init();
        
        // init comm provider
        if (commProvider == null)
        {
            // we need to recreate comm provider here because it can be changed by UI
            if (config.commSettings == null)
                throw new SensorHubException("No communication settings specified");
            commProvider = config.commSettings.getProvider();
            commProvider.start();

            // connect to comm data streams
            try
            {
            	dataIn = new BufferedReader(new InputStreamReader(commProvider.getInputStream()));
                dataOut = new BufferedWriter(new OutputStreamWriter(commProvider.getOutputStream()));
                getLogger().info("Connected to Vaisala data stream");
                
                
                /******** Configure Comm Protocol to ASCII Poll ********/
                dataOut.flush();
                dataOut.write("0XU," + commsSettingsInit + CRLF);
                dataOut.flush();
                try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                inputLine = dataIn.readLine();
                inputLine = null;
                /*******************************************************/
                
                
                /******************** Get Model Number *****************/
                dataOut.write("0XU" + CRLF);
                dataOut.flush();
                try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                inputLine = dataIn.readLine();
                String[] split = inputLine.split(",");
                modelNumber = split[11].replaceAll("N=", "");
                inputLine = null;
                /*******************************************************/
                
                
                /************ Configure Supervisor Settings ************/
                dataOut.write("0SU," + supervisorSettings1 + CRLF);
                dataOut.flush();
                try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                inputLine = dataIn.readLine();
                inputLine = null;
                
                dataOut.write("0SU," + supervisorSettings2 + CRLF);
                dataOut.flush();
                try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                inputLine = dataIn.readLine();
                inputLine = null;
                /*******************************************************/
                
                
                /*************** Configure Wind Settings ***************/
                dataOut.write("0WU," + windSettings1 + CRLF);
                dataOut.flush();
                try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                inputLine = dataIn.readLine();
                inputLine = null;
                
                dataOut.write("0WU," + windSettings2 + CRLF);
                dataOut.flush();
                try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                inputLine = dataIn.readLine();
                inputLine = null;
                /*******************************************************/
                
                
                /*************** Configure PTU Settings ****************/
                dataOut.write("0TU," + ptuSettings1 + CRLF);
                dataOut.flush();
                try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                inputLine = dataIn.readLine();
                inputLine = null;
                
                dataOut.write("0TU," + ptuSettings2 + CRLF);
                dataOut.flush();
                try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                inputLine = dataIn.readLine();
                inputLine = null;
                /*******************************************************/
                
                
                /************* Configure Precip Settings ***************/
                dataOut.write("0RU," + precipSettings1 + CRLF);
                dataOut.flush();
                try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                inputLine = dataIn.readLine();
                inputLine = null;
                
                dataOut.write("0RU," + precipSettings2 + CRLF);
                dataOut.flush();
                try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                inputLine = dataIn.readLine();
                inputLine = null;
                /*******************************************************/
                
                
                /********* Configure Comm Protocol to Auto ASCII *******/
                dataOut.write("0XU," + commsSettingsAutoASCII + CRLF);
                dataOut.flush();
                try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                inputLine = dataIn.readLine();
                inputLine = null;
                /*******************************************************/
                
            }
            catch (IOException e)
            {
                throw new RuntimeException("Error while initializing communications ", e);
            }
        }
        
        // generate identifiers: use serial number from config or first characters of local ID
        serialNumber = config.serialNumber;
        if (serialNumber == null)
        {
            int endIndex = Math.min(config.id.length(), 8);
            serialNumber = config.id.substring(0, endIndex);
        }
        // add unique ID based on serial number
        this.uniqueID = "urn:vaisala:" + modelNumber + ":" + serialNumber;
        this.xmlID = "VAISALA_" + modelNumber + "_" + serialNumber.toUpperCase();
    }

    
    @Override
    protected void updateSensorDescription()
    {
        synchronized (sensorDescription)
        {
        	// set identifiers in SensorML
            SMLFactory smlFac = new SMLFactory();            

            if (!sensorDescription.isSetDescription())
                sensorDescription.setDescription("Vaisala Weather Transmitter " + modelNumber);
          
            IdentifierList identifierList = smlFac.newIdentifierList();
            sensorDescription.addIdentification(identifierList);

            Term term;            
            term = smlFac.newTerm();
            term.setDefinition(SWEHelper.getPropertyUri("Manufacturer"));
            term.setLabel("Manufacturer Name");
            term.setValue("Vaisala");
            identifierList.addIdentifier2(term);
            
            if (modelNumber != null)
            {
                term = smlFac.newTerm();
                term.setDefinition(SWEHelper.getPropertyUri("ModelNumber"));
                term.setLabel("Model Number");
                term.setValue(modelNumber);
                identifierList.addIdentifier2(term);
            }
            
            if (serialNumber != null)
            {
                term = smlFac.newTerm();
                term.setDefinition(SWEHelper.getPropertyUri("SerialNumber"));
                term.setLabel("Serial Number");
                term.setValue(serialNumber);
                identifierList.addIdentifier2(term);
            }
            
            // Long Name
            term = smlFac.newTerm();
            term.setDefinition(SWEHelper.getPropertyUri("LongName"));
            term.setLabel("Long Name");
            term.setValue("Vaisala " + modelNumber + " Weather Transmitter #" + serialNumber);
            identifierList.addIdentifier2(term);

            // Short Name
            term = smlFac.newTerm();
            term.setDefinition(SWEHelper.getPropertyUri("ShortName"));
            term.setLabel("Short Name");
            term.setValue("Vaisala " + modelNumber);
            identifierList.addIdentifier2(term);
        }
    }


    @Override
    public void start() throws SensorHubException
    {
    	dataInterface.start();
    }
    

    @Override
    public void stop() throws SensorHubException
    {
        dataInterface.stop();
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
