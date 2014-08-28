

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.mks.api.CmdRunner;
import com.mks.api.Command;
import com.mks.api.IntegrationPoint;
import com.mks.api.IntegrationPointFactory;
import com.mks.api.Option;
import com.mks.api.Session;
import com.mks.api.response.APIException;
import com.mks.api.response.Field;
import com.mks.api.response.Response;
import com.mks.api.response.WorkItem;
import com.mks.api.response.WorkItemIterator;
import com.mks.api.util.APIVersion;


public class APIUtils {
	static final Logger log = LogManager.getLogger(APIUtils.class.getName());
	private String hostname;
	private int port;

	CmdRunner cr = null;
	Session session = null;

	public APIUtils() {
	
	}
	
	void connectToIntegrity(String userName, String password){
		String VERSION = APIVersion.getAPIReleaseVersion();
		int MAJOR_VERSION = Integer.parseInt(VERSION.substring(0, VERSION.indexOf('.'))); 
		int MINOR_VERSION = Integer.parseInt(VERSION.substring(VERSION.indexOf('.')+1, VERSION.indexOf(' ')));
		
		String hostname = App.config.getString(App.PROPERTY_SERVER_HOSTNAME);
		int port = App.config.getInt(App.PROPERTY_SERVER_PORT);
		
		try {
			IntegrationPointFactory ipf = IntegrationPointFactory.getInstance();
			IntegrationPoint ip = ipf.createLocalIntegrationPoint(MAJOR_VERSION, MINOR_VERSION);
			ip.setAutoStartIntegrityClient(true);
			session = ip.createSession(userName, password);
			cr = session.createCmdRunner();
		
		} catch (APIException apie) {
			log.error("Error occurred during initialization: "	+ apie.getMessage());
			apie.printStackTrace();
			System.exit(1);
		}
	}
	
    public List<String> getSanboxesRegisteredTo(String serverName) {
    	List<String> sandboxes  = new LinkedList<String>();
    	
    	Command cmd = new Command();
		cmd.setApp(Command.SI);
		cmd.setCommandName("sandboxes");

		try {
			Response response = cr.execute(cmd);
			if (response != null) {
				WorkItemIterator wii = response.getWorkItems();
				while (wii.hasNext()) {
					String sandbox = new String();
					boolean addToList = false;
					WorkItem wi = wii.next();
					Iterator<Field> iterator = wi.getFields();
					while (iterator.hasNext()) {
						Field field = iterator.next();
						if (field.getName().equals("sandboxName")){
							sandbox = field.getValueAsString();
						} else if (field.getName().equals("server")){
							String[] hostPort = field.getValueAsString().split(":");
							String hostname = hostPort[0];
							if (hostname.equals(serverName)){
								sandboxes.add(sandbox);
							}
						}
					}
				}
			}
		} catch (APIException e) {
			System.out.println("Error occurred when running command: "
					+ e.getMessage());
			e.printStackTrace();
		}
		
		return sandboxes;
    	
    }
    
    public void dropSanboxes(List<String> sandboxes){
    	
    	for (String sandbox : sandboxes) {
    		Command cmd = new Command();
    		cmd.setApp(Command.SI);
    		cmd.setCommandName("dropsandbox");
    		cmd.addOption(new Option("yes"));
    		cmd.addOption(new Option("delete", "none"));
    		cmd.addSelection(sandbox);
			try {
				Response response = cr.execute(cmd);
			} catch (APIException e) {
				log.error(e);
			}
    	}
    }
    
    public void reImportSandboxes(List<String> sandboxes, String user, String password, String hostname, String port){
    	for (String sandbox : sandboxes) {
    		Command cmd = new Command();
    		cmd.setApp(Command.SI);
    		cmd.setCommandName("importsandbox");
    		cmd.addOption(new Option("yes"));
    		cmd.addOption(new Option("user", user));
    		cmd.addOption(new Option("password", password));
    		cmd.addOption(new Option("hostname", hostname));
    		cmd.addOption(new Option("port", port));
    		cmd.addSelection(sandbox);
			try {
				Response response = cr.execute(cmd);
			} catch (APIException e) {
				e.printStackTrace();
			}
    	}
    }
    
    public static void exitIntegrityClient() throws InterruptedException{
    	try {
			Process p = Runtime.getRuntime().exec("cmd /c im exit --noshutdown");
    		//Process p = Runtime.getRuntime().exec("im exit --noshutdown");
			p.waitFor();
		} catch (IOException e1) {
			log.error(e1);
		}
    }
    
    public void setDefaultServerConnection(String servername) throws InterruptedException{
    	
    	try {
			Process p = Runtime.getRuntime().exec("cmd /c si setprefs --save --command=connect server.hostname=\""+servername+"\"");
			p.waitFor();
		} catch (IOException e1) {
			log.error(e1);
		}
    	 try {
 			Process p = Runtime.getRuntime().exec("cmd /c im setprefs --save --command=connect server.hostname=\""+servername+"\"");
 			p.waitFor();
 		} catch (IOException e1) {
 			log.error(e1);
 		}	
 		
    	 
    	/*
    	Command cmd = new Command();
    	cmd.setApp(Command.SI);
    	cmd.setCommandName("setprefs");
    	cmd.addOption(new Option("save"));
    	cmd.addOption(new Option("command","connect server.hostname=\""+servername+"\""));
    	try {
    		Response response = cr.execute(cmd);
    	} catch (APIException e) {
			log.error(e);
    	}
    	
    	cmd = new Command();
    	cmd.setApp(Command.IM);
    	cmd.setCommandName("setprefs");
    	cmd.addOption(new Option("save"));
    	cmd.addOption(new Option("command","connect server.hostname=\""+servername+"\""));
    	try {
    		Response response = cr.execute(cmd);
    	} catch (APIException e) {
			log.error(e);
    	}
    	*/
    	

    }
    
	public void endSession(){
		try {
			session.release();
		} catch (IOException e) {
			log.error(e);
		} catch (APIException e) {
			log.error(e);
		}
	}
	
}