package com.ptc.integrity.services.utilities.updater;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class App{
	public static final String PROPERTIES_FILE = "rict.properties";
	public static final String PROPERTY_OLD_CLIENT_DIR = "OLD_CLIENT_DIR";
	public static final String PROPERTY_NEW_CLIENT_INSTALLATOR_DIR = "NEW_CLIENT_INSTALL_APP_DIR";
	public static final String PROPERTY_PROCESS_WORK_TIME_LIMIT = "WORKING_TIME_LIMIT_OF_PROCESS";
	public static final String PROPERTY_PROCESS_CHECK_TIME_INTERVAL = "PROCESS_CHECK_INTERVAL";
	public static final String PROPERTY_SERVER_HOSTNAME = "SERVER_HOSTNAME";
	public static final String PROPERTY_SERVER_PORT = "SERVER_PORT";
	public static final String PROPERTY_OLD_SERVER_HOSTNAMES = "OLD_SERVER_HOSTNAMES";
	public static final String PROPERTY_HOTFIXES_DIR = "HOTFIXES_DIR";
	public static final String PROPERTY_SP = "SERVICEPACKS_DIR";
	
	public static String oldClientDir, installAppDir, appDir, userHome, mksDir, SIDistDir, serverHostname, serverPort, newIntegrityClientDir, hotfixesDir, spDir;
	public static List<String> oldServerHostnames;
	public static PropertiesConfiguration clientInstallProp;
	
	public static CompositeConfiguration config;
	static final Logger log = LogManager.getLogger(App.class.getName());

	private static int processWorkTimeLimit = 60*20;
	private static int processCheckInterval = 10;
	
	private static Timestamp mainStart;
	private static APIUtils api;
	
	static {
		log.info("********** Rollout Integrity Client Tool **********");
		System.out.println("********** Rollout Integrity Client Tool **********");
		config = new CompositeConfiguration();
		
		config.addConfiguration(new SystemConfiguration());
		
		File f = null;
		appDir = System.getProperty("user.dir");
		userHome = System.getProperty("user.home");
		mksDir = userHome + File.separator + ".mks";
		SIDistDir = userHome + File.separator + "SIDist";
		
		if (appDir != null) {
			f = new File(appDir+File.separator+PROPERTIES_FILE);
			if (f.canRead()) {
				try {
					config.addConfiguration(new PropertiesConfiguration(f.getAbsoluteFile()));
					log.debug("Added configuration from: " + f.getAbsoluteFile());
				} catch (ConfigurationException e) {
					e.printStackTrace();
				}

			} else {
				log.error("Configuration file: "+ f.getAbsoluteFile()+ " doesn't exist");
			}
		}
		
		processWorkTimeLimit = config.getInt(PROPERTY_PROCESS_WORK_TIME_LIMIT, 60*20);
		processCheckInterval = config.getInt(PROPERTY_PROCESS_CHECK_TIME_INTERVAL, 4);
		oldClientDir = config.getString(PROPERTY_OLD_CLIENT_DIR);
		installAppDir = config.getString(PROPERTY_NEW_CLIENT_INSTALLATOR_DIR);
		serverHostname = config.getString(PROPERTY_SERVER_HOSTNAME);
		serverPort = config.getString(PROPERTY_SERVER_PORT);
		List<Object> objects = config.getList(PROPERTY_OLD_SERVER_HOSTNAMES);
		oldServerHostnames = new LinkedList<String>();
		for (Object o : objects) {
			oldServerHostnames.add((String)o);
		}
		hotfixesDir = config.getString(PROPERTY_HOTFIXES_DIR,installAppDir + File.separator+"hotfixes");
		spDir = config.getString(PROPERTY_SP, installAppDir + File.separator+"sp");
		
		api = new APIUtils();
	
	}
	
    public static void main( String[] args ) throws InterruptedException{    
   	
        mainStart = new Timestamp(new java.util.Date().getTime());
        System.out.println("Please enter your credentials for " + serverHostname);
        String userName = readFromConsole("User Name:");
    	String password = readFromConsole("Password:");
    	
    	
        log.info("Checking rict.propeties file");
    	if( !checkProperties()) {
    		abortApp();
    	}

    	
    	log.info("Exiting Integrity client");
    	if (WindowsUtils.ifProcessRunning("IntegrityClient.exe")) {
    		APIUtils.exitIntegrityClient();

    		if (WindowsUtils.ifProcessRunning("IntegrityClient.exe")) {
    			WindowsUtils.killProcess("IntegrityClient.exe");
    		}

    	}
    	
    	Thread.sleep(2000);
		

    	backUpMksDirs();
    	uninstallIntegrityClient();
    	installNewIntegrityClient();
    	//installServicePacks();
    	installHotfixes();
    	
    	// update viewsets
    	System.out.println("Updating Viewsets...");
    	replaceViewsetsSettings(new File(mksDir + File.separator + "viewset" +File.separator+"user") , serverHostname, "7001");
    	

    	// update sandboxes
    	System.out.println("Connecting Integrity...");
    	api.connectToIntegrity(userName, password);
    	System.out.println("Reimporting sandboxes...");
    	for ( String oldHostname : oldServerHostnames) {
    		List<String> sandboxes = api.getSanboxesRegisteredTo(oldHostname);
        	for (String sandbox : sandboxes) {
        		log.info("Found Sandbox: " + sandbox + " on " +oldHostname);
        	}
        	api.dropSanboxes(sandboxes);
        	api.reImportSandboxes(sandboxes, userName, password, serverHostname, "7001");
    	}
    	
    	try {
			api.setDefaultServerConnection(serverHostname);
		} catch (InterruptedException e) {
			log.error(e);
			abortApp();
		}
    	
    	api.endSession();
    	
    	exitAppSuccessfull();
    }
    
    private static String readFromConsole(String message){
    	 BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
         System.out.print(message);
         try {
			return br.readLine();
		} catch (IOException e) {
			log.error(e);
		}
		return null;

    }
     
    public static void exitAppSuccessfull(){

    	System.out.println("Total time duration: " + Utils.timeDuration(mainStart)+"\nAll roll-out steps run successfully. Press enter to exit RICT");
    	log.info("Total time duration: " + Utils.timeDuration(mainStart));
    	try {
			System.in.read();
			System.exit(0);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    private static void installServicePacks() {
    	Timestamp start;
	    start = new Timestamp(new java.util.Date().getTime());
	    System.out.println("[" + start + "] Installing servicepacks...");
	    String pathToPatchClient = newIntegrityClientDir +File.separator +"bin"+File.separator+"PatchClient.exe";

	    
	    File patchClient = new File(pathToPatchClient);
	    if (!patchClient.exists() ){
	    	log.error("PatchClient.exe not found under: "+pathToPatchClient );
	    	abortApp();
	    	return;
	    }
	   
	    File spFolder = new File(spDir);
	    File[] spS = spFolder.listFiles();

	    for ( File sp : spS) {
	    	try {
	    		log.info("Installing service pack: "+sp.getName());
				WindowsUtils.startProcess(pathToPatchClient, "\""+sp.getAbsolutePath()+"\"", processWorkTimeLimit, processCheckInterval);

			} catch (IOException e) {
				log.error(e);
				abortApp();
			}	    	
	    }
    }
    
    public static void installHotfixes(){
    	Timestamp start;
	    start = new Timestamp(new java.util.Date().getTime());
	    System.out.println("[" + start + "] Installing hotfixes...");
	    String pathToPatchClient = newIntegrityClientDir +File.separator +"bin"+File.separator+"PatchClient.exe";

	    
	    File patchClient = new File(pathToPatchClient);
	    if (!patchClient.exists() ){
	    	log.error("PatchClient.exe not found under: "+pathToPatchClient );
	    	abortApp();
	    	return;
	    }
	   
	    File hotfixesFolder = new File(hotfixesDir);
	    if (!hotfixesFolder.exists()) {
		    System.out.println("[" + new Timestamp(new java.util.Date().getTime()) + "] Hotfixes directory does't exist in ["+hotfixesDir+"], installing hotfixes ABORTED...");
		    log.warn("[" + new Timestamp(new java.util.Date().getTime()) + "] Hotfixes directory does't exist in ["+hotfixesDir+"], installing hotfixes ABORTED...");
		    return;
	    }
	    
	    File[] hotfixes = hotfixesFolder.listFiles();

	    for ( File hotfix : hotfixes) {
	    	try {
	    		log.info("Installing hotfix: " + hotfix.getName());
				WindowsUtils.startProcess(pathToPatchClient, "\""+hotfix.getAbsolutePath()+"\"", processWorkTimeLimit, processCheckInterval);
			} catch (IOException e) {
				log.error(e);
				abortApp();
			}	    	
	    }
	    
	
	    	
    }
    
    public static void abortApp(){
    	System.out.println("Total time duration: " + Utils.timeDuration(mainStart)+"\nError occurred. Please check log file. Press enter to exit RICT");
    	log.info("Total time duration: " + Utils.timeDuration(mainStart));
    	try {
			System.in.read();
			System.exit(-1);
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
    
    private static void uninstallIntegrityClient() {
    	// UNINSTALL OLD CLIENT
    	Timestamp start, stop;
		try {
	    	start = new Timestamp(new java.util.Date().getTime());
	    	System.out.println("[" + start + "] Uninstalling old Integrity Client...");
	    	
	    	String oldClientDir = config.getString(PROPERTY_OLD_CLIENT_DIR) + File.separator + "uninstall" + File.separator + "IntegrityClientUninstall.exe";
	    	String pathToProcess = oldClientDir;
			File f = new File(pathToProcess);
			if (!f.exists()){
				log.error(f.getAbsolutePath() + " doesn't exist, uninstall abort");
			}
			
			if (oldClientDir.contains("\\")) {
				oldClientDir = f.getName();
			}
			
		    int numberOfProcesses = WindowsUtils.numberOfRunningProcesses(oldClientDir);
		    if (numberOfProcesses > 0 ) {
		    	if (numberOfProcesses == 1){
		    		log.warn("find already running process "+ oldClientDir + " process will be terminated");
		    	} else {
		       		log.warn("find already running "+numberOfProcesses+" \""+ oldClientDir + "\" processes, all of them will be terminated");
		    	}
		       	WindowsUtils.killProcess(oldClientDir);
		    }    		
		    WindowsUtils.startProcess(pathToProcess, "-i silent", processWorkTimeLimit , processCheckInterval);
	    	stop = new Timestamp(new java.util.Date().getTime());
			if (WindowsUtils.checkExitCode() == 0){
				String message = "[" + stop + "] Old Integrity Client was successful uninstalled. Time duration: " +Utils.timeDuration(start);
				WindowsUtils.deleteFolder(new File(config.getString(PROPERTY_OLD_CLIENT_DIR)));
				System.out.println(message);
				log.info(message);
				
			}else {
				String message = "[" + stop + "] Error occurred while uninstalling Integrity Client. Time duration: " +Utils.timeDuration(start);
				System.out.println(message);
				log.error(message);
				abortApp(); //TODO uncomment
			}
		} catch (IOException e) {
			log.error(e);
			log.error("Please check if \""+PROPERTY_OLD_CLIENT_DIR+"\" property is properly set and points to OLD IntegrityClient dir");
		}
		
		File oldClientDir = new File(config.getString(PROPERTY_OLD_CLIENT_DIR));
		if (oldClientDir!= null && oldClientDir.isDirectory()) {
			if (WindowsUtils.deleteFolder(oldClientDir)) {
				log.info("Old client directory ["+oldClientDir.getAbsolutePath()+"] was removed");
			} else {
				log.warn("Cannot delete Old Client directory ["+oldClientDir.getAbsolutePath()+"]" );
			}
		}
    }
    
    private static void installNewIntegrityClient() {
    	// INSTALL NEW CLIENT
    	Timestamp start, stop;
    	try {
        	start = new Timestamp(new java.util.Date().getTime());
        	System.out.println("[" + start + "] Installing new Integrity Client...");
        	String integrityInstallator = config.getString(PROPERTY_NEW_CLIENT_INSTALLATOR_DIR) + File.separator + "mksclient.exe";
        	
        	String propertiesFileName= "mksclient.properties";   
        	String pathToProcess = integrityInstallator;
        	
    		if (integrityInstallator.contains("\\")) {
    			File f = new File(pathToProcess);
    			integrityInstallator = f.getName();
    		}
    		
    	    int numberOfProcesses = WindowsUtils.numberOfRunningProcesses(integrityInstallator);
    	    if (numberOfProcesses > 0 ) {
    	    	if (numberOfProcesses == 1){
    	    		log.warn("find already running process "+ integrityInstallator + " process will be terminated");
    	    	} else {
    	       		log.warn("find already running "+numberOfProcesses+" \""+ integrityInstallator + "\" processes, all of them will be terminated");
    	       		
    	    	}
    	       	WindowsUtils.killProcess(integrityInstallator);
    	    }    			
    	    WindowsUtils.startProcess(pathToProcess, "-f "+propertiesFileName, processWorkTimeLimit , processCheckInterval);      
        	stop = new Timestamp(new java.util.Date().getTime());
    		if (WindowsUtils.checkExitCode() == 0){
    			File newIntegrityPatchClient= new File(newIntegrityClientDir + File.separator + "bin" + File.separator+ "PatchClient.exe");
    			if(newIntegrityPatchClient.exists()){
	    			String message = "[" + stop + "] New Integrity Client was successful installed. Time duration: " +Utils.timeDuration(start);
	    			System.out.println(message);
	    			log.info(message);
    			} else {
        			String message = "[" + stop + "] Error occurred while installing Integrity Client. Time duration: " +Utils.timeDuration(start);
        			System.out.println(message);
        			log.error(message);
        			return;
    			}
    			
    		}else {
    			String message = "[" + stop + "] Error occurred while installing Integrity Client. Time duration: " +Utils.timeDuration(start);
    			System.out.println(message);
    			log.error(message);
    			return;
    		}
		} catch (IOException e) {
			log.error(e);
			log.error("Please check if \""+PROPERTY_NEW_CLIENT_INSTALLATOR_DIR+"\" property is properly set");
		}
    }
    
    private static void backUpMksDirs(){
    	File mks = new File(mksDir);
    	File sidist = new File(SIDistDir);
    	String backupDir = "mks_backups" + File.separator+ new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());

    	File mksBackup = new File(appDir+File.separator+backupDir+ File.separator+".mks");
    	File SIDistBackup = new File(appDir+File.separator+backupDir+File.separator+"SIDist");
    	
    	System.out.println("Backuping .mks, SIDist folders to " + appDir+File.separator+backupDir);
    	log.info("Backuping .mks, SIDist folders to " + appDir+File.separator+backupDir);
    	mksBackup.mkdirs();
    	SIDistBackup.mkdirs();
    	
    	if(mks.isDirectory()){
    		try {
				Utils.copyFolder(mks, mksBackup);
				log.info("Directory " +mks+" was copied to "+mksBackup);
			} catch (IOException e) {
				log.error(e);
				abortApp();
				e.printStackTrace();
			}
    	} else {
    		log.error("Cannot find "+mks.getAbsolutePath() + " directory");
    	}
    	
    	if(sidist.isDirectory()){
    		try {
				Utils.copyFolder(sidist, SIDistBackup);
				log.info("Directory " +sidist+" was copied to "+SIDistBackup);
			} catch (IOException e) {
				log.error(e);
				abortApp();
				e.printStackTrace();
			}
    	} else {
    		log.error("Cannot find "+sidist.getAbsolutePath() + " directory");
    	}
    	
    }
    
    private static void replaceViewsetsSettings(File dir, String hostname, String port){
    	
	   	 //String p1 = "<Setting name=\"server.port\">";
	   	 String p3 = "<Setting name=\"server.hostname\">";
	   	 String p2 = "</Setting>";
	   	 
	   	 String hostSetting = p3 + hostname + p2;
	   	// String portSetting = p1 + port +p2;
	   	 
	   	// String portRegex = Pattern.quote(p1) + "(.*?)"  + Pattern.quote(p2);
	   	 String hostRegex = Pattern.quote(p3) + "(.*?)"  + Pattern.quote(p2);
	   	 
	   	 Pattern hostPattern = Pattern.compile(hostRegex);
	   	// Pattern portPatter = Pattern.compile(portRegex);


    	if (!dir.isDirectory()) {
    		log.error("Error while replacing hostnames and ports in viewsets directory. The directory " + dir.getAbsolutePath() + " doesn't exists");
    		return;
    	}
    	
    	try {
		    	File[] arrayOfViewsets = dir.listFiles();
		    	for (File viewset : arrayOfViewsets) {
		    		String tmpFile = dir+File.separator+"tmp_"+viewset.getName();
		    		BufferedWriter tmpBw = new BufferedWriter(new FileWriter(tmpFile));
		    		
		    		try {
		    			Scanner sc = new Scanner(viewset);
		    			String line; 
		    			while(sc.hasNextLine()) {
		    				line = sc.nextLine();
			    		   	Matcher hostMatcher = hostPattern.matcher(line);
			    		   	if (hostMatcher.find()) {
			    		   		String oldHostname = hostMatcher.group(1);
			    		   		if (oldServerHostnames.contains(oldHostname)) {
			    		   			log.info("Replacing hostname in file: " + viewset.getName() + "[OLD Value: "+line.trim() + " -> NEW Value: " + hostSetting+" ]");
			    		   			line = hostSetting;
			    		   		}
			    		   	}
		    				tmpBw.write(line+"\n");
		    			}
		    			sc.close();
		    			tmpBw.flush();
		    			tmpBw.close();
		    		} catch (FileNotFoundException e){
		    			log.error(e);
		    		} 
		    		
		    		viewset.delete();
		    		File newFile = new File(tmpFile);
		    		newFile.renameTo(viewset);
		    		/*
		    		
		    		FileInputStream fs = new FileInputStream(viewset.getAbsoluteFile());
		    		BufferedReader br = new BufferedReader(new InputStreamReader(fs));
		    		FileWriter writer = new FileWriter(viewset.getAbsoluteFile());
		    		
		    		String line = br.readLine();
		    		while (line != null) {
		    		   	Matcher hostMatcher = hostPattern.matcher(line);
		    		   	if (hostMatcher.find()) {
		    		   		String oldHostname = hostMatcher.group(1);
		    		   		if (oldServerHostnames.contains(oldHostname)) {
		    		   			//line = line.replaceAll(hostRegex, hostSetting)
		    		   			log.info("Replacing hostname in file: " + viewset.getName() + "[OLD Value: "+line + " -> NEW Value: " + hostSetting);
		    		   			line = hostSetting;
		    		   		}
		    		   	}
		    			//line = line.replaceAll(portRegex, portSetting);
		    			// line = line.replaceAll(hostRegex, hostSetting);
		    			writer.write(line);
		    			writer.write(System.getProperty("line.separator"));
		    			line = br.readLine();
		    		}
		    		writer.flush();
		    		writer.close();	
		    		br.close();
		    		fs.close();
		    	}   
		    	
		    	 		*/
		    	}
    	} catch (IOException e) {
    		log.error(e);
    	}

    }
    
    private static boolean checkProperties(){
    	File oldClientFolder = new File(oldClientDir);
    	File installatorFolder = new File(installAppDir);
    	//File hotfixesFolder = new File(hotfixesDir);
		File mksclient = new File(installatorFolder + File.separator + "mksclient.exe");
		File mksprop = new File(installatorFolder + File.separator + "mksclient.properties");
		
    	if (oldClientFolder.isDirectory()){
    		log.info("Found old Client app directory [" + oldClientFolder.getAbsolutePath()+"]");
    	} else {
    		log.error("Old Client app directory not found under [" + oldClientFolder.getAbsolutePath() +"]");
    		return false;
    	}
    	
    	/*
    	if (hotfixesFolder.isDirectory()){
    		log.info("Found hotfixes directory [" + oldClientFolder.getAbsolutePath()+"]");
    	} else {
    		log.error("Hotfixes directory not found under [" + hotfixesFolder.getAbsolutePath() +"]");
    		return false;
    	}
    	*/
    	if (installatorFolder.isDirectory()) {
    		if (mksclient.exists()) {
    			log.info("Found mksclinet.exe");
    		} else {
    			log.error("Can't find mksclient.exe in the specified folder ["+mksclient.getAbsolutePath()+"]");
    			return false;
    		}
    		if (mksprop.exists()) {
    			log.info("Found mksclient.properties");
    			try {
					clientInstallProp = new PropertiesConfiguration(mksprop);
					newIntegrityClientDir = clientInstallProp.getString("USER_INSTALL_DIR");
				} catch (ConfigurationException e) {
					e.printStackTrace();
				}
    		} else {
    			log.error("Can't find mksclient.properties in the specified folder ["+mksprop.getAbsolutePath()+"]");
    		}
    	} else {
    		log.error("Can't find the specified folder ["+installatorFolder.getAbsolutePath()+"] Please check property " + PROPERTY_NEW_CLIENT_INSTALLATOR_DIR);
    		return false;
    	}
    	return true;
    }
}
